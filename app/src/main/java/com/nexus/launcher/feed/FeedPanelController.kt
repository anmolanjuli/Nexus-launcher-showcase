package com.nexus.launcher.feed

import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.R
import com.nexus.launcher.data.FeedDao
import com.nexus.launcher.ui.HomeEditBlurCoordinator
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.SettlePhysics
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Single-surface feed panel motion:
 * - **Open from home:** feed slides in on top, 1:1 with the finger (`translationX = -width + offset`).
 * - **Dismiss to home:** feed slides out to the left (`translationX = dx`), revealing the workspace underneath.
 * The workspace and system wallpaper remain stationary and unified at all times.
 */
class FeedPanelController(
    private val activity: MainActivity,
    private val repository: NexusFeedRepository,
    private val feedDao: FeedDao
) {
    private val mainContainer: FrameLayout = activity.findViewById(R.id.main_container)
    private val workspaceContainer: FrameLayout = activity.findViewById(R.id.workspace_container)
    private var feedPage: NexusFeedPage? = null
    private var settleAnim: FeedPanelAnim.Cancellable? = null
    /** True between the moment a Frosted Glass gesture arms the workspace blur and the moment the
     *  panel is fully parked again. Off entirely in Default/Neumorphism, where there is no blur
     *  to drive — and so also the flag that decides whether there is anything to tear down. */
    private var workspaceBlurArmed = false
    var isOpen = false
        private set

    init {
        activity.window.decorView.post { warmFeedPage() }
    }

    private fun panelWidth(): Float =
        workspaceContainer.width.toFloat()
            .coerceAtLeast(activity.resources.displayMetrics.widthPixels.toFloat())
            .coerceAtLeast(1f)

    private fun warmFeedPage() {
        val page = ensureFeedPage()
        parkFeedOffscreen(page)
        page.visibility = View.GONE
    }

    private fun ensureFeedPage(): NexusFeedPage {
        var page = feedPage
        if (page == null) {
            page = NexusFeedPage(
                activity, repository, feedDao,
                onFeedDismissProgress = { dx, width -> onFeedDragProgress(dx, width) },
                onFeedDismissCommit = { commitDismiss, velocityX ->
                    if (commitDismiss) {
                        close(animated = true, velocityX = velocityX)
                    } else {
                        open(animated = true, velocityX = velocityX)
                    }
                }
            )
            feedPage = page
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            page.layoutParams = lp
            mainContainer.addView(page, lp)
            parkFeedOffscreen(page)
            page.visibility = View.GONE
        }
        return page
    }

    private fun parkFeedOffscreen(page: NexusFeedPage) {
        page.translationX = -panelWidth()
    }

    private fun bringFeedToFront(page: NexusFeedPage) {
        mainContainer.bringChildToFront(page)
    }

    private fun enableHardwareLayer(page: NexusFeedPage) {
        if (page.layerType != View.LAYER_TYPE_HARDWARE) {
            page.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }

    private fun disableHardwareLayer(page: NexusFeedPage) {
        if (page.layerType != View.LAYER_TYPE_NONE) {
            page.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun applyOpenDrag(rawOffset: Float, width: Float) {
        val w = width.coerceAtLeast(1f)
        val page = ensureFeedPage()
        bringFeedToFront(page)
        enableHardwareLayer(page)
        val tx = (-w + rawOffset).coerceIn(-w, 0f)
        page.translationX = tx
        if (page.visibility != View.VISIBLE) {
            page.visibility = View.VISIBLE
        }
        syncWorkspaceBlur(page)
    }

    /** Workspace behind the panel: opaque in Default/Neumorphism (nothing to blur into), blurred
     *  in Frosted Glass to back the panel's own translucent fill — same relationship every other
     *  overlay in the app (search, folders, pickers, sheets) already follows.
     *
     *  Uses [HomeEditBlurCoordinator] (Manage Pages / Home Edit Mode's mechanism: one single
     *  `RenderEffect` on the whole `workspace_container`) rather than
     *  [com.nexus.launcher.ui.folder.FolderBlurCoordinator] (canvas / widget overlay / dock each
     *  blurred separately). The latter gave the dock a visibly different, mismatched blur peeking
     *  through the panel's translucent bottom bar — a real workspace should read as one unified
     *  blurred surface, not three independently-blurred pieces. */
    private fun armWorkspaceBlur() {
        if (!FrostedGlassEngine.isGlobalFrostedGlassEnabled) return
        workspaceBlurArmed = true
    }

    private fun releaseWorkspaceBlur() {
        if (!workspaceBlurArmed) return
        workspaceBlurArmed = false
        HomeEditBlurCoordinator.setProgress(activity, 0f)
    }

    /** Drives the workspace blur off how far the panel is actually revealed, so it ramps in with
     *  the finger and back out with the settle rather than snapping to full the instant a gesture
     *  starts and lingering at full through the whole slide-out. */
    private fun syncWorkspaceBlur(page: NexusFeedPage) {
        if (!workspaceBlurArmed) return
        val w = panelWidth()
        HomeEditBlurCoordinator.setProgress(activity, (page.translationX + w) / w)
    }

    fun onCanvasFeedSwipeBegan() {
        settleAnim?.cancel()
        settleAnim = null
        val page = ensureFeedPage()
        page.onPanelOpening()
        bringFeedToFront(page)
        enableHardwareLayer(page)
        parkFeedOffscreen(page)
        page.visibility = View.VISIBLE
        armWorkspaceBlur()
        syncWorkspaceBlur(page)
    }

    fun onCanvasSwipeRight(rawOffset: Float, width: Float) {
        applyOpenDrag(rawOffset, width)
    }

    private fun onFeedDragProgress(dx: Float, width: Float) {
        val w = width.coerceAtLeast(1f)
        val page = feedPage ?: return
        settleAnim?.cancel()
        settleAnim = null
        enableHardwareLayer(page)
        page.translationX = dx.coerceIn(-w, 0f)
        syncWorkspaceBlur(page)
    }

    fun onCanvasSwipeReleased(rawOffset: Float, velocityX: Float, width: Float) {
        val w = width.coerceAtLeast(1f)
        val page = feedPage ?: return
        // Project the reveal forward instead of testing distance OR velocity separately. The old
        // pair of rules could not see a reversal: past 25% revealed it committed to open even
        // when the finger was flicking hard back toward home.
        val projectedReveal = SettlePhysics.project(page.translationX + w, velocityX)
        if (projectedReveal > w * 0.25f) {
            open(animated = true, velocityX = velocityX)
        } else {
            close(animated = true, velocityX = velocityX)
        }
    }

    /**
     * The screen changed size, so the panel is put back where it belongs at the new width.
     *
     * Parked means `translationX = -width`. After a rotation the panel is re-laid out wider but
     * keeps the translation it was parked with, which in landscape is only about half the new
     * width — so a closed Feed sat across half the screen until something moved it.
     */
    fun onConfigurationChanged() {
        val page = feedPage ?: return
        settleAnim?.cancel()
        settleAnim = null
        page.post {
            if (isOpen) {
                page.translationX = 0f
            } else {
                parkFeedOffscreen(page)
            }
            syncWorkspaceBlur(page)
        }
    }

    fun open(animated: Boolean = true, velocityX: Float = 0f) {
        activity.canvasView.apply {
            pageFoldHandler.onActionCancel()
            dragScrollOffset = 0f
            isMutatingState = false
            onPageScroll?.invoke(currentPage, 0f)
        }

        val w = panelWidth()
        val page = ensureFeedPage()
        page.onPanelOpening()
        bringFeedToFront(page)
        page.visibility = View.VISIBLE
        armWorkspaceBlur()
        val currentTx = page.translationX

        settleAnim?.cancel()
        settleAnim = null

        if (animated) {
            enableHardwareLayer(page)
            val settle = FeedPanelAnim.settle(activity, currentTx, 0f, velocityX)
            settleAnim = FeedPanelAnim.animateFloat(
                context = activity,
                from = currentTx,
                to = 0f,
                durationMs = settle.durationMs,
                curve = settle.interpolator,
                onUpdate = { page.translationX = it; syncWorkspaceBlur(page) },
                onEnd = {
                    page.translationX = 0f
                    syncWorkspaceBlur(page)
                    disableHardwareLayer(page)
                    settleAnim = null
                    isOpen = true
                }
            )
        } else {
            disableHardwareLayer(page)
            page.translationX = 0f
            syncWorkspaceBlur(page)
            isOpen = true
        }
    }

    fun close(animated: Boolean = true, velocityX: Float = 0f) {
        val page = feedPage ?: return
        isOpen = false
        activity.canvasView.apply {
            pageFoldHandler.onActionCancel()
            dragScrollOffset = 0f
            isMutatingState = false
            onPageScroll?.invoke(currentPage, 0f)
        }

        val w = panelWidth()
        val currentTx = page.translationX

        settleAnim?.cancel()
        settleAnim = null

        if (animated) {
            enableHardwareLayer(page)
            val settle = FeedPanelAnim.settle(activity, currentTx, -w, velocityX)
            settleAnim = FeedPanelAnim.animateFloat(
                context = activity,
                from = currentTx,
                to = -w,
                durationMs = settle.durationMs,
                curve = settle.interpolator,
                onUpdate = { page.translationX = it; syncWorkspaceBlur(page) },
                onEnd = {
                    disableHardwareLayer(page)
                    parkFeedOffscreen(page)
                    page.visibility = View.GONE
                    settleAnim = null
                    releaseWorkspaceBlur()
                    activity.canvasView.apply {
                        dragScrollOffset = 0f
                        onPageScroll?.invoke(currentPage, 0f)
                    }
                    page.onPanelClosed()
                }
            )
        } else {
            disableHardwareLayer(page)
            parkFeedOffscreen(page)
            page.visibility = View.GONE
            releaseWorkspaceBlur()
            page.onPanelClosed()
        }
    }

    val isFeedActive: Boolean
        get() = isOpen || feedPage?.visibility == View.VISIBLE

    /** Forwarded from [com.nexus.launcher.ui.MainActivityStyleSync] when the UI Style picker
     *  changes, so an already-built panel restyles in place instead of waiting to be recreated. */
    fun onUiStyleChanged() {
        val page = feedPage ?: return
        page.onUiStyleChanged()
        // Switching styles while the panel is up has to move the workspace blur with it, in both
        // directions: Frosted Glass wants a blur that was never armed, the other two want the
        // armed one gone.
        if (!FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            releaseWorkspaceBlur()
        } else if (isFeedActive) {
            armWorkspaceBlur()
            syncWorkspaceBlur(page)
        }
    }

    /**
     * Puts the workspace blur back after the launcher returns from somewhere else — another app,
     * or the document reader — with the panel still open.
     *
     * The blur lives on the workspace container's render node and does not survive the trip, while
     * the coordinator still believes it applied it, so nothing restored it: the panel came back as
     * a translucent sheet over a sharp home screen until it was closed and opened again.
     */
    fun reassertBlurOnResume() {
        val page = feedPage ?: return
        if (!isFeedActive) return
        if (!FrostedGlassEngine.isGlobalFrostedGlassEnabled) return
        workspaceBlurArmed = true
        HomeEditBlurCoordinator.invalidateApplied()
        syncWorkspaceBlur(page)
    }

    fun handleBackPressed(): Boolean {
        if (isFeedActive) {
            close(animated = true)
            return true
        }
        return false
    }
}
