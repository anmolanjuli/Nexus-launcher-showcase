package com.nexus.launcher.typography

import android.graphics.Typeface
import android.os.Build

/** Default system FontProvider implementation using platform Typeface constants. */
object SystemFontProvider : FontProvider {
    override fun getTypeface(weight: Int): Typeface {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(
                Typeface.DEFAULT,
                TypefaceWeightMapper.toOpenTypeWeight(weight),
                TypefaceWeightMapper.isItalic(weight)
            )
        } else {
            val style = TypefaceWeightMapper.toLegacyStyle(weight)
            if (style == Typeface.BOLD || style == Typeface.BOLD_ITALIC) {
                Typeface.DEFAULT_BOLD
            } else {
                Typeface.DEFAULT
            }
        }
    }
}
