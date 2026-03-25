package com.yapstudios.metabind

data class PreviewComponent(
    val id: String,
    val name: String,
    val content: String,
    val dependencies: Map<String, String>
)
