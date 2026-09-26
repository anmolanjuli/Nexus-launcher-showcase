package com.nexus.launcher.ui.model

sealed class DragState {
    object Idle : DragState()
    data class Dragging(
        val item: DisplayItem,
        val fingerX: Float,
        val fingerY: Float,
        val sourceType: DragSource
    ) : DragState()
    data class Dropping(
        val item: DisplayItem,
        val targetPage: Int,
        val targetCol: Int,
        val targetRow: Int
    ) : DragState()
}

enum class DragSource {
    DRAWER, HOME_SCREEN
}
