package com.nexus.launcher.ui.dock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

/** Dragging/hover exclusion helper and search icon painter for dock render passes. */
internal object DockInternalDragRenderer {

    private val searchRingFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val searchRingStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private var searchIconDrawable: Drawable? = null

    fun excludeItemId(
        isInternalDrag: Boolean,
        draggedItemId: Int?,
        outboundDraggedId: Int?
    ): Int? {
        if (isInternalDrag) return draggedItemId
        return draggedItemId ?: outboundDraggedId
    }

    fun activeDraggedItemId(
        isInternalDrag: Boolean,
        draggedItemId: Int?,
        outboundDraggedId: Int?
    ): Int? = excludeItemId(isInternalDrag, draggedItemId, outboundDraggedId)

    fun shouldDrawItem(item: HomeScreenItem, excludeId: Int?): Boolean =
        excludeId == null || item.id != excludeId

    fun drawSearchIcon(
        canvas: Canvas,
        context: Context?,
        left: Float,
        top: Float,
        iconSizePx: Float,
        scale: Float,
        density: Float
    ) {
        val ctx = context ?: return
        val size = iconSizePx * scale
        val cx = left + iconSizePx / 2f
        val cy = top + iconSizePx / 2f
        val radius = size / 2f

        val tokens = try {
            ThemeObserver.currentTokens(ctx)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        searchRingFillPaint.color = (tokens.textPrimary and 0x00FFFFFF) or 0x1A000000
        searchRingStrokePaint.color = tokens.divider
        searchRingStrokePaint.strokeWidth = 1.25f * density
        canvas.drawCircle(cx, cy, radius, searchRingFillPaint)
        canvas.drawCircle(cx, cy, radius - searchRingStrokePaint.strokeWidth / 2f, searchRingStrokePaint)

        var drawable = searchIconDrawable
        if (drawable == null) {
            drawable = ContextCompat.getDrawable(ctx, R.drawable.ic_search)?.mutate()
            searchIconDrawable = drawable
        }
        if (drawable == null) return
        DrawableCompat.setTint(drawable, tokens.textPrimary)
        val iconInset = size * 0.28f
        val iconDrawSize = size - iconInset * 2f
        DockIconPainter.drawIcon(
            canvas, drawable, cx - iconDrawSize / 2f, cy - iconDrawSize / 2f, iconDrawSize, 1f
        )
    }

    fun drawNeumorphicTile(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        tokens: NexusColorTokens,
        density: Float
    ) {
        val palette = com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.resolvePalette(tokens)
        val radius = size / 2f
        val bounds = android.graphics.RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        if (com.nexus.launcher.ui.glass.FrostedGlassEngine.isDefaultFlatStyleEnabled) {
            com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawFlatSurface(
                canvas = canvas,
                bounds = bounds,
                radius = radius,
                shapeStyle = 0,
                palette = palette,
                dp = density
            )
        } else {
            com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawRaisedSurface(
                canvas = canvas,
                bounds = bounds,
                radius = radius,
                shapeStyle = 0,
                palette = palette,
                dp = density
            )
        }
    }
}
