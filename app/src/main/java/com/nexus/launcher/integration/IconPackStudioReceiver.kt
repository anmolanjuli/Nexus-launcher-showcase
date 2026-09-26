package com.nexus.launcher.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.nexus.launcher.data.prefs.NexusDefaults
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Historical Icon Pack Studio apply/remove broadcasts. Undocumented in current IPS releases,
 * kept as cheap insurance so an exporter that still sends them can switch the global pack.
 * Persistence goes through [com.nexus.launcher.data.prefs.SettingsRepository.updateIconPack];
 * MainViewModel's settings collector loads the pack and resyncs apps.
 */
class IconPackStudioReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != ACTION_APPLY && action != ACTION_REMOVE) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = EntryPointAccessors.fromApplication(
                    appContext,
                    IconPackStudioEntryPoint::class.java
                ).settingsRepository()

                when (action) {
                    ACTION_APPLY -> {
                        val pack = resolvePackPackage(intent)
                        if (pack.isNullOrBlank()) {
                            Log.w(TAG, "APPLY_ICON_PACK missing pack extra; extras=${intent.extras}")
                            return@launch
                        }
                        repo.updateIconPack(pack)
                        haptic(appContext, apply = true)
                    }
                    ACTION_REMOVE -> {
                        repo.updateIconPack(NexusDefaults.ICON_PACK)
                        haptic(appContext, apply = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling $action", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun resolvePackPackage(intent: Intent): String? {
        for (key in PACK_EXTRA_KEYS) {
            val value = intent.getStringExtra(key)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun haptic(context: Context, apply: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effectId = if (apply) {
                    VibrationEffect.EFFECT_HEAVY_CLICK
                } else {
                    VibrationEffect.EFFECT_CLICK
                }
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (apply) 40L else 20L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic failed", e)
        }
    }

    companion object {
        private const val TAG = "IconPackStudioRx"
        const val ACTION_APPLY = "com.grabster.iconpackstudio.APPLY_ICON_PACK"
        const val ACTION_REMOVE = "com.grabster.iconpackstudio.REMOVE_ICON_PACK"
        private val PACK_EXTRA_KEYS = listOf(
            "icon_pack_package",
            "package_name",
            "iconpack"
        )
    }
}
