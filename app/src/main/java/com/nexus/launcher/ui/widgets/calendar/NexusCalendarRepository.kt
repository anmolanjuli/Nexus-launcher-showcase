package com.nexus.launcher.ui.widgets.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexus.launcher.ui.widgets.WidgetSize
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarEvent(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val color: Int,
    val isAllDay: Boolean,
    val eventId: Long = 0L
)

object NexusCalendarRepository {
    private const val PREFS_NAME = "nexus_calendar_cache"
    private const val CACHE_DURATION_MS = 15 * 60 * 1000L
    private const val KEY_DATA = "calendar_instances_data"
    private const val KEY_TIME = "calendar_instances_timestamp"
    private const val MAX_ROWS = 200

    suspend fun getEvents(
        context: Context,
        size: WidgetSize,
        viewMode: String
    ): List<CalendarEvent>? = withContext(Dispatchers.IO) {
        val bounds = NexusCalendarQueryWindow.forWidget(size, viewMode)
        return@withContext getEventsForWindow(context, bounds.startMs, bounds.endMs, bounds.kind)
    }

    suspend fun getAgendaEvents(
        context: Context,
        range: String = com.nexus.launcher.ui.widgets.agenda.AgendaRange.DEFAULT
    ): List<CalendarEvent>? = withContext(Dispatchers.IO) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startMs = cal.timeInMillis
        val days = com.nexus.launcher.ui.widgets.agenda.AgendaRange.days(range)
        val endMs = startMs + days * 86_400_000L
        return@withContext getEventsForWindow(context, startMs, endMs, "agenda_$range")
    }

    private fun getEventsForWindow(
        context: Context,
        startMs: Long,
        endMs: Long,
        kind: String
    ): List<CalendarEvent>? {
        val dataKey = "${KEY_DATA}_$kind"
        val timeKey = "${KEY_TIME}_$kind"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString(dataKey, null)
        val cachedTime = prefs.getLong(timeKey, 0)

        if (cachedJson != null &&
            System.currentTimeMillis() - cachedTime < CACHE_DURATION_MS
        ) {
            try {
                val parsed = parseEventsJson(JSONArray(cachedJson))
                NexusCalendarDebug.d(
                    "getEvents CACHE HIT kind=$kind ageMs=${System.currentTimeMillis() - cachedTime} count=${parsed.size}"
                )
                return parsed
            } catch (e: Exception) {
                NexusCalendarDebug.e("Failed to parse cached calendar events", e)
                Log.e("CalendarRepo", "Failed to parse cached calendar events", e)
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            NexusCalendarDebug.d("getEvents no READ_CALENDAR permission → null")
            return null
        }

        NexusCalendarDebug.d(
            "getEvents INSTANCES kind=$kind start=$startMs end=$endMs " +
                "locale=${context.resources.configuration.locales[0]}"
        )
        try {
            val events = queryInstances(context, startMs, endMs)
            prefs.edit()
                .putString(dataKey, eventsToJson(events).toString())
                .putLong(timeKey, System.currentTimeMillis())
                .apply()
            NexusCalendarDebug.d("getEvents QUERY ok kind=$kind count=${events.size} titles=${events.map { it.title }}")
            return events
        } catch (e: Exception) {
            NexusCalendarDebug.e("Failed to query calendar instances — returning null unless cache works", e)
            Log.e("CalendarRepo", "Failed to query calendar", e)
            if (cachedJson != null) {
                try {
                    val parsed = parseEventsJson(JSONArray(cachedJson))
                    NexusCalendarDebug.d("getEvents QUERY fail, cache fallback count=${parsed.size}")
                    return parsed
                } catch (ex: Exception) {
                    NexusCalendarDebug.e("cache fallback parse failed", ex)
                }
            }
        }
        NexusCalendarDebug.d("getEvents returning NULL")
        return null
    }

    private fun queryInstances(context: Context, startMs: Long, endMs: Long): List<CalendarEvent> {
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_ID
        )
        val cursor: Cursor? = CalendarContract.Instances.query(
            context.contentResolver,
            projection,
            startMs,
            endMs
        )
        val events = mutableListOf<CalendarEvent>()
        cursor?.use { c ->
            val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
            val startIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = c.getColumnIndex(CalendarContract.Instances.END)
            val colorIdx = c.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
            val allDayIdx = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val eventIdIdx = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            while (c.moveToNext() && events.size < MAX_ROWS) {
                events.add(
                    CalendarEvent(
                        title = if (titleIdx >= 0) c.getString(titleIdx) ?: context.getString(com.nexus.launcher.R.string.calendar_event_no_title) else context.getString(com.nexus.launcher.R.string.calendar_event_no_title),
                        startMs = if (startIdx >= 0) c.getLong(startIdx) else 0L,
                        endMs = if (endIdx >= 0) c.getLong(endIdx) else 0L,
                        color = if (colorIdx >= 0) c.getInt(colorIdx) else 0xFF99C1F1.toInt(),
                        isAllDay = if (allDayIdx >= 0) c.getInt(allDayIdx) == 1 else false,
                        eventId = if (eventIdIdx >= 0) c.getLong(eventIdIdx) else 0L
                    )
                )
            }
        }
        return events
    }

    private fun eventsToJson(events: List<CalendarEvent>): JSONArray {
        val jsonArray = JSONArray()
        for (e in events) {
            val obj = JSONObject()
            obj.put("title", e.title)
            obj.put("startMs", e.startMs)
            obj.put("endMs", e.endMs)
            obj.put("color", e.color)
            obj.put("isAllDay", e.isAllDay)
            obj.put("eventId", e.eventId)
            jsonArray.put(obj)
        }
        return jsonArray
    }

    private fun parseEventsJson(array: JSONArray): List<CalendarEvent> {
        val list = mutableListOf<CalendarEvent>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                CalendarEvent(
                    title = obj.getString("title"),
                    startMs = obj.getLong("startMs"),
                    endMs = obj.getLong("endMs"),
                    color = obj.getInt("color"),
                    isAllDay = obj.getBoolean("isAllDay"),
                    eventId = obj.optLong("eventId", 0L)
                )
            )
        }
        return list
    }
}
