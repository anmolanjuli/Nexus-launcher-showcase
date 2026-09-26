package com.nexus.launcher.typography

/** Represents a user-imported custom font asset stored in app-private storage. */
data class CustomFontEntry(
    val id: String,
    val name: String,
    val filePath: String
)
