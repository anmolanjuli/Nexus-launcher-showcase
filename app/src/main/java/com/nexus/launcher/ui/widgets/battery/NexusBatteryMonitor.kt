package com.nexus.launcher.ui.widgets.battery

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

/**
 * Keeps the Battery widget current. Registered once, in code, for the life of the process.
 *
 * ## Why not the manifest
 *
 * The widget's receiver listed `BATTERY_CHANGED`, `SCREEN_ON`/`OFF` and `POWER_CONNECTED`/
 * `DISCONNECTED`, and Android delivers none of them to a manifest-declared receiver: the first
 * three only ever reach receivers registered at runtime, and the power events stopped reaching
 * manifests in Android 8's implicit-broadcast limits. Left like that, the widget refreshed only on
 * its 30-minute update tick, so the percentage and charging state could be half an hour stale.
 *
 * Nexus is the home screen, so its process is effectively always alive — a runtime receiver here
 * is exactly as long-lived as the widget it serves.
 *
 * ## Only real changes redraw
 *
 * `BATTERY_CHANGED` also fires for temperature and voltage movement — every few seconds while
 * charging. A battery widget that redrew on each would be spending battery to report on it, so a
 * refresh goes out only when level, status or plug state differ from the last one sent.
 */
object NexusBatteryMonitor {

    @Volatile
    private var started = false

    /** The last level|status|plugged state a refresh was sent for. */
    private var lastState: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val state = "${level * 100 / scale}|" +
                    "${intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)}|" +
                    "${intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)}"
                if (state == lastState) return
                lastState = state
            }
            refresh(context)
        }
    }

    fun start(context: Context) {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // NOT_EXPORTED still receives these: they are protected broadcasts sent by the system.
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Explicit update to the provider — the same route every other Nexus widget refresh takes. */
    private fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NexusBatteryWidgetProvider::class.java))
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(context, NexusBatteryWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            },
        )
    }
}
