package com.nexus.launcher.ui.island

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Vector glyphs drawn with the current theme accent — no extra icon assets. */
object IslandGlyphs {
    private val path = Path()
    private val tmp = RectF()

    fun draw(
        canvas: Canvas,
        kind: IslandKind,
        cx: Float,
        cy: Float,
        size: Float,
        paint: Paint,
    ) {
        path.reset()
        path.fillType = android.graphics.Path.FillType.EVEN_ODD
        val s = size / 2f
        tmp.set(cx - s, cy - s, cx + s, cy + s)
        when (kind) {
            IslandKind.CALL -> {
                path.moveTo(cx - s * 0.6f, cy - s * 0.2f)
                path.quadTo(cx - s * 0.2f, cy - s * 0.7f, cx + s * 0.4f, cy - s * 0.3f)
                path.lineTo(cx + s * 0.2f, cy - s * 0.05f)
                path.quadTo(cx - s * 0.05f, cy - s * 0.2f, cx - s * 0.2f, cy + s * 0.1f)
                path.quadTo(cx - s * 0.1f, cy + s * 0.35f, cx + s * 0.1f, cy + s * 0.3f)
                path.lineTo(cx + s * 0.3f, cy + s * 0.6f)
                path.quadTo(cx - s * 0.2f, cy + s * 0.8f, cx - s * 0.6f, cy - s * 0.2f)
                path.close()
            }
            IslandKind.MUSIC -> {
                path.addCircle(cx - s * 0.35f, cy + s * 0.35f, s * 0.32f, Path.Direction.CW)
                path.moveTo(cx - s * 0.03f, cy + s * 0.35f)
                path.lineTo(cx - s * 0.03f, cy - s * 0.7f)
                path.lineTo(cx + s * 0.7f, cy - s * 0.45f)
                path.lineTo(cx + s * 0.7f, cy + s * 0.15f)
                path.lineTo(cx + s * 0.35f, cy + s * 0.15f)
                path.lineTo(cx + s * 0.35f, cy - s * 0.2f)
                path.lineTo(cx - s * 0.03f, cy - s * 0.38f)
                path.close()
            }
            IslandKind.TIMER, IslandKind.STOPWATCH -> {
                path.addCircle(cx, cy + s * 0.08f, s * 0.72f, Path.Direction.CW)
                path.addRect(cx - s * 0.18f, cy - s, cx + s * 0.18f, cy - s * 0.55f, Path.Direction.CW)
            }
            IslandKind.BATTERY_LOW, IslandKind.CHARGING -> {
                path.addRoundRect(
                    cx - s * 0.7f, cy - s * 0.42f, cx + s * 0.5f, cy + s * 0.42f,
                    s * 0.12f, s * 0.12f, Path.Direction.CW,
                )
                path.addRect(cx + s * 0.5f, cy - s * 0.18f, cx + s * 0.72f, cy + s * 0.18f, Path.Direction.CW)
            }
            IslandKind.DND -> {
                path.addCircle(cx, cy, s * 0.72f, Path.Direction.CW)
                path.addCircle(cx + s * 0.28f, cy - s * 0.12f, s * 0.5f, Path.Direction.CCW)
            }
            IslandKind.BLUETOOTH -> {
                path.moveTo(cx - s * 0.2f, cy - s * 0.55f)
                path.lineTo(cx + s * 0.35f, cy - s * 0.05f)
                path.lineTo(cx - s * 0.05f, cy + s * 0.25f)
                path.lineTo(cx - s * 0.05f, cy - s * 0.25f)
                path.lineTo(cx + s * 0.35f, cy + s * 0.05f)
                path.lineTo(cx - s * 0.2f, cy + s * 0.55f)
            }
            IslandKind.CALENDAR -> {
                path.addRoundRect(
                    cx - s * 0.7f, cy - s * 0.45f, cx + s * 0.7f, cy + s * 0.7f,
                    s * 0.14f, s * 0.14f, Path.Direction.CW,
                )
            }
            // A part-drawn ring: something is under way. Only a fallback — a live activity
            // normally draws the app's own icon.
            IslandKind.ACTIVITY -> {
                tmp.set(cx - s * 0.72f, cy - s * 0.72f, cx + s * 0.72f, cy + s * 0.72f)
                path.addArc(tmp, -90f, 270f)
                tmp.set(cx - s * 0.44f, cy - s * 0.44f, cx + s * 0.44f, cy + s * 0.44f)
                path.addArc(tmp, 270f, -270f)
                path.close()
            }
        }
        canvas.drawPath(path, paint)
    }

    fun drawTransport(canvas: Canvas, symbol: String, cx: Float, cy: Float, size: Float, paint: Paint) {
        path.reset()
        val s = size / 2f
        when (symbol) {
            "pause" -> {
                canvas.drawRoundRect(cx - s * 0.72f, cy - s, cx - s * 0.18f, cy + s, s * 0.12f, s * 0.12f, paint)
                canvas.drawRoundRect(cx + s * 0.18f, cy - s, cx + s * 0.72f, cy + s, s * 0.12f, s * 0.12f, paint)
                return
            }
            "next", "prev" -> {
                val dir = if (symbol == "next") 1f else -1f
                val bar = cx + dir * s * 0.7f
                canvas.drawRect(bar - s * 0.16f, cy - s * 0.82f, bar + s * 0.16f, cy + s * 0.82f, paint)
                // The point leads and the bar follows: next points right, previous left.
                path.moveTo(cx + dir * s * 0.34f, cy)
                path.lineTo(cx - dir * s * 0.72f, cy - s * 0.78f)
                path.lineTo(cx - dir * s * 0.72f, cy + s * 0.78f)
                path.close()
            }
            else -> {
                path.moveTo(cx - s * 0.42f, cy - s * 0.82f)
                path.lineTo(cx + s * 0.72f, cy)
                path.lineTo(cx - s * 0.42f, cy + s * 0.82f)
                path.close()
            }
        }
        canvas.drawPath(path, paint)
    }
}
