package com.nexus.launcher.ui.widgets.shortcutbox

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/**
 * Dual inline stepper row for configuring Shortcut Box grid columns and rows side-by-side.
 * Format: "Column: - 2 +  |  Row: - 2 +"
 */
class ShortcutGridStepperRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val dp = resources.displayMetrics.density
    private var currentCols = 2
    private var currentRows = 2

    private val colLabel: TextView
    private val colMinusBtn: TextView
    private val colValueText: TextView
    private val colPlusBtn: TextView

    private val dividerView: View

    private val rowLabel: TextView
    private val rowMinusBtn: TextView
    private val rowValueText: TextView
    private val rowPlusBtn: TextView

    var onGridChanged: ((cols: Int, rows: Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = (54 * dp).toInt()
        val hPad = (16 * dp).toInt()
        val vPad = (8 * dp).toInt()
        setPadding(hPad, vPad, hPad, vPad)

        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        // Left Section: Columns
        val colSection = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        colLabel = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.shortcut_grid_column)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textSecondary)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
        }

        colMinusBtn = createStepperButton(tokens, "−") {
            if (currentCols > ShortcutBoxConfig.MIN_GRID_DIM) {
                LivingMosaicHaptics.tick(this)
                currentCols--
                updateUI(tokens)
                onGridChanged?.invoke(currentCols, currentRows)
            }
        }

        colValueText = TextView(context).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            minWidth = (22 * dp).toInt()
        }

        colPlusBtn = createStepperButton(tokens, "+") {
            if (currentCols < ShortcutBoxConfig.MAX_GRID_DIM) {
                LivingMosaicHaptics.tick(this)
                currentCols++
                updateUI(tokens)
                onGridChanged?.invoke(currentCols, currentRows)
            }
        }

        colSection.addView(colLabel)
        colSection.addView(colMinusBtn)
        colSection.addView(colValueText)
        colSection.addView(colPlusBtn)

        // Middle Divider
        dividerView = View(context).apply {
            setBackgroundColor(tokens.divider)
            layoutParams = LayoutParams((1 * dp).toInt(), (24 * dp).toInt()).apply {
                marginStart = (10 * dp).toInt()
                marginEnd = (10 * dp).toInt()
            }
        }

        // Right Section: Rows
        val rowSection = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        rowLabel = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.shortcut_grid_row)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textSecondary)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
        }

        rowMinusBtn = createStepperButton(tokens, "−") {
            if (currentRows > ShortcutBoxConfig.MIN_GRID_DIM) {
                LivingMosaicHaptics.tick(this)
                currentRows--
                updateUI(tokens)
                onGridChanged?.invoke(currentCols, currentRows)
            }
        }

        rowValueText = TextView(context).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            minWidth = (22 * dp).toInt()
        }

        rowPlusBtn = createStepperButton(tokens, "+") {
            if (currentRows < ShortcutBoxConfig.MAX_GRID_DIM) {
                LivingMosaicHaptics.tick(this)
                currentRows++
                updateUI(tokens)
                onGridChanged?.invoke(currentCols, currentRows)
            }
        }

        rowSection.addView(rowLabel)
        rowSection.addView(rowMinusBtn)
        rowSection.addView(rowValueText)
        rowSection.addView(rowPlusBtn)

        addView(colSection)
        addView(dividerView)
        addView(rowSection)

        updateUI(tokens)
    }

    private fun createStepperButton(tokens: NexusColorTokens, symbol: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = symbol
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            val btnSize = (30 * dp).toInt()
            layoutParams = LayoutParams(btnSize, btnSize)
            isClickable = true
            isFocusable = true

            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                val bgCol = ColorUtils.setAlphaComponent(tokens.surfaceRaised, 0xB0)
                setColor(bgCol)
                setStroke((1 * dp).toInt(), ColorUtils.setAlphaComponent(tokens.divider, 0x90))
            }
            background = btnBg
            setOnClickListener { onClick() }
        }
    }

    fun configure(initialCols: Int, initialRows: Int) {
        currentCols = initialCols.coerceIn(ShortcutBoxConfig.MIN_GRID_DIM, ShortcutBoxConfig.MAX_GRID_DIM)
        currentRows = initialRows.coerceIn(ShortcutBoxConfig.MIN_GRID_DIM, ShortcutBoxConfig.MAX_GRID_DIM)
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        updateUI(tokens)
    }

    private fun updateUI(tokens: NexusColorTokens) {
        colValueText.text = currentCols.toString()
        rowValueText.text = currentRows.toString()

        colMinusBtn.alpha = if (currentCols > ShortcutBoxConfig.MIN_GRID_DIM) 1.0f else 0.35f
        colPlusBtn.alpha = if (currentCols < ShortcutBoxConfig.MAX_GRID_DIM) 1.0f else 0.35f

        rowMinusBtn.alpha = if (currentRows > ShortcutBoxConfig.MIN_GRID_DIM) 1.0f else 0.35f
        rowPlusBtn.alpha = if (currentRows < ShortcutBoxConfig.MAX_GRID_DIM) 1.0f else 0.35f
    }
}
