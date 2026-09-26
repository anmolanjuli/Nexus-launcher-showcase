package com.nexus.launcher.ui.widgets.notes

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Data model for a single Notes widget instance with status and progress percentage.
 */
data class NoteData(
    val title: String = "",
    val body: String = "",
    val urgency: String = URGENCY_LATER,
    val status: String = STATUS_OPEN,
    val progress: Int = 0,
    val updatedAt: Long = 0L
) {
    fun isEmpty(): Boolean = title.isBlank() && body.isBlank()

    fun isDone(): Boolean = status == STATUS_DONE || progress >= 100

    fun getUrgencyLabel(context: Context): String = when (urgency) {
        URGENCY_DO_FIRST -> context.getString(R.string.notes_urgency_do_first)
        URGENCY_SCHEDULE -> context.getString(R.string.notes_urgency_schedule)
        URGENCY_QUICK -> context.getString(R.string.notes_urgency_quick)
        else -> context.getString(R.string.notes_urgency_later)
    }

    fun getUrgencyColor(tokens: NexusColorTokens): Int = when (urgency) {
        URGENCY_DO_FIRST -> tokens.danger
        URGENCY_SCHEDULE -> tokens.accent
        URGENCY_QUICK -> 0xFFF59E0B.toInt() // Amber / Warning semantic
        else -> tokens.textSecondary
    }

    fun getStatusLabel(context: Context): String = when (status) {
        STATUS_WIP -> context.getString(R.string.notes_status_wip)
        STATUS_DONE -> context.getString(R.string.notes_status_done)
        else -> context.getString(R.string.notes_status_open)
    }

    fun getStatusColor(tokens: NexusColorTokens): Int = when (status) {
        STATUS_DONE -> COLOR_DONE
        STATUS_WIP -> COLOR_WIP
        else -> COLOR_OPEN
    }

    companion object {
        const val URGENCY_DO_FIRST = "DO_FIRST"
        const val URGENCY_SCHEDULE = "SCHEDULE"
        const val URGENCY_QUICK = "QUICK"
        const val URGENCY_LATER = "LATER"

        const val STATUS_OPEN = "OPEN"
        const val STATUS_WIP = "WIP"
        const val STATUS_DONE = "DONE"

        const val COLOR_OPEN = 0xFF64748B.toInt() // Slate / Neutral Open
        const val COLOR_WIP = 0xFFF59E0B.toInt()  // Amber WIP
        const val COLOR_DONE = 0xFF10B981.toInt() // Emerald Green Done

        const val MAX_TITLE_CHARS = 60
        const val MAX_BODY_CHARS = 500
    }
}
