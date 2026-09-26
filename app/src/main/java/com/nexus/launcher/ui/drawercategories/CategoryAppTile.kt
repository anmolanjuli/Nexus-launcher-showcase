package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View

class CategoryAppTile(context: Context) : View(context) {
    private val palette = CategoryPalette(context)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val letterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = palette.textPrimary
    }
    private val iconRect = RectF()
    private var letter: String = ""
    private var label: String = ""
    private var displayLabel: String = ""
    private var iconDp = 52f
    private var showLabel = true
    private var iconDrawable: android.graphics.drawable.Drawable? = null

    fun iconSizePx(): Int = (iconDp * resources.displayMetrics.density).toInt()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun bind(
        app: CategoryApp,
        iconDp: Float = 52f,
        showLabel: Boolean = true,
        lightPlate: Boolean = false,
    ) {
        this.iconDp = iconDp
        this.showLabel = showLabel
        iconDrawable = app.icon
        letter = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: ""
        label = app.label
        displayLabel = app.label
        if (lightPlate) {
            fillPaint.color = palette.plateFill()
            letterPaint.color = app.color
        } else {
            fillPaint.color = app.color
            letterPaint.color = palette.onFill(app.color)
        }
        labelPaint.color = palette.textPrimary
        contentDescription = app.label
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val scaled = resources.displayMetrics.scaledDensity
        val icon = (iconDp * density).toInt()
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(icon)
            else -> icon
        }
        letterPaint.textSize = (iconDp * 0.34f) * scaled
        labelPaint.textSize = 10f * scaled
        val gap = if (showLabel) (5f * density).toInt() else 0
        val labelBand = if (showLabel) (labelPaint.textSize * 1.25f).toInt() else 0
        ellipsizeLabel(width)
        setMeasuredDimension(width, icon + gap + labelBand)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val iconSize = iconDp * density
        val left = (width - iconSize) / 2f
        iconRect.set(left, 0f, left + iconSize, iconSize)
        val drawable = iconDrawable
        if (drawable != null) {
            drawable.setBounds(
                iconRect.left.toInt(),
                iconRect.top.toInt(),
                iconRect.right.toInt(),
                iconRect.bottom.toInt(),
            )
            drawable.draw(canvas)
        } else {
            val radius = 12f * density
            canvas.drawRoundRect(iconRect, radius, radius, fillPaint)
            val letterY = iconRect.centerY() - (letterPaint.ascent() + letterPaint.descent()) / 2f
            canvas.drawText(letter, iconRect.centerX(), letterY, letterPaint)
        }
        if (!showLabel) return
        val labelY = iconRect.bottom + 5f * density - labelPaint.ascent()
        canvas.drawText(displayLabel, width / 2f, labelY, labelPaint)
    }

    private fun ellipsizeLabel(availableWidth: Int) {
        val density = resources.displayMetrics.density
        val maxLabel = (availableWidth - 4f * density).coerceAtLeast(0f)
        displayLabel = TextUtils.ellipsize(label, labelPaint, maxLabel, TextUtils.TruncateAt.END).toString()
    }
}
