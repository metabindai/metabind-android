/*
 * HomeViewModel.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.ui.screens

import ai.metabind.assistant.ChatMessage
import ai.metabind.assistant.MessageRole
import ai.metabind.assistant.MetabindAgentProvider
import ai.metabind.assistant.ToolStatus
import ai.metabind.assistant.ToolUIContent
import ai.metabind.assistant.demo.BuildConfig
import ai.metabind.assistant.demo.data.ApiKeyRepository
import ai.metabind.mcpappshost.LLMMessage
import ai.metabind.mcpappshost.LLMStreamEvent
import ai.metabind.mcpappshost.LLMToolCall
import ai.metabind.mcpappshost.MCPAppsClient
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
    private val agentProvider: MetabindAgentProvider
) : ViewModel() {

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
        private const val TAG = "HomeViewModel"
        private val ORG_ID = BuildConfig.METABIND_ORG_ID
        private val PROJECT_ID = BuildConfig.METABIND_PROJECT_ID
        private val AGENT_HOST = BuildConfig.METABIND_AGENT_HOST
        private val MCP_SERVER_URL =
            "${BuildConfig.METABIND_MCP_HOST}/$ORG_ID/projects/$PROJECT_ID"
    }

    private var mcpClient: MCPAppsClient? = null

    /** Maps tool name -> UI resource URI for tools that have visual UIs. */
    private var toolUIMap: Map<String, String> = emptyMap()

    private var llmHistory: MutableList<LLMMessage> = mutableListOf()
    private val pendingContext: MutableMap<String, JsonElement> = linkedMapOf()
    private var initJob: Job? = null

    init {
        initJob = viewModelScope.launch(Dispatchers.IO) {
            initMCPClient()
        }
    }

    private suspend fun initMCPClient() {
        val apiKey = apiKeyRepository.getApiKey() ?: return
        val client = MCPAppsClient(
            url = MCP_SERVER_URL,
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

    fun sendMessage(text: String) {
        Log.d(TAG, "Sending a message: `$text`")
        val apiKey = apiKeyRepository.getApiKey() ?: return

        val userMessage = ChatMessage(role = MessageRole.USER, content = text)
        _messages.value = _messages.value + userMessage
        _isLoading.value = true
        _error.value = null

        // Pending context (from BindJS host.updateModelContext) is prefixed
        // to the user-visible text on the model side only.
        val modelText = consumePendingContextPrefix()?.let { "$it\n\n$text" } ?: text
        llmHistory.add(LLMMessage.User(modelText))

        viewModelScope.launch(Dispatchers.IO) {
            // Wait for MCP init to complete before processing
            initJob?.join()
            if (mcpClient == null) initMCPClient()

            try {
                streamAgentResponse(apiKey)
            } catch (e: Exception) {
                _error.value = e.message ?: "Something went wrong"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun streamAgentResponse(apiKey: String) {
        var assistantMessageId: String? = null
        var accumulatedText: String? = null
        val toolCalls = mutableListOf<LLMToolCall>()

        agentProvider.streamMessage(
            baseUrl = AGENT_HOST,
            apiKey = apiKey,
            orgId = ORG_ID,
            projectId = PROJECT_ID,
            messages = llmHistory
        ).collect { event ->
            when (event) {
                is LLMStreamEvent.TextDelta -> {
                    Log.d(TAG, "TextDelta len=${event.text.length} text=${event.text.take(40)}")
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
                            arguments = event.arguments ?: kotlinx.serialization.json.JsonObject(emptyMap())
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

                    // Fetch UI content if this tool has a UI resource
                    val resourceUri = toolUIMap[event.name]
                    if (resourceUri != null) {
                        fetchToolUIContent(event.id, event.name, resourceUri, event.arguments)
                    }
                }

                is LLMStreamEvent.ToolResult -> {
                    // Update tool UI content with result
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
                    // Reset for next text block
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

                is LLMStreamEvent.Error -> {
                    throw Exception(event.message)
                }
            }
        }
    }

    private fun fetchToolUIContent(
        toolCallId: String,
        toolName: String,
        resourceUri: String,
        toolArguments: JsonElement?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = mcpClient ?: return@launch
                val resource = client.readResource(resourceUri)

                val content = ToolUIContent.fromResource(resource, toolArguments)
                _toolUIContent.value = _toolUIContent.value + (toolCallId to content)

                val type = when (content) {
                    is ToolUIContent.BindJS -> "BindJS(${content.layoutComponentName})"
                    is ToolUIContent.Html -> "HTML(${resource.mimeType})"
                }
                Log.d(TAG, "Loaded UI content for $toolName: $type")
                when (content) {
                    is ToolUIContent.Html ->
                        Log.d(TAG, "HTML content for $toolName:\n${content.html}")
                    is ToolUIContent.BindJS ->
                        Log.d(TAG, "BindJS bundle for $toolName:\n${resource.text}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch UI content for $toolName", e)
            }
        }
    }

    private fun updateAssistantMessage(id: String, text: String) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == id) msg.copy(content = text) else msg
        }
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

    fun clearError() {
        _error.value = null
    }

    /**
     * Invoke a tool on the MCP server on behalf of a BindJS view that called
     * `host.toolCall(name, args)`. Returns the parsed tool result (typically
     * the JSON the JS data source's handler returned), or throws if the call
     * fails. We re-init the MCP client if it hasn't connected yet — the
     * BindJS view may render before [initMCPClient] has finished on first
     * launch.
     */
    suspend fun callMcpTool(name: String, args: Map<String, Any?>): Any? {
        initJob?.join()
        if (mcpClient == null) initMCPClient()
        val client = mcpClient ?: throw IllegalStateException("MCP client not initialized")

        val argsJson = JsonObject(args.mapValues { anyToJsonElement(it.value) })
        val result = client.callTool(name, argsJson)
        val text = result.textContent
        if (result.isError) {
            throw IllegalStateException("tool '$name' failed: $text")
        }
        if (text.isBlank()) return null
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(text)
        return jsonElementToPlain(parsed)
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

    /**
     * Merge structured context contributed by a BindJS view (via
     * `host.updateModelContext({...})`). The next [sendMessage] will prefix
     * this as a `<context>...</context>` block in the model-visible message.
     */
    fun mergePendingContext(content: Map<String, Any?>) {
        for ((key, value) in content) {
            pendingContext[key] = anyToJsonElement(value)
        }
    }

    private fun consumePendingContextPrefix(): String? {
        if (pendingContext.isEmpty()) return null
        val sorted = pendingContext.entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
        pendingContext.clear()
        val json = JsonObject(sorted).toString()
        return "<context>\n$json\n</context>"
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
