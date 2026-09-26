package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View

/** Dimmed scrim with shape-aware punch-out — canvas icon stays sharp underneath. */
class FolderWindowScrimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B8000000")
        style = Paint.Style.FILL
    }
    private var highlightBounds: FolderScrimHighlight.Bounds? = null
    private var iconSpotBounds: FolderScrimHighlight.Bounds? = null
    private var suppressedHighlightBounds: FolderScrimHighlight.Bounds? = null
    private var punchOutSource: String = "none"
    private var openAppCount: Int = 0
    private var openFolderId: Int = 0

    init {
        // LAYER_TYPE_SOFTWARE removed to fix SurfaceFlinger collision with RenderEffect
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bounds = highlightBounds ?: iconSpotBounds
        if (bounds != null) {
            val pad = 6f * resources.displayMetrics.density
            val punchOutPath = FolderScrimHighlight.clipPath(bounds, pad)

            val fullScreenPath = android.graphics.Path().apply {
                addRect(0f, 0f, width.toFloat(), height.toFloat(), android.graphics.Path.Direction.CW)
            }
            val finalPath = android.graphics.Path()
            finalPath.op(fullScreenPath, punchOutPath, android.graphics.Path.Op.DIFFERENCE)
            
            canvas.drawPath(finalPath, dimPaint)

            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = bounds.accentColor.takeIf { it != 0 }
                    ?: FolderScrimHighlight.resolvedSilhouetteStrokeColor()
                strokeWidth = 3f * resources.displayMetrics.density
                alpha = 204
            }
            FolderScrimHighlight.drawAccentRing(canvas, bounds, 3f * resources.displayMetrics.density, ringPaint)
            FolderScrimOpenBadge.draw(canvas, bounds, openAppCount, openFolderId, resources.displayMetrics.density)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        }
    }

    fun setOpenFolderBadge(
        appCount: Int,
        folderId: Int,
        screenX: Float,
        screenY: Float,
        iconSize: Float,
        shapeStyle: Int,
        accentColor: Int = FolderScrimHighlight.resolvedSilhouetteStrokeColor()
    ) {
        openAppCount = appCount
        openFolderId = folderId
        post {
            val density = resources.displayMetrics.density
            val pad = 6f * density
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val r = iconSize / 2f + pad
            val cx = screenX - loc[0]
            val cy = screenY - loc[1]
            if (highlightBounds == null) {
                iconSpotBounds = FolderScrimHighlight.Bounds(
                    cx - r, cy - r, cx + r, cy + r,
                    shapeStyle,
                    showAccentRing = true,
                    accentColor = accentColor
                )
            }
            invalidate()
        }
    }

    fun setHighlight(bounds: FolderScrimHighlight.Bounds?) {
        if (bounds == null) {
            highlightBounds = null
            invalidate()
            return
        }
        post {
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            punchOutSource = "highlight"
            highlightBounds = FolderScrimHighlight.Bounds(
                bounds.left - loc[0], bounds.top - loc[1],
                bounds.right - loc[0], bounds.bottom - loc[1],
                bounds.shapeStyle,
                bounds.showAccentRing,
                bounds.accentColor,
                bounds.flipHorizontal,
                bounds.flipVertical,
                bounds.isGeneric
            )
            iconSpotBounds = null
            Log.d(
                TAG,
                "punch-out at screen=(${bounds.left}, ${bounds.top}) " +
                        "source=$punchOutSource"
            )
            invalidate()
        }
    }

    /**
     * Temporarily hides the icon-hugging punch-out (e.g. while an in-folder app's context menu
     * covers it with a translucent fill — a sharp hole showing through breaks the frosted read)
     * without discarding it, so it reappears once the caller un-suppresses instead of staying
     * gone until the folder is closed and reopened.
     */
    fun setHighlightSuppressed(suppressed: Boolean) {
        if (suppressed) {
            if (highlightBounds != null) {
                suppressedHighlightBounds = highlightBounds
                highlightBounds = null
                invalidate()
            }
        } else {
            if (suppressedHighlightBounds != null) {
                highlightBounds = suppressedHighlightBounds
                suppressedHighlightBounds = null
                invalidate()
            }
        }
    }

    fun setIconSpot(
        screenX: Float,
        screenY: Float,
        iconSize: Float,
        shapeStyle: Int = 0,
        accentColor: Int = 0
    ) {
        post {
            if (highlightBounds != null) return@post
            val density = resources.displayMetrics.density
            val pad = 6f * density
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val r = iconSize / 2f + pad
            val cx = screenX - loc[0]
            val cy = screenY - loc[1]
            punchOutSource = "dock"
            iconSpotBounds = FolderScrimHighlight.Bounds(
                cx - r, cy - r, cx + r, cy + r,
                shapeStyle,
                showAccentRing = true,
                accentColor = accentColor
            )
            Log.d(TAG, "punch-out at $screenX, $screenY source=$punchOutSource")
            invalidate()
        }
    }

    companion object {
        private const val TAG = "FolderScrim"
    }
}