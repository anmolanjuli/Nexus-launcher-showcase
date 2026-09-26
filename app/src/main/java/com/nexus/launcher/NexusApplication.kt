package com.nexus.launcher

import android.app.Application
import com.nexus.launcher.premium.PremiumManager
import com.nexus.launcher.premium.billing.PremiumBilling
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NexusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Read once at start so every gate can answer synchronously while drawing.
        PremiumManager.init(this)
        // Connecting at launch is what restores Premium on a new device or after a reinstall, and
        // what takes it away again after a refund. The cached flag above is only a first answer.
        PremiumBilling.init(this)
        clearLegacyNightOverrideOnce()
        // Battery events never reach a manifest receiver; see NexusBatteryMonitor.
        com.nexus.launcher.ui.widgets.battery.NexusBatteryMonitor.start(this)
    }

    /**
     * Widget caches are conveniences, not state: album art, the pre-rendered static parts of a
     * retro widget and its double-buffered canvases are all rebuilt on the next frame. Under
     * memory pressure they go back rather than being trimmed out of the process by the system.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < TRIM_MEMORY_RUNNING_LOW) return
        com.nexus.launcher.ui.widgets.music.RetroMusicArtDecoder.clearCache()
        com.nexus.launcher.ui.widgets.music.RetroMusicStaticCache.clear()
        com.nexus.launcher.ui.widgets.music.RetroMusicBitmapPool.clear()
    }

    /**
     * The shortcut box's old Dark mode button pinned this app's night mode instead of changing the
     * phone's, and the system persists that pin. Cleared once here so anyone it stranded follows
     * the phone again without having to find and tap the button. Guarded so the system call —
     * which can reconfigure the app's activities — runs only the first time.
     */
    private fun clearLegacyNightOverrideOnce() {
        val prefs = getSharedPreferences("nexus_migrations", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NIGHT_OVERRIDE_CLEARED, false)) return
        com.nexus.launcher.ui.widgets.shortcutbox.NexusShortcutActions.clearAppNightOverride(this)
        prefs.edit().putBoolean(KEY_NIGHT_OVERRIDE_CLEARED, true).apply()
    }

    private companion object {
        const val KEY_NIGHT_OVERRIDE_CLEARED = "night_override_cleared_v1"
    }
}
