package com.nexus.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.nexus.launcher.theme.NexusColorTokens
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** One icon in a radial menu branch. Icons only, on purpose: branches are a power-user shortcut. */
data class RadialBranchItem(@DrawableRes val iconRes: Int, val action: () -> Unit)

/**
 * The fan of icon bubbles that grows out past a radial menu slice when the finger rests on it.
 *
 * Bubbles sit on a ring just outside the donut, centred on their slice's direction. When that
 * would push any of them off screen the whole fan turns towards the middle of the screen in
 * 10° steps until it fits, so a menu opened near an edge still shows every icon.
 */
class HomeEditRadialBranch(
    private val context: Context,
    private val density: Float,
    private val invalidate: () -> Unit,
) {
    var openSlice = -1
        private set
    val isOpen get() = openSlice != -1
    var hovered = -1
        private set

    private var items: List<RadialBranchItem> = emptyList()
    private var centerX = 0f
    private var centerY = 0f
    private var angles = FloatArray(0)
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val bubbleRadius = 21f * density
    private val ringRadius = 176f * density
    private val iconSize = (22f * density).toInt()
    private val icons = mutableMapOf<Int, Bitmap>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val iconRect = RectF()

    private var pendingSlice = -1
    private var pendingOpen: Runnable? = null

    /**
     * Follows the ring's hovered slice. Resting on a slice that has a branch opens it after
     * [DWELL_MS], so a quick tap still just runs the slice. An open branch stays open until
     * another slice is hovered — a finger that slips off the bubbles, into the hub or past the
     * ring does not fold it away, and neither does lifting it.
     */
    fun trackSlice(
        host: View, event: MotionEvent, slice: Int, sliceSweepDeg: Float,
        branches: Map<Int, List<RadialBranchItem>>, cx: Float, cy: Float, onOpened: () -> Unit,
    ) {
        val pressed = event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE
        if (!pressed) {
            cancelPending(host)
            return
        }
        // A finger crossing a slice edge or the hub reads as no slice for a frame; that must not
        // restart the rest-to-open wait or close what is open.
        if (slice == -1 || slice == openSlice || slice == pendingSlice) return
        close()
        cancelPending(host)
        val branch = branches[slice]
        if (branch.isNullOrEmpty()) return
        pendingSlice = slice
        pendingOpen = Runnable {
            pendingSlice = -1
            pendingOpen = null
            open(slice, slice * sliceSweepDeg, cx, cy, branch, host.width, host.height)
            onOpened()
        }.also { host.postDelayed(it, DWELL_MS) }
    }

    fun cancelPending(host: View) {
        pendingOpen?.let(host::removeCallbacks)
        pendingOpen = null
        pendingSlice = -1
    }

    /** Whether this touch is on or between the open branch's bubbles rather than on the ring. */
    fun owns(event: MotionEvent): Boolean =
        isOpen && (hitTest(event.x, event.y) != -1 || isInZone(event.x, event.y))

    /** Handles a touch [owns] claimed; returns the item released on, if any. */
    fun onTouch(event: MotionEvent, onHoverTick: () -> Unit): RadialBranchItem? {
        val hit = hitTest(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> if (setHovered(hit)) onHoverTick()
            MotionEvent.ACTION_UP -> {
                val chosen = itemAt(hit)
                if (chosen != null) close() else setHovered(-1)
                return chosen
            }
            MotionEvent.ACTION_CANCEL -> setHovered(-1)
        }
        return null
    }

    fun open(slice: Int, sliceAngleDeg: Float, cx: Float, cy: Float, branch: List<RadialBranchItem>, width: Int, height: Int) {
        if (branch.isEmpty()) return
        openSlice = slice
        items = branch
        centerX = cx
        centerY = cy
        hovered = -1
        angles = fitAngles(sliceAngleDeg, branch.size, width, height)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 140
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun close() {
        if (!isOpen) return
        animator?.cancel()
        openSlice = -1
        hovered = -1
        progress = 0f
        invalidate()
    }

    /** Returns true when the hovered bubble changed, so the caller can tick a haptic. */
    fun setHovered(index: Int): Boolean {
        if (index == hovered) return false
        hovered = index
        invalidate()
        return index != -1
    }

    fun itemAt(index: Int): RadialBranchItem? = items.getOrNull(index)

    fun hitTest(x: Float, y: Float): Int {
        if (!isOpen) return -1
        val slop = bubbleRadius + 8f * density
        angles.forEachIndexed { i, a ->
            if (hypot(x - bubbleX(a), y - bubbleY(a)) <= slop) return i
        }
        return -1
    }

    /** The band the fan occupies, so moving between bubbles does not fold the branch away. */
    fun isInZone(x: Float, y: Float): Boolean {
        if (!isOpen || angles.isEmpty()) return false
        val distance = hypot(x - centerX, y - centerY)
        if (distance < ringRadius - bubbleRadius * 2.4f || distance > ringRadius + bubbleRadius * 2f) return false
        val angle = Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()
        val margin = angularStep(angles.size) * 0.75f
        val first = angles.first()
        return deltaDeg(angle, first) >= -margin && deltaDeg(angle, first) <= deltaDeg(angles.last(), first) + margin
    }

    fun draw(canvas: Canvas, tokens: NexusColorTokens) {
        if (!isOpen) return
        val alpha = (255 * progress).toInt().coerceIn(0, 255)
        val innerRadius = 140f * density
        angles.forEachIndexed { i, a ->
            // Each bubble slides out from the donut's rim to its place on the ring.
            val r = innerRadius + (ringRadius - innerRadius) * progress
            val rad = Math.toRadians(a.toDouble())
            val x = centerX + r * cos(rad).toFloat()
            val y = centerY + r * sin(rad).toFloat()
            val isHovered = i == hovered
            val radius = bubbleRadius * (0.6f + 0.4f * progress) * if (isHovered) 1.12f else 1f
            fill.color = if (isHovered) tokens.textPrimary else tokens.surface
            fill.alpha = alpha
            canvas.drawCircle(x, y, radius, fill)
            stroke.color = tokens.divider
            stroke.strokeWidth = 1f * density
            stroke.alpha = alpha
            canvas.drawCircle(x, y, radius, stroke)
            val bmp = icon(items[i].iconRes) ?: return@forEachIndexed
            iconPaint.colorFilter = PorterDuffColorFilter(
                if (isHovered) tokens.surface else tokens.textPrimary, PorterDuff.Mode.SRC_IN,
            )
            iconPaint.alpha = alpha
            val half = iconSize / 2f * (radius / bubbleRadius)
            iconRect.set(x - half, y - half, x + half, y + half)
            canvas.drawBitmap(bmp, null, iconRect, iconPaint)
        }
    }

    private companion object {
        const val DWELL_MS = 120L
    }

    private fun fitAngles(sliceAngle: Float, count: Int, width: Int, height: Int): FloatArray {
        val step = angularStep(count)
        val span = step * (count - 1)
        var best = FloatArray(0)
        var bestOutside = Int.MAX_VALUE
        for (offset in listOf(0f) + (1..18).flatMap { listOf(it * 10f, -it * 10f) }) {
            val start = sliceAngle + offset - span / 2f
            val candidate = FloatArray(count) { start + it * step }
            val outside = candidate.count { !fits(it, width, height) }
            if (outside < bestOutside) {
                best = candidate
                bestOutside = outside
            }
            if (outside == 0) break
        }
        return best
    }

    private fun fits(angle: Float, width: Int, height: Int): Boolean {
        val margin = bubbleRadius + 6f * density
        val x = bubbleX(angle)
        val y = bubbleY(angle)
        return x >= margin && x <= width - margin && y >= margin + 24f * density && y <= height - margin
    }

    private fun angularStep(count: Int): Float {
        val gap = 6f * density
        return Math.toDegrees(2.0 * asin(((bubbleRadius * 2 + gap) / 2f / ringRadius).toDouble())).toFloat()
            .let { if (count > 1) it else 0f }
    }

    private fun bubbleX(angle: Float) = centerX + ringRadius * cos(angle * PI / 180.0).toFloat()
    private fun bubbleY(angle: Float) = centerY + ringRadius * sin(angle * PI / 180.0).toFloat()

    private fun deltaDeg(angle: Float, from: Float): Float = ((angle - from) % 360f + 540f) % 360f - 180f

    private fun icon(@DrawableRes res: Int): Bitmap? = icons.getOrPut(res) {
        val drawable = ContextCompat.getDrawable(context, res) ?: return null
        createBitmap(iconSize, iconSize).also { bmp ->
            drawable.setBounds(0, 0, iconSize, iconSize)
            drawable.draw(Canvas(bmp))
        }
    }
}
