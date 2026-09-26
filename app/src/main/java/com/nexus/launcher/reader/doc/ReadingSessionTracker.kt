package com.nexus.launcher.reader.doc

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * How long a document has been read, so the library can say how much of it is left.
 *
 * Time is counted only while the reader is in the foreground: [start] on resume, [stop] on pause.
 * Two guards keep the number honest — a session shorter than [MIN_SESSION_MS] is someone opening
 * the wrong book, and one longer than [MAX_SESSION_MS] is a reader left open on a table, neither
 * of which is reading. [SystemClock.elapsedRealtime] is used rather than wall-clock time so a
 * clock change or a time-zone flight cannot invent an afternoon of reading.
 */
class ReadingSessionTracker(
    private val repository: DocumentLibraryRepository,
    private val uri: String,
) {
    private var startedAt = 0L

    fun start() {
        startedAt = SystemClock.elapsedRealtime()
    }

    fun stop(scope: CoroutineScope) {
        val began = startedAt
        startedAt = 0L
        if (began <= 0L || uri.isBlank()) return
        val elapsed = SystemClock.elapsedRealtime() - began
        if (elapsed < MIN_SESSION_MS) return
        val counted = elapsed.coerceAtMost(MAX_SESSION_MS)
        scope.launch {
            try {
                repository.addReadingTime(uri, counted)
            } catch (_: Exception) {
                // A lost session costs a slightly stale estimate, nothing more.
            }
        }
    }

    private companion object {
        const val MIN_SESSION_MS = 10_000L
        const val MAX_SESSION_MS = 90L * 60 * 1000
    }
}
