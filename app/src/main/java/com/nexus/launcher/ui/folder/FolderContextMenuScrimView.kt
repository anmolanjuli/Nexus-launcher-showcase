package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View

class FolderContextMenuScrimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.FILL
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private var highlightBounds: FolderScrimHighlight.Bounds? = null
    private var folderId: Int = 0

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        val bounds = highlightBounds ?: return
        val density = resources.displayMetrics.density
        val pad = 8f * density
        FolderScrimHighlight.punchOut(canvas, bounds, pad, clearPaint, density)
        if (folderId != 0) {
            FolderScrimSpotDraw.drawMenuGlyph(canvas, bounds, folderId, density)
        }
    }

    fun setFolderSpot(folderId: Int) {
        this.folderId = folderId
        invalidate()
    }

    fun setHighlight(bounds: FolderScrimHighlight.Bounds?) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        highlightBounds = bounds?.let {
            FolderScrimHighlight.Bounds(
                it.left - loc[0], it.top - loc[1],
                it.right - loc[0], it.bottom - loc[1],
                it.shapeStyle,
                it.showAccentRing,
                it.accentColor,
                it.flipHorizontal,
                it.flipVertical,
                it.isGeneric
            )
        }
        invalidate()
    }

    fun setIconSpot(
        screenX: Float,
        screenY: Float,
        iconSize: Float,
        shapeStyle: Int = 0,
        showAccentRing: Boolean = false,
        accentColor: Int = 0
    ) {
        val density = resources.displayMetrics.density
        val pad = 8f * density
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val r = iconSize / 2f + pad
        val cx = screenX - loc[0]
        val cy = screenY - loc[1]
        highlightBounds = FolderScrimHighlight.Bounds(
            cx - r, cy - r, cx + r, cy + r, shapeStyle, showAccentRing, accentColor
        )
        invalidate()
    }
}
