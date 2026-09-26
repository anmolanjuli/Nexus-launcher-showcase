package com.nexus.launcher.ui.widgets.shortcutbox

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.nexus.launcher.R
import com.nexus.launcher.ui.MainActivity

/** Execution implementations for custom Nexus shortcuts. */
object NexusShortcutActions {

    private var isFlashlightActive = false

    fun openAppDrawer(context: Context) {
        val act = context as? MainActivity
        if (act != null) {
            act.viewModel.handleSwipeUp()
        } else {
            // Fallback only: the shortcut box is hosted inside MainActivity, so the branch above is
            // the one that runs. Nothing reads OPEN_DRAWER, so this path would bring the launcher
            // forward without opening the drawer. Routed so it at least never makes a second
            // instance.
            context.startActivity(
                com.nexus.launcher.ui.HomeRoute.to(context, "com.nexus.launcher.action.OPEN_DRAWER"),
            )
        }
    }

    fun toggleBluetooth(context: Context) {
        val adapter = try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (_: Exception) {
            null
        }

        if (adapter == null) {
            openBluetoothSettings(context)
            return
        }

        try {
            if (adapter.isEnabled) {
                @Suppress("DEPRECATION")
                val success = adapter.disable()
                if (success) {
                    Toast.makeText(context, context.getString(R.string.nexus_shortcut_bluetooth_disabled), Toast.LENGTH_SHORT).show()
                } else {
                    openBluetoothSettings(context)
                }
            } else {
                @Suppress("DEPRECATION")
                val success = adapter.enable()
                if (success) {
                    Toast.makeText(context, context.getString(R.string.nexus_shortcut_bluetooth_enabled), Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val requestEnable = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(requestEnable)
                    } catch (_: Exception) {
                        openBluetoothSettings(context)
                    }
                }
            }
        } catch (_: Exception) {
            openBluetoothSettings(context)
        }
    }

    private fun openBluetoothSettings(context: Context) {
        val intents = listOf(
            Intent("android.settings.BLUETOOTH_SETTINGS"),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    fun toggleFlashlight(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            Toast.makeText(context, context.getString(R.string.nexus_shortcut_flashlight_error), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId == null) {
                Toast.makeText(context, context.getString(R.string.nexus_shortcut_flashlight_error), Toast.LENGTH_SHORT).show()
                return
            }

            isFlashlightActive = !isFlashlightActive
            cameraManager.setTorchMode(cameraId, isFlashlightActive)
            val msgRes = if (isFlashlightActive) R.string.nexus_shortcut_flashlight_on else R.string.nexus_shortcut_flashlight_off
            Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.nexus_shortcut_flashlight_error), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens the system Dark theme setting. It does not — cannot — flip the theme itself.
     *
     * Android gives third-party apps no way to change the system-wide dark theme:
     * `UiModeManager.setNightMode` requires `MODIFY_DAY_NIGHT_MODE`, a privileged permission no
     * Play Store app can hold. One tap to the real switch is the most any launcher can offer.
     *
     * This used to call `UiModeManager.setApplicationNightMode` (and AppCompat's
     * `setDefaultNightMode` below Android 12). Both change *this app's* night mode only, so the
     * phone's theme never moved — and the first is persisted by the system, so a tap could leave
     * Nexus pinned to light or dark, detached from the phone's setting. Every tap now clears any
     * such leftover first; [clearAppNightOverride] also runs once at start for installs that were
     * left pinned and never tap again.
     */
    fun toggleDarkMode(context: Context) {
        clearAppNightOverride(context)
        val targets = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("android.settings.DARK_THEME_SETTINGS")
            add(Settings.ACTION_DISPLAY_SETTINGS)
        }
        for (action in targets) {
            val opened = runCatching {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return
        }
    }

    /**
     * Removes a per-app night mode override, so Nexus follows the phone's theme again.
     *
     * `MODE_NIGHT_AUTO` is the "no override" value for `setApplicationNightMode`: the system maps
     * it to an undefined night mode for this package, which means inherit the system's.
     */
    fun clearAppNightOverride(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            (context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager)
                ?.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_AUTO)
        }
    }

    fun toggleDoNotDisturb(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (nm == null) return
        try {
            if (nm.isNotificationPolicyAccessGranted) {
                val currentFilter = nm.currentInterruptionFilter
                val isDndOn = currentFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                if (isDndOn) {
                    nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                    Toast.makeText(context, context.getString(R.string.nexus_shortcut_dnd_off), Toast.LENGTH_SHORT).show()
                } else {
                    nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    Toast.makeText(context, context.getString(R.string.nexus_shortcut_dnd_on), Toast.LENGTH_SHORT).show()
                }
            } else {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (_: Exception) {}
        }
    }

    fun toggleWifi(context: Context) {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } catch (_: Exception) {
                openWifiSettings(context)
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                if (wm != null) {
                    val enabled = !wm.isWifiEnabled
                    wm.isWifiEnabled = enabled
                    val msg = if (enabled) R.string.nexus_shortcut_wifi_enabled else R.string.nexus_shortcut_wifi_disabled
                    Toast.makeText(context, context.getString(msg), Toast.LENGTH_SHORT).show()
                } else {
                    openWifiSettings(context)
                }
            } catch (_: Exception) {
                openWifiSettings(context)
            }
        }
    }

    private fun openWifiSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openSystemSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openVolumePanel(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        try {
            audioManager?.adjustVolume(android.media.AudioManager.ADJUST_SAME, android.media.AudioManager.FLAG_SHOW_UI)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun isShortcutActive(context: Context, shortcutId: String?): Boolean {
        if (shortcutId == null) return false
        return when (shortcutId) {
            NexusBuiltinShortcuts.ID_BLUETOOTH -> {
                try {
                    BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
                } catch (_: Exception) { false }
            }
            NexusBuiltinShortcuts.ID_FLASHLIGHT -> isFlashlightActive
            NexusBuiltinShortcuts.ID_WIFI -> {
                val isConnected = try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
                    caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                } catch (_: Exception) { false }

                val isEnabled = try {
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                    @Suppress("DEPRECATION")
                    wm?.isWifiEnabled == true
                } catch (_: Exception) { false }

                isConnected || isEnabled
            }
            NexusBuiltinShortcuts.ID_DARK_MODE -> {
                val mode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            NexusBuiltinShortcuts.ID_DO_NOT_DISTURB -> {
                try {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    nm != null && nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                } catch (_: Exception) { false }
            }
            else -> false
        }
    }

    fun getActiveAccentColor(context: Context, shortcutId: String?): Int {
        return when (shortcutId) {
            NexusBuiltinShortcuts.ID_BLUETOOTH -> android.graphics.Color.parseColor("#3B82F6")
            NexusBuiltinShortcuts.ID_FLASHLIGHT -> android.graphics.Color.parseColor("#F59E0B")
            NexusBuiltinShortcuts.ID_WIFI -> android.graphics.Color.parseColor("#10B981")
            NexusBuiltinShortcuts.ID_DARK_MODE -> android.graphics.Color.parseColor("#8B5CF6")
            NexusBuiltinShortcuts.ID_DO_NOT_DISTURB -> android.graphics.Color.parseColor("#EF4444")
            NexusBuiltinShortcuts.ID_VOLUME -> android.graphics.Color.parseColor("#14B8A6")
            else -> {
                val tokens = try {
                    com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
                } catch (_: Exception) {
                    com.nexus.launcher.theme.NexusColorTokens.Dark
                }
                tokens.accent
            }
        }
    }
}
