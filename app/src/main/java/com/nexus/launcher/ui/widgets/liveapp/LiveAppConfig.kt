package com.nexus.launcher.ui.widgets.liveapp

import com.nexus.launcher.ui.dock.DockFrostedGradients
import com.nexus.launcher.ui.widgets.BoxSlotGrid
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import org.json.JSONObject

data class LiveAppConfig(
    val backgroundMode: String = NexusWidgetConfig.BG_GLASS,
    val surfaceOpacity: Float = DEFAULT_OPACITY,
    val glassRefraction: Float = DEFAULT_REFRACTION,
    val frostedGradientIndex: Int = 0,
    val isExpressive: Boolean = false,
    val isFlushBorder: Boolean = false,
    val wFrac: Float = -1f,
    val hFrac: Float = -1f,
    val showLabels: Boolean = false,
    val iconSpacing: Int = BoxSlotGrid.SPACING_NORMAL,
    val gridCols: Int = DEFAULT_GRID_COLS,
    val gridRows: Int = DEFAULT_GRID_ROWS,
    val themeMode: String = NexusWidgetConfig.THEME_FOLLOW,
    val shapePreset: String = PRESET_GRID
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put(KEY_KIND, KIND)
        root.put(KEY_BG_MODE, backgroundMode)
        root.put(KEY_OPACITY, surfaceOpacity.coerceIn(MIN_OPACITY, 1f).toDouble())
        root.put(KEY_REFRACTION, glassRefraction.coerceIn(0f, 1f).toDouble())
        root.put(KEY_FROSTED, frostedGradientIndex)
        root.put(KEY_EXPRESSIVE, isExpressive)
        root.put(KEY_FLUSH_BORDER, isFlushBorder)
        if (wFrac > 0f) root.put(KEY_W_FRAC, wFrac.toDouble())
        if (hFrac > 0f) root.put(KEY_H_FRAC, hFrac.toDouble())
        root.put(KEY_SHOW_LABELS, showLabels)
        root.put(KEY_ICON_SPACING, iconSpacing)
        root.put(KEY_GRID_COLS, gridCols.coerceIn(MIN_GRID_DIM, MAX_GRID_DIM))
        root.put(KEY_GRID_ROWS, gridRows.coerceIn(MIN_GRID_DIM, MAX_GRID_DIM))
        root.put(KEY_THEME_MODE, themeMode)
        root.put(KEY_SHAPE_PRESET, shapePreset)
        return root.toString()
    }

    companion object {
        const val KIND = "live_app_box"
        const val DEFAULT_OPACITY = 0.82f
        const val MIN_OPACITY = 0f
        const val DEFAULT_REFRACTION = 0.70f
        const val DEFAULT_GRID_COLS = 3
        const val DEFAULT_GRID_ROWS = 3
        const val MIN_GRID_DIM = 1
        const val MAX_GRID_DIM = 9
        const val MAX_ICONS = 9

        const val PRESET_GRID = "grid"
        const val PRESET_HORIZONTAL = "horizontal"
        const val PRESET_VERTICAL = "vertical"
        const val PRESET_COMPACT = "compact"

        private const val KEY_KIND = "kind"
        private const val KEY_BG_MODE = "backgroundMode"
        private const val KEY_OPACITY = "surfaceOpacity"
        private const val KEY_REFRACTION = "glassRefraction"
        private const val KEY_FROSTED = "frostedGradientIndex"
        private const val KEY_EXPRESSIVE = "isExpressive"
        private const val KEY_FLUSH_BORDER = "isFlushBorder"
        private const val KEY_W_FRAC = "wFrac"
        private const val KEY_H_FRAC = "hFrac"
        private const val KEY_SHOW_LABELS = "showLabels"
        private const val KEY_ICON_SPACING = "iconSpacing"
        private const val KEY_GRID_COLS = "gridCols"
        private const val KEY_GRID_ROWS = "gridRows"
        private const val KEY_THEME_MODE = "themeMode"
        private const val KEY_SHAPE_PRESET = "shapePreset"

        fun parse(json: String?): LiveAppConfig {
            if (json.isNullOrBlank()) return LiveAppConfig()
            return try {
                val root = JSONObject(json)
                val bgRaw = root.optString(KEY_BG_MODE, NexusWidgetConfig.BG_GLASS).uppercase()
                val bgMode = when (bgRaw) {
                    NexusWidgetConfig.BG_SOLID -> NexusWidgetConfig.BG_SOLID
                    NexusWidgetConfig.BG_NEUMORPHIC -> NexusWidgetConfig.BG_NEUMORPHIC
                    NexusWidgetConfig.BG_FROSTED -> NexusWidgetConfig.BG_FROSTED
                    else -> NexusWidgetConfig.BG_GLASS
                }
                val themeRaw = root.optString(KEY_THEME_MODE, NexusWidgetConfig.THEME_FOLLOW).uppercase()
                val themeMode = when (themeRaw) {
                    NexusWidgetConfig.THEME_LIGHT -> NexusWidgetConfig.THEME_LIGHT
                    NexusWidgetConfig.THEME_DARK -> NexusWidgetConfig.THEME_DARK
                    else -> NexusWidgetConfig.THEME_FOLLOW
                }
                LiveAppConfig(
                    backgroundMode = bgMode,
                    surfaceOpacity = root.optDouble(KEY_OPACITY, DEFAULT_OPACITY.toDouble())
                        .toFloat().coerceIn(MIN_OPACITY, 1f),
                    glassRefraction = root.optDouble(KEY_REFRACTION, DEFAULT_REFRACTION.toDouble())
                        .toFloat().coerceIn(0f, 1f),
                    frostedGradientIndex = root.optInt(KEY_FROSTED, 0)
                        .coerceIn(0, DockFrostedGradients.PRESET_COUNT - 1),
                    isExpressive = root.optBoolean(KEY_EXPRESSIVE, false),
                    isFlushBorder = root.optBoolean(KEY_FLUSH_BORDER, false),
                    wFrac = root.optDouble(KEY_W_FRAC, -1.0).toFloat(),
                    hFrac = root.optDouble(KEY_H_FRAC, -1.0).toFloat(),
                    showLabels = root.optBoolean(KEY_SHOW_LABELS, false),
                    // Absent in boxes saved before Icon spacing existed: they get Normal.
                    iconSpacing = BoxSlotGrid.parseSpacing(root.optInt(KEY_ICON_SPACING, BoxSlotGrid.SPACING_NORMAL)),
                    gridCols = root.optInt(KEY_GRID_COLS, DEFAULT_GRID_COLS).coerceIn(MIN_GRID_DIM, MAX_GRID_DIM),
                    gridRows = root.optInt(KEY_GRID_ROWS, DEFAULT_GRID_ROWS).coerceIn(MIN_GRID_DIM, MAX_GRID_DIM),
                    themeMode = themeMode,
                    shapePreset = root.optString(KEY_SHAPE_PRESET, PRESET_GRID)
                )
            } catch (_: Exception) {
                LiveAppConfig()
            }
        }
    }
}
