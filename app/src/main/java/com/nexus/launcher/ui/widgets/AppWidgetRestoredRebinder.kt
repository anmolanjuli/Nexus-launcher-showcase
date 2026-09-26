package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.widgets.mosaic.MosaicConfig

/**
 * Rebinds backup-restored widgets / mosaic children to fresh host IDs.
 * Extracted from [AppWidgetController] — no drop coordinates or picker state.
 */
class AppWidgetRestoredRebinder(
    private val activity: ComponentActivity,
    private val appWidgetHost: AppWidgetHost,
    private val appWidgetManager: AppWidgetManager,
    private val homeScreenViewModel: HomeScreenViewModel
) {
    private fun attemptBindRestoredWidget(pkg: String, cls: String, item: HomeScreenItem? = null): Pair<RebindOutcome, Int> {
        val component = ComponentName(pkg, cls)
        val isInstalled = try {
            activity.packageManager.getReceiverInfo(component, 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        if (!isInstalled) return RebindOutcome.PROVIDER_NOT_INSTALLED to -1
        val newId = appWidgetHost.allocateAppWidgetId()
        // Pre-populate restored SharedPreferences state for first-party widgets before bind,
        // so when Android's framework invokes AppWidgetProvider.onUpdate() during bind,
        // the provider immediately renders RemoteViews with the restored config and note!
        if (item != null) {
            applyRestoredNexusState(item.folderConfigJson, newId)
        }
        val allowed = appWidgetManager.bindAppWidgetIdIfAllowed(newId, component)
        return if (allowed) RebindOutcome.BOUND to newId else {
            appWidgetHost.deleteAppWidgetId(newId)
            if (item != null) {
                NexusWidgetConfig.delete(activity, newId)
                com.nexus.launcher.ui.widgets.notes.NotesDataStore.delete(activity, newId)
            }
            RebindOutcome.NEEDS_USER_CONFIRMATION to -1
        }
    }

    suspend fun rebindRestoredWidgets(pendingItems: List<HomeScreenItem>): Map<HomeScreenItem, RebindOutcome> {
        val outcomes = mutableMapOf<HomeScreenItem, RebindOutcome>()
        for (item in pendingItems) {
            if (item.itemType != 3 || item.appWidgetId != -1) continue
            val pkg = item.packageName
            val cls = item.providerClassName
            if (cls.isNullOrBlank()) {
                android.util.Log.w(
                    "WidgetGhostCleanup",
                    "Removing orphaned ghost widget id=${item.id}, pkg=$pkg, provider=null, pos=(page=${item.page}, col=${item.column}, row=${item.row})"
                )
                homeScreenViewModel.deleteItem(item)
                continue
            }
            val (outcome, newAppWidgetId) = attemptBindRestoredWidget(pkg, cls, item)
            outcomes[item] = outcome
            if (outcome == RebindOutcome.BOUND && newAppWidgetId != -1) {
                finalizeRestoredWidget(item, newAppWidgetId)
            }
        }
        return outcomes
    }

    private suspend fun finalizeRestoredWidget(item: HomeScreenItem, newAppWidgetId: Int) {
        // Dedicated minimal finalization: NO hit-testing, NO drop coordinates, NO Activity launch.
        // Update DB with the new real ID in-place to preserve position and zIndex.
        val cleanedConfig = stripRestoredNexusKeys(item.folderConfigJson)
        val updatedItem = item.copy(appWidgetId = newAppWidgetId, folderConfigJson = cleanedConfig)
        homeScreenViewModel.updateItemImmediately(updatedItem)

        // Broadcast to first-party Nexus widget providers (Notes, Clock, Weather, etc.) so they
        // re-render RemoteViews with the restored instance state from SharedPreferences.
        // Addressed to this widget's own provider, never package-wide: AppWidgetProvider hands
        // whatever ids arrive to its own onUpdate without checking they are its own, so a
        // package-scoped broadcast makes every Nexus provider paint itself onto every id in the
        // extra — widgets visibly flip through each other's layouts and keep whichever receiver
        // ran last.
        val provider = item.providerClassName?.let { ComponentName(item.packageName, it) }

        val configIntent = android.content.Intent("com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED").apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newAppWidgetId)
            putExtra("appWidgetId", newAppWidgetId)
            if (provider != null) component = provider else setPackage(activity.packageName)
        }
        activity.sendBroadcast(configIntent)

        val updateIntent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(newAppWidgetId))
            if (provider != null) component = provider else setPackage(activity.packageName)
        }
        activity.sendBroadcast(updateIntent)
    }

    private fun stripRestoredNexusKeys(folderConfigJson: String): String {
        val json = try { org.json.JSONObject(folderConfigJson.ifBlank { "{}" }) } catch (_: Exception) { return folderConfigJson }
        val configKey = com.nexus.launcher.ui.backup.BackupImporter.RESTORED_NEXUS_CONFIG_KEY
        val noteKey = com.nexus.launcher.ui.backup.BackupImporter.RESTORED_NOTE_KEY
        if (!json.has(configKey) && !json.has(noteKey)) return folderConfigJson
        json.remove(configKey)
        json.remove(noteKey)
        return json.toString()
    }

    /**
     * First-party widgets keep per-instance state keyed by appWidgetId. BackupImporter stashes
     * the exported state inside folderConfigJson; re-key it to the freshly bound id.
     */
    private fun applyRestoredNexusState(folderConfigJson: String, newAppWidgetId: Int) {
        val json = try { org.json.JSONObject(folderConfigJson.ifBlank { "{}" }) } catch (_: Exception) { return }
        val configKey = com.nexus.launcher.ui.backup.BackupImporter.RESTORED_NEXUS_CONFIG_KEY
        val noteKey = com.nexus.launcher.ui.backup.BackupImporter.RESTORED_NOTE_KEY
        if (!json.has(configKey) && !json.has(noteKey)) return
        val gson = com.google.gson.Gson()
        json.optJSONObject(configKey)?.let { obj ->
            try {
                val config = gson.fromJson(obj.toString(), NexusWidgetConfig.InstanceConfig::class.java)
                NexusWidgetConfig.write(activity, config.copy(appWidgetId = newAppWidgetId))
            } catch (e: Exception) {
                android.util.Log.w("WidgetRestore", "Could not re-key Nexus widget config for id=$newAppWidgetId", e)
            }
        }
        json.optJSONObject(noteKey)?.let { obj ->
            try {
                val note = gson.fromJson(obj.toString(), com.nexus.launcher.ui.widgets.notes.NoteData::class.java)
                com.nexus.launcher.ui.widgets.notes.NotesDataStore.write(activity, newAppWidgetId, note)
            } catch (e: Exception) {
                android.util.Log.w("WidgetRestore", "Could not restore note for id=$newAppWidgetId", e)
            }
        }
    }

    suspend fun rebindRestoredMosaics(
        pendingItems: List<HomeScreenItem>
    ): Map<HomeScreenItem, Map<String, RebindOutcome>> {
        val mosaicOutcomes = mutableMapOf<HomeScreenItem, Map<String, RebindOutcome>>()
        for (item in pendingItems) {
            if (item.itemType != 4 || item.folderConfigJson.isBlank()) continue
            val config = MosaicConfig.parse(item.folderConfigJson)
            var changed = false
            val outcomes = mutableMapOf<String, RebindOutcome>()
            val newPages = config.pages.map { page ->
                page.copy(children = page.children.map { child ->
                    if (child.providerPackage.isBlank() || child.providerClassName.isBlank()) return@map child
                    val (outcome, newId) = attemptBindRestoredWidget(child.providerPackage, child.providerClassName)
                    outcomes["${child.providerPackage}/${child.providerClassName}"] = outcome
                    changed = true
                    child.copy(appWidgetId = if (outcome == RebindOutcome.BOUND) newId else -1)
                })
            }
            if (changed) {
                mosaicOutcomes[item] = outcomes
                homeScreenViewModel.updateItemImmediately(
                    item.copy(folderConfigJson = config.copy(pages = newPages).toJson())
                )
            }
        }
        return mosaicOutcomes
    }
}
