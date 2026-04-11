/*
 * ComponentRepository.kt.
 *
 * © 2026 Yap Studios LLC
 */
package com.yapstudios.metabind

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.cache.normalized.FetchPolicy
import com.apollographql.apollo.cache.normalized.api.MemoryCacheFactory
import com.apollographql.apollo.cache.normalized.apolloStore
import com.apollographql.apollo.cache.normalized.fetchPolicy
import com.apollographql.apollo.cache.normalized.normalizedCache
import com.apollographql.apollo.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.apollo.cache.normalized.watch
import com.apollographql.apollo.network.ws.GraphQLWsProtocol
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.reflect.TypeToken
import com.yapstudios.metabind.fragment.ContentFields
import com.yapstudios.metabind.fragment.PreviewResultFields
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ComponentRepository(private val apolloClient: ApolloClient, private val gson: Gson) {

    companion object {
        private fun normalizedCacheFactory() =
            MemoryCacheFactory(10 * 1024 * 1024).chain(SqlNormalizedCacheFactory("apollo.db"))

        private fun gson() =
            GsonBuilder()
                .registerTypeAdapter(
                    LocalDateTime::class.java,
                    JsonDeserializer { json, type, jsonDeserializationContext ->
                        LocalDateTime.parse(
                            json.asJsonPrimitive.asString,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                        )
                    })
                .create()

        private fun apolloClient(context: Context): ApolloClient {
            val bundle = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            ).metaData
            val url = bundle.getString("com.yapstudios.metabind.url")
            val wsUrl = bundle.getString("com.yapstudios.metabind.ws.url")
            if (url == null || wsUrl == null) {
                throw RuntimeException("com.yapstudios.metabind.url and com.yapstudios.metabind.ws.url are not set in AndroidManifest.xml")
            }
            return ApolloClient.Builder()
                .serverUrl(url)
                .webSocketServerUrl(wsUrl)
                .wsProtocol(GraphQLWsProtocol.Factory())
                .webSocketReopenWhen { t, attempt ->
                    Log.w(
                        "MetabindApolloClient",
                        "WebSocket got disconnected, reopening after a delay",
                        t
                    )
                    delay(attempt * 1000)
                    true
                }
                .normalizedCache(
                    normalizedCacheFactory = normalizedCacheFactory(),
                )
                .build()
        }

        fun get(context: Context): ComponentRepository =
            ComponentRepository(apolloClient(context), gson())
    }

    suspend fun getPreviewByToken(
        token: String,
        refresh: Boolean = false,
    ): Result<PreviewComponent> {
        val preview =
            apolloClient.query(PreviewQuery(token))
                .fetchPolicy(if (refresh) FetchPolicy.NetworkFirst else FetchPolicy.CacheFirst)
                .execute()

        preview.errors?.let { errors ->
            return Result.failure(
                RuntimeException(
                    errors.firstOrNull()?.message ?: "Unknown Error"
                )
            )
        }

        preview.data?.preview?.previewResultFields?.let {
            val component = createPreviewComponent(token, it)
            return if (component != null) {
                Result.success(component)
            } else {
                Result.failure(RuntimeException("Error rendering the component"))
            }
        }
        return Result.failure(RuntimeException("Unknown Error"))
    }

    fun watchPreviewByToken(token: String): Flow<Result<PreviewComponent>> {
        return apolloClient.query(PreviewQuery(token))
            .fetchPolicy(FetchPolicy.CacheAndNetwork)
            .watch()
            .mapNotNull { response ->
                response.exception?.let {
                    return@mapNotNull Result.failure(
                        it
                    )
                }
                response.errors?.firstOrNull()?.let { error ->
                    return@mapNotNull Result.failure(
                        RuntimeException(error.message)
                    )
                }
                response.data?.preview?.previewResultFields?.let {
                    val component = createPreviewComponent(token, it)
                    if (component != null) {
                        Result.success(component)
                    } else {
                        Result.failure(RuntimeException("Error rendering the component"))
                    }
                }
            }
    }

    suspend fun getContentByToken(token: String, refresh: Boolean = false): PreviewComponent? {
        val preview = apolloClient.query(ContentQuery(token))
            .fetchPolicy(if (refresh) FetchPolicy.NetworkFirst else FetchPolicy.CacheFirst)
            .execute()
        preview.data?.content?.contentFields?.let {
            return createComponent(it)
        }
        return null
    }

    private suspend fun createComponent(
        contentFields: ContentFields,
    ): PreviewComponent {
        val componentId = contentFields.id
        val componentName = contentFields.name
        val content = contentFields.compiled

        val dependencies: Map<String, String> = getContentPackage(
            packageId = contentFields.resolvedRef.resolvedPackageRefFields.`package`
        )

        return PreviewComponent(
            id = componentId,
            name = componentName,
            content = content,
            dependencies = dependencies
        )
    }

    suspend fun getContentPackage(packageId: String): Map<String, String> {
        val isDraft = packageId.startsWith("draft:")
        val fetchPolicy = if (isDraft) FetchPolicy.NetworkFirst else FetchPolicy.CacheFirst
        val resolvedPackage =
            apolloClient.query(ResolvedPackageDataQuery(packageId))
                .fetchPolicy(fetchPolicy)
                .execute()
        val componentsJson =
            resolvedPackage.data?.resolvedPackageData?.resolvedPackageDataFields?.components

        val type = object : TypeToken<Map<String, String>>() {}.type
        val componentList: Map<String, String> = gson.fromJson(componentsJson, type)
        return componentList
    }

    suspend fun getPreviewPackageByToken(token: String, packageId: String): Map<String, String> {
        val isDraft = packageId.startsWith("draft:")
        val fetchPolicy = if (isDraft) FetchPolicy.NetworkFirst else FetchPolicy.CacheFirst
        val resolvedPackage =
            apolloClient.query(PreviewResolvedPackageDataQuery(token, packageId))
                .fetchPolicy(fetchPolicy)
                .execute()
        val componentsJson =
            resolvedPackage.data?.previewResolvedPackageData?.resolvedPackageDataFields?.components

        val type = object : TypeToken<Map<String, String>>() {}.type
        return if (componentsJson != null) {
            val componentList: Map<String, String> = gson.fromJson(componentsJson, type)
            componentList
        } else {
            emptyMap()
        }
    }

    fun subscribeToPreviewByToken(token: String): Flow<PreviewComponent?> {
        return apolloClient.subscription(PreviewUpdatedSubscription(token)).toFlow().map {
            var result: PreviewComponent? = null
            it.data?.previewUpdated?.preview?.let { subscriptionPreview ->
                subscriptionPreview.previewResultFields.let { previewResultFields ->
                    result = createPreviewComponent(token, previewResultFields)
                }
                // Write updated data into the Apollo cache so that
                // re-entering the screen with CacheFirst returns fresh data
                val queryData = PreviewQuery.Data(
                    preview = PreviewQuery.Preview(
                        __typename = subscriptionPreview.__typename,
                        previewResultFields = subscriptionPreview.previewResultFields,
                    )
                )
                apolloClient.apolloStore.writeOperation(PreviewQuery(token), queryData)
            }
            result
        }
    }

    private suspend fun createPreviewComponent(
        token: String,
        previewResultFields: PreviewResultFields,
    ): PreviewComponent? {
        var componentId: String? = null
        var componentName: String? = null
        var content: String? = null
        var packageId: String? = null

        previewResultFields.onComponentPreview?.let { onComponentPreview ->
            val componentPreviewFields = onComponentPreview.componentPreviewFields
            componentId = componentPreviewFields.componentId
            componentName = componentPreviewFields.componentName
            content = componentPreviewFields.component?.componentFields?.compiled
            packageId = componentPreviewFields.resolvedRef.resolvedPackageRefFields.`package`
        }

        previewResultFields.onContentPreview?.let { onContentPreview ->
            val contentPreviewFields = onContentPreview.contentPreviewFields
            componentId = contentPreviewFields.contentId
            componentName = contentPreviewFields.contentName
            content = contentPreviewFields.content?.contentFields?.compiled
            packageId = contentPreviewFields.resolvedRef.resolvedPackageRefFields.`package`
        }

        val dependencies = packageId?.let { packageId ->
            val packageComponents: Map<String, String> = getPreviewPackageByToken(
                token = token,
                packageId = packageId
            )
            packageComponents
        } ?: emptyMap()

        return if (componentId != null && componentName != null && content != null) {
            PreviewComponent(
                id = componentId,
                name = componentName,
                content = content,
                dependencies = dependencies
            )
        } else {
            null
        }
    }
}