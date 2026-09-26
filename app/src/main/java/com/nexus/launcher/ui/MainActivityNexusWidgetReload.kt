package com.nexus.launcher.ui

import android.content.Context
import com.nexus.launcher.ui.settings.NotificationPermissionStatus
import com.nexus.launcher.ui.widgets.NexusWidgetHostPoke
import com.nexus.launcher.ui.widgets.music.NexusMusicManager

private const val PREFS = "nexus_prefs"
private const val KEY_MUSIC_ACCESS = "music_notification_access_last_granted"

internal fun MainActivity.reloadNexusWidgetsAfterPermission() {
    val overlay = widgetHostLifecycle.widgetOverlayLayout
    overlay.post { NexusWidgetHostPoke.poke(overlay) }
    overlay.postDelayed({ NexusWidgetHostPoke.poke(overlay) }, 2500)
}

internal fun MainActivity.refreshMusicWidgetsIfAccessGained() {
    val granted = NotificationPermissionStatus.hasNotificationAccess(this)
    val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val wasGranted = prefs.getBoolean(KEY_MUSIC_ACCESS, false)
    if (granted != wasGranted) {
        prefs.edit().putBoolean(KEY_MUSIC_ACCESS, granted).apply()
    }
    if (granted) {
        NexusMusicManager.startListening(this)
        NexusMusicManager.notifyWidgets()
        if (!wasGranted) {
            reloadNexusWidgetsAfterPermission()
        }
    }
}
