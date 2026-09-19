package ai.metabind.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class DraftPreviewTest {
    private val main = newSingleThreadContext("preview-test")
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<Pair<String, JsonObject>>()
    private val authorizationHeaders = CopyOnWriteArrayList<String>()
    @Volatile private var html = "<p>First draft</p>"
    @Volatile private var resourceUri = "ui://card"
    @Volatile private var resourceDelay = 0L
    private var assistant: MetabindAssistant? = null

    @Before fun setup() {
        Dispatchers.setMain(main)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                authorizationHeaders.add(request.getHeader("Authorization") ?: "")
                val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                requests.add(request.path!! to body)
                if (request.path!!.endsWith("/chat")) {
                    val stream = listOf(
                        "message_start" to """{"conversationId":"conversation-one"}""",
                        "tool_use" to """{"id":"call-${requests.size}","name":"card","input":{"range":"month"}}""",
                        "tool_result" to """{"toolUseId":"call-${requests.size}","content":[{"type":"text","text":"result data"}],"isError":false}""",
                        "text_delta" to """{"text":"Here is your card"}""",
                        "message_stop" to """{"stopReason":"end_turn"}"""
                    ).joinToString("") { (event, data) -> "event: $event\ndata: $data\n\n" }
                    return MockResponse().setHeader("Content-Type", "text/event-stream").setBody(stream)
                }
                val result = when (body["method"]!!.jsonPrimitive.content) {
                    "initialize" -> """{"protocolVersion":"2025-03-26"}"""
                    "notifications/initialized" -> return MockResponse().setResponseCode(202)
                    "tools/list" -> """{"tools":[{"name":"card","inputSchema":{"type":"object"},"_meta":{"ui":{"resourceUri":"$resourceUri"}}}]}"""
                    "resources/read" -> {
                        Thread.sleep(resourceDelay)
                        """{"contents":[{"uri":"$resourceUri","mimeType":"text/html;profile=mcp-app","text":${JsonPrimitive(html)}}]}"""
                    }
                    else -> error("Unexpected request")
                }
                return MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("""{"jsonrpc":"2.0","id":${body["id"]},"result":$result}""")
            }
        }
        server.start()
    }

    @After fun teardown() {
        runBlocking(main) { assistant?.close() }
        server.shutdown()
        Dispatchers.resetMain()
        main.close()
    }

    private suspend fun create(draft: Boolean): MetabindAssistant {
        val host = server.url("/").toString().trimEnd('/')
        return MetabindAssistant("test-key", "org", "project", host, host, draft).also {
            assistant = it
            it.awaitReady()
        }
    }

    private suspend fun turn(chat: MetabindAssistant) {
        chat.send("Show a card")
        withTimeout(10_000) { chat.isLoading.first { !it } }
        assertNull(chat.error.value)
        assertEquals(1, chat.toolUIContent.value.size)
    }

    @Test fun draftUsesDraftMcpAndAgentFlag() = runBlocking(main) {
        val chat = create(true)
        turn(chat)
        assertTrue(requests.filter { !it.first.endsWith("/chat") }.all { it.first == "/org/projects/project/draft" })
        assertEquals(JsonPrimitive(true), requests.single { it.first.endsWith("/chat") }.second["draft"])
    }

    @Test fun publishedBehaviorRemainsTheDefault() = runBlocking(main) {
        val chat = create(false)
        turn(chat)
        assertTrue(requests.filter { !it.first.endsWith("/chat") }.all { it.first == "/org/projects/project" })
        assertNull(requests.single { it.first.endsWith("/chat") }.second["draft"])
        val count = requests.size
        chat.refreshPreviewResources()
        assertEquals(count, requests.size)
    }

    @Test fun guestAssistantDiscoversToolsAndLoadsCardsWithoutCredentials() = runBlocking(main) {
        val host = server.url("/").toString().trimEnd('/')
        val chat = MetabindAssistant(orgId = "org", projectId = "project", agentHost = host, mcpHost = host)
        assistant = chat
        chat.awaitReady()
        turn(chat)
        assertTrue(authorizationHeaders.all { it.isEmpty() })
        assertTrue(requests.filter { !it.first.endsWith("/chat") }.all { it.first == "/org/projects/project" })
        val count = requests.size
        chat.refreshPreviewResources()
        assertEquals(count, requests.size)
    }

    @Test fun refreshedTokenIsUsedForMcpDiscoveryAndResourceReads() = runBlocking(main) {
        val host = server.url("/").toString().trimEnd('/')
        var token = "initial"
        val chat = MetabindAssistant(orgId = "org", projectId = "project", agentHost = host, mcpHost = host,
            draft = true, accessTokenProvider = { token })
        assistant = chat
        chat.awaitReady()
        assertTrue(authorizationHeaders.all { it == "Bearer initial" })
        authorizationHeaders.clear()
        token = "renewed"
        turn(chat)
        assertTrue(authorizationHeaders.isNotEmpty())
        assertTrue(authorizationHeaders.all { it == "Bearer renewed" })
    }

    @Test fun resultArrivingBeforeResourceIsRetained() = runBlocking(main) {
        resourceDelay = 250
        val chat = create(true)
        turn(chat)
        assertEquals("result data", chat.toolUIContent.value.values.single().toolResultText)
    }

    @Test fun refreshPreservesChatArgumentsResultsAndDoesNotReplayTools() = runBlocking(main) {
        val chat = create(true)
        turn(chat)
        val messages = chat.messages.value
        val initial = chat.toolUIContent.value.values.single()
        chat.refreshPreviewResources()
        assertSame(initial, chat.toolUIContent.value.values.single())
        html = "<p>Saved unpublished edit</p>"
        resourceUri = "ui://edited-card"
        chat.refreshPreviewResources()
        val refreshed = chat.toolUIContent.value.values.single() as ToolUIContent.Html
        assertEquals(html, refreshed.html)
        assertEquals(initial.toolArguments, refreshed.toolArguments)
        assertEquals(initial.toolResultText, refreshed.toolResultText)
        assertEquals(messages, chat.messages.value)
        assertEquals(1, requests.count { it.first.endsWith("/chat") })
        assertFalse(requests.any { it.second["method"] == JsonPrimitive("tools/call") })
        assertTrue(requests.any { it.second["params"]?.jsonObject?.get("uri") == JsonPrimitive("ui://edited-card") })
    }

    @Test fun resetDiscardsAnInFlightRefresh() = runBlocking(main) {
        val chat = create(true)
        turn(chat)
        html = "<p>Changed</p>"
        resourceDelay = 300
        val before = requests.count { it.second["method"] == JsonPrimitive("resources/read") }
        val refresh = async { chat.refreshPreviewResources() }
        withTimeout(10_000) {
            while (requests.count { it.second["method"] == JsonPrimitive("resources/read") } == before) delay(10)
        }
        chat.reset()
        refresh.await()
        assertTrue(chat.messages.value.isEmpty())
        assertTrue(chat.toolUIContent.value.isEmpty())
    }
}
