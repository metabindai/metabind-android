package ai.metabind.ai

import ai.metabind.mcpappshost.LLMMessage
import ai.metabind.mcpappshost.LLMStreamEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AuthenticatedChatTest {
    private fun response() = MockResponse().setHeader("Content-Type", "text/event-stream")
        .setBody("event: message_stop\ndata: {\"stopReason\":\"end_turn\"}\n\n")

    @Test fun publishedAndDraftRequireCredentialsBeforeNetwork() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            for (draft in listOf(false, true)) {
                for (token in listOf("", "   ")) {
                    val events = MetabindAgentProvider().streamMessage(server.url("/").toString(), apiKey = token,
                        orgId = "org", projectId = "project", messages = emptyList(), draft = draft).toList()
                    assertTrue(events.any { it is LLMStreamEvent.Error })
                }
            }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun failedTokenRefreshNeverFallsBackToAKeyOrAnonymousRequest() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val events = MetabindAgentProvider().streamMessage(server.url("/").toString(), apiKey = "old-key",
                orgId = "org", projectId = "project", messages = emptyList(),
                accessTokenProvider = { error("Sign in required") }).toList()
            assertTrue(events.any { it is LLMStreamEvent.Error })
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun tokenProviderRunsAgainForPublishedAndDraftTurns() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val provider = MetabindAgentProvider()
            var token = "first-token"
            for (draft in listOf(false, true)) {
                server.enqueue(response())
                provider.streamMessage(server.url("/").toString().trimEnd('/'), orgId = "org", projectId = "project", messages = emptyList(), draft = draft, accessTokenProvider = { token }).toList()
                val request = server.takeRequest()
                assertEquals("Bearer $token", request.getHeader("Authorization"))
                assertNull(request.getHeader("X-Metabind-Guest-Session"))
                token = "refreshed-token"
            }
        } finally { server.shutdown() }
    }
}
