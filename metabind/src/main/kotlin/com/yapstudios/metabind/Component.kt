/*
 * Component.kt.
 *
 * © 2026 Yap Studios LLC
 */
package com.yapstudios.metabind

data class PreviewComponent(
    val id: String,
    val name: String,
    val content: String,
    val dependencies: Map<String, String>,
    val isContent: Boolean = false
)
