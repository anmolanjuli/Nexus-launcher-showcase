package com.nexus.launcher.ui.widgets.calendar

import android.util.Log

/** Temporary diagnostics for Nepali-locale empty-state. Filter logcat: CalDebug */
internal object NexusCalendarDebug {
    const val TAG = "CalDebug"

    fun d(msg: String) = Log.d(TAG, msg)

    fun e(msg: String, t: Throwable? = null) {
        if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
    }
}
