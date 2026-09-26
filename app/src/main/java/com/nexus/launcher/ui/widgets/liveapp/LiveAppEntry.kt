package com.nexus.launcher.ui.widgets.liveapp

import android.graphics.drawable.Drawable

data class LiveAppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val lastTimeUsed: Long,
    val isForegroundService: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiveAppEntry) return false
        return packageName == other.packageName &&
            label == other.label &&
            lastTimeUsed == other.lastTimeUsed &&
            isForegroundService == other.isForegroundService
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + lastTimeUsed.hashCode()
        result = 31 * result + isForegroundService.hashCode()
        return result
    }
}
