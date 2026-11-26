package ai.metabind.data.home.preview

import android.net.Uri
import net.openid.appauth.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PreviewOAuthRequestsTest {
    private val callback = Uri.parse("ai.metabind.app.oauth://callback")
    private val base = "/00000000000000000001/projects/00000000000000000002"

    @Test fun resourceSurvivesAuthorizationExchangeAndPersistedRefresh() {
        for (host in listOf("mcp.metabind.ai", "mcp-dev.metabind.ai")) {
            for (suffix in listOf("", "/draft")) {
                val resource = "https://$host$base$suffix"
                val project = MCPPreviewLink.parse(resource)!!
                val web = if (project.isDevelopment) "dev.metabind.ai" else "www.metabind.ai"
                val api = if (project.isDevelopment) "api-dev.metabind.ai" else "api.metabind.ai"
                val config = AuthorizationServiceConfiguration(
                    Uri.parse("https://$web/oauth/authorize"), Uri.parse("https://$api/oauth/token"))
                val request = PreviewOAuthRequests.authorize(project, config, "client", callback)
                assertEquals(listOf(resource), request.toUri().getQueryParameters("resource"))
                assertEquals(project.projectId, request.toUri().getQueryParameter("project_id"))
                assertEquals("S256", request.codeVerifierChallengeMethod)
                assertNotNull(request.codeVerifier)
                val response = AuthorizationResponse.Builder(request).setAuthorizationCode("code")
                    .setState(request.state).build()
                val exchange = PreviewOAuthRequests.exchange(project, response)
                assertEquals(resource, exchange.requestParameters["resource"])
                assertEquals("authorization_code", exchange.requestParameters["grant_type"])
                assertEquals(request.codeVerifier, exchange.requestParameters["code_verifier"])
                val token = TokenResponse.Builder(exchange).setAccessToken("access").setTokenType("Bearer")
                    .setRefreshToken("refresh").setAccessTokenExpirationTime(1L).build()
                val state = AuthState.jsonDeserialize(AuthState(response, token, null).jsonSerializeString())
                val refresh = state.createTokenRefreshRequest(PreviewOAuthRequests.tokenParameters(project))
                assertEquals(resource, refresh.requestParameters["resource"])
                assertEquals("refresh_token", refresh.requestParameters["grant_type"])
                assertEquals("refresh", refresh.requestParameters["refresh_token"])
            }
        }
    }

    @Test fun exchangeRejectsAnotherModeProjectOrEnvironmentAndMissingResource() {
        val project = MCPPreviewLink.parse("https://mcp.metabind.ai$base/draft")!!
        val config = AuthorizationServiceConfiguration(Uri.parse("https://www.metabind.ai/oauth/authorize"),
            Uri.parse("https://api.metabind.ai/oauth/token"))
        val request = PreviewOAuthRequests.authorize(project, config, "client", callback)
        val response = AuthorizationResponse.Builder(request).setAuthorizationCode("code").build()
        for (other in listOf("https://mcp.metabind.ai$base", "https://mcp-dev.metabind.ai$base/draft",
            "https://mcp.metabind.ai/00000000000000000001/projects/00000000000000000003/draft")) {
            assertThrows(IllegalArgumentException::class.java) {
                PreviewOAuthRequests.exchange(MCPPreviewLink.parse(other)!!, response)
            }
        }
        val legacy = AuthorizationRequest.Builder(config, "client", ResponseTypeValues.CODE, callback)
            .setAdditionalParameters(mapOf("project_id" to project.projectId)).build()
        assertThrows(IllegalArgumentException::class.java) {
            PreviewOAuthRequests.exchange(project, AuthorizationResponse.Builder(legacy).setAuthorizationCode("code").build())
        }
    }
}
