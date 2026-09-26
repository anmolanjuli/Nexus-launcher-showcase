package com.nexus.launcher.ui.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import com.nexus.launcher.R

class IslandBatterySource(private val context: Context) {
    private var lastLowIdentity: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            emit(intent)
        }
    }

    fun start() {
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        emit(sticky)
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        handler.removeCallbacksAndMessages(null)
        IslandTriggerBus.publish(IslandKind.CHARGING, null)
        IslandTriggerBus.publish(IslandKind.BATTERY_LOW, null)
    }

    private fun emit(intent: Intent?) {
        if (intent == null) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        if (charging) {
            IslandTriggerBus.publish(
                IslandKind.CHARGING,
                IslandPayload(
                    kind = IslandKind.CHARGING,
                    title = context.getString(R.string.island_charging, percent),
                    percent = percent,
                    identity = "charging:$percent",
                ),
            )
            IslandTriggerBus.publish(IslandKind.BATTERY_LOW, null)
            return
        }
        IslandTriggerBus.publish(IslandKind.CHARGING, null)
        if (percent in 1..15) {
            val id = "low:$percent"
            if (id != lastLowIdentity) {
                lastLowIdentity = id
                IslandTriggerBus.publish(
                    IslandKind.BATTERY_LOW,
                    IslandPayload(
                        kind = IslandKind.BATTERY_LOW,
                        title = context.getString(R.string.island_battery_low, percent),
                        percent = percent,
                        identity = id,
                    ),
                )
                IslandController.onBatteryLowPulse()
                handler.postDelayed({
                    IslandTriggerBus.publish(IslandKind.BATTERY_LOW, null)
                }, 4_000L)
            }
        } else {
            lastLowIdentity = null
            IslandTriggerBus.publish(IslandKind.BATTERY_LOW, null)
        }
    }
}
