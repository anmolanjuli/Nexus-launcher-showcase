package com.nexus.launcher.ui.dock

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import com.nexus.launcher.ui.MainActivity

object DockLayoutFinder {

    /** Locates the dock sibling without requiring a layout id or MainActivity wiring. */
    fun findFrom(anchor: View): DockLayout? {
        var parent = anchor.parent
        while (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is DockLayout) return child
            }
            parent = parent.parent
        }
        return null
    }

    fun getActivitySafe(context: Context): MainActivity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is MainActivity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * Cross-window lookup. A dialog's DecorView parent is a ViewRootImpl (not a ViewGroup),
     * so [findFrom] can never reach the dock from a dialog view — search the Activity's
     * own decor tree instead.
     */
    fun findInActivity(context: Context): DockLayout? {
        val decor = getActivitySafe(context)?.window?.decorView ?: return null
        return findInTree(decor)
    }

    private fun findInTree(view: View): DockLayout? {
        if (view is DockLayout) return view
        if (view !is ViewGroup) return null
        for (i in 0 until view.childCount) {
            findInTree(view.getChildAt(i))?.let { return it }
        }
        return null
    }
}
