package com.nexus.launcher.ui.icons

import android.graphics.Path
import android.graphics.RectF
import com.nexus.launcher.ui.folder.FolderIconShapeDraw
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Global app-icon mask paths — distinct from folder [FolderIconShapeDraw.getShapePath].
 *
 * IDs are persisted, in `iconShape` and in per-app overrides, so they are append-only: 3, 8 and
 * 10 are retired (Shield, Cloud, Cube) and must never be handed to a new shape, or someone's
 * saved choice silently becomes a different one on update.
 */
object IconShapePaths {

    fun build(shapeType: Int, bounds: RectF): Path {
        val path = Path()
        val l = bounds.left
        val t = bounds.top
        val r = bounds.right
        val b = bounds.bottom
        val w = bounds.width()
        val h = bounds.height()
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val rad = min(w, h) / 2f
        when (shapeType) {
            // System = no mask. It used to share the circle branch, so "System" and "Circle" drew
            // identically in the picker and the preview while behaving differently on the home
            // screen, where IconResolver returns the raw icon for -1 and never masks at all. The
            // full bounds is the honest path: whatever shape the icon already has survives.
            -1 -> path.addRect(l, t, r, b, Path.Direction.CW)
            0 -> path.addCircle(cx, cy, rad, Path.Direction.CW)
            1 -> hexagon(path, cx, cy, rad * 0.96f)
            2 -> pebble(path, l, t, r, b, w, h)
            4 -> leaf(path, l, t, r, b, w, h)
            5 -> archBadge(path, l, t, r, b, w, h)
            6 -> path.addPath(FolderIconShapeDraw.getShapePath(bounds, rad, 3))
            7 -> capsule(path, l, t, r, b, w, h)
            // A hard 90-degree square read as unfinished beside every other option; 8% is enough
            // to look intentional without becoming the rounded square below.
            9 -> path.addRoundRect(l, t, r, b, w * 0.08f, h * 0.08f, Path.Direction.CW)
            11 -> path.addRoundRect(l, t, r, b, w * 0.36f, h * 0.36f, Path.Direction.CW)
            12 -> path.addRoundRect(l, t, r, b, w * 0.22f, h * 0.22f, Path.Direction.CW)
            13 -> polygon(path, cx, cy, rad * 0.99f, sides = 8, startAngleDeg = 22.5f)
            14 -> polygon(path, cx, cy, rad, sides = 4, startAngleDeg = 0f)
            15 -> polygon(path, cx, cy, rad * 0.96f, sides = 6, startAngleDeg = 0f, cornerRatio = 0.22f)
            // 3 (Shield), 8 (Cloud) and 10 (Cube) were removed. Anyone still holding one of those
            // ids lands here, on the rounded square — a visible change, but a safe one. Their ids
            // are retired rather than reused: handing 8 to a new shape would silently turn a saved
            // "Cloud" into something unrelated on update.
            else -> path.addRoundRect(l, t, r, b, w * 0.22f, h * 0.22f, Path.Direction.CW)
        }
        return path
    }

    /**
     * Regular polygon, optionally with rounded vertices.
     *
     * [startAngleDeg] rotates the first vertex: 0 puts a point at the right (a diamond for four
     * sides), 22.5 flats the top of an octagon. [cornerRatio] is a fraction of the edge length
     * pulled back from each vertex and replaced with a quadratic through it — 0 leaves it sharp.
     */
    private fun polygon(
        path: Path,
        cx: Float,
        cy: Float,
        radius: Float,
        sides: Int,
        startAngleDeg: Float,
        cornerRatio: Float = 0f,
    ) {
        if (sides < 3) return
        val pts = (0 until sides).map { i ->
            val a = Math.toRadians((startAngleDeg + i * 360f / sides).toDouble())
            (cx + radius * cos(a)).toFloat() to (cy + radius * sin(a)).toFloat()
        }
        if (cornerRatio <= 0f) {
            path.moveTo(pts[0].first, pts[0].second)
            for (i in 1 until sides) path.lineTo(pts[i].first, pts[i].second)
            path.close()
            return
        }
        val cut = cornerRatio.coerceIn(0f, 0.5f)
        fun lerp(a: Pair<Float, Float>, bp: Pair<Float, Float>, f: Float) =
            (a.first + (bp.first - a.first) * f) to (a.second + (bp.second - a.second) * f)

        for (i in 0 until sides) {
            val prev = pts[(i - 1 + sides) % sides]
            val cur = pts[i]
            val next = pts[(i + 1) % sides]
            val enter = lerp(cur, prev, cut)
            val exit = lerp(cur, next, cut)
            if (i == 0) path.moveTo(enter.first, enter.second) else path.lineTo(enter.first, enter.second)
            path.quadTo(cur.first, cur.second, exit.first, exit.second)
        }
        path.close()
    }

    /** Flat-top hexagon — tech / gem cut. */
    private fun hexagon(path: Path, cx: Float, cy: Float, radius: Float) {
        for (i in 0 until 6) {
            val angle = Math.toRadians((60.0 * i) - 30.0)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    /** Organic soft pebble — asymmetric cubic silhouette. */
    private fun pebble(path: Path, l: Float, t: Float, r: Float, b: Float, w: Float, h: Float) {
        val x0 = l + w * 0.50f
        val y0 = t + h * 0.06f
        path.moveTo(x0, y0)
        path.cubicTo(r - w * 0.02f, t + h * 0.08f, r + w * 0.02f, t + h * 0.55f, r - w * 0.08f, b - h * 0.18f)
        path.cubicTo(r - w * 0.22f, b + h * 0.02f, l + w * 0.28f, b + h * 0.04f, l + w * 0.12f, b - h * 0.22f)
        path.cubicTo(l - w * 0.04f, t + h * 0.55f, l + w * 0.05f, t + h * 0.12f, x0, y0)
        path.close()
    }


    /** Pointed leaf — tip at top-right, stem at bottom-left. */
    private fun leaf(path: Path, l: Float, t: Float, r: Float, b: Float, w: Float, h: Float) {
        path.moveTo(l + w * 0.18f, b - h * 0.12f)
        path.cubicTo(
            l - w * 0.02f, cy(t, h, 0.45f),
            l + w * 0.20f, t + h * 0.02f,
            r - w * 0.08f, t + h * 0.10f
        )
        path.cubicTo(
            r + w * 0.04f, t + h * 0.35f,
            r - w * 0.05f, b - h * 0.15f,
            l + w * 0.18f, b - h * 0.12f
        )
        path.close()
    }

    /** Arch / medal badge — rounded top, flat base. */
    private fun archBadge(path: Path, l: Float, t: Float, r: Float, b: Float, w: Float, h: Float) {
        val inset = w * 0.06f
        val left = l + inset
        val right = r - inset
        val top = t + h * 0.08f
        val bottom = b - h * 0.06f
        val midY = top + (bottom - top) * 0.55f
        path.moveTo(left, bottom)
        path.lineTo(left, midY)
        path.cubicTo(left, top, right, top, right, midY)
        path.lineTo(right, bottom)
        path.close()
    }

    /** Horizontal capsule / pill. */
    private fun capsule(path: Path, l: Float, t: Float, r: Float, b: Float, w: Float, h: Float) {
        val insetY = h * 0.16f
        val top = t + insetY
        val bottom = b - insetY
        val radius = (bottom - top) / 2f
        path.addRoundRect(l, top, r, bottom, radius, radius, Path.Direction.CW)
    }



    private fun cy(t: Float, h: Float, f: Float) = t + h * f
}
