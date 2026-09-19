package ai.metabind

import ai.metabind.data.home.preview.MCPPreviewLink
import ai.metabind.data.home.preview.PreviewCredentials
import ai.metabind.data.home.preview.PreviewOAuth
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.openid.appauth.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewSignInTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val link = "https://mcp.metabind.ai/00000000000000000000/projects/11111111111111111111/draft"

    @Test fun credentialFreeDraftOpensSignInAndSurvivesRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreviewCredentials(context).remove(MCPPreviewLink.parse(link)!!)
        compose.onNodeWithContentDescription("Preview").performClick()
        compose.onNodeWithText("Preview URL").performTextInput(link)
        compose.onNodeWithText("Open Preview").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Sign in to Metabind").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sign in to preview drafts").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Sign in to Metabind").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun oauthRejectsWrongStateOrProjectBeforeExchangingCode() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val auth = PreviewOAuth(context, PreviewCredentials(context))
        val project = MCPPreviewLink.parse(link)!!
        PreviewCredentials(context).remove(project)
        val config = AuthorizationServiceConfiguration(Uri.parse("https://www.metabind.ai/oauth/authorize"), Uri.parse("https://api.metabind.ai/oauth/token"))
        val request = AuthorizationRequest.Builder(config, "test-client", ResponseTypeValues.CODE, Uri.parse("${context.packageName}.oauth://callback"))
            .setScope("read:types read:packages read:assets execute:mcp execute:mcp-draft offline_access")
            .setAdditionalParameters(mapOf("project_id" to project.projectId)).build()
        assertEquals("S256", request.codeVerifierChallengeMethod)
        assertNotNull(request.codeVerifier)
        assertNotEquals(request.state, AuthorizationRequest.Builder(config, "test-client", ResponseTypeValues.CODE, request.redirectUri).build().state)
        val response = AuthorizationResponse.Builder(request).setState("wrong-state").setAuthorizationCode("test-code").build()
        assertTrue(runCatching { auth.finish(project, response.toIntent()) }.isFailure)
        val wrongProject = MCPPreviewLink.parse(link.replace("11111111111111111111", "22222222222222222222"))!!
        val validState = AuthorizationResponse.Builder(request).setState(request.state).setAuthorizationCode("test-code").build()
        assertTrue(runCatching { auth.finish(wrongProject, validState.toIntent()) }.isFailure)
        assertFalse(auth.hasSession(project))
    }

    @Test fun encryptedOAuthSessionReopensAndDisconnectRemovesAccess() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val credentials = PreviewCredentials(context)
        val project = MCPPreviewLink.parse(link)!!
        val config = AuthorizationServiceConfiguration(Uri.parse("https://www.metabind.ai/oauth/authorize"), Uri.parse("https://api.metabind.ai/oauth/token"))
        val request = AuthorizationRequest.Builder(config, "test-client", ResponseTypeValues.CODE, Uri.parse("${context.packageName}.oauth://callback"))
            .setScope("read:types read:packages read:assets execute:mcp execute:mcp-draft offline_access")
            .setAdditionalParameters(mapOf("project_id" to project.projectId)).build()
        val response = AuthorizationResponse.Builder(request).setState(request.state).setAuthorizationCode("test-code").build()
        val token = TokenResponse.Builder(response.createTokenExchangeRequest()).setTokenType("Bearer")
            .setAccessToken("test-access-token").setAccessTokenExpiresIn(3600L).setRefreshToken("test-refresh-token").build()
        try {
            credentials.save(project, AuthState(response, token, null).jsonSerializeString())
            val restored = PreviewOAuth(context, PreviewCredentials(context))
            assertEquals("test-access-token", restored.accessToken(project))
            val dev = MCPPreviewLink.parse(link.replace("mcp.", "mcp-dev."))!!
            assertFalse(restored.hasSession(dev))
            restored.disconnect(project)
            assertTrue(runCatching { restored.accessToken(project) }.exceptionOrNull() is PreviewOAuth.SignInRequired)
        } finally { credentials.remove(project) }
    }
}
