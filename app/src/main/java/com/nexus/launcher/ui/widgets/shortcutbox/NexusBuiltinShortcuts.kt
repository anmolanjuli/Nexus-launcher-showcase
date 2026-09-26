package com.nexus.launcher.ui.widgets.shortcutbox

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import com.nexus.launcher.R

/** Registry and dispatcher for custom Nexus built-in shortcuts. */
object NexusBuiltinShortcuts {

    const val ID_APP_DRAWER = "nexus_shortcut_app_drawer"
    const val ID_BLUETOOTH = "nexus_shortcut_bluetooth"
    const val ID_FLASHLIGHT = "nexus_shortcut_flashlight"
    const val ID_DARK_MODE = "nexus_shortcut_dark_mode"
    const val ID_DO_NOT_DISTURB = "nexus_shortcut_do_not_disturb"
    const val ID_WIFI = "nexus_shortcut_wifi"
    const val ID_SETTINGS = "nexus_shortcut_settings"
    const val ID_VOLUME = "nexus_shortcut_volume"
    const val NEXUS_PKG = "com.nexus.launcher"

    private val ITEMS = listOf(
        NexusShortcutItem(
            id = ID_APP_DRAWER,
            titleRes = R.string.nexus_shortcut_app_drawer,
            iconRes = R.drawable.ic_grid
        ),
        NexusShortcutItem(
            id = ID_BLUETOOTH,
            titleRes = R.string.nexus_shortcut_bluetooth,
            iconRes = R.drawable.sc_bluetooth
        ),
        NexusShortcutItem(
            id = ID_FLASHLIGHT,
            titleRes = R.string.nexus_shortcut_flashlight,
            iconRes = R.drawable.sc_ightbulb
        ),
        NexusShortcutItem(
            id = ID_DARK_MODE,
            titleRes = R.string.nexus_shortcut_dark_mode,
            iconRes = R.drawable.sc_dark_mode
        ),
        NexusShortcutItem(
            id = ID_DO_NOT_DISTURB,
            titleRes = R.string.nexus_shortcut_do_not_disturb,
            iconRes = R.drawable.sc_do_not_disturb
        ),
        NexusShortcutItem(
            id = ID_WIFI,
            titleRes = R.string.nexus_shortcut_wifi,
            iconRes = R.drawable.sc_android_wifi
        ),
        NexusShortcutItem(
            id = ID_VOLUME,
            titleRes = R.string.nexus_shortcut_volume,
            iconRes = R.drawable.sc_volume
        ),
        NexusShortcutItem(
            id = ID_SETTINGS,
            titleRes = R.string.nexus_shortcut_settings,
            iconRes = R.drawable.ic_settings
        )
    )

    fun isBuiltin(shortcutId: String?): Boolean {
        if (shortcutId == null) return false
        return ITEMS.any { it.id == shortcutId }
    }

    fun get(shortcutId: String?): NexusShortcutItem? {
        if (shortcutId == null) return null
        return ITEMS.firstOrNull { it.id == shortcutId }
    }

    fun getAllItems(): List<NexusShortcutItem> = ITEMS

    fun getDrawable(context: Context, shortcutId: String?): Drawable? {
        val item = get(shortcutId) ?: return null
        return ContextCompat.getDrawable(context, item.iconRes)
    }

    fun buildShortcutInfo(context: Context, item: NexusShortcutItem): ShortcutInfo {
        val label = context.getString(item.titleRes)
        val intent = Intent("nexus.shortcut.START").apply {
            `package` = context.packageName
            putExtra("packageName", context.packageName)
            putExtra("shortcutId", item.id)
        }
        return ShortcutInfo.Builder(context, item.id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Icon.createWithResource(context, item.iconRes))
            .setIntent(intent)
            .build()
    }

    fun getAllShortcuts(context: Context): List<ShortcutInfo> {
        return ITEMS.map { buildShortcutInfo(context, it) }
    }

    fun execute(context: Context, shortcutId: String?): Boolean {
        when (shortcutId) {
            ID_APP_DRAWER -> {
                NexusShortcutActions.openAppDrawer(context)
                return true
            }
            ID_BLUETOOTH -> {
                NexusShortcutActions.toggleBluetooth(context)
                return true
            }
            ID_FLASHLIGHT -> {
                NexusShortcutActions.toggleFlashlight(context)
                return true
            }
            ID_DARK_MODE -> {
                NexusShortcutActions.toggleDarkMode(context)
                return true
            }
            ID_DO_NOT_DISTURB -> {
                NexusShortcutActions.toggleDoNotDisturb(context)
                return true
            }
            ID_WIFI -> {
                NexusShortcutActions.toggleWifi(context)
                return true
            }
            ID_VOLUME -> {
                NexusShortcutActions.openVolumePanel(context)
                return true
            }
            ID_SETTINGS -> {
                NexusShortcutActions.openSystemSettings(context)
                return true
            }
        }
        return false
    }
}
