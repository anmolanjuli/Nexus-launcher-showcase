package com.nexus.launcher.ui.island

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.calendar.NexusCalendarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IslandCalendarSource(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 30_000L)
        }
    }

    fun start() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        job?.cancel()
        IslandTriggerBus.publish(IslandKind.CALENDAR, null)
    }

    private fun refresh() {
        job?.cancel()
        job = scope.launch {
            val events = withContext(Dispatchers.IO) {
                NexusCalendarRepository.getAgendaEvents(context, com.nexus.launcher.ui.widgets.agenda.AgendaRange.TODAY)
            } ?: return@launch
            val now = System.currentTimeMillis()
            val soon = events.firstOrNull { event ->
                !event.isAllDay && event.startMs in (now + 1)..(now + 15 * 60_000L)
            }
            if (soon == null) {
                IslandTriggerBus.publish(IslandKind.CALENDAR, null)
            } else {
                IslandTriggerBus.publish(
                    IslandKind.CALENDAR,
                    IslandPayload(
                        kind = IslandKind.CALENDAR,
                        title = context.getString(R.string.island_event_soon, soon.title),
                        eventId = soon.eventId,
                        identity = "cal:${soon.eventId}:${soon.startMs}",
                    ),
                )
            }
        }
    }
}
