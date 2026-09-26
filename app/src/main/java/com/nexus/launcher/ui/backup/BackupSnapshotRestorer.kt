package com.nexus.launcher.ui.backup

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.prefs.SettingsRepository

/**
 * Restores the v2 `settingsSnapshot` block — the whole [NexusSettingsData] in one piece — plus
 * the `behavior` block.
 *
 * This is what keeps backup from drifting again: the exporter serializes the data class whole, so
 * a field added later is carried without anyone editing the manifest builder.
 */
object BackupSnapshotRestorer {

    private val gson = Gson()

    /**
     * Deserializes [snapshot] over the current defaults.
     *
     * Gson allocates Kotlin data classes through `Unsafe` and never runs their constructor, so a
     * field missing from the JSON would come back `null`/`0` rather than its declared default —
     * a non-null `String` property would then hold null and blow up somewhere far away. Merging
     * the backup's object onto a serialized `NexusSettingsData()` first means every absent field
     * lands on its real default, and does it generically rather than through a per-field list
     * that would rot the same way the old manifest blocks did.
     */
    fun parse(snapshot: JsonObject): NexusSettingsData {
        val merged = gson.toJsonTree(NexusSettingsData()).asJsonObject
        for (key in snapshot.keySet()) {
            val value = snapshot.get(key) ?: continue
            if (value.isJsonNull) continue
            merged.add(key, value)
        }
        return gson.fromJson(merged, NexusSettingsData::class.java)
    }

    /**
     * Writes the snapshot, deliberately preserving this device's own backup folder: restoring a
     * backup must never repoint where future backups are written, least of all to a folder URI
     * this install has no permission for.
     */
    suspend fun restore(snapshot: JsonObject, settingsRepo: SettingsRepository) {
        val incoming = parse(snapshot)
        val localFolder = settingsRepo.snapshot().backupFolderUri
        settingsRepo.restoreSnapshot(incoming.copy(backupFolderUri = localFolder))
    }

    /**
     * Settings kept in their own SharedPreferences files — see
     * `BackupManifestBuilder.standaloneSettingsObject`.
     */
    fun restoreStandaloneSettings(obj: JsonObject?, context: android.content.Context) {
        if (obj == null) return
        obj.getAsJsonObject("search")?.let { s ->
            val settings = com.nexus.launcher.search.NexusSearchSettings(context)
            s.get("contacts")?.takeIf { !it.isJsonNull }?.let { settings.searchContactsEnabled = it.asBoolean }
            s.get("web")?.takeIf { !it.isJsonNull }?.let { settings.searchWebEnabled = it.asBoolean }
            s.get("calculator")?.takeIf { !it.isJsonNull }?.let { settings.searchCalculatorEnabled = it.asBoolean }
            s.get("conversion")?.takeIf { !it.isJsonNull }?.let { settings.searchConversionEnabled = it.asBoolean }
            s.get("maps")?.takeIf { !it.isJsonNull }?.let { settings.searchMapsEnabled = it.asBoolean }
        }
        obj.getAsJsonObject("weather")?.get("unitOverride")?.takeIf { !it.isJsonNull }?.let {
            // Goes through the repository so its cache-invalidation stays in one place.
            runCatching {
                com.nexus.launcher.ui.widgets.weather.NexusWeatherRepository
                    .setTemperatureUnit(context, it.asString)
            }
        }
    }

    /** Launcher behaviour kept in [PreferenceManager] rather than the settings DataStore. */
    fun restoreBehavior(obj: JsonObject?, prefs: PreferenceManager) {
        if (obj == null) return
        // Categories the user added or deleted, before the assignments that point at them.
        if (obj.has("customCategories") || obj.has("hiddenBuiltinCategories")) runCatching {
            prefs.setDrawerCategories(
                obj.get("customCategories")?.takeIf { !it.isJsonNull }?.asString,
                obj.getAsJsonArray("hiddenBuiltinCategories")?.map { it.asString }?.toSet().orEmpty(),
                obj.get("categoryIcons")?.takeIf { !it.isJsonNull }?.asString,
                obj.get("categoryNames")?.takeIf { !it.isJsonNull }?.asString,
                obj.getAsJsonArray("addedSuggestedCategories")?.map { it.asString }?.toSet().orEmpty(),
            )
        }
        obj.getAsJsonObject("folderCategories")?.let { categories ->
            for (key in categories.keySet()) {
                val folderId = key.toLongOrNull() ?: continue
                val category = categories.get(key)?.takeIf { !it.isJsonNull }?.asInt
                runCatching { prefs.setFolderCategory(folderId, category) }
            }
        }
        obj.getAsJsonObject("appCategoryOverrides")?.let { overrides ->
            for (pkg in overrides.keySet()) {
                val category = overrides.get(pkg)?.takeIf { v -> !v.isJsonNull }?.asInt
                runCatching { prefs.setAppCategoryOverride(pkg, category) }
            }
        }
        obj.get("animSpeedMultiplier")?.takeIf { !it.isJsonNull }?.let {
            runCatching { prefs.setAnimSpeedMultiplier(it.asFloat) }
        }
        obj.get("reduceMotion")?.takeIf { !it.isJsonNull }?.let {
            runCatching { prefs.setReduceMotion(it.asBoolean) }
        }
    }
}
