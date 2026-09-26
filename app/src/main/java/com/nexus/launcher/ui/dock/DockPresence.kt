package com.nexus.launcher.ui.dock

/**
 * Whether the dock is shown (Settings → Home → Show Dock, `homeShowDock`). When off, the dock is sized to nothing
 * by [DockLayoutSettingsBinder.updateOrientationBounds] — so it takes no touches or drops however
 * its visibility is set — and [DockHomeGridSync] gives its space to the home grid. Its apps stay
 * saved and come back with it.
 */
object DockPresence {

    @Volatile var enabled: Boolean = true
        private set

    fun apply(dock: DockLayout?, value: Boolean) {
        if (value == enabled) return
        enabled = value
        dock ?: return
        dock.updateOrientationBounds()
        dock.requestLayout()
        dock.invalidate()
        DockHomeGridSync.notifyCanvasFromDock(dock)
    }
}
