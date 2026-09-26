package com.nexus.launcher.ui.contextmenu

data class UniversalRadialMenuItem(
    val label: String,
    val iconRes: Int,
    val action: () -> Unit
)
