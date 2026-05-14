/*
 * BindJSToolContent.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant

import ai.metabind.mcpappshost.ResourceContent
import com.yapstudios.bindjs.DesignerComponent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents the rendered UI content for a tool call.
 * Mirrors the iOS MCPAppsHost's ResolvedAppContent with two content types:
 * - BindJS: Native component rendering via the BindJS JS runtime
 * - HTML: Web content rendered in a WebView
 */
sealed class ToolUIContent {
    abstract val toolArguments: JsonElement?
    abstract val toolResultText: String?
    abstract val isError: Boolean

    abstract fun withResult(resultText: String, isError: Boolean): ToolUIContent

    data class BindJS(
        val designerComponent: DesignerComponent,
        val layoutComponentName: String,
        override val toolArguments: JsonElement? = null,
        override val toolResultText: String? = null,
        override val isError: Boolean = false
    ) : ToolUIContent() {
        override fun withResult(resultText: String, isError: Boolean) =
            copy(toolResultText = resultText, isError = isError)
    }

    data class Html(
        val html: String,
        override val toolArguments: JsonElement? = null,
        override val toolResultText: String? = null,
        override val isError: Boolean = false
    ) : ToolUIContent() {
        override fun withResult(resultText: String, isError: Boolean) =
            copy(toolResultText = resultText, isError = isError)
    }

    companion object {
        /**
         * Create the appropriate content type from an MCP resource based on its mimeType.
         */
        fun fromResource(
            resource: ResourceContent,
            toolArguments: JsonElement? = null
        ): ToolUIContent {
            val text = resource.text
                ?: throw IllegalStateException("Empty resource content")

            return when {
                resource.mimeType.contains("bindjs") || resource.mimeType == "application/json" -> {
                    parseBindJSBundle(text, toolArguments)
                }
                resource.mimeType.contains("html") -> {
                    Html(html = text, toolArguments = toolArguments)
                }
                else -> {
                    throw IllegalStateException("Unsupported resource mimeType: ${resource.mimeType}")
                }
            }
        }

        /**
         * Parse a BindJS bundle JSON into a DesignerComponent.
         *
         * Bundle format:
         * ```json
         * {
         *   "layoutComponentName": "Layout",
         *   "packageVersion": "1.0.0",
         *   "package": {
         *     "compiled": {
         *       "components": { "Layout": "<js>", "Card": "<js>" }
         *     }
         *   }
         * }
         * ```
         */
        private fun parseBindJSBundle(bundleJson: String, toolArguments: JsonElement?): BindJS {
            val bundle = kotlinx.serialization.json.Json.parseToJsonElement(bundleJson).jsonObject
            val layoutName = bundle["layoutComponentName"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing layoutComponentName in BindJS bundle")

            val pkg = bundle["package"]?.jsonObject
                ?: throw IllegalArgumentException("Missing package in BindJS bundle")
            val compiled = pkg["compiled"]?.jsonObject
                ?: throw IllegalArgumentException("Missing compiled in BindJS bundle")
            val components = compiled["components"]?.jsonObject
                ?: throw IllegalArgumentException("Missing components in BindJS bundle")

            val dependencies = components.entries
                .filter { it.key != layoutName }
                .map { (name, js) ->
                    DesignerComponent(name = name, content = js.jsonPrimitive.content)
                }

            val rootContent = components[layoutName]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Layout component '$layoutName' not found in bundle")

            val designerComponent = DesignerComponent(
                name = layoutName,
                content = rootContent,
                dependencies = dependencies
            )

            return BindJS(
                designerComponent = designerComponent,
                layoutComponentName = layoutName,
                toolArguments = toolArguments
            )
        }
    }
}
