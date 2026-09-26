package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.view.View

/**
 * Custom View that renders a document cover thumbnail with a squircle progress ring
 * hugging the thumbnail with uniform distance, and a 100% completion checkmark badge.
 * Zero Paint or Path allocations inside onDraw().
 */
class CoverProgressThumbnailView(context: Context) : View(context) {

    private val dp = resources.displayMetrics.density

    // Configurable state
    var coverBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var title: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var fileType: String = "pdf"
        set(value) {
            field = value
            badgeLabel = value.uppercase()
            invalidate()
        }

    /** Built on assignment: onDraw allocates nothing. */
    private var badgeLabel: String = "PDF"

    var progressPercent: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var ringStrokeWidthDp: Float = 3f
        set(value) {
            field = value
            updateStrokeWidths()
            invalidate()
        }

    var trackColor: Int = 0x22FFFFFF
        set(value) {
            field = value
            trackPaint.color = value
            invalidate()
        }

    var progressColor: Int = Color.WHITE
        set(value) {
            field = value
            progressPaint.color = value
            checkBgPaint.color = value
            invalidate()
        }

    private val paperInk: Int get() = if (isEInk) PAPER_INK_EINK else PAPER_INK_SCREEN
    private val paperFill: Int get() = if (isEInk) PAPER_FILL_EINK else PAPER_FILL_SCREEN

