package com.nexus.launcher.typography

import android.graphics.Typeface

/**
 * OpenType weights (1–1000) used by [Typeface.create] on API 28+.
 * [Typeface.NORMAL]/[Typeface.BOLD] are legacy style bits (0/1), not weights.
 */
object TypefaceWeightMapper {
    const val NORMAL = 400
    const val BOLD = 700

    fun toOpenTypeWeight(weightOrStyle: Int): Int {
        return when (weightOrStyle) {
            Typeface.NORMAL -> NORMAL
            Typeface.BOLD -> BOLD
            Typeface.ITALIC -> NORMAL
            Typeface.BOLD_ITALIC -> BOLD
            else -> weightOrStyle
        }
    }

    fun isItalic(weightOrStyle: Int): Boolean {
        return weightOrStyle == Typeface.ITALIC || weightOrStyle == Typeface.BOLD_ITALIC
    }

    fun toLegacyStyle(weightOrStyle: Int): Int {
        val italic = isItalic(weightOrStyle)
        val bold = toOpenTypeWeight(weightOrStyle) >= BOLD
        return when {
            bold && italic -> Typeface.BOLD_ITALIC
            italic -> Typeface.ITALIC
            bold -> Typeface.BOLD
            else -> Typeface.NORMAL
        }
    }
}
