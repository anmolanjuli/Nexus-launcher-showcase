package com.nexus.launcher.ui.canvas

import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Build

/**
 * 3D perspective and depth transformations for drawer transitions.
 *
 * The one place the transition geometry is defined. The canvas concatenates these matrices, and
 * [Drawer3DViewTransform] hands the same matrices to the views that sit over the canvas (widgets,
 * dock, the drawer's search pill and overflow, the Categories overlay) — so nothing works the
 * shape out a second time. Camera and Matrix are allocated once (zero onDraw allocations).
 */
object Drawer3DTransitionEngine {
    private val camera = Camera()
    private val matrix = Matrix()

    /** Below this the home pass draws directly rather than through its node; see DrawEngineHomeBlurPass. */
    const val HOME_NODE_MIN_PROGRESS = 0.01f

    /** Whether the home half is transformed at [progress] — the same gate the home node uses. */
    fun homeApplies(progress: Float): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            progress > HOME_NODE_MIN_PROGRESS && progress < 1f

    /** Whether the drawer half is transformed at [progress] — the drawer blur node needs API 31. */
    fun drawerApplies(progress: Float): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && progress > 0f && progress < 1f

    /**
     * Writes the home transform for [progress] into [out], in canvas coordinates.
     * Returns false, leaving [out] untouched, when this style does not move home.
     */
    fun homeMatrix(style: String, progress: Float, width: Float, height: Float, out: Matrix): Boolean {
        if (progress <= 0f) return false
        when (style) {
            "cube" -> perspective(
                -progress * 60f, progress * width * 0.35f, width / 2f, height * 0.65f, out,
            )
            "zoom" -> {
                val scale = (1f - (progress * 0.22f)).coerceIn(0.5f, 1f)
                out.setScale(scale, scale, width / 2f, height / 2f)
            }
            "tilt" -> perspective(-progress * 38f, 0f, width / 2f, height * 0.15f, out)
            "stack" -> {
                val scale = (1f - (progress * 0.12f)).coerceIn(0.8f, 1f)
                out.setScale(scale, scale, width / 2f, height * 0.4f)
                out.postTranslate(0f, -progress * height * 0.06f)
            }
            else -> return false // "default": stationary crossfade and blur
        }
        return true
    }

    /**
     * Writes the drawer transform for [progress] into [out], in canvas coordinates.
     * Returns false, leaving [out] untouched, when this style does not move the drawer.
     */
    fun drawerMatrix(style: String, progress: Float, width: Float, height: Float, out: Matrix): Boolean {
        if (progress >= 1f) return false
        val rest = 1f - progress
        when (style) {
            "cube" -> perspective(rest * 60f, rest * width * 0.35f, width / 2f, height * 0.35f, out)
            "zoom" -> {
                val scale = (0.85f + (progress * 0.15f)).coerceIn(0.5f, 1f)
                out.setScale(scale, scale, width / 2f, height * 0.45f)
            }
            "tilt" -> perspective(rest * 38f, 0f, width / 2f, height * 0.85f, out)
            "stack" -> {
                val scale = (0.92f + (progress * 0.08f)).coerceIn(0.8f, 1f)
                out.setScale(scale, scale, width / 2f, height * 0.5f)
                out.postTranslate(0f, rest * height * 0.22f)
            }
            else -> return false // "default": stationary crossfade and blur
        }
        return true
    }

    fun applyHomeTransform(canvas: Canvas, style: String, progress: Float, width: Float, height: Float) {
        if (homeMatrix(style, progress, width, height, matrix)) canvas.concat(matrix)
    }

    fun applyDrawerTransform(canvas: Canvas, style: String, progress: Float, width: Float, height: Float) {
        if (drawerMatrix(style, progress, width, height, matrix)) canvas.concat(matrix)
    }

    /** An X-axis roll of [angle] degrees, pushed [depth] away, about ([pivotX], [pivotY]). */
    private fun perspective(angle: Float, depth: Float, pivotX: Float, pivotY: Float, out: Matrix) {
        camera.save()
        camera.translate(0f, 0f, depth)
        camera.rotateX(angle)
        camera.getMatrix(out)
        camera.restore()
        out.preTranslate(-pivotX, -pivotY)
        out.postTranslate(pivotX, pivotY)
    }
}
