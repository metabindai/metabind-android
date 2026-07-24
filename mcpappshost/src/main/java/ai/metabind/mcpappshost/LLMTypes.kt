/*
 * LLMTypes.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.mcpappshost

import kotlinx.serialization.json.JsonElement

sealed class LLMMessage {
    data class User(val text: String) : LLMMessage()
    data class Assistant(val text: String?, val toolCalls: List<LLMToolCall>) : LLMMessage()
    data class ToolResults(val results: List<LLMToolResult>) : LLMMessage()
}

data class LLMToolCall(
    val id: String,
    val name: String,
    val arguments: JsonElement
)

data class LLMToolResult(
    val toolCallId: String,
    val content: String,
    val isError: Boolean = false
)

data class LLMTool(
    val name: String,
    val description: String,
    val inputSchema: JsonElement
)

enum class LLMStopReason {
    END_TURN,
    TOOL_USE,
    MAX_TOKENS
}

sealed class LLMStreamEvent {
    data class TextDelta(val text: String) : LLMStreamEvent()
    data class ToolCallStart(val index: Int, val id: String, val name: String, val arguments: JsonElement? = null) : LLMStreamEvent()
    data class ToolCallArgumentDelta(val fragment: String) : LLMStreamEvent()
    data class ContentBlockStop(val index: Int) : LLMStreamEvent()
    data class ToolResult(
        val toolCallId: String,
        val content: String,
        val isError: Boolean
    ) : LLMStreamEvent()
    data class Done(val stopReason: LLMStopReason) : LLMStreamEvent()
    data class Error(val message: String) : LLMStreamEvent()
}
