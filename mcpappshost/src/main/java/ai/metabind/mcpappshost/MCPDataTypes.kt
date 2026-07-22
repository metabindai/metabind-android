/*
 * MCPDataTypes.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.mcpappshost

import kotlinx.serialization.json.JsonElement

data class MCPToolDefinition(
    val name: String,
    val description: String?,
    val inputSchema: JsonElement?,
    val ui: UIMetadata? = null
)

data class UIMetadata(
    val resourceUri: String,
    val visibility: Set<String> = emptySet()
)

sealed class ContentBlock {
    data class Text(val text: String) : ContentBlock()
    data class Image(val data: String, val mimeType: String) : ContentBlock()
    data class Resource(val uri: String, val mimeType: String, val text: String?) : ContentBlock()
}

data class ToolResult(
    val content: List<ContentBlock>,
    val isError: Boolean = false
) {
    companion object {
        fun text(text: String, isError: Boolean = false) = ToolResult(
            content = listOf(ContentBlock.Text(text)),
            isError = isError
        )
    }

    val textContent: String
        get() = content.filterIsInstance<ContentBlock.Text>()
            .joinToString("\n") { it.text }
}

data class ResourceContent(
    val uri: String,
    val mimeType: String,
    val text: String? = null,
    val blob: String? = null
)
