package com.nexus.launcher.typography

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint

/**
 * Shared zero-allocation utility for Canvas-based renderers (widgets, context menus, overlays)
 * to obtain font-aware Typeface and cached TextPaint objects without allocation in draw passes.
 */
object CanvasTypographyHelper {

    fun getTypeface(context: Context, weight: Int = TypefaceWeightMapper.NORMAL): Typeface {
        return try {
            FontFamilyController.resolve(context).getFontProvider().getTypeface(weight)
        } catch (_: Exception) {
            SystemFontProvider.getTypeface(weight)
        }
    }

    fun getPaint(context: Context, slot: NexusTypeSlot): TextPaint {
        return try {
            NexusTypographyResolver.resolve(context).getPaint(slot)
        } catch (_: Exception) {
            val scale = when (slot) {
                NexusTypeSlot.LABEL_SMALL -> NexusTypeScale.labelSmall
                NexusTypeSlot.BODY -> NexusTypeScale.body
                NexusTypeSlot.BODY_STRONG -> NexusTypeScale.bodyStrong
                NexusTypeSlot.CAPTION -> NexusTypeScale.caption
                NexusTypeSlot.ICON_LABEL -> NexusTypeScale.iconLabel
                NexusTypeSlot.TITLE -> NexusTypeScale.title
            }
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = scale.sizeSp * context.resources.displayMetrics.scaledDensity
                typeface = SystemFontProvider.getTypeface(scale.weight)
                letterSpacing = scale.letterSpacingEm
            }
        }
    }

    fun applyTypeface(context: Context, paint: Paint, weight: Int = TypefaceWeightMapper.NORMAL) {
        paint.typeface = getTypeface(context, weight)
    }
}
