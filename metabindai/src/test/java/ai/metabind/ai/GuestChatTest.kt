package ai.metabind.ai

import ai.metabind.mcpappshost.LLMMessage
import ai.metabind.mcpappshost.LLMStreamEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GuestChatTest {
    private fun response() = MockResponse().setHeader("Content-Type", "text/event-stream")
        .setBody("event: message_stop\ndata: {\"stopReason\":\"end_turn\"}\n\n")

    @Test fun publishedRequestsOmitBearerAndKeepPrivateGuestSession() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val provider = MetabindAgentProvider()
            repeat(2) {
                server.enqueue(response())
                provider.streamMessage(server.url("/").toString().trimEnd('/'), orgId = "org", projectId = "project", messages = listOf(LLMMessage.User("Hello"))).toList()
                val request = server.takeRequest()
                assertNull(request.getHeader("Authorization"))
                assertEquals(provider.guestSessionId, request.getHeader("X-Metabind-Guest-Session"))
                assertFalse(request.body.readUtf8().contains("\"draft\":true"))
            }
            assertNotEquals(provider.guestSessionId, MetabindAgentProvider().guestSessionId)
        } finally { server.shutdown() }
    }

    @Test fun anonymousDraftFailsBeforeNetwork() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val events = MetabindAgentProvider().streamMessage(server.url("/").toString(), orgId = "org", projectId = "project", messages = emptyList(), draft = true).toList()
            assertTrue(events.any { it is LLMStreamEvent.Error })
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun draftTokenProviderRunsAgainForEveryTurn() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val provider = MetabindAgentProvider()
            var token = "first-token"
            repeat(2) {
                server.enqueue(response())
                provider.streamMessage(server.url("/").toString().trimEnd('/'), orgId = "org", projectId = "project", messages = emptyList(), draft = true, accessTokenProvider = { token }).toList()
                val request = server.takeRequest()
                assertEquals("Bearer $token", request.getHeader("Authorization"))
                assertNull(request.getHeader("X-Metabind-Guest-Session"))
                token = "refreshed-token"
            }
        } finally { server.shutdown() }
    }
}
