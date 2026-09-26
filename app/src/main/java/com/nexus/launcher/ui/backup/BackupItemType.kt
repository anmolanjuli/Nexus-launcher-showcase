package com.nexus.launcher.ui.backup

import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.ui.widgets.liveapp.LiveAppPlacement

/** Reads a backed-up item's type name back into a [HomeItemTypes] value. */
internal object BackupItemType {

    /** Null for a name this build does not know, so the item is skipped rather than guessed at. */
    fun resolve(typeName: String, packageName: String): Int? {
        val named = HomeItemTypes.fromBackupName(typeName) ?: return null
        // Backups made before 2026-09-24 wrote Live App boxes as "APP"; their placeholder
        // package still says what they are, and their config was saved in full.
        return if (named == HomeItemTypes.APP && packageName == LiveAppPlacement.PACKAGE) {
            HomeItemTypes.LIVE_APP_BOX
        } else {
            named
        }
    }
}
