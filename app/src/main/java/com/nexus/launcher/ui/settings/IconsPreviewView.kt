package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.view.View
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Compact live preview card displayed at the top of [IconsSettingsFragment].
 * Renders sample icons masked to the active shape and tinted to the active theme tokens.
 */
class IconsPreviewView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark
    private var currentShape: Int = -1
    private var packBitmaps: List<Bitmap?>? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }

    private val bgRect = RectF()
    private val destRect = Rect()

    private val sampleIndices = listOf(0, 1, 2, 7, 12) // Camera, Photos, Settings, Clock, Home
    private val sampleLabels = listOf(
        com.nexus.launcher.R.string.preview_app_camera, com.nexus.launcher.R.string.preview_app_photos, com.nexus.launcher.R.string.preview_app_settings,
        com.nexus.launcher.R.string.preview_app_clock, com.nexus.launcher.R.string.preview_app_home,
    ).map { context.getString(it) }

    init {
        try {
            currentTokens = ThemeObserver.currentTokens(context)
        } catch (_: Exception) {}
    }

    fun applyTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        invalidate()
    }

    fun setShape(shape: Int) {
        if (currentShape != shape) {
            currentShape = shape
            invalidate()
        }
    }

    fun setPackIcons(bitmaps: List<Bitmap?>?) {
        packBitmaps = bitmaps
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val targetHeight = (108 * density).toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> if (heightSize > 0) targetHeight.coerceAtMost(heightSize) else targetHeight
            else -> targetHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = 16f * density
        val isGlass = FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val frosted = if (isGlass) FrostedGlassEngine.resolveFrostedTokens(currentTokens) else null

        // 1. Card Background
        bgRect.set(1f, 1f, w - 1f, h - 1f)
        if (isGlass && frosted != null) {
            val fillAlpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt().coerceIn(0, 255)
            bgPaint.color = (frosted.surface and 0x00FFFFFF) or (fillAlpha shl 24)
            borderPaint.color = frosted.border
        } else {
            bgPaint.color = currentTokens.surface
            borderPaint.color = currentTokens.divider
        }
        borderPaint.strokeWidth = (1f * density).coerceAtLeast(1f)

        canvas.drawRoundRect(bgRect, radius, radius, bgPaint)
        canvas.drawRoundRect(bgRect, radius, radius, borderPaint)

        // 2. Draw 5 sample icons
        val count = sampleIndices.size
        val cellWidth = w / count
        // 56dp is what the old IconCustomizationSheet drew, which read correctly; the inline
        // preview had dropped to 40dp and looked undersized next to the tiles below it. Both
        // bitmap sources render at 80dp, so this is still a downscale and stays sharp.
        //
        // Capped against the cell so five icons cannot touch on a narrow screen: at 360dp the
        // cell is only ~68dp wide, where a flat 56 would leave a 6dp gutter.
        val iconSize = minOf(
            (56 * density).toInt(),
            (cellWidth - 14 * density).toInt(),
        ).coerceAtLeast((32 * density).toInt())
        val iconTop = (16 * density).toInt()
        val isLight = currentTokens.bg == 0xFFF5F5F5.toInt()

        labelPaint.color = currentTokens.textSecondary

        for (i in 0 until count) {
            val centerX = cellWidth * i + cellWidth / 2f
            val iconLeft = (centerX - iconSize / 2f).toInt()
            val iconRight = iconLeft + iconSize
            val iconBottom = iconTop + iconSize

            val packBmp = packBitmaps?.getOrNull(i)
            val bmp: Bitmap = packBmp ?: SettingsPreviewIconRenderer.getBitmap(
                context = context,
                index = sampleIndices[i],
                shapeType = currentShape,
                isLight = isLight,
                tokens = currentTokens
            )

            destRect.set(iconLeft, iconTop, iconRight, iconBottom)
            canvas.drawBitmap(bmp, null, destRect, iconPaint)

            val labelY = iconBottom + (16 * density)
            canvas.drawText(sampleLabels[i], centerX, labelY, labelPaint)
        }
    }
}
