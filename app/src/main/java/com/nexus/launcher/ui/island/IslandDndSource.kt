package com.nexus.launcher.ui.island

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nexus.launcher.R

class IslandDndSource(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            emit()
            handler.postDelayed(this, 2_000L)
        }
    }

    fun start() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        IslandTriggerBus.publish(IslandKind.DND, null)
    }

    private fun emit() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val on = nm != null && nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        if (on) {
            IslandTriggerBus.publish(
                IslandKind.DND,
                IslandPayload(
                    kind = IslandKind.DND,
                    title = context.getString(R.string.island_dnd),
                    identity = "dnd",
                ),
            )
        } else {
            IslandTriggerBus.publish(IslandKind.DND, null)
        }
    }
}
