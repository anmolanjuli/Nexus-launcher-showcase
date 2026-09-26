package com.nexus.launcher.ui.settings

import android.view.View
import android.view.ViewGroup

/**
 * A settings row that belongs to a switch which is currently off: it dims and stops taking
 * touches, rather than disappearing. The options stay visible, so what a module offers can be
 * read before turning it on.
 */
object StatusRowDimming {

    private const val DIMMED_ALPHA = 0.4f

    fun apply(row: View, enabled: Boolean) {
        row.alpha = if (enabled) 1f else DIMMED_ALPHA
        setInteractive(row, enabled)
    }

    private fun setInteractive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        // A row's controls are its children; disabling only the row would leave the switch or
        // the slider inside it still taking touches.
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) setInteractive(view.getChildAt(i), enabled)
        }
    }
}
