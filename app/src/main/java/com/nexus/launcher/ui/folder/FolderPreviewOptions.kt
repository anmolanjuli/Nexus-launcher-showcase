package com.nexus.launcher.ui.folder

object FolderPreviewOptions {
    /**
     * Index is the persisted `FolderConfig.previewStyle` id, so this list must never be
     * reordered — doing so would silently reinterpret every saved folder (shuffling Bento in
     * front of Custom Icon here would turn every existing Custom Icon folder into a Bento one,
     * and `FolderEditBottomSheet` keys its cover-icon picker off the literal id 3).
     * Append only. Picker order is [displayOrder]'s job.
     */
    val labels = listOf(
        "Grid",
        "Fan of 3",
        "Hero + Orbit",
        "Custom Icon",
        "Bento"
    )

    /** Order the picker presents the styles in, by stored id. Safe to rearrange. */
    private val displayOrder = listOf(0, 1, 2, 4, 3)

    fun getAvailableLabels(context: android.content.Context, spanX: Int, spanY: Int): List<Pair<String, String>> {
        val labelRes = listOf(
            com.nexus.launcher.R.string.folder_preview_style_grid,
            com.nexus.launcher.R.string.folder_preview_style_fan,
            com.nexus.launcher.R.string.folder_preview_style_hero_orbit,
            com.nexus.launcher.R.string.folder_preview_style_custom_icon,
            com.nexus.launcher.R.string.folder_preview_style_bento
        )
        return displayOrder.map { id -> id.toString() to context.getString(labelRes[id]) }.toMutableList()
    }

    fun getAvailableLabels(spanX: Int, spanY: Int): List<Pair<String, String>> {
        val available = displayOrder.map { id -> id.toString() to labels[id] }.toMutableList()
        // if (spanX >= 2 && spanY >= 2) {
        //     available.add("5" to "Summary Card") // Disabled until post-V1
        // }
        return available
    }

    /** Maps stored style index to internal draw style index. */
    fun drawStyle(stored: Int, spanX: Int = 1, spanY: Int = 1): Int {
        if (stored == 5 && (spanX < 2 || spanY < 2)) return 0
        return when (stored) {
            0 -> 0 // Grid
            1 -> 5 // Fan of 3
            2 -> 13 // Hero + Orbit
            3 -> 10 // Custom Icon
            4 -> 15 // Bento
            5 -> 14 // Summary Card (still disabled above)
            else -> 0
        }
    }
}
