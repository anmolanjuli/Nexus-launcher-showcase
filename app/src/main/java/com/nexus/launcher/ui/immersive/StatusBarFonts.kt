package com.nexus.launcher.ui.immersive

import android.content.Context
import android.graphics.Typeface
import com.nexus.launcher.R
import com.nexus.launcher.typography.AppFontFamily
import com.nexus.launcher.typography.CanvasTypographyHelper
import com.nexus.launcher.typography.DynamicFontProvider
import com.nexus.launcher.typography.FontFamilyController

/** Resolves the status row's typeface from [ImmersiveStatusStyle.fontKey]. */
internal object StatusBarFonts {

    fun typeface(context: Context, fontKey: String, weight: Int): Typeface {
        if (fontKey.isBlank() || fontKey == ImmersiveStatusStyle.FONT_FOLLOW) {
            return CanvasTypographyHelper.getTypeface(context, weight)
        }
        val custom = try {
            FontFamilyController.resolve(context).customFonts.value
        } catch (_: Exception) {
            emptyList()
        }
        return DynamicFontProvider(context, fontKey, custom).getTypeface(weight)
    }

    fun displayName(context: Context, fontKey: String): String {
        if (fontKey.isBlank() || fontKey == ImmersiveStatusStyle.FONT_FOLLOW) {
            return context.getString(R.string.status_font_follow)
        }
        if (fontKey.startsWith("custom:")) {
            val id = fontKey.removePrefix("custom:")
            val match = try {
                FontFamilyController.resolve(context).customFonts.value.firstOrNull { it.id == id }
            } catch (_: Exception) {
                null
            }
            return match?.name ?: context.getString(R.string.typography_custom_font)
        }
        return context.getString(AppFontFamily.fromKey(fontKey).displayNameRes)
    }
}
