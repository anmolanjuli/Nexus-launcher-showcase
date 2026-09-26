package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.nexus.launcher.theme.ColorBlindMode
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.model.GridItem

/** Visual overlay for drawer + home multi-select — accent-free, neutral glass tokens. */
class SelectionRenderer(private val density: Float) {

    private val dashedEffect = DashPathEffect(floatArrayOf(12f * density, 8f * density), 0f)

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 2.5f
        color = android.graphics.Color.parseColor(NexusDesignSystem.COLOR_TEXT_PRIMARY)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.parseColor(NexusDesignSystem.COLOR_TEXT_PRIMARY)
    }

    private val badgeCheckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.parseColor(NexusDesignSystem.COLOR_TEXT_DARK)
    }

    private val checkPath = Path()
    private var isColorBlindActive: Boolean = false

    /** No-op — selection chrome is accent-free by design. */
    fun setAccentColor(@Suppress("UNUSED_PARAMETER") color: Int) = Unit

    fun setColorBlindMode(mode: ColorBlindMode) {
        isColorBlindActive = mode != ColorBlindMode.NONE
        outlinePaint.pathEffect = if (isColorBlindActive) dashedEffect else null
    }

    fun drawDrawerSelection(
        canvas: Canvas,
        items: List<GridItem>,
        selectedPackages: Set<String>,
        alpha: Int = 255
    ) {
        if (selectedPackages.isEmpty()) return
        for (item in items) {
            val pkg = item.intent?.component?.packageName ?: continue
            val selected = pkg in selectedPackages
            drawItemOverlay(canvas, item.drawRect, isFolder = false, selected = selected, alpha)
        }
    }

    fun drawHomeSelection(
        canvas: Canvas,
        targets: List<HomeSelectionTarget>,
        alpha: Int = 255
    ) {
        if (targets.isEmpty()) return
        for (target in targets) {
            drawItemOverlay(canvas, target.rect, target.isFolder, target.isSelected, alpha)
        }
    }

    fun drawHomeSelection(
        canvas: Canvas,
        rects: Collection<Rect>,
        alpha: Int = 255
    ) {
        drawHomeSelection(
            canvas,
            rects.map { HomeSelectionTarget(it, isFolder = false, isSelected = true) },
            alpha
        )
    }

    private fun drawItemOverlay(
        canvas: Canvas,
        rect: Rect,
        isFolder: Boolean,
        selected: Boolean,
        alpha: Int
    ) {
        if (!selected) {
            drawDimming(canvas, rect, alpha)
            return
        }
        val stroke = android.graphics.Color.argb(alpha, 224, 255, 255)

        if (!isColorBlindActive) {
            fillPaint.color = android.graphics.Color.argb((alpha * 0.12f).toInt(), 255, 255, 255)
            if (isFolder) {
                val pad = density * 4f
                val rf = RectF(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
                canvas.drawRoundRect(rf, density * 14f, density * 14f, fillPaint)
            } else {
                val cx = rect.exactCenterX()
                val cy = rect.exactCenterY()
                val radius = rect.width() / 2f + density * 3f
                canvas.drawCircle(cx, cy, radius, fillPaint)
            }
        }

        outlinePaint.color = stroke
        if (isFolder) {
            val pad = density * 4f
            val rf = RectF(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
            canvas.drawRoundRect(rf, density * 14f, density * 14f, outlinePaint)
        } else {
            val cx = rect.exactCenterX()
            val cy = rect.exactCenterY()
            val radius = rect.width() / 2f + density * 3f
            canvas.drawCircle(cx, cy, radius, outlinePaint)
        }
        drawTickBadge(canvas, rect, alpha)
    }

    private fun drawDimming(canvas: Canvas, rect: Rect, alpha: Int) {
        fillPaint.color = android.graphics.Color.argb((alpha * 0.35f).toInt(), 0, 0, 0)
        val pad = density * 2f
        val rf = RectF(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
        canvas.drawRoundRect(rf, density * 12f, density * 12f, fillPaint)
    }

    private fun drawTickBadge(canvas: Canvas, rect: Rect, alpha: Int) {
        val badgeR = density * 10f
        val cx = rect.right - density * 2f
        val cy = rect.top + density * 2f
        badgeFillPaint.alpha = alpha
        canvas.drawCircle(cx, cy, badgeR, badgeFillPaint)
        checkPath.reset()
        val s = badgeR * 0.55f
        checkPath.moveTo(cx - s, cy)
        checkPath.lineTo(cx - s * 0.2f, cy + s * 0.75f)
        checkPath.lineTo(cx + s, cy - s * 0.55f)
        badgeCheckPaint.alpha = alpha
        canvas.drawPath(checkPath, badgeCheckPaint)
    }
}

data class HomeSelectionTarget(
    val rect: Rect,
    val isFolder: Boolean,
    val isSelected: Boolean
)
