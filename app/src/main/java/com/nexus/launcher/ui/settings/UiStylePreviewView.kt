package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Small live reference swatch shown under the UI Style picker — draws an actual card in the
 * currently-selected style (Neumorphism / Default / Frosted Glass) over a stand-in "wallpaper"
 * gradient, using the SAME drawing primitives the real widgets/folders/dock use
 * ([NexusNeumorphicDraw.drawRaisedSurface]/`drawFlatSurface`, [FrostedGlassEngine.frostFillAlpha]),
 * so it's a genuine preview rather than a mocked-up illustration.
 */
class UiStylePreviewView(context: Context) : View(context) {

    private var mode: String = FrostedGlassEngine.UI_STYLE_DEFAULT
    private var tokens: NexusColorTokens = NexusColorTokens.Dark

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cardRect = RectF()

    fun setMode(mode: String) {
        if (this.mode != mode) {
            this.mode = mode
            invalidate()
        }
    }

    fun setTokens(tokens: NexusColorTokens) {
        this.tokens = tokens
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val dp = resources.displayMetrics.density

        // Stand-in "wallpaper" — a simple gradient. There's no live wallpaper source available
        // in a Settings screen, so this is deliberately just something with enough tonal variation
        // to make Frosted Glass's translucency visually legible against Default/Neumorphism's
        // fully opaque cards.
        bgPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.parseColor("#5B7FBF"), Color.parseColor("#B0648C"),
            Shader.TileMode.CLAMP
        )
        val outerR = 14f * dp
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), outerR, outerR, bgPaint)

        val margin = 14f * dp
        cardRect.set(margin, margin, width - margin, height - margin)
        val cardR = 10f * dp

        when (mode) {
            FrostedGlassEngine.UI_STYLE_NEUMORPHISM -> {
                val palette = NexusNeumorphicDraw.resolvePalette(tokens)
                NexusNeumorphicDraw.drawRaisedSurface(canvas, cardRect, cardR, 1, palette, dp)
            }
            FrostedGlassEngine.UI_STYLE_DEFAULT -> {
                val palette = NexusNeumorphicDraw.resolvePalette(tokens)
                NexusNeumorphicDraw.drawFlatSurface(canvas, cardRect, cardR, 1, palette, dp)
            }
            else -> {
                val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (FrostedGlassEngine.frostFillAlpha(0.65f, 0.70f) * 255f).toInt()
                cardPaint.shader = null
                cardPaint.color = (frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24)
                canvas.drawRoundRect(cardRect, cardR, cardR, cardPaint)
                borderPaint.strokeWidth = 1f * dp
                borderPaint.color = frostedTokens.border
                canvas.drawRoundRect(cardRect, cardR, cardR, borderPaint)
            }
        }
    }
}
