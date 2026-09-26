package com.nexus.launcher.data

/** Canonical [HomeScreenItem.itemType] values — prefer these over raw ints in new code. */
object HomeItemTypes {
    const val APP = 0
    const val FOLDER = 1
    const val SHORTCUT = 2
    const val WIDGET = 3
    const val MOSAIC = 4
    const val SHORTCUT_BOX = 5
    const val APP_BOX = 6
    const val LIVE_APP_BOX = 7

    /**
     * The name each type is written under in a backup, read by both export and import. Until
     * 2026-09-24 each side had its own table, neither knew [LIVE_APP_BOX], and both turned an
     * unknown type into [APP], so a Live App box was backed up as an app and restored as one
     * (showing the stock Android icon). A type added here without a name now fails loudly.
     */
    private val BACKUP_NAMES = mapOf(
        APP to "APP",
        FOLDER to "FOLDER",
        SHORTCUT to "SHORTCUT",
        WIDGET to "WIDGET",
        MOSAIC to "MOSAIC",
        SHORTCUT_BOX to "SHORTCUT_BOX",
        APP_BOX to "APP_BOX",
        LIVE_APP_BOX to "LIVE_APP_BOX",
    )

    fun backupName(itemType: Int): String =
        BACKUP_NAMES[itemType] ?: error("Item type $itemType has no backup name; add it to HomeItemTypes")

    /** Null for a name this build does not know, e.g. from a backup made by a newer version. */
    fun fromBackupName(name: String): Int? = BACKUP_NAMES.entries.firstOrNull { it.value == name }?.key
}
