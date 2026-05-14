/*
 * ChatMessage.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant

import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL
}

enum class ToolStatus {
    LOADING,
    COMPLETED,
    ERROR
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val toolName: String? = null,
    val toolStatus: ToolStatus? = null
)
