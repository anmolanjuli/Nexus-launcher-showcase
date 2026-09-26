package com.nexus.launcher.ui.glass

import com.nexus.launcher.ui.dock.DockBackgroundRenderer

/**
 * The single writer of the UI Style flags, and the single place that decides the style changed.
 *
 * ## What went wrong before
 *
 * [FrostedGlassEngine.isGlobalFrostedGlassEnabled] and
 * [FrostedGlassEngine.isDefaultFlatStyleEnabled] had three writers: two collectors inside
 * `DockLayoutSettingsBinder.observeAppearance` (one on `uiStyleMode`, one a "safety net" on the
 * legacy `frostedGlassEnabled` field) and `MainActivityStyleSync`. Each also decided whether the
 * style had changed by comparing the incoming settings against those same shared flags.
 *
 * That is not a change detector, it is a race. The collectors sit on different flows —
 * `settingsRepo.settingsFlow` and `viewModel.nexusSettings` — with no ordering between them, so
 * whichever observed a change first wrote the flags and left the other looking at a no-op. The
 * loser then skipped its entire refresh: the dock's, or the canvas/widgets/folders/feed one.
 * Which one lost was stable within a process but not across launches, so a working toggle could
 * come back broken after a restart. The second dock collector could also write
 * `isGlobalFrostedGlassEnabled` without touching `isDefaultFlatStyleEnabled`, leaving the pair
 * describing a style that does not exist.
 *
 * ## The shape now
 *
 * Everyone reports the settings they saw to [apply]. It owns the comparison, writes the flags
 * once, and fans out to every registered listener — so a real change always runs every refresh
 * exactly once, no matter who noticed it first, and a non-change runs none.
 *
 * Listeners hold their own owner weakly and are keyed, so re-registering replaces rather than
 * accumulates and a destroyed activity or detached dock cannot be retained by this object.
 */
object UiStyleCoordinator {

    /** Registered under a stable key so a recreated owner replaces its previous registration. */
    const val LISTENER_DOCK = "dock"
    const val LISTENER_LAUNCHER = "launcher"
    const val LISTENER_SETTINGS = "settings"

    private val listeners = LinkedHashMap<String, () -> Unit>()

    /** `isDefault|isGlass` of the last style actually applied; null until the first [apply]. */
    private var appliedKey: String? = null

    @Synchronized
    fun setListener(key: String, onStyleChanged: () -> Unit) {
        listeners[key] = onStyleChanged
    }

    /**
     * Removes [key]'s listener, but only if it is still [listener].
     *
     * The identity check matters when an owner is replaced: a dock whose scope is cancelling runs
     * its cleanup *after* the replacement dock has already registered under the same key, so an
     * unconditional remove would silently unregister the live one and leave the new dock never
     * hearing about a style change again. Pass null only when tearing down unconditionally.
     */
    @Synchronized
    fun removeListener(key: String, listener: (() -> Unit)? = null) {
        if (listener == null || listeners[key] === listener) {
            listeners.remove(key)
        }
    }

    /**
     * Resolves [uiStyleMode] (falling back to the legacy [legacyFrostedGlassEnabled] boolean only
     * when no mode is stored, which is the precedence the launcher already used), and if that
     * differs from the style currently applied, writes the flags and notifies every listener.
     *
     * Safe to call from any thread and from as many collectors as want to report — that is the
     * point. Redundant calls are free.
     */
    fun apply(uiStyleMode: String, legacyFrostedGlassEnabled: Boolean) {
        val isDefault = uiStyleMode == FrostedGlassEngine.UI_STYLE_DEFAULT
        val isGlass = if (uiStyleMode.isNotEmpty()) {
            uiStyleMode == FrostedGlassEngine.UI_STYLE_FROSTED_GLASS
        } else {
            legacyFrostedGlassEnabled
        }

        val key = "$isDefault|$isGlass"
        val toNotify: List<() -> Unit>
        synchronized(this) {
            if (key == appliedKey) return
            appliedKey = key
            // Written together, always. The flags describe one style; a reader that catches them
            // disagreeing (both false, or both true) sees a style the picker cannot produce.
            FrostedGlassEngine.isDefaultFlatStyleEnabled = isDefault
            FrostedGlassEngine.isGlobalFrostedGlassEnabled = isGlass
            DockBackgroundRenderer.frostedGlassEnabled = isGlass
            toNotify = listeners.values.toList()
        }
        // Outside the lock: a listener rebinding widgets or folders must not hold it, and one
        // that throws must not stop the others from running.
        com.nexus.launcher.ui.widgets.NexusWidgetPreviewCache.clear()
        com.nexus.launcher.ui.widgets.performance.PerformancePreviewBuilder.clearCache()
        com.nexus.launcher.ui.widgets.mosaic.NexusCatalogPreviewDrawers.clearCache()
        for (listener in toNotify) {
            runCatching { listener() }
        }
    }
}
