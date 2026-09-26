package com.nexus.launcher.data.prefs

import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/**
 * One-shot removal of the DataStore / SharedPreferences keys left behind by the deleted
 * "Minimal UI" feature (Appearance › Launcher UI). Runs once per install, guarded by
 * [NexusSettingsKeys.LEGACY_MINIMAL_CLEANUP_DONE]; removing an already-absent key is a no-op.
 */
private val LEGACY_MINIMAL_DATASTORE_KEYS = listOf(
    androidx.datastore.preferences.core.stringPreferencesKey("drawer_ui_mode"),
    androidx.datastore.preferences.core.booleanPreferencesKey("drawer_minimal_normal_letter"),
    androidx.datastore.preferences.core.stringPreferencesKey("launcher_ui_mode"),
    androidx.datastore.preferences.core.booleanPreferencesKey("launcher_ui_mode_migrated_v1"),
    androidx.datastore.preferences.core.intPreferencesKey("minimal_home_columns"),
    androidx.datastore.preferences.core.booleanPreferencesKey("minimal_normal_letter"),
    androidx.datastore.preferences.core.stringPreferencesKey("minimal_text_align"),
    androidx.datastore.preferences.core.stringPreferencesKey("minimal_row_height"),
    androidx.datastore.preferences.core.booleanPreferencesKey("minimal_dock_enabled"),
    androidx.datastore.preferences.core.intPreferencesKey("minimal_dock_max_items"),
    androidx.datastore.preferences.core.floatPreferencesKey("minimal_widget_band_max"),
    androidx.datastore.preferences.core.booleanPreferencesKey("minimal_show_badges"),
    androidx.datastore.preferences.core.intPreferencesKey("minimal_home_page_count"),
    androidx.datastore.preferences.core.booleanPreferencesKey("minimal_first_entry_done")
)

suspend fun SettingsRepository.cleanupRemovedMinimalUiKeys(nexusPrefs: SharedPreferences) {
    val alreadyDone = dataStore.data.first()[NexusSettingsKeys.LEGACY_MINIMAL_CLEANUP_DONE] ?: false
    if (alreadyDone) return

    dataStore.edit { prefs ->
        if (prefs[NexusSettingsKeys.LEGACY_MINIMAL_CLEANUP_DONE] == true) return@edit
        LEGACY_MINIMAL_DATASTORE_KEYS.forEach { prefs.remove(it) }
        prefs[NexusSettingsKeys.LEGACY_MINIMAL_CLEANUP_DONE] = true
    }
    nexusPrefs.edit()
        .remove("minimal_home_page_count")
        .remove("minimal_dock_seeded")
        .apply()
}
