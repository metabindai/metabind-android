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
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class BindJSViewChannelTest {
    private val main = newSingleThreadContext("view-channel-test")
    private val server = MockWebServer()
    private val reads = CopyOnWriteArrayList<Pair<String, String>>()
    private val packageText = """{"name":"com.shop.ui","version":"1.0.0","spec":"1.0","components":{"Layout":"layout()","Card":"card()"}}"""
    @Volatile private var digest = sha256(packageText)
    private var assistant: MetabindAssistant? = null

    private fun sha256(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Before fun setup() {
        Dispatchers.setMain(main)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                if (request.path!!.endsWith("/chat")) {
                    val stream = listOf(
                        "message_start" to """{"conversationId":"conversation-one"}""",
                        "tool_use" to """{"id":"call-1","name":"card","input":{"range":"month"}}""",
                        "tool_result" to """{"toolUseId":"call-1","content":[{"type":"text","text":"result data"}],"isError":false}""",
                        "message_stop" to """{"stopReason":"end_turn"}"""
                    ).joinToString("") { (event, data) -> "event: $event\ndata: $data\n\n" }
                    return MockResponse().setHeader("Content-Type", "text/event-stream").setBody(stream)
                }
                val session = request.getHeader("Mcp-Session-Id")
                val method = body["method"]!!.jsonPrimitive.content
                val result = when (method) {
                    "initialize" -> {
                        val declared = body["params"]!!.jsonObject["capabilities"]!!.jsonObject["extensions"]!!
                            .jsonObject["io.modelcontextprotocol/ui"]!!.jsonObject["mimeTypes"]!!.jsonArray.map { it.jsonPrimitive.content }
                        // The server picks the representation per session, from what was declared.
                        val picked = if ("application/bindjs+json;version=1.0" in declared) "bindjs" else "html"
                        return MockResponse().setHeader("Content-Type", "application/json").setHeader("Mcp-Session-Id", picked)
                            .setBody("""{"jsonrpc":"2.0","id":${body["id"]},"result":{"protocolVersion":"2025-03-26"}}""")
                    }
                    "notifications/initialized" -> return MockResponse().setResponseCode(202)
                    "tools/list" -> """{"tools":[{"name":"card","inputSchema":{"type":"object"},"_meta":{"ui":{"resourceUri":"ui://view/layout"}}}]}"""
                    "resources/list" -> """{"resources":[]}"""
                    "resources/read" -> {
                        val uri = body["params"]!!.jsonObject["uri"]!!.jsonPrimitive.content
                        reads.add(session!! to uri)
                        when {
                            uri == "ui://pkg/shop" ->
                                """{"contents":[{"uri":"$uri","mimeType":"application/bindjs-package+json","text":${JsonPrimitive(packageText)}}]}"""
                            session == "html" ->
                                """{"contents":[{"uri":"$uri","mimeType":"text/html;profile=mcp-app","text":"<p>card</p>"}]}"""
                            else -> """{"contents":[{"uri":"$uri","mimeType":"application/bindjs+json",""" +
                                """"text":${JsonPrimitive("""{"spec":"1.0","component":"Layout"}""")},""" +
                                """"_meta":{"ui":{"bindjs":{"spec":"1.0","component":"Layout","package":"ui://pkg/shop",""" +
                                """"sha256":"$digest","size":${packageText.toByteArray().size}}}}}]}"""
                        }
                    }
                    else -> error("Unexpected $method")
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

    private suspend fun cardFromOneTurn(): ToolUIContent {
        val host = server.url("/").toString().trimEnd('/')
        val chat = MetabindAssistant("test-key", "org", "project", host, host).also {
            assistant = it
            it.awaitReady()
        }
        chat.send("Show a card")
        withTimeout(10_000) { chat.isLoading.first { !it } }
        assertNull(chat.error.value)
        return chat.toolUIContent.value.values.single()
    }

    @Test fun aViewRendersFromItsVerifiedPackage() = runBlocking(main) {
        val card = cardFromOneTurn() as ToolUIContent.BindJS
        assertEquals("Layout", card.layoutComponentName)
        assertEquals("layout()", card.designerComponent.content)
        assertEquals(listOf("Card"), card.designerComponent.dependencies.map { it.name })
        assertEquals("result data", card.toolResultText)
        assertTrue(reads.none { it.first == "html" })
    }

    @Test fun aPackageFailingItsDigestFallsBackToHtml() = runBlocking(main) {
        digest = "0".repeat(64)
        val card = cardFromOneTurn() as ToolUIContent.Html
        assertEquals("<p>card</p>", card.html)
        assertEquals("result data", card.toolResultText)
        assertTrue("html" to "ui://view/layout" in reads)
    }
}
