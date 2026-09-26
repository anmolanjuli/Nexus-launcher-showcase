package com.nexus.launcher.ui.widgets.progress

import org.json.JSONObject
import java.util.UUID

/**
 * Data model for an individual progress track slot in the Progress Bar widget.
 */
data class ProgressTrack(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val presetType: String? = null, // "YEAR", "MONTH", "WEEK", or null for custom
    val startDateMillis: Long = 0L,
    val targetDateMillis: Long = 0L,
    val displayMode: String = MODE_REMAINING_DAYS,
    val colorHex: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put(KEY_ID, id)
            put(KEY_LABEL, label)
            if (presetType != null) put(KEY_PRESET_TYPE, presetType)
            put(KEY_START_DATE, startDateMillis)
            put(KEY_TARGET_DATE, targetDateMillis)
            put(KEY_DISPLAY_MODE, displayMode)
            if (colorHex != null) put(KEY_COLOR_HEX, colorHex)
        }
    }

    companion object {
        const val PRESET_YEAR = "YEAR"
        const val PRESET_MONTH = "MONTH"
        const val PRESET_WEEK = "WEEK"
        const val PRESET_CUSTOM = "CUSTOM"

        const val MODE_REMAINING_DAYS = "REMAINING_DAYS"
        const val MODE_REMAINING_PERCENT = "REMAINING_PERCENT"
        const val MODE_ELAPSED_DAYS = "ELAPSED_DAYS"
        const val MODE_ELAPSED_PERCENT = "ELAPSED_PERCENT"

        private const val KEY_ID = "id"
        private const val KEY_LABEL = "label"
        private const val KEY_PRESET_TYPE = "presetType"
        private const val KEY_START_DATE = "startDate"
        private const val KEY_TARGET_DATE = "targetDate"
        private const val KEY_DISPLAY_MODE = "displayMode"
        private const val KEY_COLOR_HEX = "colorHex"

        fun fromJson(json: JSONObject): ProgressTrack {
            return ProgressTrack(
                id = json.optString(KEY_ID, UUID.randomUUID().toString()),
                label = json.optString(KEY_LABEL, "Track"),
                presetType = if (json.has(KEY_PRESET_TYPE)) json.getString(KEY_PRESET_TYPE) else null,
                startDateMillis = json.optLong(KEY_START_DATE, 0L),
                targetDateMillis = json.optLong(KEY_TARGET_DATE, 0L),
                displayMode = json.optString(KEY_DISPLAY_MODE, MODE_REMAINING_DAYS),
                colorHex = if (json.has(KEY_COLOR_HEX)) json.getString(KEY_COLOR_HEX) else null
            )
        }
    }
}
