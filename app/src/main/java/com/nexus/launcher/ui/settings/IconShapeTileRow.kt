package com.nexus.launcher.ui.settings

import android.content.Context
import android.view.View
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

/**
 * Global icon-shape tiles for [IconCustomizationSheet].
 * IDs match [com.nexus.launcher.ui.icons.IconShapePaths] — not folder shapes.
 */
object IconShapeTileRow {

    data class Option(val id: Int, @androidx.annotation.StringRes val labelRes: Int)

    /**
     * Sentinel for Icon Edit: follow global Theme shape (no per-app override).
     * Never passed to [com.nexus.launcher.ui.icons.IconShapeMasker].
     */
    const val FOLLOW_GLOBAL = -2

    /**
     * Theme sheet options (no Global tile), in picker order.
     *
     * The three everyday choices lead — System, Circle, Squircle — because they are what most
     * people pick; the geometric and decorative shapes follow. Squircle in particular was buried
     * at the end despite being the Android default look.
     *
     * Shield (3), Cloud (8) and Cube (10) were dropped: Cloud's bumps sit over the lower 60% of
     * the bounds and cut away the top third of every icon, Cube is a 3/4 perspective solid that
     * shears flat icon art, and Shield spans only 72% of the width while tapering to a point.
     * Their ids stay retired — see [com.nexus.launcher.ui.icons.IconShapePaths].
     */
    val OPTIONS = listOf(
        Option(-1, com.nexus.launcher.R.string.icon_shape_system),
        Option(0, com.nexus.launcher.R.string.icon_shape_circle),
        Option(11, com.nexus.launcher.R.string.icon_shape_squircle),
        Option(12, com.nexus.launcher.R.string.icon_shape_rounded),
        Option(9, com.nexus.launcher.R.string.icon_shape_square),
        Option(7, com.nexus.launcher.R.string.icon_shape_pill),
        Option(1, com.nexus.launcher.R.string.icon_shape_hex),
        Option(15, com.nexus.launcher.R.string.icon_shape_soft_hex),
        Option(13, com.nexus.launcher.R.string.icon_shape_octagon),
        Option(14, com.nexus.launcher.R.string.icon_shape_diamond),
        Option(2, com.nexus.launcher.R.string.icon_shape_pebble),
        Option(6, com.nexus.launcher.R.string.icon_shape_teardrop),
        Option(4, com.nexus.launcher.R.string.icon_shape_leaf),
        Option(5, com.nexus.launcher.R.string.icon_shape_badge)
    )

    /** Icon Edit options — Global first, then Theme shapes. */
    val PER_APP_OPTIONS = listOf(Option(FOLLOW_GLOBAL, com.nexus.launcher.R.string.icon_shape_global)) + OPTIONS

    fun entriesFor(context: Context, options: List<Option>): List<IconShapeCarouselEntry> {
        val global = options.firstOrNull { it.id == FOLLOW_GLOBAL }
        val rest = options.filter { it.id != FOLLOW_GLOBAL }
        return buildList {
            if (global != null) {
                add(IconShapeCarouselEntry.Section(context.getString(R.string.icon_shape_section_override)))
                add(IconShapeCarouselEntry.Shape(global.id, context.getString(global.labelRes)))
                add(IconShapeCarouselEntry.Section(context.getString(R.string.icon_shape_section_shapes)))
            }
            rest.forEach { opt -> add(IconShapeCarouselEntry.Shape(opt.id, context.getString(opt.labelRes))) }
        }
    }

    fun build(
        context: Context,
        density: Float,
        selectedId: Int,
        options: List<Option> = OPTIONS,
        @Suppress("UNUSED_PARAMETER") sectionTitle: String = "Icon Shape",
        @Suppress("UNUSED_PARAMETER") horizontalPaddingDp: Float = 16f,
        @Suppress("UNUSED_PARAMETER") tokens: NexusColorTokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        },
        onSelect: (Int) -> Unit
    ): View {
        return IconShapeCarouselRow(context).apply {
            configure(entriesFor(context, options), selectedId)
            onValueChanged = onSelect
        }
    }

    fun refreshSelection(section: View, selectedId: Int, @Suppress("UNUSED_PARAMETER") tokens: NexusColorTokens) {
        (section as? IconShapeCarouselRow)?.setSelectedId(selectedId)
    }
}
