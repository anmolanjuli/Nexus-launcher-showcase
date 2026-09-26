package com.nexus.launcher.ui.model

sealed class SelectionState {
    object Idle : SelectionState()
    data class Selecting(
        val selectedPackages: Set<String> = emptySet(),
        val selectedIds: Set<Int> = emptySet(),
        val source: SelectionSource = SelectionSource.DRAWER
    ) : SelectionState()
}

enum class SelectionSource {
    DRAWER,
    HOME_SCREEN
}
