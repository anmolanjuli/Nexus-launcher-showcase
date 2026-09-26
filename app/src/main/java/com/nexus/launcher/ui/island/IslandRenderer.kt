package com.nexus.launcher.ui.island

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens

object IslandRenderer {

    fun drawPill(
        canvas: Canvas,
        rect: RectF,
        tokens: NexusColorTokens,
        fillPaint: Paint,
        strokePaint: Paint,
        pulse: Float,
        density: Float,
    ) {
        val radius = rect.height() / 2f
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.BLACK
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (pulse <= 0f) return
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = IslandGeometry.STROKE_DP * density
        strokePaint.color = ColorUtils.blendARGB(Color.BLACK, tokens.danger, pulse.coerceIn(0f, 1f))
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }
}
