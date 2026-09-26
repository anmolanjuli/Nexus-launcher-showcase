package com.nexus.launcher.ui.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

/**
 * The activity behind a settings row's context.
 *
 * A row built inside a fragment is given Hilt's `FragmentContextWrapper`, not the activity, so
 * anything that needs a lifecycle owner — the live status-row preview, which starts and stops
 * its own battery and signal readers — has to unwrap it first.
 */
internal object SettingsActivityOf {

    fun find(context: Context): ComponentActivity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is ComponentActivity) return current
            current = current.baseContext
        }
        return null
    }
}
