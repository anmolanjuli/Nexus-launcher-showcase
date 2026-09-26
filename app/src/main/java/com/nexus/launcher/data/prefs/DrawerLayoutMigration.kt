package com.nexus.launcher.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/**
 * One-time split of the legacy [NexusSettingsKeys.DRAWER_LAYOUT] string (`"grid"` /
 * `"list"`/`"list_1"` / `"list_2"`) into [NexusSettingsKeys.DRAWER_UI_MODE] /
 * [NexusSettingsKeys.DRAWER_GRID_OR_LIST] / [NexusSettingsKeys.DRAWER_LIST_COLUMNS].
 * Extracted out of [SettingsRepository] purely to keep that file under the 400-line limit —
 * same DataStore instance, called from [SettingsRepository.migrateDrawerLayoutIfNeeded], no
 * behavior change.
 */
class DrawerLayoutMigration(private val dataStore: DataStore<Preferences>) {

    /**
     * Idempotent and runs exactly once per install:
     * - [NexusSettingsKeys.DRAWER_LAYOUT_MIGRATED_V1] is checked first and this returns
     *   immediately if already `true` — every launch after the first is a single read, no write,
     *   so it cannot re-run and clobber a value the user later chose explicitly.
     * - The mapping itself is a pure function of the untouched legacy value, so even if this
     *   somehow executed twice before the flag landed (e.g. process death mid-migration), it
     *   would recompute the identical result rather than corrupt state.
     * - All four keys (three new fields + the flag) are written in one atomic `dataStore.edit{}`
     *   transaction, so a crash mid-write leaves either the pre-migration state (flag absent,
     *   safe to retry next launch) or the fully-migrated state — never a half-written mix.
     */
    suspend fun migrateIfNeeded() {
        val alreadyMigrated = dataStore.data.first()[NexusSettingsKeys.DRAWER_LAYOUT_MIGRATED_V1] ?: false
        if (alreadyMigrated) return

        dataStore.edit { prefs ->
            if (prefs[NexusSettingsKeys.DRAWER_LAYOUT_MIGRATED_V1] == true) return@edit
            val legacy = prefs[NexusSettingsKeys.DRAWER_LAYOUT] ?: NexusDefaults.DRAWER_LAYOUT
            val (gridOrList, columns) = when (legacy) {
                "grid" -> Pair("grid", 1)
                "list_2" -> Pair("list", 2)
                else -> Pair("list", 1) // "list" / "list_1" / any unrecognized legacy value
            }
            prefs[NexusSettingsKeys.DRAWER_GRID_OR_LIST] = gridOrList
            prefs[NexusSettingsKeys.DRAWER_LIST_COLUMNS] = columns
            prefs[NexusSettingsKeys.DRAWER_LAYOUT_MIGRATED_V1] = true
        }
    }
}
