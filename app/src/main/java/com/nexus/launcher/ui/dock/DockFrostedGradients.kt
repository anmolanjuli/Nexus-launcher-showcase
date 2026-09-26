package com.nexus.launcher.ui.dock

import android.graphics.Color

/** Preset left-to-right frosted gradients for dock background (85% opacity). */
internal object DockFrostedGradients {

    const val GRADIENT_ALPHA = 0xD9
    const val PRESET_COUNT = 15

    data class Preset(val name: String, val startRgb: Int, val endRgb: Int)

    val presets: List<Preset> = listOf(
        Preset("Arctic", Color.parseColor("#1A1A2E"), Color.parseColor("#16213E")),
        Preset("Dusk", Color.parseColor("#2D1B69"), Color.parseColor("#11998E")),
        Preset("Ember", Color.parseColor("#1a1a1a"), Color.parseColor("#C0392B")),
        Preset("Forest", Color.parseColor("#134E5E"), Color.parseColor("#71B280")),
        Preset("Rose", Color.parseColor("#1a1a1a"), Color.parseColor("#FF6B9D")),
        Preset("Gold", Color.parseColor("#1a1a1a"), Color.parseColor("#F7971E")),
        Preset("Slate", Color.parseColor("#2C3E50"), Color.parseColor("#3498DB")),
        Preset("Midnight", Color.parseColor("#0F0C29"), Color.parseColor("#302B63")),
        Preset("Ocean", Color.parseColor("#0F2027"), Color.parseColor("#2C5364")),
        Preset("Candy", Color.parseColor("#FC466B"), Color.parseColor("#3F5EFB")),
        Preset("Aurora", Color.parseColor("#00C9FF"), Color.parseColor("#92FE9D")),
        Preset("Volcanic", Color.parseColor("#1a1a1a"), Color.parseColor("#FF4500")),
        Preset("Cosmic", Color.parseColor("#0F0C29"), Color.parseColor("#9B59B6")),
        Preset("Neon", Color.parseColor("#1a1a1a"), Color.parseColor("#39FF14")),
        Preset("Copper", Color.parseColor("#1a1a1a"), Color.parseColor("#B87333"))
    )

    fun clampIndex(index: Int): Int = index.coerceIn(0, presets.lastIndex)

    fun startArgb(index: Int, isLight: Boolean = false): Int {
        val rgb = presets[clampIndex(index)].startRgb
        val adaptedRgb = if (isLight) {
            val r = (Color.red(rgb) + 255) / 2
            val g = (Color.green(rgb) + 255) / 2
            val b = (Color.blue(rgb) + 255) / 2
            Color.rgb(r, g, b)
        } else {
            rgb
        }
        return withAlpha(adaptedRgb)
    }

    fun endArgb(index: Int, isLight: Boolean = false): Int {
        val rgb = presets[clampIndex(index)].endRgb
        val adaptedRgb = if (isLight) {
            val r = (Color.red(rgb) * 0.75f + 255 * 0.25f).toInt()
            val g = (Color.green(rgb) * 0.75f + 255 * 0.25f).toInt()
            val b = (Color.blue(rgb) * 0.75f + 255 * 0.25f).toInt()
            Color.rgb(r, g, b)
        } else {
            rgb
        }
        return withAlpha(adaptedRgb)
    }

    fun labelColorForLabels(index: Int): Int {
        val preset = presets[clampIndex(index)]
        val r = (Color.red(preset.startRgb) + Color.red(preset.endRgb)) / 2
        val g = (Color.green(preset.startRgb) + Color.green(preset.endRgb)) / 2
        val b = (Color.blue(preset.startRgb) + Color.blue(preset.endRgb)) / 2
        return Color.argb(GRADIENT_ALPHA, r, g, b)
    }

    private fun withAlpha(rgb: Int): Int = (GRADIENT_ALPHA shl 24) or (rgb and 0x00FFFFFF)
}
