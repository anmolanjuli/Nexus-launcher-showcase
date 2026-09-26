package com.nexus.launcher.typography

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import java.io.File

/** Dynamic FontProvider implementation resolving Typefaces for system, bundled, and custom user fonts. */
class DynamicFontProvider(
    private val context: Context? = null,
    private val familyName: String? = null,
    @FontRes private val fontResId: Int? = null,
    private val customFontPath: String? = null
) : FontProvider {

    constructor(context: Context?, font: AppFontFamily?) : this(
        context = context,
        familyName = font?.familyName,
        fontResId = font?.fontResId,
        customFontPath = null
    )

    constructor(context: Context?, fontKey: String?, customFonts: List<CustomFontEntry> = emptyList()) : this(
        context = context,
        familyName = if (fontKey != null && !fontKey.startsWith("custom:")) AppFontFamily.fromKey(fontKey).familyName else null,
        fontResId = if (fontKey != null && !fontKey.startsWith("custom:")) AppFontFamily.fromKey(fontKey).fontResId else null,
        customFontPath = if (fontKey != null && fontKey.startsWith("custom:")) {
            val id = fontKey.removePrefix("custom:")
            customFonts.firstOrNull { it.id == id }?.filePath
        } else null
    )

    override fun getTypeface(weight: Int): Typeface {
        val baseTypeface = if (customFontPath != null && File(customFontPath).exists()) {
            try {
                Typeface.createFromFile(customFontPath)
            } catch (e: Exception) {
                Typeface.DEFAULT
            }
        } else if (fontResId != null && context != null) {
            try {
                ResourcesCompat.getFont(context, fontResId) ?: Typeface.DEFAULT
            } catch (e: Exception) {
                Typeface.DEFAULT
            }
        } else if (familyName != null) {
            Typeface.create(familyName, Typeface.NORMAL)
        } else {
            Typeface.DEFAULT
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(
                baseTypeface,
                TypefaceWeightMapper.toOpenTypeWeight(weight),
                TypefaceWeightMapper.isItalic(weight)
            )
        } else {
            Typeface.create(baseTypeface, TypefaceWeightMapper.toLegacyStyle(weight))
        }
    }
}
