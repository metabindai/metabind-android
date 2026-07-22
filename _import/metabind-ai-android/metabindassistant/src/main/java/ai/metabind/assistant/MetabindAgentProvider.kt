/*
 * MetabindAgentProvider.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant

import ai.metabind.mcpappshost.LLMMessage
import ai.metabind.mcpappshost.LLMStopReason
import ai.metabind.mcpappshost.LLMStreamEvent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * LLM provider backed by the Metabind Agent proxy (agent.metabind.ai).
 *
 * The proxy holds upstream LLM credentials server-side, fetches the project's
 * published MCP tools, runs the tool-call loop, and streams normalized events
 * back over SSE. Clients authenticate with a project-scoped Metabind API key.
 */
class MetabindAgentProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private var conversationId: String? = null
    private var toolIndexCounter = 0

    companion object {
        private const val TAG = "MetabindAgentProvider"
        const val DEVELOPMENT_HOST = "https://agent-dev.metabind.ai"
        const val PRODUCTION_HOST = "https://agent.metabind.ai"
    }

    fun resetConversation() {
        conversationId = null
        toolIndexCounter = 0
    }

    fun streamMessage(
        baseUrl: String,
        apiKey: String,
        orgId: String,
        projectId: String,
        messages: List<LLMMessage>
    ): Flow<LLMStreamEvent> = callbackFlow {
        try {
            val url = "$baseUrl/$orgId/$projectId/chat"

            val scopedMessages = scopedMessages(messages)

            val bodyMap = mutableMapOf<String, JsonElement>(
                "messages" to scopedMessages,
                "stream" to JsonPrimitive(true)
            )
            conversationId?.let {
                bodyMap["conversationId"] = JsonPrimitive(it)
            }

            val requestBody = JsonObject(bodyMap).toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(requestBody)
                .build()

            Log.d(TAG, "POST /chat orgId=$orgId conversationId=${conversationId ?: "<new>"}")

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (response.code != 200) {
                val errorBody = withContext(Dispatchers.IO) {
                    response.body?.string() ?: ""
                }
                response.close()

                val errorMsg = try {
                    val errorJson = json.parseToJsonElement(errorBody).jsonObject
                    val code = errorJson["error"]?.jsonPrimitive?.content ?: "unknown"
                    val message = errorJson["message"]?.jsonPrimitive?.content ?: errorBody
                    "$code: $message"
                } catch (_: Exception) {
                    "Agent HTTP ${response.code}: $errorBody"
                }

                trySend(LLMStreamEvent.Error(errorMsg))
                close()
                return@callbackFlow
            }

            val reader = withContext(Dispatchers.IO) {
                response.body?.byteStream()?.bufferedReader()
            }

            if (reader == null) {
                trySend(LLMStreamEvent.Error("Empty response body"))
                close()
                return@callbackFlow
            }

            withContext(Dispatchers.IO) {
                parseSSEStream(reader) { event, data ->
                    handleFrame(event, data)?.let { streamEvent ->
                        trySend(streamEvent)
                    }
                }
            }

            close()
        } catch (e: Exception) {
            trySend(LLMStreamEvent.Error(e.message ?: "Connection failed"))
            close()
        }

        awaitClose()
    }

    private fun parseSSEStream(
        reader: BufferedReader,
        onFrame: (String, String) -> Unit
    ) {
        var pendingEvent: String? = null
        var pendingData = ""

        reader.useLines { lines ->
            for (line in lines) {
                if (line.startsWith(":")) continue // heartbeat / comment

                if (line.startsWith("event:")) {
                    // Flush previous frame
                    pendingEvent?.let { name ->
                        onFrame(name, pendingData)
                    }
                    pendingEvent = line.removePrefix("event:").trim()
                    pendingData = ""
                } else if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    pendingData = if (pendingData.isEmpty()) payload else "$pendingData\n$payload"
                }
            }
        }

        // Flush final frame
        pendingEvent?.let { name ->
            onFrame(name, pendingData)
        }
    }

    /**
     * Returns an LLMStreamEvent for the given SSE frame, or null to skip.
     */
    private fun handleFrame(name: String, data: String): LLMStreamEvent? {
        val jsonObj = try {
            json.parseToJsonElement(data).jsonObject
        } catch (_: Exception) {
            Log.w(TAG, "Dropped malformed SSE frame (event=$name)")
            return null
        }

        return when (name) {
            "message_start" -> {
                jsonObj["conversationId"]?.jsonPrimitive?.content?.let {
                    conversationId = it
                    Log.i(TAG, "SSE message_start conversationId=$it")
                }
                null
            }

            "text_delta" -> {
                val text = jsonObj["text"]?.jsonPrimitive?.content ?: return null
                LLMStreamEvent.TextDelta(text)
            }

            "tool_use" -> {
                val id = jsonObj["id"]?.jsonPrimitive?.content ?: return null
                val toolName = jsonObj["name"]?.jsonPrimitive?.content ?: return null
                val index = toolIndexCounter++
                val input = jsonObj["input"]
                Log.i(TAG, "SSE tool_use name=$toolName id=$id index=$index")
                LLMStreamEvent.ToolCallStart(index, id, toolName, input)
            }

            "tool_result" -> {
                val toolUseId = jsonObj["toolUseId"]?.jsonPrimitive?.content ?: return null
                val isError = jsonObj["isError"]?.jsonPrimitive?.booleanOrNull ?: false
                val text = flattenMcpContent(jsonObj["content"])

                if (isError) {
                    Log.e(TAG, "SSE tool_result toolUseId=$toolUseId isError=true")
                } else {
                    Log.i(TAG, "SSE tool_result toolUseId=$toolUseId bytes=${text.length}")
                }

                LLMStreamEvent.ToolResult(
                    toolCallId = toolUseId,
                    content = text,
                    isError = isError
                )
            }

            "message_stop" -> {
                val raw = jsonObj["stopReason"]?.jsonPrimitive?.content ?: "unknown"
                Log.i(TAG, "SSE message_stop stopReason=$raw")
                val stop = when (raw) {
                    "end_turn" -> LLMStopReason.END_TURN
                    "tool_use" -> LLMStopReason.TOOL_USE
                    "max_tokens" -> LLMStopReason.MAX_TOKENS
                    else -> LLMStopReason.END_TURN
                }
                // tool_use means the agent is executing a tool server-side,
                // the stream continues — don't emit Done yet
                if (stop == LLMStopReason.TOOL_USE) return null
                LLMStreamEvent.Done(stop)
            }

            "error" -> {
                val code = jsonObj["code"]?.jsonPrimitive?.content ?: "unknown"
                val message = jsonObj["message"]?.jsonPrimitive?.content ?: ""
                Log.e(TAG, "SSE error code=$code message=$message")
                LLMStreamEvent.Error("$code: $message")
            }

            else -> {
                Log.d(TAG, "Unhandled SSE event '$name'")
                null
            }
        }
    }

    /**
     * On a resumed conversation, only the newest user turn is sent; the server
     * merges it with persisted history. On a fresh conversation, full history.
     */
    private fun scopedMessages(messages: List<LLMMessage>): JsonArray {
        val scoped = if (conversationId != null) {
            val lastUser = messages.lastOrNull { it is LLMMessage.User }
            if (lastUser != null) listOf(lastUser) else emptyList()
        } else {
            messages
        }
        return JsonArray(scoped.map { encodeMessage(it) })
    }

    private fun encodeMessage(message: LLMMessage): JsonElement {
        return when (message) {
            is LLMMessage.User -> JsonObject(mapOf(
                "role" to JsonPrimitive("user"),
                "content" to JsonPrimitive(message.text)
            ))
            is LLMMessage.Assistant -> {
                val blocks = mutableListOf<JsonElement>()
                if (!message.text.isNullOrEmpty()) {
                    blocks.add(JsonObject(mapOf(
                        "type" to JsonPrimitive("text"),
                        "text" to JsonPrimitive(message.text)
                    )))
                }
                for (call in message.toolCalls) {
                    blocks.add(JsonObject(mapOf(
                        "type" to JsonPrimitive("tool_use"),
                        "id" to JsonPrimitive(call.id),
                        "name" to JsonPrimitive(call.name),
                        "input" to call.arguments
                    )))
                }
                JsonObject(mapOf(
                    "role" to JsonPrimitive("assistant"),
                    "content" to JsonArray(blocks)
                ))
            }
            is LLMMessage.ToolResults -> {
                val blocks = message.results.map { result ->
                    val block = mutableMapOf<String, JsonElement>(
                        "type" to JsonPrimitive("tool_result"),
                        "tool_use_id" to JsonPrimitive(result.toolCallId),
                        "content" to JsonPrimitive(result.content)
                    )
                    if (result.isError) {
                        block["is_error"] = JsonPrimitive(true)
                    }
                    JsonObject(block)
                }
                JsonObject(mapOf(
                    "role" to JsonPrimitive("user"),
                    "content" to JsonArray(blocks)
                ))
            }
        }
    }

    private fun flattenMcpContent(content: JsonElement?): String {
        if (content == null) return ""
        // If it's a string, return directly
        if (content is JsonPrimitive && content.isString) return content.content
        // If it's an array of content blocks, flatten text blocks
        try {
            val blocks = content.jsonArray
            return blocks.mapNotNull { block ->
                val obj = block.jsonObject
                if (obj["type"]?.jsonPrimitive?.content == "text") {
                    obj["text"]?.jsonPrimitive?.content
                } else null
            }.joinToString("\n")
        } catch (_: Exception) {
            return content.toString()
        }
    }
}
