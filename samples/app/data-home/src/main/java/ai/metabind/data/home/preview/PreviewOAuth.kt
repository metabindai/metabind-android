package ai.metabind.data.home.preview

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Browser-based authorization with AppAuth's state validation and S256 PKCE. */
@Singleton
class PreviewOAuth @Inject constructor(
    @ApplicationContext context: Context,
    private val credentials: PreviewCredentials,
) {
    private val callback = Uri.parse("${context.packageName}.oauth://callback")
    private val service = AuthorizationService(context, AppAuthConfiguration.Builder()
        .setConnectionBuilder { uri -> DefaultConnectionBuilder.INSTANCE.openConnection(uri).apply {
            instanceFollowRedirects = false
        } }.build())
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()
    private val mutex = Mutex()

    class SignInRequired : Exception("Sign in to Metabind to access this project's drafts.")

    suspend fun hasSession(project: MCPPreviewLink): Boolean = withContext(Dispatchers.IO) {
        credentials.load(project) != null
    }

    suspend fun authorizationIntent(project: MCPPreviewLink): Intent = withContext(Dispatchers.IO) {
        require(project.isDraft)
        val metadata = json(Request.Builder().url(
            project.serverUrl.removeSuffix("/draft") + "/.well-known/oauth-authorization-server"
        ).build())
        val authorization = metadata.getString("authorization_endpoint")
        val token = metadata.getString("token_endpoint")
        val registration = metadata.getString("registration_endpoint")
        OAuthTrust.validate(project.isDevelopment, authorization, token, registration)
        val registered = json(Request.Builder().url(registration).post(JSONObject()
            .put("client_name", "Metabind for Android")
            .put("redirect_uris", org.json.JSONArray(listOf(callback.toString())))
            .put("grant_types", org.json.JSONArray(listOf("authorization_code", "refresh_token")))
            .put("response_types", org.json.JSONArray(listOf("code")))
            .put("token_endpoint_auth_method", "none").put("scope", OAuthTrust.scopes)
            .toString().toRequestBody("application/json".toMediaType())).build())
        val clientId = registered.getString("client_id")
        require(clientId.isNotBlank() && OAuthTrust.validScope(registered.getString("scope")))
        val config = AuthorizationServiceConfiguration(Uri.parse(authorization), Uri.parse(token))
        val request = AuthorizationRequest.Builder(config, clientId, ResponseTypeValues.CODE, callback)
            .setScope(OAuthTrust.scopes)
            .setAdditionalParameters(mapOf("project_id" to project.projectId)).build()
        service.getAuthorizationRequestIntent(request)
    }

    suspend fun finish(project: MCPPreviewLink, data: Intent?) = mutex.withLock {
        if (data == null || AuthorizationException.fromIntent(data) != null) throw SignInRequired()
        val response = AuthorizationResponse.fromIntent(data) ?: throw SignInRequired()
        val request = response.request
        require(project.isDraft && request.redirectUri == callback && request.responseType == ResponseTypeValues.CODE)
        require(request.additionalParameters["project_id"] == project.projectId)
        require(!request.state.isNullOrBlank() && request.state == response.state)
        require(!request.codeVerifier.isNullOrBlank() && request.codeVerifierChallengeMethod == "S256")
        require(OAuthTrust.validScope(request.scope.orEmpty()))
        validateConfiguration(project, request.configuration)
        val token = suspendCancellableCoroutine<TokenResponse> { continuation ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { value, error ->
                if (continuation.isActive) {
                    if (value != null && error == null) continuation.resume(value)
                    else continuation.resumeWithException(SignInRequired())
                }
            }
        }
        require(token.tokenType.equals("Bearer", true) && !token.accessToken.isNullOrBlank())
        require((token.accessTokenExpirationTime ?: 0) > System.currentTimeMillis())
        val state = AuthState(response, token, null)
        require(OAuthTrust.validScope(state.scope.orEmpty()))
        withContext(Dispatchers.IO) { credentials.save(project, state.jsonSerializeString()) }
    }

    /** Resolve a fresh token for every MCP request and Agent turn, without resetting chat. */
    suspend fun accessToken(project: MCPPreviewLink): String = mutex.withLock {
        require(project.isDraft)
        val encoded = withContext(Dispatchers.IO) { credentials.load(project) } ?: throw SignInRequired()
        val state = try { AuthState.jsonDeserialize(encoded) } catch (_: Exception) { throw SignInRequired() }
        validateConfiguration(project, state.authorizationServiceConfiguration ?: throw SignInRequired())
        if (!state.isAuthorized || !OAuthTrust.validScope(state.scope.orEmpty())) throw SignInRequired()
        if ((state.accessTokenExpirationTime ?: 0) <= System.currentTimeMillis() + 60_000 && state.refreshToken == null) throw SignInRequired()
        try {
            val token = suspendCancellableCoroutine<String> { continuation ->
                state.performActionWithFreshTokens(service) { accessToken, _, error ->
                    if (continuation.isActive) {
                        when {
                            error?.error in setOf("invalid_grant", "invalid_client") -> continuation.resumeWithException(SignInRequired())
                            error != null || accessToken.isNullOrBlank() -> continuation.resumeWithException(IllegalStateException("Unable to refresh sign-in. Try again."))
                            !OAuthTrust.validScope(state.scope.orEmpty()) -> continuation.resumeWithException(SignInRequired())
                            else -> continuation.resume(accessToken)
                        }
                    }
                }
            }
            val updated = state.jsonSerializeString()
            if (updated != encoded) withContext(Dispatchers.IO) { credentials.save(project, updated) }
            token
        } catch (error: SignInRequired) {
            withContext(Dispatchers.IO) { credentials.remove(project) }
            throw error
        }
    }

    suspend fun disconnect(project: MCPPreviewLink) = mutex.withLock {
        withContext(Dispatchers.IO) { credentials.remove(project) }
    }

    private fun validateConfiguration(project: MCPPreviewLink, config: AuthorizationServiceConfiguration) {
        OAuthTrust.validate(project.isDevelopment, config.authorizationEndpoint.toString(), config.tokenEndpoint.toString())
    }

    private fun json(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "Unable to connect to Metabind sign-in." }
        JSONObject(response.body?.string() ?: error("Invalid sign-in response"))
    }
}

/** Kept independent of Android so hostile discovery documents can be unit tested. */
internal object OAuthTrust {
    const val scopes = "read:types read:packages read:assets execute:mcp execute:mcp-draft offline_access"
    fun validScope(value: String) = value.split(' ').filter { it.isNotBlank() }.toSet() == scopes.split(' ').toSet()

    fun validate(development: Boolean, authorization: String, token: String, registration: String? = null) {
        val web = if (development) "dev.metabind.ai" else "www.metabind.ai"
        val api = if (development) "api-dev.metabind.ai" else "api.metabind.ai"
        val mcp = if (development) "mcp-dev.metabind.ai" else "mcp.metabind.ai"
        fun endpoint(value: String, hosts: Set<String>) {
            val uri = URI(value)
            require(uri.scheme == "https" && uri.host in hosts && uri.port == -1 &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        }
        endpoint(authorization, setOf(web))
        endpoint(token, setOf(api))
        registration?.let { endpoint(it, setOf(api, mcp)) }
    }
}
