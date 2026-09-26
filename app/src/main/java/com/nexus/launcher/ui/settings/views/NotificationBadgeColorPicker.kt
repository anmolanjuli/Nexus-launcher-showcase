package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.settings.SolidColorPickerDialog

class NotificationBadgeColorPicker @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    val fixedColors = listOf(
        "#E5484D", // Red
        "#4CAF50", // Green
        "#2196F3"  // Blue
    )

    private var customColorHex: String? = null
    private var selectedIndex = 0 // 0..2 for fixed, 3 for custom
    var onColorSelected: ((String) -> Unit)? = null

    /** Asked before a colour is taken; the hex, or null for the custom circle. False leaves it. */
    var allowPick: (hex: String?) -> Boolean = { true }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val customBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val dp = context.resources.displayMetrics.density
    private val circleRadius = 14f * dp
    private val spacing = 10f * dp

    private val rainbowColors = intArrayOf(
        Color.RED, Color.YELLOW, Color.GREEN,
        Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalCount = fixedColors.size + 1 // 3 fixed + 1 custom
        val totalWidth = ((circleRadius * 2 + spacing) * totalCount - spacing + paddingLeft + paddingRight + (8 * dp)).toInt()
        val height = (circleRadius * 2 + 8 * dp + paddingTop + paddingBottom + (4 * dp)).toInt()
        setMeasuredDimension(totalWidth, height)
    }

    override fun onDraw(canvas: Canvas) {
        val tokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark }
        ringPaint.color = tokens.textPrimary
        customBorderPaint.color = (tokens.textSecondary and 0x00FFFFFF) or (0x66 shl 24)

        val step = circleRadius * 2 + spacing

        // 1. Draw 3 fixed swatches
        fixedColors.forEachIndexed { i, hex ->
            val cx = paddingLeft + circleRadius + (4 * dp) + i * step
            val cy = paddingTop + circleRadius + 4 * dp
            fillPaint.shader = null
            fillPaint.color = Color.parseColor(hex)
            canvas.drawCircle(cx, cy, circleRadius, fillPaint)
            if (i == selectedIndex) {
                canvas.drawCircle(cx, cy, circleRadius + 4 * dp, ringPaint)
            }
        }

        // 2. Draw 4th custom circle
        val customIndex = fixedColors.size
        val cx = paddingLeft + circleRadius + (4 * dp) + customIndex * step
        val cy = paddingTop + circleRadius + 4 * dp

        if (customColorHex != null) {
            fillPaint.shader = null
            fillPaint.color = Color.parseColor(customColorHex)
            canvas.drawCircle(cx, cy, circleRadius, fillPaint)
        } else {
            fillPaint.shader = SweepGradient(cx, cy, rainbowColors, null)
            canvas.drawCircle(cx, cy, circleRadius, fillPaint)
            fillPaint.shader = null
        }
        canvas.drawCircle(cx, cy, circleRadius, customBorderPaint)

        if (selectedIndex == customIndex) {
            canvas.drawCircle(cx, cy, circleRadius + 4 * dp, ringPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val step = circleRadius * 2 + spacing
            val totalCount = fixedColors.size + 1
            val idx = ((event.x - paddingLeft) / step).toInt().coerceIn(0, totalCount - 1)

            if (!allowPick(fixedColors.getOrNull(idx))) return true
            if (idx < fixedColors.size) {
                selectedIndex = idx
                performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                invalidate()
                onColorSelected?.invoke(fixedColors[idx])
            } else {
                // Clicked custom circle -> launch SolidColorPickerDialog
                val tokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark }
                val initial = customColorHex ?: "#E5484D"
                val accentHex = "#" + Integer.toHexString(tokens.accent).padStart(8, '0').substring(2)
                SolidColorPickerDialog.show(context, initial, accentHex) { pickedHex ->
                    customColorHex = pickedHex
                    selectedIndex = customIndex
                    invalidate()
                    onColorSelected?.invoke(pickedHex)
                }
            }
        }
        return true
    }

    private val customIndex get() = fixedColors.size

    fun setSelectedColor(hex: String?) {
        if (hex.isNullOrBlank()) {
            selectedIndex = 0
            customColorHex = null
        } else {
            val idx = fixedColors.indexOfFirst { it.equals(hex, ignoreCase = true) }
            if (idx >= 0) {
                selectedIndex = idx
                customColorHex = null
            } else {
                selectedIndex = customIndex
                customColorHex = hex
            }
        }
        invalidate()
    }
}
