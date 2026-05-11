/*
 * Component.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.metabind

data class PreviewComponent(
    val id: String,
    val name: String,
    val content: String,
    val dependencies: Map<String, String>,
    val isContent: Boolean = false
)
