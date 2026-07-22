/*
 * MCPAppsClient.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.mcpappshost

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MCPAppsClient(
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val maxRetries: Int = 2,
    requestTimeoutSeconds: Long = 30
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val nextRequestId = AtomicInteger(1)
    private var sessionId: String? = null
    private var isInitialized = false
    private val initMutex = Mutex()

    companion object {
        private val SUPPORTED_VERSIONS = listOf("2025-03-26", "2024-11-05")
        private val SUPPORTED_MIME_TYPES = listOf(
            "application/vnd.bindjs+json",
            "text/html;profile=mcp-app"
        )
        private const val CLIENT_NAME = "MCPAppsHost"
        private const val CLIENT_VERSION = "1.0.0"
        private val RETRYABLE_CODES = setOf(500, 502, 503, 504)
    }

    private suspend fun ensureInitialized() {
        if (isInitialized) return
        initMutex.withLock {
            if (isInitialized) return
            initialize()
        }
    }

    private suspend fun initialize() {
        val params = JsonObject(mapOf(
            "protocolVersion" to JsonPrimitive(SUPPORTED_VERSIONS.first()),
            "capabilities" to JsonObject(mapOf(
                "extensions" to JsonObject(mapOf(
                    "io.modelcontextprotocol/ui" to JsonObject(mapOf(
                        "mimeTypes" to JsonArray(SUPPORTED_MIME_TYPES.map { JsonPrimitive(it) })
                    ))
                ))
            )),
            "clientInfo" to JsonObject(mapOf(
                "name" to JsonPrimitive(CLIENT_NAME),
                "version" to JsonPrimitive(CLIENT_VERSION)
            ))
        ))

        val response = sendRequest("initialize", params, skipInitCheck = true)

        val protocolVersion = response["protocolVersion"]?.jsonPrimitive?.content
        if (protocolVersion != null && protocolVersion !in SUPPORTED_VERSIONS) {
            throw MCPClientException("Version mismatch: server returned $protocolVersion")
        }

        isInitialized = true

        // Send initialized notification
        sendNotification("notifications/initialized")
    }

    suspend fun listTools(): List<MCPToolDefinition> {
        ensureInitialized()

        val allTools = mutableListOf<MCPToolDefinition>()
        var cursor: String? = null

        do {
            val params = if (cursor != null) {
                JsonObject(mapOf("cursor" to JsonPrimitive(cursor)))
            } else {
                JsonObject(emptyMap())
            }

            val result = sendRequest("tools/list", params)
            val tools = result["tools"]?.jsonArray ?: break

            for (toolJson in tools) {
                val tool = toolJson.jsonObject
                val name = tool["name"]?.jsonPrimitive?.content ?: continue
                val description = tool["description"]?.jsonPrimitive?.content
                val inputSchema = tool["inputSchema"]

                var ui: UIMetadata? = null
                val meta = tool["_meta"]?.jsonObject
                val uiMeta = meta?.get("ui")?.jsonObject
                if (uiMeta != null) {
                    val resourceUri = uiMeta["resourceUri"]?.jsonPrimitive?.content
                    val visibility = uiMeta["visibility"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.content }
                        ?.toSet() ?: emptySet()
                    if (resourceUri != null) {
                        ui = UIMetadata(resourceUri, visibility)
                    }
                }

                allTools.add(MCPToolDefinition(name, description, inputSchema, ui))
            }

            cursor = result["nextCursor"]?.jsonPrimitive?.content
        } while (cursor != null)

        return allTools
    }

    suspend fun callTool(name: String, arguments: JsonElement): ToolResult {
        ensureInitialized()

        val params = JsonObject(mapOf(
            "name" to JsonPrimitive(name),
            "arguments" to arguments
        ))

        val result = sendRequest("tools/call", params)
        return parseToolResult(result)
    }

    suspend fun readResource(uri: String): ResourceContent {
        ensureInitialized()

        val params = JsonObject(mapOf(
            "uri" to JsonPrimitive(uri)
        ))

        val result = sendRequest("resources/read", params)
        val contents = result["contents"]?.jsonArray
            ?: throw MCPClientException("Missing contents in resource response")

        val first = contents.firstOrNull()?.jsonObject
            ?: throw MCPClientException("Empty contents array")

        return ResourceContent(
            uri = first["uri"]?.jsonPrimitive?.content ?: uri,
            mimeType = first["mimeType"]?.jsonPrimitive?.content ?: "text/plain",
            text = first["text"]?.jsonPrimitive?.content,
            blob = first["blob"]?.jsonPrimitive?.content
        )
    }

    private fun parseToolResult(resultObj: JsonObject): ToolResult {
        val isError = resultObj["isError"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val contentArray = resultObj["content"]?.jsonArray ?: return ToolResult.text("", isError)

        val blocks = contentArray.mapNotNull { element ->
            val block = element.jsonObject
            when (block["type"]?.jsonPrimitive?.content) {
                "text" -> {
                    val text = block["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    ContentBlock.Text(text)
                }
                "image" -> {
                    val data = block["data"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val mimeType = block["mimeType"]?.jsonPrimitive?.content ?: "image/png"
                    ContentBlock.Image(data, mimeType)
                }
                "resource" -> {
                    val uri = block["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val mimeType = block["mimeType"]?.jsonPrimitive?.content ?: "text/plain"
                    val text = block["text"]?.jsonPrimitive?.content
                    ContentBlock.Resource(uri, mimeType, text)
                }
                else -> null
            }
        }

        return ToolResult(blocks, isError)
    }

    private suspend fun sendRequest(
        method: String,
        params: JsonObject,
        skipInitCheck: Boolean = false,
        retryCount: Int = 0
    ): JsonObject {
        if (!skipInitCheck) {
            ensureInitialized()
        }

        val id = nextRequestId.getAndIncrement()
        val body = JsonObject(mapOf(
            "jsonrpc" to JsonPrimitive("2.0"),
            "id" to JsonPrimitive(id),
            "method" to JsonPrimitive(method),
            "params" to params
        ))

        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")

        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        sessionId?.let {
            requestBuilder.addHeader("Mcp-Session-Id", it)
        }

        requestBuilder.post(body.toString().toRequestBody("application/json".toMediaType()))

        val request = requestBuilder.build()

        try {
            val (statusCode, responseHeaders, responseBody) = withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                val code = response.code
                val headers = mapOf(
                    "Mcp-Session-Id" to response.header("Mcp-Session-Id"),
                    "Content-Type" to response.header("Content-Type")
                )
                val body = response.body?.string()
                response.close()
                Triple(code, headers, body)
            }

            // Extract session ID from response
            responseHeaders["Mcp-Session-Id"]?.let {
                sessionId = it
            }

            // Session expired - re-initialize. Skip if this *is* the init call,
            // otherwise a 404/410 on initialize would recurse until the stack
            // overflows. Surface the error instead.
            if ((statusCode == 404 || statusCode == 410) && method != "initialize") {
                isInitialized = false
                sessionId = null
                initialize()
                return sendRequest(method, params, skipInitCheck = true)
            }

            if (statusCode !in listOf(200, 202)) {
                if (statusCode in RETRYABLE_CODES && retryCount < maxRetries) {
                    val delayMs = (500L * (1 shl retryCount))
                    delay(delayMs)
                    return sendRequest(method, params, skipInitCheck, retryCount + 1)
                }
                throw MCPClientException("Server error ($statusCode): ${responseBody ?: ""}")
            }

            if (responseBody == null) {
                throw MCPClientException("Empty response body")
            }

            // Handle SSE responses
            val contentType = responseHeaders["Content-Type"] ?: ""
            val jsonBody = if (contentType.contains("text/event-stream")) {
                parseSSEData(responseBody)
            } else {
                responseBody
            }

            val parsed = json.parseToJsonElement(jsonBody).jsonObject

            // Check for JSON-RPC error
            val error = parsed["error"]?.jsonObject
            if (error != null) {
                val code = error["code"]?.jsonPrimitive?.int ?: -1
                val message = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
                throw MCPClientException("RPC error ($code): $message")
            }

            return parsed["result"]?.jsonObject
                ?: throw MCPClientException("Missing result in response")
        } catch (e: MCPClientException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            if (retryCount < maxRetries) {
                val delayMs = (500L * (1 shl retryCount))
                delay(delayMs)
                return sendRequest(method, params, skipInitCheck, retryCount + 1)
            }
            throw MCPClientException("Request timed out: ${e.message}")
        } catch (e: java.io.IOException) {
            if (retryCount < maxRetries) {
                val delayMs = (500L * (1 shl retryCount))
                delay(delayMs)
                return sendRequest(method, params, skipInitCheck, retryCount + 1)
            }
            throw MCPClientException("Connection failed: ${e.message}")
        }
    }

    private suspend fun sendNotification(method: String) {
        val body = JsonObject(mapOf(
            "jsonrpc" to JsonPrimitive("2.0"),
            "method" to JsonPrimitive(method)
        ))

        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")

        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        sessionId?.let {
            requestBuilder.addHeader("Mcp-Session-Id", it)
        }

        requestBuilder.post(body.toString().toRequestBody("application/json".toMediaType()))

        try {
            withContext(Dispatchers.IO) {
                client.newCall(requestBuilder.build()).execute().close()
            }
        } catch (_: Exception) {
            // Notifications are fire-and-forget
        }
    }

    private fun parseSSEData(sseBody: String): String {
        var lastData = ""
        for (line in sseBody.lines()) {
            if (line.startsWith("data:")) {
                lastData = line.removePrefix("data:").trim()
            }
        }
        return lastData
    }
}

class MCPClientException(message: String, cause: Throwable? = null) : Exception(message, cause)
