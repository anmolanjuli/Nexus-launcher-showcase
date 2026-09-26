package com.nexus.launcher.ui.canvas

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.nexus.launcher.ui.folder.FolderBlurCoordinator
import com.nexus.launcher.ui.model.LauncherState

/**
 * Gravity Fold touch state machine. Ownership flag [isFoldGestureOwned] gates the
 * stream the same way FolderDragState.isDraggingFromFolder gates folder-origin drags:
 * set on confirmed blank-area (non-widget) claim, cleared when the gesture ends.
 */
class PageFoldGestureHandler(private val view: LauncherCanvasView) {
    var isFoldGestureOwned = false
        private set
    val isActivelyDrawing: Boolean
        get() = snapshot?.isRecycled == false

    private val renderer = PageFoldRenderer()
    private var widgetBlocked = false
    private var touchDownX = 0f
    private var hingeX = 0f
    private var rotationAngle = 0f
    private var progress = 0f
    private var foldLeftPiece = true
    private var snapshot: Bitmap? = null
    private var snapshotPrev: Bitmap? = null
    private var snapshotNext: Bitmap? = null
    private var overlayAlphaSaved = 1f
    private var overlayHidden = false

    fun onActionDown(event: MotionEvent) {
        if (isActivelyDrawing) {
            view.pageAnimator?.cancel()
            releaseOwnership(recycle = true)
            view.isMutatingState = false
        }
        isFoldGestureOwned = false
        widgetBlocked = isTouchOnFocusMosaic(event)
        touchDownX = event.x
        hingeX = event.x
        rotationAngle = 0f
        progress = 0f
    }

    fun tryClaimHorizontalSwipe(event: MotionEvent): Boolean {
        if (isFoldGestureOwned) return true
        if (widgetBlocked) return false
        if (!isGravityFoldEnabled()) return false
        if (view.uiState != LauncherState.HOME) return false
        if (view.drawerTranslationY < view.viewHeight - 1) return false
        if (view.totalPages <= 1) return false
        if (view.draggedItem != null) return false
        if (view.currentPage == 0 && (event.x - touchDownX) > 0 && view.showFeed) return false
        val w = view.viewWidth.coerceAtLeast(1)
        recycleSnapshot()
        snapshot = capturePage(view.currentPage) ?: return false
        snapshotPrev = capturePage(view.currentPage - 1)
        snapshotNext = capturePage(view.currentPage + 1)
        hingeX = touchDownX.coerceIn(1f, (w - 1).toFloat())
        isFoldGestureOwned = true
        view.isMutatingState = true
        setOverlayHidden(true)
        onActionMove(event)
        return true
    }

    fun onActionMove(event: MotionEvent) {
        if (!isFoldGestureOwned || snapshot == null) return
        val dragDistance = event.x - touchDownX
        foldLeftPiece = dragDistance < 0f
        val foldMax = foldMaxDistance()
        progress = if (foldMax <= 0f) 0f else (kotlin.math.abs(dragDistance) / foldMax).coerceIn(0f, 1f)
        rotationAngle = progress * MAX_ANGLE
        view.postInvalidateOnAnimation()
    }

