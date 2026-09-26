package com.nexus.launcher.ui.immersive

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.MainActivity

/**
 * Owns the immersive status row ([ImmersiveStatusBarView]): shows it while immersive mode is on
 * and at least one item is enabled, and tells the home grid and drawer how much height at the top
 * to leave for it ([reservedTopPx]).
 *
 * In portrait the row sits in the camera cutout's band, which is already kept clear; in
 * landscape (camera on the side) there is no such band, so the row's own height is reserved.
 */
object ImmersiveStatus {

    const val ROW_HEIGHT_DP = 28f

    data class Items(
        val clock: Boolean = true,
        val notifications: Boolean = true,
        val wifi: Boolean = true,
        val signal: Boolean = true,
        val battery: Boolean = true
    ) {
        val any: Boolean get() = clock || notifications || wifi || signal || battery
    }

    /** True when the launcher is keeping notifications, so the swipe-down opens its own sheet. */
    @Volatile var ownSheet: Boolean = false
        private set

    /** Height to keep clear at the top for the row; 0 when it is not shown, or it is at the bottom. */
    @Volatile var reservedTopPx: Int = 0
        private set

    /** The same, for a row placed along the bottom edge (DockHomeGridSync adds it). */
    @Volatile var reservedBottomPx: Int = 0
        private set

    private var view: ImmersiveStatusBarView? = null
    val statusBarView: ImmersiveStatusBarView?
        get() = view
    private var hookedCanvas: com.nexus.launcher.ui.canvas.LauncherCanvasView? = null

    val isActive: Boolean
        get() = view != null && view?.isAttachedToWindow == true

    fun setIslandBounds(left: Float?, right: Float?) {
        view?.setIslandCutout(left, right)
    }

    fun apply(activity: MainActivity, settings: NexusSettingsData) {
        val items = Items(
            clock = settings.statusShowClock,
            notifications = settings.statusShowNotifications,
            wifi = settings.statusShowWifi,
            signal = settings.statusShowSignal,
            battery = settings.statusShowBattery
        )
        ownSheet = settings.notificationHistory
        val show = settings.immersiveMode && settings.statusBarEnabled && items.any
        val atBottom = settings.statusBarPosition == ImmersiveStatusStyle.BAR_BOTTOM
        val density = activity.resources.displayMetrics.density
        val reserve = if (show) (settings.statusBarHeightDp * density).toInt() else 0
        if (show) {
            attach(activity, atBottom).apply {
                setItems(items)
                setItemStyle(ImmersiveStatusStyle.of(settings))
                setBar(settings.statusBarHeightDp, settings.statusBarPaddingDp, settings.statusBarBackground)
            }
            if (com.nexus.launcher.ui.canvas.SelectionModeTransform.isCardTrackActive(activity.canvasView)) {
                view?.let {
                    com.nexus.launcher.ui.canvas.SelectionModeCardTrack.applyStatusBarViewTransform(activity.canvasView, it)
                }
            }
        } else detach()
        val top = if (atBottom) 0 else reserve
        val bottom = if (atBottom) reserve else 0
        if (top != reservedTopPx || bottom != reservedBottomPx) {
            reservedTopPx = top
            reservedBottomPx = bottom
            ViewCompat.requestApplyInsets(activity.canvasView)
            // The dock sits along the same edge, so it has to move out of the row's way too.
            com.nexus.launcher.ui.dock.DockLayout.findFrom(activity.canvasView)?.let { dock ->
                dock.updateOrientationBounds()
                com.nexus.launcher.ui.dock.DockHomeGridSync.notifyCanvasFromDock(dock)
            }
            activity.canvasView.recalculateLayout()
        }
    }

    private fun attach(activity: MainActivity, atBottom: Boolean): ImmersiveStatusBarView {
        view?.let {
            if (it.isAttachedToWindow) {
                val params = it.layoutParams as? FrameLayout.LayoutParams
                val gravity = if (atBottom) android.view.Gravity.BOTTOM else android.view.Gravity.TOP
                if (params != null && params.gravity != gravity) {
                    params.gravity = gravity
                    it.layoutParams = params
                }
                return it
            }
        }
        val root = activity.findViewById<ViewGroup>(R.id.main_container)
        val created = ImmersiveStatusBarView(activity) { activity.canvasView.homeLabelColor }
        // Just above the workspace (home + drawer), below the drawer chrome, sheets and menus.
        val index = (root.indexOfChild(activity.findViewById(R.id.workspace_container)) + 1)
            .coerceIn(0, root.childCount)
        root.addView(
            created, index,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (atBottom) android.view.Gravity.BOTTOM else android.view.Gravity.TOP,
            )
        )
        view = created
        hookDrawerProgress(activity)
        updateVisibility()
        return created
    }

    /**
     * The row belongs to the home screen, not the drawer: it fades out as the drawer opens and
     * takes no touches once hidden. Chained onto the canvas's per-frame progress callback, like
     * the dock and the widget overlay.
     */
    private fun hookDrawerProgress(activity: MainActivity) {
        val canvas = activity.canvasView
        if (hookedCanvas === canvas) return
        hookedCanvas = canvas
        val previous = canvas.onDrawerAnimationProgress
        canvas.onDrawerAnimationProgress = { progress ->
            previous?.invoke(progress)
            drawerAlpha = (1f - progress * 2f).coerceIn(0f, 1f)
            updateVisibility()
        }
    }

    private var drawerAlpha = 1f
    private var systemStatusShowing = false

    /**
     * While the system status bar is on screen (a top-edge swipe, until it is hidden again) the
     * row steps aside, so the two never overlap.
     */
    fun setSystemStatusShowing(showing: Boolean) {
        val v = view
        v?.removeCallbacks(restoreAfterSystemBar)
        if (showing) {
            v?.animate()?.cancel()
            systemStatusShowing = true
            updateVisibility()
        } else if (systemStatusShowing) {
            // The insets read "hidden" as soon as the hide is requested, while the system bar is
            // still sliding away — wait for it to be gone, then fade back in.
            v?.postDelayed(restoreAfterSystemBar, SYSTEM_BAR_EXIT_MS) ?: run { systemStatusShowing = false }
        }
    }

    private const val SYSTEM_BAR_EXIT_MS = 450L

    private val restoreAfterSystemBar = Runnable {
        systemStatusShowing = false
        val v = view ?: return@Runnable
        if (drawerAlpha <= 0f) { updateVisibility(); return@Runnable }
        v.alpha = 0f
        v.visibility = android.view.View.VISIBLE
        v.animate().alpha(drawerAlpha).setDuration(180).start()
    }

    private fun updateVisibility() {
        val v = view ?: return
        val alpha = if (systemStatusShowing) 0f else drawerAlpha
        if (v.alpha != alpha) v.alpha = alpha
        val visibility = if (alpha <= 0f) android.view.View.INVISIBLE else android.view.View.VISIBLE
        if (v.visibility != visibility) v.visibility = visibility
    }

    private fun detach() {
        view?.let {
            com.nexus.launcher.ui.canvas.SelectionModeCardTrack.clearViewTransform(it)
            (it.parent as? ViewGroup)?.removeView(it)
        }
        view = null
    }
}
