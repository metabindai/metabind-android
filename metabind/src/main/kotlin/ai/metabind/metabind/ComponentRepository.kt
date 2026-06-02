/*
 * ComponentRepository.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.metabind

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
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
import ai.metabind.metabind.fragment.AssetFields
import ai.metabind.metabind.fragment.ComponentFields
import ai.metabind.metabind.fragment.ContentFields
import ai.metabind.metabind.fragment.ContentTypeFields
import ai.metabind.metabind.fragment.PackageFields
import ai.metabind.metabind.fragment.PreviewResultFields
import ai.metabind.metabind.fragment.SavedSearchFields
import ai.metabind.metabind.fragment.TagFields
import ai.metabind.metabind.type.AssetFilter
import ai.metabind.metabind.type.ContentFilter
import ai.metabind.metabind.type.SavedSearchType
import ai.metabind.metabind.type.SortCriteria
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
            val url = bundle.getString("ai.metabind.metabind.url")
            val wsUrl = bundle.getString("ai.metabind.metabind.ws.url")
            if (url == null || wsUrl == null) {
                throw RuntimeException("ai.metabind.metabind.url and ai.metabind.metabind.ws.url are not set in AndroidManifest.xml")
            }
            val apiKey = bundle.getString("ai.metabind.metabind.api.key") ?: ""
            val organizationId = bundle.getString("ai.metabind.metabind.organization.id") ?: ""
            val projectId = bundle.getString("ai.metabind.metabind.project.id") ?: ""
            // Matches the metabind-apple SDK: HTTP requests carry an
            // "x-api-key: <org>:<project>:<apiKey>" header, and the WebSocket
            // connection sends the same credentials in its init payload.
            val apiKeyHeader = "$organizationId:$projectId:$apiKey"
            return ApolloClient.Builder()
                .serverUrl(url)
                .addHttpHeader("x-api-key", apiKeyHeader)
                .webSocketServerUrl(wsUrl)
                .wsProtocol(
                    GraphQLWsProtocol.Factory(
                        connectionPayload = {
                            mapOf(
                                "apiKey" to apiKey,
                                "organizationId" to organizationId,
                                "projectId" to projectId,
                            )
                        }
                    )
                )
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
        var isContent = false

        previewResultFields.onComponentPreview?.let { onComponentPreview ->
            val componentPreviewFields = onComponentPreview.componentPreviewFields
            componentId = componentPreviewFields.componentId
            componentName = componentPreviewFields.componentName
            content = componentPreviewFields.component?.componentFields?.compiled
            packageId = componentPreviewFields.resolvedRef.resolvedPackageRefFields.`package`
            isContent = false
        }

        previewResultFields.onContentPreview?.let { onContentPreview ->
            val contentPreviewFields = onContentPreview.contentPreviewFields
            componentId = contentPreviewFields.contentId
            componentName = contentPreviewFields.contentName
            content = contentPreviewFields.content?.contentFields?.compiled
            packageId = contentPreviewFields.resolvedRef.resolvedPackageRefFields.`package`
            isContent = true
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
                dependencies = dependencies,
                isContent = isContent
            )
        } else {
            null
        }
    }

    // region CMS browse queries
    //
    // Read-only access to the published CMS catalog (contents, components,
    // content types, assets, tags, packages, and saved searches), mirroring the
    // operations exposed by the metabind-apple SDK. Results are returned as the
    // generated Apollo fragment types, wrapped in [Page] for the paginated list
    // queries.

    suspend fun getContents(
        typeId: String? = null,
        tags: List<String>? = null,
        locale: String? = null,
        search: String? = null,
        filter: ContentFilter? = null,
        sort: List<SortCriteria>? = null,
        cursor: String? = null,
        limit: Int? = null,
        refresh: Boolean = false,
    ): Page<ContentFields> {
        val contents = apolloClient.query(
            ContentsQuery(
                typeId = Optional.presentIfNotNull(typeId),
                tags = Optional.presentIfNotNull(tags),
                locale = Optional.presentIfNotNull(locale),
                search = Optional.presentIfNotNull(search),
                filter = Optional.presentIfNotNull(filter),
                sort = Optional.presentIfNotNull(sort),
                cursor = Optional.presentIfNotNull(cursor),
                limit = Optional.presentIfNotNull(limit),
            )
        ).fetchPolicy(fetchPolicy(refresh)).execute().dataOrThrow().contents
        return Page(
            data = contents.data.map { it.contentFields },
            cursor = contents.pagination.cursor,
            hasMore = contents.pagination.hasMore,
            limit = contents.pagination.limit,
        )
    }

    suspend fun getComponent(id: String, refresh: Boolean = false): ComponentFields? =
        apolloClient.query(ComponentQuery(id))
            .fetchPolicy(fetchPolicy(refresh))
            .execute()
            .dataOrThrow()
            .component
            ?.componentFields

    suspend fun getComponents(
        search: String? = null,
        cursor: String? = null,
        limit: Int? = null,
        refresh: Boolean = false,
    ): Page<ComponentFields> {
        val components = apolloClient.query(
            ComponentsQuery(
                search = Optional.presentIfNotNull(search),
                cursor = Optional.presentIfNotNull(cursor),
                limit = Optional.presentIfNotNull(limit),
            )
        ).fetchPolicy(fetchPolicy(refresh)).execute().dataOrThrow().components
        return Page(
            data = components.data.map { it.componentFields },
            cursor = components.pagination.cursor,
            hasMore = components.pagination.hasMore,
            limit = components.pagination.limit,
        )
    }

    suspend fun getContentType(id: String, refresh: Boolean = false): ContentTypeFields? =
        apolloClient.query(ContentTypeQuery(id))
            .fetchPolicy(fetchPolicy(refresh))
            .execute()
            .dataOrThrow()
            .contentType
            ?.contentTypeFields

    suspend fun getContentTypes(
        search: String? = null,
        cursor: String? = null,
        limit: Int? = null,
        refresh: Boolean = false,
    ): Page<ContentTypeFields> {
        val contentTypes = apolloClient.query(
            ContentTypesQuery(
                search = Optional.presentIfNotNull(search),
                cursor = Optional.presentIfNotNull(cursor),
                limit = Optional.presentIfNotNull(limit),
            )
        ).fetchPolicy(fetchPolicy(refresh)).execute().dataOrThrow().contentTypes
        return Page(
            data = contentTypes.data.map { it.contentTypeFields },
            cursor = contentTypes.pagination.cursor,
            hasMore = contentTypes.pagination.hasMore,
            limit = contentTypes.pagination.limit,
        )
    }

    suspend fun getAsset(id: String, refresh: Boolean = false): AssetFields? =
        apolloClient.query(AssetQuery(id))
            .fetchPolicy(fetchPolicy(refresh))
            .execute()
            .dataOrThrow()
            .asset
            ?.assetFields

    suspend fun getAssets(
        type: String? = null,
        tags: List<String>? = null,
        search: String? = null,
        filter: AssetFilter? = null,
        sort: List<SortCriteria>? = null,
        cursor: String? = null,
        limit: Int? = null,
        refresh: Boolean = false,
    ): Page<AssetFields> {
        val assets = apolloClient.query(
            AssetsQuery(
                type = Optional.presentIfNotNull(type),
                tags = Optional.presentIfNotNull(tags),
                search = Optional.presentIfNotNull(search),
                filter = Optional.presentIfNotNull(filter),
                sort = Optional.presentIfNotNull(sort),
                cursor = Optional.presentIfNotNull(cursor),
                limit = Optional.presentIfNotNull(limit),
            )
        ).fetchPolicy(fetchPolicy(refresh)).execute().dataOrThrow().assets
        return Page(
            data = assets.data.map { it.assetFields },
            cursor = assets.pagination.cursor,
            hasMore = assets.pagination.hasMore,
            limit = assets.pagination.limit,
        )
    }

    suspend fun getTag(id: String, refresh: Boolean = false): TagFields? =
        apolloClient.query(TagQuery(id))
            .fetchPolicy(fetchPolicy(refresh))
            .execute()
            .dataOrThrow()
            .tag
            ?.tagFields

    suspend fun getTags(
        search: String? = null,
        cursor: String? = null,
        limit: Int? = null,
        refresh: Boolean = false,
    ): Page<TagFields> {
        val tags = apolloClient.query(
            TagsQuery(
                search = Optional.presentIfNotNull(search),
                cursor = Optional.presentIfNotNull(cursor),
                limit = Optional.presentIfNotNull(limit),
            )
        ).fetchPolicy(fetchPolicy(refresh)).execute().dataOrThrow().tags
        return Page(
            data = tags.data.map { it.tagFields },
            cursor = tags.pagination.cursor,
            hasMore = tags.pagination.hasMore,
            limit = tags.pagination.limit,
        )
    }

    suspend fun getPackage(version: String, refresh: Boolean = false): PackageFields? =
        apolloClient.query(PackageQuery(version))
            .fetchPolicy(fetchPolicy(refresh))
            .execute()
            .dataOrThrow()
            .`package`
            ?.packageFields

    suspend fun getPackages(
        cursor: String? = null,
        limit: Int? = null,
        refresh: Boolean = false,
    ): Page<PackageFields> {
        val packages = apolloClient.query(
            PackagesQuery(
                cursor = Optional.presentIfNotNull(cursor),
                limit = Optional.presentIfNotNull(limit),
            )
        ).fetchPolicy(fetchPolicy(refresh)).execute().dataOrThrow().packages
        return Page(
            data = packages.data.map { it.packageFields },
            cursor = packages.pagination.cursor,
            hasMore = packages.pagination.hasMore,
            limit = packages.pagination.limit,
        )
    }

    suspend fun getSavedSearch(id: String, refresh: Boolean = false): SavedSearchFields? =
        apolloClient.query(SavedSearchQuery(id))
            .fetchPolicy(fetchPolicy(refresh))
            .execute()
            .dataOrThrow()
            .savedSearch
            ?.savedSearchFields

    suspend fun getSavedSearches(
        type: SavedSearchType? = null,
        cursor: String? = null,
        limit: Int? = null,
        refresh: Boolean = false,
    ): Page<SavedSearchFields> {
        val savedSearches = apolloClient.query(
            SavedSearchesQuery(
                type = Optional.presentIfNotNull(type),
                cursor = Optional.presentIfNotNull(cursor),
                limit = Optional.presentIfNotNull(limit),
            )
        ).fetchPolicy(fetchPolicy(refresh)).execute().dataOrThrow().savedSearches
        return Page(
            data = savedSearches.data.map { it.savedSearchFields },
            cursor = savedSearches.pagination.cursor,
            hasMore = savedSearches.pagination.hasMore,
            limit = savedSearches.pagination.limit,
        )
    }

    suspend fun executeSavedSearch(
        id: String,
        cursor: String? = null,
        limit: Int? = null,
        refresh: Boolean = false,
    ): SavedSearchResults {
        val result = apolloClient.query(
            ExecuteSavedSearchQuery(
                id = id,
                cursor = Optional.presentIfNotNull(cursor),
                limit = Optional.presentIfNotNull(limit),
            )
        ).fetchPolicy(fetchPolicy(refresh)).execute().dataOrThrow().executeSavedSearch

        result.onContentList?.let { contents ->
            return SavedSearchResults.Contents(
                Page(
                    data = contents.data.map { it.contentFields },
                    cursor = contents.pagination.cursor,
                    hasMore = contents.pagination.hasMore,
                    limit = contents.pagination.limit,
                )
            )
        }
        result.onAssetList?.let { assets ->
            return SavedSearchResults.Assets(
                Page(
                    data = assets.data.map { it.assetFields },
                    cursor = assets.pagination.cursor,
                    hasMore = assets.pagination.hasMore,
                    limit = assets.pagination.limit,
                )
            )
        }
        throw RuntimeException("Unknown saved search result type")
    }

    private fun fetchPolicy(refresh: Boolean): FetchPolicy =
        if (refresh) FetchPolicy.NetworkFirst else FetchPolicy.CacheFirst

    private fun <D : Operation.Data> ApolloResponse<D>.dataOrThrow(): D {
        exception?.let { throw it }
        errors?.firstOrNull()?.let { throw RuntimeException(it.message) }
        return data ?: throw RuntimeException("Unknown Error")
    }
    // endregion
}