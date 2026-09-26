package com.nexus.launcher.ui.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

/**
 * The dark wash behind an opened island.
 *
 * The card is a small black shape sitting on whatever wallpaper happens to be behind it, and a
 * busy wallpaper made it hard to read. This dims the screen under it: heaviest at the top, where
 * the card is, fading out by about two thirds of the way down so the home screen is still
 * visible and the page is clearly the island's, not a dialog's.
 *
 * It takes touches only while it is showing, and a tap anywhere on it closes the island.
 */
class IslandScrimView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Black at the top, gone by [FADE_END] of the height. */
    private fun buildShader(h: Float) {
        if (h <= 0f) return
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.argb((255 * TOP_ALPHA).toInt(), 0, 0, 0),
                Color.argb((255 * MID_ALPHA).toInt(), 0, 0, 0),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, MID_STOP, FADE_END),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildShader(h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        if (paint.shader == null) buildShader(height.toFloat())
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        private const val FADE_MS = 180f

        /** Adds the wash under the island, invisible until something opens. */
        fun install(root: android.widget.FrameLayout, index: Int, onTap: () -> Unit): IslandScrimView {
            val wash = IslandScrimView(root.context).apply {
                alpha = 0f
                visibility = View.GONE
                setOnClickListener { onTap() }
            }
            root.addView(
                wash, index,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            return wash
        }

        /** Fades with the card, and takes touches only while it is there. */
        fun show(wash: IslandScrimView?, show: Boolean, animSpeed: Float) {
            wash ?: return
            if (show == (wash.visibility == View.VISIBLE)) return
            val duration = (FADE_MS / animSpeed.coerceAtLeast(0.1f)).toLong()
            wash.animate().cancel()
            if (show) {
                wash.visibility = View.VISIBLE
                wash.animate().alpha(1f).setDuration(duration).start()
            } else {
                wash.animate().alpha(0f).setDuration(duration)
                    .withEndAction { wash.visibility = View.GONE }.start()
            }
        }

        const val TOP_ALPHA = 0.72f
        const val MID_ALPHA = 0.5f
        const val MID_STOP = 0.32f
        const val FADE_END = 0.72f
    }
}