    fun onActionUp(event: MotionEvent) {
        if (!isFoldGestureOwned) {
            releaseOwnership(recycle = true)
            return
        }
        onActionMove(event)
        val delta = commitDelta()
        val canCommit = progress >= COMMIT_PROGRESS && delta != 0
        if (canCommit) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            animateAngle(rotationAngle, MAX_ANGLE, COMMIT_MS, DecelerateInterpolator(1.0f), commit = true, delta = delta)
        } else {
            animateAngle(rotationAngle, 0f, SPRING_MS, OvershootInterpolator(1.2f), commit = false, delta = 0)
        }
    }

    fun onActionCancel() {
        if (!isFoldGestureOwned && snapshot == null) return
        animateAngle(rotationAngle, 0f, SPRING_MS, OvershootInterpolator(1.2f), commit = false, delta = 0)
    }

    fun draw(canvas: Canvas, drawHomeAlpha: Int, bounceScale: Float) {
        val bmp = snapshot ?: return
        if (bmp.isRecycled) return
        val reveal = if (foldLeftPiece) snapshotNext else snapshotPrev
        if (reveal != null && !reveal.isRecycled) {
            renderer.drawUnderlay(canvas, reveal)
        }
        renderer.draw(
            canvas, bmp, hingeX, rotationAngle, foldLeftPiece,
            view.viewWidth, view.viewHeight,
            view.resources.displayMetrics.density, view.accentColor
        )
    }

    private fun animateAngle(
        from: Float,
        to: Float,
        durationMs: Long,
        interpolator: android.view.animation.Interpolator,
        commit: Boolean,
        delta: Int
    ) {
        view.pageAnimator?.cancel()
        val speedMult = view.context.getSharedPreferences(
            "nexus_prefs", android.content.Context.MODE_PRIVATE
        ).getFloat("anim_speed_multiplier", 1.0f)
        val animator = ValueAnimator.ofFloat(from, to).apply {
            duration = (durationMs * speedMult).toLong().coerceIn(80L, 300L)
            this.interpolator = interpolator
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                progress = (kotlin.math.abs(rotationAngle) / MAX_ANGLE).coerceIn(0f, 1f)
                view.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false
                override fun onAnimationCancel(a: Animator) { canceled = true }
                override fun onAnimationEnd(a: Animator) {
                    if (canceled) return
                    view.pageAnimator = null
                    view.isMutatingState = false
                    if (commit && delta != 0) {
                        view.onPageSwipe?.invoke(delta)
                        FolderBlurCoordinator.findWidgetOverlay(view.context)
                            ?.syncScroll(view.currentPage, 0f)
                    }
                    releaseOwnership(recycle = true)
                    view.postInvalidateOnAnimation()
                }
            })
        }
        view.pageAnimator = animator
        animator.start()
    }

    private fun releaseOwnership(recycle: Boolean) {
        isFoldGestureOwned = false
        widgetBlocked = false
        setOverlayHidden(false)
        if (recycle) {
            recycleSnapshot()
            rotationAngle = 0f
            progress = 0f
        }
    }

    private fun recycleSnapshot() {
        snapshot?.let { if (!it.isRecycled) it.recycle() }
        snapshotPrev?.let { if (!it.isRecycled) it.recycle() }
        snapshotNext?.let { if (!it.isRecycled) it.recycle() }
        snapshot = null
        snapshotPrev = null
        snapshotNext = null
    }

    private fun capturePage(page: Int): Bitmap? {
        if (page < 0 || page >= view.totalPages) return null
        val w = view.viewWidth.coerceAtLeast(1)
        val h = view.viewHeight.coerceAtLeast(1)
        return PageThumbnailRenderer.renderPage(
            view, page, w, h, includeDock = false, includeWidgets = true
        )
    }

    private fun foldMaxDistance(): Float = view.viewWidth * FOLD_DISTANCE_FRACTION

    private fun commitDelta(): Int {
        val maxPage = view.totalPages - 1
        return when {
            foldLeftPiece && view.currentPage < maxPage -> 1
            !foldLeftPiece && view.currentPage > 0 -> -1
            else -> 0
        }
    }

    private fun isGravityFoldEnabled(): Boolean {
        if (!view.transitionStyle.equals(STYLE_KEY, ignoreCase = true)) return false
        return !isReduceMotion(view.context)
    }

    private fun isTouchOnFocusMosaic(event: MotionEvent): Boolean {
        val overlay = FolderBlurCoordinator.findWidgetOverlay(view.context) ?: return false
        val loc = IntArray(2)
        overlay.getLocationOnScreen(loc)
        val localX = event.rawX - loc[0] + overlay.scrollX
        val localY = event.rawY - loc[1]
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (localX < child.left || localX >= child.right ||
                localY < child.top || localY >= child.bottom
            ) continue
            val mosaic = child as? com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView
                ?: return false
            return mosaic.currentConfig().mode ==
                com.nexus.launcher.ui.widgets.mosaic.MosaicConfig.MODE_SINGLE
        }
        return false
    }

    private fun setOverlayHidden(hidden: Boolean) {
        val overlay = FolderBlurCoordinator.findWidgetOverlay(view.context) ?: return
        if (hidden == overlayHidden) return
        if (hidden) {
            overlayAlphaSaved = overlay.alpha
            overlay.alpha = 0f
            overlayHidden = true
        } else {
            overlay.alpha = overlayAlphaSaved
            overlayHidden = false
        }
    }

    companion object {
        const val STYLE_KEY = "gravity_fold"
        const val MAX_ANGLE = 100f
        const val COMMIT_PROGRESS = 0.35f
        const val FOLD_DISTANCE_FRACTION = 0.6f
        const val COMMIT_MS = 220L
        const val SPRING_MS = 240L

        fun isReduceMotion(context: android.content.Context): Boolean {
            return context.getSharedPreferences(
                "nexus_prefs", android.content.Context.MODE_PRIVATE
            ).getBoolean("reduce_motion", false)
        }
    }
}
