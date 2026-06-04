/*
 * MetabindAssistant.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant

import ai.metabind.mcpappshost.LLMMessage
import ai.metabind.mcpappshost.LLMStreamEvent
import ai.metabind.mcpappshost.LLMToolCall
import ai.metabind.mcpappshost.MCPAppsClient
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Manages the full conversation loop for a Metabind AI assistant.
 *
 * Create an instance, retain it at an appropriate scope (e.g. inside a ViewModel),
 * and pass it to [MetabindAssistantView] for a drop-in chat UI. For custom UIs,
 * observe [messages], [isLoading], and [error] directly and call [send] to submit
 * user messages.
 *
 * Call [close] when the instance is no longer needed to cancel the internal
 * coroutine scope.
 *
 * ```kotlin
 * val assistant = remember {
 *     MetabindAssistant(apiKey = key, orgId = orgId, projectId = projectId)
 * }
 * MetabindAssistantView(assistant = assistant)
 * ```
 */
class MetabindAssistant(
    val apiKey: String,
    val orgId: String,
    val projectId: String,
    val agentHost: String = MetabindAgentProvider.PRODUCTION_HOST,
    val mcpHost: String = DEFAULT_MCP_HOST,
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Tool UI content (BindJS or HTML) keyed by tool call ID. */
    private val _toolUIContent = MutableStateFlow<Map<String, ToolUIContent>>(emptyMap())
    val toolUIContent: StateFlow<Map<String, ToolUIContent>> = _toolUIContent.asStateFlow()

    companion object {
        private const val TAG = "MetabindAssistant"
        const val DEFAULT_MCP_HOST = "https://mcp.metabind.ai"
    }

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.Main)
    private val agentProvider = MetabindAgentProvider()
    private val mcpServerUrl = "$mcpHost/$orgId/projects/$projectId"

    private var mcpClient: MCPAppsClient? = null
    private var toolUIMap: Map<String, String> = emptyMap()
    private var llmHistory: MutableList<LLMMessage> = mutableListOf()
    private val pendingContext: MutableMap<String, JsonElement> = linkedMapOf()
    private var initJob: Job? = null
    private var streamJob: Job? = null

    init {
        initJob = scope.launch(Dispatchers.IO) { initMCPClient() }
    }

    private suspend fun initMCPClient() {
        val client = MCPAppsClient(
            url = mcpServerUrl,
            headers = mapOf("authorization" to "Bearer $apiKey")
        )
        mcpClient = client
        try {
            val tools = client.listTools()
            toolUIMap = tools
                .filter { it.ui?.resourceUri != null }
                .associate { it.name to it.ui!!.resourceUri }
            Log.d(TAG, "Loaded ${tools.size} tools, ${toolUIMap.size} with UI: ${toolUIMap.keys}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tools from MCP", e)
        }
    }

    /** Send a user message and begin streaming the response. No-op if already loading. */
    fun send(text: String) {
        if (text.isBlank() || _isLoading.value) return
        Log.d(TAG, "Sending message: `$text`")

        val userMessage = ChatMessage(role = MessageRole.USER, content = text)
        _messages.value = _messages.value + userMessage
        _isLoading.value = true
        _error.value = null

        val modelText = consumePendingContextPrefix()?.let { "$it\n\n$text" } ?: text
        llmHistory.add(LLMMessage.User(modelText))

        streamJob = scope.launch(Dispatchers.IO) {
            initJob?.join()
            if (mcpClient == null) initMCPClient()
            try {
                streamAgentResponse()
            } catch (e: Exception) {
                val message = e.message ?: "Something went wrong"
                _error.value = message
                _messages.value = _messages.value + ChatMessage(
                    role = MessageRole.ERROR,
                    content = message
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Cancel any in-progress response. */
    fun cancel() {
        streamJob?.cancel()
        streamJob = null
        _isLoading.value = false
    }

    /** Clear conversation history and reset the agent's server-side session. */
    fun reset() {
        cancel()
        _messages.value = emptyList()
        _toolUIContent.value = emptyMap()
        _error.value = null
        llmHistory.clear()
        pendingContext.clear()
        agentProvider.resetConversation()
    }

    /** Release the internal coroutine scope. Call when discarding the instance. */
    fun close() {
        supervisorJob.cancel()
    }

    private suspend fun streamAgentResponse() {
        var assistantMessageId: String? = null
        var accumulatedText: String? = null
        val toolCalls = mutableListOf<LLMToolCall>()

        agentProvider.streamMessage(
            baseUrl = agentHost,
            apiKey = apiKey,
            orgId = orgId,
            projectId = projectId,
            messages = llmHistory
        ).collect { event ->
            when (event) {
                is LLMStreamEvent.TextDelta -> {
                    if (accumulatedText == null) {
                        accumulatedText = ""
                        val msg = ChatMessage(role = MessageRole.ASSISTANT, content = "")
                        assistantMessageId = msg.id
                        _messages.value = _messages.value + msg
                    }
                    accumulatedText = accumulatedText + event.text
                    updateAssistantMessage(assistantMessageId!!, accumulatedText!!)
                }

                is LLMStreamEvent.ToolCallStart -> {
                    toolCalls.add(
                        LLMToolCall(
                            id = event.id,
                            name = event.name,
                            arguments = event.arguments ?: JsonObject(emptyMap())
                        )
                    )
                    val toolMsg = ChatMessage(
                        id = event.id,
                        role = MessageRole.TOOL,
                        content = "",
                        toolName = event.name,
                        toolStatus = ToolStatus.LOADING
                    )
                    _messages.value = _messages.value + toolMsg

                    val resourceUri = toolUIMap[event.name]
                    if (resourceUri != null) {
                        fetchToolUIContent(event.id, event.name, resourceUri, event.arguments)
                    }
                }

                is LLMStreamEvent.ToolResult -> {
                    val existing = _toolUIContent.value[event.toolCallId]
                    if (existing != null) {
                        _toolUIContent.value = _toolUIContent.value + (
                            event.toolCallId to existing.withResult(event.content, event.isError)
                        )
                    }
                    updateToolMessage(
                        event.toolCallId,
                        null,
                        if (event.isError) ToolStatus.ERROR else ToolStatus.COMPLETED,
                        event.content
                    )
                    accumulatedText = null
                    assistantMessageId = null
                }

                is LLMStreamEvent.ToolCallArgumentDelta -> {}
                is LLMStreamEvent.ContentBlockStop -> {}

                is LLMStreamEvent.Done -> {
                    if (accumulatedText != null || toolCalls.isNotEmpty()) {
                        llmHistory.add(LLMMessage.Assistant(accumulatedText, toolCalls.toList()))
                    }
                }

                is LLMStreamEvent.Error -> throw Exception(event.message)
            }
        }
    }

    private fun fetchToolUIContent(
        toolCallId: String,
        toolName: String,
        resourceUri: String,
        toolArguments: JsonElement?,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val client = mcpClient ?: return@launch
                val resource = client.readResource(resourceUri)
                val content = ToolUIContent.fromResource(resource, toolArguments)
                _toolUIContent.value = _toolUIContent.value + (toolCallId to content)
                Log.d(TAG, "Loaded UI content for $toolName: ${content::class.simpleName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch UI content for $toolName", e)
            }
        }
    }

    private fun updateAssistantMessage(id: String, text: String) {
        _messages.value = _messages.value.map { if (it.id == id) it.copy(content = text) else it }
    }

    private fun updateToolMessage(id: String, toolName: String?, status: ToolStatus, content: String) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == id) msg.copy(
                content = content,
                toolName = toolName ?: msg.toolName,
                toolStatus = status
            ) else msg
        }
    }

    internal fun clearError() {
        _error.value = null
    }

    internal fun mergePendingContext(content: Map<String, Any?>) {
        for ((key, value) in content) pendingContext[key] = anyToJsonElement(value)
    }

    internal suspend fun callMcpTool(name: String, args: Map<String, Any?>): Any? {
        initJob?.join()
        if (mcpClient == null) initMCPClient()
        val client = mcpClient ?: throw IllegalStateException("MCP client not initialized")
        val argsJson = JsonObject(args.mapValues { anyToJsonElement(it.value) })
        val result = client.callTool(name, argsJson)
        val text = result.textContent
        if (result.isError) throw IllegalStateException("tool '$name' failed: $text")
        if (text.isBlank()) return null
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(text)
        return jsonElementToPlain(parsed)
    }

    private fun consumePendingContextPrefix(): String? {
        if (pendingContext.isEmpty()) return null
        val sorted = pendingContext.entries.sortedBy { it.key }.associate { it.key to it.value }
        pendingContext.clear()
        return "<context>\n${JsonObject(sorted)}\n</context>"
    }

    private fun jsonElementToPlain(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (element.isString) element.content
            else element.content.toBooleanStrictOrNull()
                ?: element.content.toLongOrNull()
                ?: element.content.toDoubleOrNull()
                ?: element.content
        }
        is JsonObject -> element.entries.associate { (k, v) -> k to jsonElementToPlain(v) }
        is JsonArray -> element.map { jsonElementToPlain(it) }
    }

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries
                .filter { it.key is String }
                .associate { (it.key as String) to anyToJsonElement(it.value) }
        )
        is Iterable<*> -> JsonArray(value.map { anyToJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }
}
