package com.nexus.launcher.ui

import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.ui.dock.DockBackgroundRenderer
import com.nexus.launcher.ui.dock.DockLayout

/** Applies Home Edit / Manage Pages blur to the unified workspace container. */
internal object HomeEditBlurCoordinator {

    /** Radius the workspace blur settles at, and the window blur-behind radius that backs it. */
    // The numbers are the chrome role's — see ChromeBackdrop for why they are not local.
    private val MAX_BLUR_RADIUS = com.nexus.launcher.ui.glass.ChromeBackdrop.RADIUS_PX
    private val MAX_WINDOW_BLUR_RADIUS = com.nexus.launcher.ui.glass.ChromeBackdrop.WINDOW_RADIUS_PX

    /** Quantisation for [setProgress]. A [RenderEffect] is a new object every time the radius
     *  changes, so a finger-tracked blur that recomputed it on every fractional pixel allocated
     *  one per frame for a difference no eye can resolve. Same trick, same reason, as the
     *  drawer's home blur in `DrawEngineHomeBlurPass`. The window radius is coarser still because
     *  changing it crosses into the window manager. */
    private const val BLUR_STEP_PX = 3f
    private const val WINDOW_BLUR_STEP_PX = 8

    private var blurAnimator: ValueAnimator? = null
    private var currentBlurRadius = 0f
    private var lastBlurStep = -1
    private var lastWindowBlurStep = -1

    private fun workspaceOf(activity: MainActivity): FrameLayout? =
        activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.workspace_container)
            ?: activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)

    private fun applyDockSuppression(workspace: FrameLayout, isBlurred: Boolean) {
        for (i in 0 until workspace.childCount) {
            val child = workspace.getChildAt(i)
            if (child is DockLayout) {
                DockBackgroundRenderer.isSuppressed = isBlurred
                child.dockBackgroundInternal.isSuppressed = isBlurred
                if (isBlurred) {
                    child.outlineProvider = null
                    child.clipToOutline = false
                } else {
                    child.applyCornerRadiusOutline()
                    DockBackgroundRenderer.syncBackgroundLayer(child)
                }
                child.invalidate()
            }
        }
    }

    /**
     * Finger-tracked variant of [set] for the Feed panel: [fraction] is how far the panel has
     * been revealed (0 = fully parked offscreen, 1 = fully open) and the workspace blur is driven
     * straight off it, so the blur arrives and leaves with the panel instead of running its own
     * 250 ms ramp the moment the gesture starts.
     *
     * Unlike [set] this does not put a `LAYER_TYPE_HARDWARE` layer on the workspace.
     * `setRenderEffect` already draws the view through its own render node; adding an explicit
     * layer on top of that allocates a second full-screen offscreen buffer, which is exactly the
     * per-frame cost you feel while dragging.
     *
     * It also deliberately skips [applyDockSuppression]. Hiding the dock's own background is a
     * Home Edit / Manage Pages behaviour — that mode is *removing* the dock plate, so suppressing
     * it is the point. Blurring is not: the dock sits inside `workspace_container` and is
     * therefore already inside the [RenderEffect], so suppressing it as well made the frosted
     * dock background vanish the instant a Feed drag began and pop back on release. A blur should
     * only blur; nothing should appear or disappear.
     */
    /**
     * Forgets the last applied step, so the next [setProgress] re-applies even if the number has
     * not changed. Needed after the launcher has been away: the window comes back without the
     * blur, while this still believes it is applied.
     */
    fun invalidateApplied() {
        lastBlurStep = -1
        lastWindowBlurStep = -1
    }

    fun setProgress(activity: MainActivity, fraction: Float) {
        val workspace = workspaceOf(activity) ?: return
        val f = fraction.coerceIn(0f, 1f)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        // A settle animation drives this every frame; any leftover ramp from `set` would fight it.
        blurAnimator?.cancel()
        blurAnimator = null

        val blurStep = (f * MAX_BLUR_RADIUS / BLUR_STEP_PX).toInt()
        if (blurStep != lastBlurStep) {
            lastBlurStep = blurStep
            val radius = blurStep * BLUR_STEP_PX
            currentBlurRadius = radius
            if (radius > 0.5f) {
                workspace.setRenderEffect(com.nexus.launcher.ui.glass.ChromeBackdrop.effect(radius))
            } else {
                workspace.setRenderEffect(null)
                workspace.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }

        val windowStep = (f * MAX_WINDOW_BLUR_RADIUS / WINDOW_BLUR_STEP_PX).toInt()
        if (windowStep != lastWindowBlurStep) {
            lastWindowBlurStep = windowStep
            activity.window.apply {
                val radius = windowStep * WINDOW_BLUR_STEP_PX
                if (radius > 0) {
                    addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    setBackgroundBlurRadius(radius)
                } else {
                    clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    setBackgroundBlurRadius(0)
                }
                setDimAmount(0f)
            }
        }
    }

    fun set(activity: MainActivity, isBlurred: Boolean) {
        val workspace = workspaceOf(activity) ?: return

        lastBlurStep = -1
        lastWindowBlurStep = -1
        // Blur is a Frosted Glass effect: the other styles got a crisp sheet over a smeared home
        // screen. Same rule as FolderBlurCoordinator.setWorkspaceBlur, and "off" always runs.
        val blur = isBlurred && com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
        // The dock plate is suppressed only *as part of* that blur. With no blur to hide it, the
        // suppression was the only visible change: in Neumorphism the dock lost its raised plate
        // the moment the radial menu opened, and got it back on close.
        applyDockSuppression(workspace, blur)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (blur) {
                workspace.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                activity.window.apply {
                    addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    setBackgroundBlurRadius(MAX_WINDOW_BLUR_RADIUS)
                    setDimAmount(0f)
                }
            } else {
                activity.window.apply {
                    clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    setBackgroundBlurRadius(0)
                    setDimAmount(0f)
                }
            }

            blurAnimator?.cancel()
            val start = currentBlurRadius
            val target = if (blur) MAX_BLUR_RADIUS else 0f
            val durationMs = if (blur) 250L else 150L

            blurAnimator = ValueAnimator.ofFloat(start, target).apply {
                duration = durationMs
                addUpdateListener { va ->
                    val radius = va.animatedValue as Float
                    currentBlurRadius = radius
                    if (radius > 0.5f) {
                        workspace.setRenderEffect(com.nexus.launcher.ui.glass.ChromeBackdrop.effect(radius))
                    } else {
                        workspace.setRenderEffect(null)
                        if (!blur) {
                            workspace.setLayerType(View.LAYER_TYPE_NONE, null)
                        }
                    }
                }
                start()
            }
        }

        // Gated at the call site as well as inside the diagnostic, because the delayed probe
        // allocates a Runnable and schedules it on every blur change whether or not it logs.
        if (com.nexus.launcher.util.NexusDiag.ENABLED) {
            HomeEditBlurDiagnostics.logWorkspaceBlurState(activity, "SET_BLUR_STATE (isBlurred=$isBlurred, immediate)")
            workspace.postDelayed({
                HomeEditBlurDiagnostics.logWorkspaceBlurState(activity, "POST_ANIMATION (isBlurred=$isBlurred, +300ms)")
            }, 300L)
        }
    }
}
