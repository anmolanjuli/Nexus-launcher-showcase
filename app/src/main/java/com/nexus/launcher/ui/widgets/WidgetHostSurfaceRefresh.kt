package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.view.View
import android.view.ViewGroup
import com.nexus.launcher.ui.model.LauncherState

/**
 * After stopListening/startListening (app switch, Settings on top),
 * existing widget host views may go blank until a visibility cycle.
 * This object orchestrates recovery in two phases to minimise flicker:
 *
 * Phase 1 (immediate, non-flashing): restores overlay presentation state
 * and pokes Nexus-only widget options so content is available as soon as
 * the launcher window reappears.
 *
 * Phase 2 (delayed [ANIMATION_SETTLE_MS], after animation completes):
 * performs the INVISIBLE→VISIBLE toggle that resets the stale composited
 * surface and forces collection-widget adapters to reconnect.
 *
 * Why ALL host views need the visibility cycle in Phase 2:
 * Collection-based third-party widgets (e.g. Google Calendar) use a
 * ListView backed by RemoteViewsAdapter. stopListening() severs that
 * adapter connection. The INVISIBLE→VISIBLE toggle triggers
 * onWindowVisibilityChanged, which causes RemoteViewsAdapter to reconnect
 * and refetch data. Without it, those widgets show their empty initial
 * layout ("Nothing planned") on every app-return.
 * The 380ms delay means the window-return animation is already complete,
 * so the 1-frame INVISIBLE flash is hidden from the user.
 *
 * Rules enforced:
 * - The pulse is only armed after a genuine stopListening→startListening
 *   cycle via [markStopped]. onHomeResumed() (no stopListening) skips it.
 * - A [DEBOUNCE_MS] guard prevents onStart + onResume from double-firing.
 * - NexusWidgetView additionally receives notifyAppWidgetViewDataChanged
 *   for its own collection adapters after the visibility toggle.
 */
object WidgetHostSurfaceRefresh {

    /** Approx. duration of the window-return animation on modern Android. */
    private const val ANIMATION_SETTLE_MS = 380L

    /**
     * Prevents the onStart + onResume pair from each scheduling a full pulse.
     * Both arrive within ~16 ms of each other; one pass is sufficient.
     */
    private const val DEBOUNCE_MS = 600L
    private var lastScheduledAt = 0L

    /**
     * Armed by [markStopped] (called from onStop) so the next [schedule]
     * knows a genuine restart occurred and all host views need recovery.
     */
    private var genuineRestartPending = false

    /** Must be called from WidgetHostLifecycle.onStop to arm the next pulse. */
    fun markStopped() {
        genuineRestartPending = true
    }

    fun schedule(overlay: WidgetOverlayLayout) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastScheduledAt < DEBOUNCE_MS) {
            // Discard the onResume duplicate that always follows onStart.
            genuineRestartPending = false
            return
        }
        lastScheduledAt = now
        val doInvisiblePulse = genuineRestartPending
        genuineRestartPending = false

        // Phase 1: immediate, non-flashing.
        overlay.post { applyNonFlashing(overlay) }

        // Phase 2: delayed — only after a genuine stopListening→startListening.
        if (doInvisiblePulse) {
            overlay.postDelayed({ applyPulse(overlay) }, ANIMATION_SETTLE_MS)
        }
    }

    /** Applies both phases immediately (for forced refreshes, e.g. permission grants). */
    fun apply(overlay: WidgetOverlayLayout) {
        applyNonFlashing(overlay)
        applyPulse(overlay)
    }

    internal fun applyNonFlashing(overlay: WidgetOverlayLayout) {
        if (overlay.width <= 0 || overlay.height <= 0) return
        val canvas = overlay.canvasView
        if (canvas != null && canvas.uiState != LauncherState.HOME) {
            NexusWidgetHostPoke.poke(overlay)
            return
        }
        overlay.restoreHomePresentation()
        NexusWidgetHostPoke.poke(overlay)
    }

    private fun applyPulse(overlay: WidgetOverlayLayout) {
        if (overlay.width <= 0 || overlay.height <= 0) return
        pulseHosts(overlay)
    }

    private fun pulseHosts(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is AppWidgetHostView -> pulse(child)
                is ViewGroup -> pulseHosts(child)
            }
        }
    }

    private fun pulse(host: AppWidgetHostView) {
        if (host.visibility != View.VISIBLE) return
        host.visibility = View.INVISIBLE
        host.post {
            host.visibility = View.VISIBLE
            // NexusWidgetView also receives an explicit notifyAppWidgetViewDataChanged
            // for its own first-party collection adapters. Third-party adapters are
            // refreshed by the host's built-in onWindowVisibilityChanged handler
            // triggered by the INVISIBLE→VISIBLE toggle above.
            if (host is NexusWidgetView) NexusWidgetViewNotify.refresh(host)
            host.requestLayout()
            host.invalidate()
        }
    }
}
