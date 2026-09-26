package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.view.View
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.data.prefs.DrawerLayoutModes
import com.nexus.launcher.ui.canvas.GridMetrics
import com.nexus.launcher.ui.canvas.IconLabelText
class DrawerPreviewView(context: Context) : View(context) {

    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark


    init {
        try {
            currentTokens = ThemeObserver.currentTokens(context)
        } catch (_: Exception) {}
    }

    fun applyTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        invalidate()
    }



    var pendingSettings: PendingDrawerSettings? = null
        set(value) {
            val heightChanged = field?.drawerGridOrList != value?.drawerGridOrList
            field = value?.copy()
            if (heightChanged) requestLayout()
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val railPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val bgRect = RectF()
    private val pillRect = RectF()
    private val destRect = Rect()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val categories = pendingSettings?.drawerGridOrList == "categories"
        val targetHeight = ((if (categories) 220f else 150f) * density).toInt()
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
        val settings = pendingSettings ?: PendingDrawerSettings()
        val density = resources.displayMetrics.density
        val isLight = currentTokens.bg == NexusColorTokens.Light.bg ||
                ColorUtils.calculateLuminance(currentTokens.bg) > 0.5

        borderPaint.strokeWidth = 1f * density
        borderPaint.color = currentTokens.divider

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val radius = 20 * density
        bgRect.set(0f, 0f, viewW, viewH)
        bgPaint.color = currentTokens.surface
        canvas.drawRoundRect(bgRect, radius, radius, bgPaint)
        canvas.drawRoundRect(bgRect, radius, radius, borderPaint)

        val realDisplayWidth = resources.displayMetrics.widthPixels.toFloat()
        val scaleFactor = viewW / realDisplayWidth

        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)

        // The drawer's two floating bars, each docked to its own edge. Drawn before the grid so
        // `renderY` already accounts for whatever clearance they reserve at the top.
        val previewH = viewH / scaleFactor
        val left = 14f * density
        val right = realDisplayWidth - 14f * density
        val gap = 10f * density
        val pillH = DrawerPreviewSearchPill.heightPx(density)
        val barH = DrawerPreviewSearchPill.categoryBarHeightPx(density)

        val pillOn = settings.drawerShowSearchPill
        val pillAtBottom = settings.drawerSearchBarPosition == "bottom"
        val categoryMode = settings.effectiveCategoryMode
        // A dropdown chip rides the pill's row; only a strip (or a dropdown with no pill) is a
        // bar of its own. Mirrors DrawerChromeLayout.
        val categoriesMode = settings.drawerGridOrList == "categories"
        val chipBesidePill = !categoriesMode && pillOn && settings.drawerShowCategoryBar &&
            categoryMode == "dropdown"
        val categoryBarOn = !categoriesMode && settings.drawerShowCategoryBar &&
            (categoryMode == "strip" || (categoryMode == "dropdown" && !pillOn))
        val categoryAtBottom = settings.drawerCategoryPosition == "bottom"
        // The overflow trigger follows the same fallback chain the drawer itself uses.
        val overflowInPill = pillOn
        val overflowInBar = !pillOn && categoryBarOn

        // The pill sits outermost at whichever edge it shares with the category bar.
        var topY = 12f * density
        var bottomY = previewH - 12f * density

        if (pillOn) {
            val top = if (pillAtBottom) bottomY - pillH else topY
            DrawerPreviewSearchPill.draw(
                context, canvas, currentTokens, density, left, top, right,
                showCategorySegment = !categoriesMode && settings.drawerShowCategoryBar &&
                    categoryMode == "in_pill",
                showChipBeside = chipBesidePill,
                showOverflow = overflowInPill,
                pillPaint = pillPaint, borderPaint = borderPaint,
                textPaint = textPaint, rect = pillRect
            )
            if (pillAtBottom) bottomY = top - gap else topY = top + pillH + gap
        }

        if (categoryBarOn) {
            val top = if (categoryAtBottom) bottomY - barH else topY
            DrawerPreviewSearchPill.drawCategoryBar(
                context, canvas, currentTokens, density, left, top, right,
                isStrip = categoryMode == "strip",
                showOverflow = overflowInBar,
                pillPaint = pillPaint, borderPaint = borderPaint,
                textPaint = textPaint, rect = pillRect
            )
            if (categoryAtBottom) bottomY = top - gap else topY = top + barH + gap
        }

        var renderY = topY

        // Side Rail — Categories List draws its own; Spatial and Strip have none.
        val showRail = settings.drawerShowRail &&
            (!categoriesMode || settings.drawerCategoryLayout == DrawerLayoutModes.CAT_LIST)
        val canvasRailW = if (showRail && !categoriesMode) 20f * density else 0f
        if (canvasRailW > 0f) {
            val railX = realDisplayWidth - 10f * density
            val letters = listOf("A", "D", "M", "S", "Z")
            val railSpacing = (70f * density) / letters.size
            railPaint.textSize = 9f * density
            railPaint.color = currentTokens.textSecondary
            letters.forEachIndexed { i, letter ->
                canvas.drawText(letter, railX, renderY + 12f * density + i * railSpacing, railPaint)
            }
        }

        val contentW = realDisplayWidth - canvasRailW - 8f * density
        val cols = settings.drawerColumns.coerceIn(2, 10)
        val cellW = GridMetrics.compute(availableWidthPx = contentW, availableHeightPx = 1f, columns = cols, rows = 1).cellWidthPx
        val iconSize = (cellW * settings.drawerIconSizeMultiplier).toInt()

        val effectiveMode = settings.drawerEffectiveLayoutMode
        val isList1 = effectiveMode == "list_1"
        val isList2 = effectiveMode == "list_2"

        if (effectiveMode == "categories") {
            DrawerPreviewCategoriesDraw.draw(
                context = context,
                canvas = canvas,
                tokens = currentTokens,
                density = density,
                settings = settings,
                left = left,
                top = renderY,
                contentW = contentW,
                bottomY = bottomY,
                showRail = showRail,
                pillPaint = pillPaint,
                borderPaint = borderPaint,
                textPaint = textPaint,
                railPaint = railPaint,
                iconPaint = iconPaint,
                destRect = destRect,
                plate = bgRect,
                isLight = isLight,
            )
        } else if (isList1) {
            val twoLine = settings.drawerShowLabels && settings.drawerTwoLineLabels
            val extra = if (twoLine) 16f * density else 0f
            val rowH = (iconSize + 22f * density + extra).coerceAtLeast(64f * density)
            val iconLeft = 16f * density
            val textLeft = iconLeft + iconSize + 16f * density
            val maxTextW = (contentW - textLeft - 16f * density).coerceAtLeast(0f)
            textPaint.textSize = 16f * density
            textPaint.color = currentTokens.textPrimary
            textPaint.textAlign = Paint.Align.LEFT

            for (i in 0 until 1) {
                val rowTop = renderY + i * rowH
                val iconTop = rowTop + (rowH - iconSize) / 2f
                val bmp = SettingsPreviewIconRenderer.getBitmap(
                    context, i, settings.iconShape, isLight, currentTokens
                )
                destRect.set(iconLeft.toInt(), iconTop.toInt(), (iconLeft + iconSize).toInt(), (iconTop + iconSize).toInt())
                canvas.drawBitmap(bmp, null, destRect, iconPaint)
                if (settings.drawerShowLabels) {
                    val label = SettingsPreviewIconRenderer.getLabel(context, i)
                    val wrapped = IconLabelText.wrap(label, textPaint, maxTextW, settings.drawerTwoLineLabels)
                    val step = IconLabelText.lineStepPx(textPaint.textSize)
                    val firstY = if (wrapped.line2 != null) {
                        rowTop + rowH / 2f - step / 2f + 5.5f * density
                    } else {
                        rowTop + rowH / 2f + 5.5f * density
                    }
                    IconLabelText.drawStart(
                        canvas, textPaint, textLeft, firstY, wrapped.line1, wrapped.line2, step
                    )
                }
            }
        } else if (isList2) {
            val twoLine = settings.drawerShowLabels && settings.drawerTwoLineLabels
            val extra = if (twoLine) 14f * density else 0f
            val rowH = (iconSize + 18f * density + extra).coerceAtLeast(58f * density)
            val colW = contentW / 2f
            textPaint.textSize = 14.5f * density
            textPaint.color = currentTokens.textPrimary
            textPaint.textAlign = Paint.Align.LEFT

            for (i in 0 until 2) {
                val row = i / 2
                val col = i % 2
                val rowTop = renderY + row * rowH
                val itemLeft = col * colW
                val iconLeft = itemLeft + 12f * density
                val iconTop = rowTop + (rowH - iconSize) / 2f
                val bmp = SettingsPreviewIconRenderer.getBitmap(
                    context, i, settings.iconShape, isLight, currentTokens
                )
                destRect.set(iconLeft.toInt(), iconTop.toInt(), (iconLeft + iconSize).toInt(), (iconTop + iconSize).toInt())
                canvas.drawBitmap(bmp, null, destRect, iconPaint)
                if (settings.drawerShowLabels) {
                    val textLeft = iconLeft + iconSize + 10f * density
                    val maxTextW = (colW - (textLeft - itemLeft) - 8f * density).coerceAtLeast(0f)
                    val label = SettingsPreviewIconRenderer.getLabel(context, i)
                    val wrapped = IconLabelText.wrap(label, textPaint, maxTextW, settings.drawerTwoLineLabels)
                    val step = IconLabelText.lineStepPx(textPaint.textSize)
                    val firstY = if (wrapped.line2 != null) {
                        rowTop + rowH / 2f - step / 2f + 5f * density
                    } else {
                        rowTop + rowH / 2f + 5f * density
                    }
                    IconLabelText.drawStart(
                        canvas, textPaint, textLeft, firstY, wrapped.line1, wrapped.line2, step
                    )
                }
            }
        } else {
            // Grid Mode: one row × N columns. Fixed column anchors, icons expand around cell
            // center. A single row is enough to convey column count and icon size, and keeps the
            // preview card from stretching the settings page.
            val totalItems = cols
            val labelH = when {
                !settings.drawerShowLabels -> 0f
                settings.drawerTwoLineLabels -> 30f * density
                else -> 18f * density
            }
            val rowH = iconSize + labelH + 10f * density
            textPaint.textSize = 11.5f * density
            textPaint.color = currentTokens.textPrimary
            val oldAlign = textPaint.textAlign
            textPaint.textAlign = Paint.Align.CENTER

            for (i in 0 until totalItems) {
                val row = i / cols
                val col = i % cols
                val cellLeft = col * cellW
                val cellTop = renderY + row * rowH
                val iconLeft = cellLeft + (cellW - iconSize) / 2f
                val iconTop = cellTop + 2f * density
                val bmp = SettingsPreviewIconRenderer.getBitmap(
                    context, i, settings.iconShape, isLight, currentTokens
                )
                destRect.set(iconLeft.toInt(), iconTop.toInt(), (iconLeft + iconSize).toInt(), (iconTop + iconSize).toInt())
                canvas.drawBitmap(bmp, null, destRect, iconPaint)

                if (settings.drawerShowLabels) {
                    val label = SettingsPreviewIconRenderer.getLabel(context, i)
                    val wrapped = IconLabelText.wrap(
                        label, textPaint, cellW * 0.92f, settings.drawerTwoLineLabels
                    )
                    val step = IconLabelText.lineStepPx(textPaint.textSize)
                    val textY = iconTop + iconSize + 13f * density
                    IconLabelText.drawCentered(
                        canvas, textPaint, cellLeft + cellW / 2f, textY,
                        wrapped.line1, wrapped.line2, step
                    )
                }
            }
            textPaint.textAlign = oldAlign
        }

        canvas.restore()
    }
}
