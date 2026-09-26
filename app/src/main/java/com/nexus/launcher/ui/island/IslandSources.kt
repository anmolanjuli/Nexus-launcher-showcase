package com.nexus.launcher.ui.island

import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.ui.MainActivity

/**
 * Everything that can put something on the island, started and stopped together.
 *
 * Each source watches one thing — the call state, the media session, the clock's notifications,
 * the battery — and publishes to [IslandTriggerBus]; the island itself knows nothing about any
 * of them. They live and die with the launcher being on screen, so this is one switch rather
 * than eight fields on the controller.
 */
internal class IslandSources(activity: MainActivity) {

    private val scope = activity.lifecycleScope

    private val music = IslandMusicSource(activity)
    private val battery = IslandBatterySource(activity)
    private val dnd = IslandDndSource(activity)
    private val clock = IslandClockSource(activity, scope)
    private val bluetooth = IslandBluetoothSource(activity)
    private val calendar = IslandCalendarSource(activity, scope)
    private val call = IslandCallSource(activity, scope)
    private val liveActivity = IslandActivitySource(activity, scope)

    fun start() {
        music.start()
        battery.start()
        dnd.start()
        clock.start()
        bluetooth.start()
        calendar.start()
        call.start()
        liveActivity.start()
    }

    fun stop() {
        music.stop()
        battery.stop()
        dnd.stop()
        clock.stop()
        bluetooth.stop()
        calendar.stop()
        call.stop()
        liveActivity.stop()
        IslandTriggerBus.clear()
    }
}