    var isEInk: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var spineWidthDp: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var spineColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            spinePaint.color = value
            invalidate()
        }

    var bottomProgressBarHeightDp: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    // Geometry objects (pre-allocated)
    private val ringBounds = RectF()
    private val coverBounds = RectF()
    private val coverClipPath = Path()
    private val squircleTrackPath = Path()
    private val squircleProgressPath = Path()
    private val pathMeasure = PathMeasure()
    private var squircleTotalLength = 0f
    private val checkPath = Path()
    private val srcRect = Rect()
    private val spineRect = RectF()
    private val bottomBarTrackRect = RectF()
    private val bottomBarProgressRect = RectF()

    // Paints (pre-allocated, never inside onDraw)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val spinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bottomBarTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bottomBarProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }

    private val checkBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Covers are photographs; in E-Ink they are drawn desaturated, as feed images are. */
    private val coverPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val paperBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paperBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val txtBadgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val txtTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    init {
        updateStrokeWidths()
    }

    private fun updateStrokeWidths() {
        val strokePx = ringStrokeWidthDp * dp
        trackPaint.strokeWidth = strokePx
        progressPaint.strokeWidth = strokePx
        checkPaint.strokeWidth = 2 * dp
        paperBorderPaint.strokeWidth = (1 * dp).coerceAtLeast(1f)
        txtBadgePaint.textSize = 10 * dp
        txtTitlePaint.textSize = 11 * dp
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val strokePx = if (bottomProgressBarHeightDp > 0f) 0f else ringStrokeWidthDp * dp
        val halfStroke = strokePx / 2f

        // Progress squircle wraps perimeter cleanly when enabled
        val ringPadding = halfStroke + 1.5f * dp
        ringBounds.set(ringPadding, ringPadding, w - ringPadding, h - ringPadding)

        // Inside cover thumbnail bounds
        val coverMargin = if (bottomProgressBarHeightDp > 0f || ringStrokeWidthDp <= 0f) 0f else strokePx + 3f * dp
        coverBounds.set(coverMargin, coverMargin, w - coverMargin, h - coverMargin)

        val coverRadius = if (isEInk) 2f * dp else 8f * dp
        coverClipPath.reset()
        coverClipPath.addRoundRect(coverBounds, coverRadius, coverRadius, Path.Direction.CW)

        if (ringStrokeWidthDp > 0f && bottomProgressBarHeightDp <= 0f) {
            val squircleRadius = if (isEInk) 3f * dp else 10f * dp
            buildSquirclePath(squircleTrackPath, ringBounds, squircleRadius)
            pathMeasure.setPath(squircleTrackPath, false)
            squircleTotalLength = pathMeasure.length
        } else {
            squircleTotalLength = 0f
        }
    }

    private fun buildSquirclePath(path: Path, rect: RectF, radius: Float) {
        path.reset()
        val r = radius.coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
        val cx = rect.centerX()
        path.moveTo(cx, rect.top)
        path.lineTo(rect.right - r, rect.top)
        path.arcTo(rect.right - 2 * r, rect.top, rect.right, rect.top + 2 * r, -90f, 90f, false)
        path.lineTo(rect.right, rect.bottom - r)
        path.arcTo(rect.right - 2 * r, rect.bottom - 2 * r, rect.right, rect.bottom, 0f, 90f, false)
        path.lineTo(rect.left + r, rect.bottom)
        path.arcTo(rect.left, rect.bottom - 2 * r, rect.left + 2 * r, rect.bottom, 90f, 90f, false)
        path.lineTo(rect.left, rect.top + r)
        path.arcTo(rect.left, rect.top, rect.left + 2 * r, rect.top + 2 * r, 180f, 90f, false)
        path.lineTo(cx, rect.top)
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Cover Thumbnail with center crop (ContentScale.Crop)
        val bmp = coverBitmap
        canvas.save()
        canvas.clipPath(coverClipPath)

        if (bmp != null && !bmp.isRecycled) {
            val bmpW = bmp.width
            val bmpH = bmp.height
            val targetW = coverBounds.width()
            val targetH = coverBounds.height()

            if (bmpW > 0 && bmpH > 0 && targetW > 0f && targetH > 0f) {
                val bmpAspect = bmpW.toFloat() / bmpH.toFloat()
                val targetAspect = targetW / targetH
                if (bmpAspect > targetAspect) {
                    val cropW = (bmpH * targetAspect).toInt().coerceAtMost(bmpW)
                    val cropX = (bmpW - cropW) / 2
                    srcRect.set(cropX, 0, cropX + cropW, bmpH)
                } else {
                    val cropH = (bmpW / targetAspect).toInt().coerceAtMost(bmpH)
                    val cropY = (bmpH - cropH) / 2
                    srcRect.set(0, cropY, bmpW, cropY + cropH)
                }
                coverPaint.colorFilter =
                    if (isEInk) com.nexus.launcher.feed.NexusFeedEInkCoordinator.grayscaleColorFilter else null
                canvas.drawBitmap(bmp, srcRect, coverBounds, coverPaint)
            }
        } else {
            // Placeholder for TXT or loading PDF
            paperBgPaint.color = paperFill
            paperBorderPaint.color = trackColor
            canvas.drawRect(coverBounds, paperBgPaint)
            canvas.drawRect(coverBounds, paperBorderPaint)

            // Badge
            txtBadgePaint.color = paperInk
            txtBadgePaint.typeface = if (isEInk) Typeface.SERIF else Typeface.MONOSPACE
            val badgeText = badgeLabel
            canvas.drawText(badgeText, coverBounds.centerX(), coverBounds.centerY() - 6 * dp, txtBadgePaint)

            // Title excerpt
            txtTitlePaint.color = if (isEInk) Color.parseColor("#1A1A1A") else Color.WHITE
            txtTitlePaint.typeface = if (isEInk) Typeface.SERIF else Typeface.DEFAULT
            val titleExcerpt = if (title.length > 8) title.substring(0, 7) + "…" else title
            canvas.drawText(titleExcerpt, coverBounds.centerX(), coverBounds.centerY() + 12 * dp, txtTitlePaint)
        }

        // Spine effect: 2dp vertical accent line on left of cover
        if (spineWidthDp > 0f && spineColor != Color.TRANSPARENT) {
            spineRect.set(coverBounds.left, coverBounds.top, coverBounds.left + spineWidthDp * dp, coverBounds.bottom)
            canvas.drawRect(spineRect, spinePaint)
        }

        // Bottom progress bar (3dp tall, accent fill on surfaceRaised track)
        if (bottomProgressBarHeightDp > 0f) {
            val barH = bottomProgressBarHeightDp * dp
            bottomBarTrackRect.set(coverBounds.left, coverBounds.bottom - barH, coverBounds.right, coverBounds.bottom)
            bottomBarTrackPaint.color = trackColor
            canvas.drawRect(bottomBarTrackRect, bottomBarTrackPaint)

            if (progressPercent > 0) {
                val progW = coverBounds.width() * (progressPercent.coerceIn(0, 100) / 100f)
                bottomBarProgressRect.set(coverBounds.left, coverBounds.bottom - barH, coverBounds.left + progW, coverBounds.bottom)
                bottomBarProgressPaint.color = progressColor
                canvas.drawRect(bottomBarProgressRect, bottomBarProgressPaint)
            }
        }

        canvas.restore()

        // 2. Draw Squircle Progress Ring hugging the thumbnail (when bottom bar disabled)
        if (bottomProgressBarHeightDp <= 0f && progressPercent > 0 && squircleTotalLength > 0f) {
            canvas.drawPath(squircleTrackPath, trackPaint)
            val progressLength = squircleTotalLength * (progressPercent.coerceIn(0, 100) / 100f)
            squircleProgressPath.reset()
            pathMeasure.getSegment(0f, progressLength, squircleProgressPath, true)
            canvas.drawPath(squircleProgressPath, progressPaint)
        }

        // 3. 100% Completion Checkmark Badge (Top-Right)
        if (progressPercent >= 100) {
            val badgeRadius = 8f * dp
            val badgeCenterX = width - badgeRadius - 2f * dp
            val badgeCenterY = badgeRadius + 2f * dp

            checkBgPaint.color = progressColor
            canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius, checkBgPaint)

            checkPath.reset()
            checkPath.moveTo(badgeCenterX - 4f * dp, badgeCenterY)
            checkPath.lineTo(badgeCenterX - 1.2f * dp, badgeCenterY + 2.5f * dp)
            checkPath.lineTo(badgeCenterX + 3.8f * dp, badgeCenterY - 2.5f * dp)
            checkPaint.color = if (isEInk || progressColor == Color.WHITE) Color.BLACK else Color.WHITE
            canvas.drawPath(checkPath, checkPaint)
        }
    }

    private companion object {
        val PAPER_FILL_EINK = Color.parseColor("#F5F2EB")
        val PAPER_FILL_SCREEN = Color.parseColor("#1C242D")
        val PAPER_INK_EINK = Color.parseColor("#2B2B2B")
        val PAPER_INK_SCREEN = Color.parseColor("#A0AEC0")
    }
}
