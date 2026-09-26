package com.nexus.launcher.ui.premium.showcase

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.view.View
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.settings.DrawerPreviewSearchPill
import com.nexus.launcher.ui.settings.PendingDrawerSettings
import com.nexus.launcher.ui.settings.SettingsPreviewIconRenderer

/**
 * Drawer customization, in motion: the app grid folds into a list, then the search pill travels
 * from the top of the drawer to the bottom, and both return — on a loop, like a short clip.
 *
 * Built from the drawer settings preview's own parts ([DrawerPreviewSearchPill] and
 * [SettingsPreviewIconRenderer]), so it is the same drawer the Drawer page previews. Drawn at a
 * phone's width and scaled to the frame, so the tile and the full-screen preview match. The loop
 * runs only while the view is attached.
 */
internal class PremiumShowcaseDrawerAnimation(
    context: Context,
    private val tokens: NexusColorTokens,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = tokens.divider
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = tokens.textPrimary }
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val pillRect = RectF()
    private val panel = RectF()
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG)
    private var groundHeight = 0
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dest = Rect()
    private val isLight = ColorUtils.calculateLuminance(tokens.bg) > 0.5
    private val iconShape = PendingDrawerSettings().iconShape

    /** Position in the loop, 0..[CYCLE]. */
    private var clock = 0f
    private val ticker = ShowcaseTicker(this, startAt = 0.65f) { seconds -> clock = seconds % CYCLE }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val d = density
        val virtualW = PHONE_WIDTH_DP * d
        val scale = width / virtualW
        val virtualH = height / scale

        // A ground tinted by the accent, so the drawer reads as a panel on a wallpaper rather
        // than a flat sketch. One gradient and one rounded rect: no cost per frame worth naming.
        if (ground.shader == null || groundHeight != height) {
            groundHeight = height
            ground.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                ColorUtils.blendARGB(tokens.bg, tokens.accent, 0.22f), tokens.bg, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), ground)

        canvas.save()
        canvas.scale(scale, scale)
        val panelInset = 6f * d
        panel.set(panelInset, panelInset, virtualW - panelInset, virtualH - panelInset)
        panelPaint.color = tokens.surface
        canvas.drawRoundRect(panel, 18f * d, 18f * d, panelPaint)
        canvas.drawRoundRect(panel, 18f * d, 18f * d, borderPaint)

        // 0 = grid, 1 = list; 0 = pill at the top, 1 = at the bottom.
        val listness = phase(0.8f, 1.5f, 4.2f, 4.9f)
        val pillDown = phase(2.3f, 3.0f, 4.2f, 4.9f)

        val pillH = DrawerPreviewSearchPill.heightPx(d)
        val margin = 12f * d
        val gap = 10f * d
        val pillTop = lerp(panel.top + margin, panel.bottom - margin - pillH, pillDown)
        val contentTop = lerp(panel.top + margin + pillH + gap, panel.top + margin, pillDown)
        val contentBottom = lerp(panel.bottom - margin, panel.bottom - margin - pillH - gap, pillDown)

        canvas.save()
        canvas.clipRect(panel.left, contentTop, panel.right, contentBottom)
        drawApps(canvas, virtualW, contentTop, contentBottom, listness)
        canvas.restore()

        DrawerPreviewSearchPill.draw(
            context, canvas, tokens, d, panel.left + 8f * d, pillTop, panel.right - 8f * d,
            showCategorySegment = true, showChipBeside = false, showOverflow = true,
            pillPaint = pillPaint, borderPaint = borderPaint, textPaint = textPaint, rect = pillRect,
        )
        canvas.restore()
    }

    private fun drawApps(canvas: Canvas, virtualW: Float, top: Float, bottom: Float, listness: Float) {
        val d = density
        val cellW = (virtualW - 36f * d) / GRID_COLUMNS
        val gridIcon = 48f * d
        val gridRow = gridIcon + 30f * d
        val listIcon = 40f * d
        val listRow = 58f * d

        for (i in 0 until APP_COUNT) {
            val col = i % GRID_COLUMNS
            val row = i / GRID_COLUMNS
            val gx = 18f * d + col * cellW + (cellW - gridIcon) / 2f
            val gy = top + 8f * d + row * gridRow
            val lx = 26f * d
            val ly = top + i * listRow + (listRow - listIcon) / 2f

            val size = lerp(gridIcon, listIcon, listness)
            val x = lerp(gx, lx, listness)
            val y = lerp(gy, ly, listness)
            if (y > bottom) continue

            val bitmap = SettingsPreviewIconRenderer.getBitmap(context, i, iconShape, isLight, tokens)
            dest.set(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
            canvas.drawBitmap(bitmap, null, dest, iconPaint)

            val label = SettingsPreviewIconRenderer.getLabel(context, i)
            labelPaint.textSize = lerp(11.5f, 16f, listness) * d
            val labelW = labelPaint.measureText(label)
            val gridLabelX = gx + gridIcon / 2f - labelW / 2f
            val gridLabelY = gy + gridIcon + 16f * d
            val listLabelX = lx + listIcon + 16f * d
            val listLabelY = ly + listIcon / 2f + 5.5f * d
            canvas.drawText(label, lerp(gridLabelX, listLabelX, listness), lerp(gridLabelY, listLabelY, listness), labelPaint)

            // A hairline under each row as the list arrives, the way the drawer's list draws them.
            if (listness > 0.02f) {
                val lineY = ly + listRow - 6f * d
                if (lineY < bottom) {
                    borderPaint.color = ColorUtils.setAlphaComponent(tokens.divider, (listness * 255).toInt())
                    canvas.drawLine(lx, lineY, virtualW - 26f * d, lineY, borderPaint)
                    borderPaint.color = tokens.divider
                }
            }
        }
    }

    /** 0 before [inStart], eases to 1 by [inEnd], holds, eases back to 0 between [outStart] and [outEnd]. */
    private fun phase(inStart: Float, inEnd: Float, outStart: Float, outEnd: Float): Float {
        val t = clock
        val raw = when {
            t < inStart -> 0f
            t < inEnd -> (t - inStart) / (inEnd - inStart)
            t < outStart -> 1f
            t < outEnd -> 1f - (t - outStart) / (outEnd - outStart)
            else -> 0f
        }
        return raw * raw * (3f - 2f * raw)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private companion object {
        /** Seconds in one loop: grid, list, pill down, hold, back. */
        const val CYCLE = 5.6f
        const val PHONE_WIDTH_DP = 360f
        const val GRID_COLUMNS = 4
        const val APP_COUNT = 16
    }
}
