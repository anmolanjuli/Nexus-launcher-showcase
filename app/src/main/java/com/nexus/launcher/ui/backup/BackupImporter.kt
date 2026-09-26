package com.nexus.launcher.ui.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.ui.dock.settings.DockBackgroundMode
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

object BackupImporter {
    private const val TAG = "BackupImporter"

    /** Highest manifest version this build writes and can read. */
    const val CURRENT_FORMAT_VERSION = 2

    /** Keys stashed in a restored widget's folderConfigJson until it is rebound to a new id. */
    const val RESTORED_NEXUS_CONFIG_KEY = "restoredNexusConfig"
    const val RESTORED_NOTE_KEY = "restoredNote"

    class ImportException(message: String) : Exception(message)

    private fun JsonObject.reqInt(field: String): Int {
        val element = this.get(field)
        if (element == null || element.isJsonNull) throw ImportException("Missing required field '$field'")
        return element.asInt
    }

    private fun JsonObject.reqFloat(field: String): Float {
        val element = this.get(field)
        if (element == null || element.isJsonNull) throw ImportException("Missing required field '$field'")
        return element.asFloat
    }

    private fun JsonObject.reqString(field: String): String {
        val element = this.get(field)
        if (element == null || element.isJsonNull) throw ImportException("Missing required field '$field'")
        return element.asString
    }

    private fun JsonObject.reqBool(field: String): Boolean {
        val element = this.get(field)
        if (element == null || element.isJsonNull) throw ImportException("Missing required field '$field'")
        return element.asBoolean
    }

    private fun JsonObject.reqObject(field: String): JsonObject {
        val element = this.get(field)
        if (element == null || element.isJsonNull) throw ImportException("Missing required object '$field'")
        return element.asJsonObject
    }

    private fun JsonObject.optString(field: String): String? {
        val element = this.get(field)
        if (element == null || element.isJsonNull) return null
        return element.asString
    }


    suspend fun restore(
        context: Context,
        uri: Uri,
        dao: HomeScreenDao,
        settingsRepository: SettingsRepository,
        dockSettingsRepository: DockSettingsRepository,
        preferenceManager: PreferenceManager,
        themeController: com.nexus.launcher.theme.ThemeController? = null,
        fontFamilyController: com.nexus.launcher.typography.FontFamilyController? = null,
        feedDao: com.nexus.launcher.data.FeedDao? = null,
        localeController: com.nexus.launcher.locale.LocaleController? = null
    ) = withContext(Dispatchers.IO) {
        val stagingRoot = File(context.cacheDir, "restore_staging")
        stagingRoot.deleteRecursively()
        stagingRoot.mkdirs()

        try {
            Log.d(TAG, "Extracting zip...")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(stagingRoot, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { os ->
                                zis.copyTo(os)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: throw ImportException("Could not open InputStream for URI")

            val manifestFile = File(stagingRoot, "manifest.json")
            if (!manifestFile.exists()) {
                throw ImportException("manifest.json not found in backup")
            }

            Log.d(TAG, "Parsing manifest.json...")
            val rawJson = manifestFile.readText()
            val root = JsonParser.parseString(rawJson).asJsonObject

            val formatVersion = root.reqInt("formatVersion")
            // v2 adds settingsSnapshot/themeEngine/typography/feedSources on top of v1 and is
            // read by the same code path, which falls back to the v1 blocks when they are absent.
            if (formatVersion !in 1..CURRENT_FORMAT_VERSION) {
                throw ImportException("Unsupported formatVersion: $formatVersion")
            }

            Log.d(TAG, "Validating JSON blocks...")
            val pagesArr = root.getAsJsonArray("pages") ?: throw ImportException("Missing pages array")
            val settingsObj = root.reqObject("settings")
            val bundledAssets = root.reqObject("bundledAssets")
            val itemsArr = root.getAsJsonArray("homeScreenItems") ?: throw ImportException("Missing homeScreenItems array")

            itemsArr.forEach { i ->
                val itemObj = i.asJsonObject
                val element = itemObj.get("id")
                if (element == null || element.isJsonNull) {
                    throw ImportException("This backup was created with an older, incompatible format and cannot be restored")
                }
            }

            Log.d(TAG, "Clearing local state...")
            dao.deleteAll()
            
            val prefs = context.applicationContext.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
            val prefsEd = prefs.edit()
            prefs.all.keys.filter { it.startsWith("page_meta_") }.forEach { prefsEd.remove(it) }
            prefsEd.apply()

            // Page types no longer exist — every page is a grid page — so the manifest's pages
            // array only carries the count, which homeScreen.pageCount already restores. The
            // stale page_meta_ keys were cleared just above.
            Log.d(TAG, "Pages: ${pagesArr.size()} (grid)")

            Log.d(TAG, "Restoring settings...")
            // v2 carries the whole NexusSettingsData; the v1 blocks are the fallback for a zip
            // written before that existed. Dock settings are a separate store either way.
            val snapshotObj = root.getAsJsonObject("settingsSnapshot")
            if (snapshotObj != null) {
                BackupSnapshotRestorer.restore(snapshotObj, settingsRepository)
                BackupSettingsRestorer.restoreDock(settingsObj, dockSettingsRepository)
            } else {
                BackupSettingsRestorer.restoreSettings(settingsObj, settingsRepository, dockSettingsRepository)
            }
            // E-Ink is read on the draw path, from SharedPreferences rather than the settings
            // store, so a restore has to write that copy itself — nothing else will.
            val einkSource = snapshotObj ?: settingsObj.getAsJsonObject("theme")
            einkSource?.get("feedEInkMode")?.asBoolean?.let { enabled ->
                com.nexus.launcher.feed.NexusFeedEInkCoordinator.restoreFastCopy(
                    context, enabled, einkSource.get("feedEInkDark")?.asBoolean ?: false
                )
            }
            BackupSnapshotRestorer.restoreBehavior(root.getAsJsonObject("behavior"), preferenceManager)
            BackupSnapshotRestorer.restoreStandaloneSettings(
                root.getAsJsonObject("standaloneSettings"), context
            )
            
            val homeScreenObj = settingsObj.getAsJsonObject("homeScreen")
            if (homeScreenObj != null) {
                val pCount = homeScreenObj.get("pageCount")?.asInt ?: 1
                val dPage = homeScreenObj.get("defaultPage")?.asInt ?: 0
                prefs.edit()
                    .putInt("home_page_count", pCount)
                    .putInt("default_page", dPage)
                    .apply()
            }

            val perAppPrefs = root.getAsJsonObject("perAppPreferences")
            if (perAppPrefs != null) {
                Log.d(TAG, "Restoring per-app preferences...")
                BackupSettingsRestorer.restorePerAppPreferences(perAppPrefs, preferenceManager)
            }

            Log.d(TAG, "Restoring assets...")
            val newFolderCoverPaths = restoreAssets(context, stagingRoot, bundledAssets, preferenceManager, settingsRepository)

            Log.d(TAG, "Restoring items (Pass 1 - Roots/Folders)...")
            val oldIdToNewId = mutableMapOf<Int, Long>()

            itemsArr.forEach { i ->
                val itemObj = i.asJsonObject
                val parentIdOpt = itemObj.get("parentFolderId")
                if (parentIdOpt == null || parentIdOpt.isJsonNull) {
                    val oldId = itemObj.reqInt("id")
                    val item = parseItem(itemObj, oldId, newFolderCoverPaths) ?: return@forEach
                    // Every root item gets its new id recorded, not just folders. Shortcut Boxes
                    // and App Boxes hold their contents as child rows keyed by containerId too,
                    // so mapping only folders left those children pointing at a stale id — they
                    // were reinserted as loose home items and the box restored empty.
                    oldIdToNewId[oldId] = dao.insertItemAndGetId(item)
                }
            }
            BackupItemPositions.restore(context, root, oldIdToNewId, dao) // hand-placed landscape etc.

            Log.d(TAG, "Restoring items (Pass 2 - Children)...")
            itemsArr.forEach { i ->
                val itemObj = i.asJsonObject
                val parentIdOpt = itemObj.get("parentFolderId")
                if (parentIdOpt != null && !parentIdOpt.isJsonNull) {
                    val oldParentId = parentIdOpt.asInt
                    val newParentId = oldIdToNewId[oldParentId]
                    if (newParentId == null) {
                        // Container missing from the backup: dropping the orphan is better than
                        // spilling a folder's or box's contents loose onto the home screen.
                        Log.w(TAG, "Skipping child of unknown container $oldParentId")
                        return@forEach
                    }
                    val oldId = itemObj.reqInt("id")
                    val item = parseItem(itemObj, oldId, newFolderCoverPaths)?.copy(containerId = newParentId)
                        ?: return@forEach
                    dao.insertItem(item)
                }
            }

            // Everything below runs only once the layout itself is committed. These touch live
            // UI state — re-theming every surface, and a locale change that can recreate the
            // hosting Activity on Android 13+, which cancels the coroutine this restore runs in.
            // Doing them earlier meant that recreate could abort the item passes entirely.
            if (fontFamilyController != null) {
                Log.d(TAG, "Restoring typography...")
                BackupThemeState.restoreTypography(
                    fontFamilyController, root.getAsJsonObject("typography")
                ) { relative -> restoreFontFile(context, stagingRoot, relative) }
            }
            if (feedDao != null) {
                Log.d(TAG, "Restoring feed sources...")
                BackupThemeState.restoreFeedSources(feedDao, root.getAsJsonArray("feedSources"), context)
            }
            if (themeController != null) {
                Log.d(TAG, "Restoring theme engine...")
                BackupThemeState.restoreThemeEngine(themeController, root.getAsJsonObject("themeEngine"))
            }
            // Genuinely last: this one can pull the Activity out from under us.
            if (localeController != null) {
                Log.d(TAG, "Restoring app language...")
                BackupThemeState.restoreLocale(localeController, root.getAsJsonObject("locale"))
            }

            preferenceManager.setPendingWidgetRebind(true)
            preferenceManager.setPendingPrefsRefresh(true)
            Log.d(TAG, "Restore completed successfully!")
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    /** Null for an item type this build does not know; the caller skips it rather than guess. */
    private fun parseItem(itemObj: JsonObject, oldId: Int, newFolderCoverPaths: Map<String, String>): HomeScreenItem? {
        val typeStr = itemObj.reqString("itemType")
        val packageName = itemObj.optString("packageName") ?: ""
        val itemType = BackupItemType.resolve(typeStr, packageName) ?: run {
            Log.w(TAG, "Skipping item $oldId of unknown type $typeStr")
            return null
        }
        var providerClassName: String? = null
        var folderTitle = ""
        // Older backups (no "configJson") fall back to "{}" — same as a freshly placed item.
        var folderConfigJson = itemObj.optString("configJson")?.takeIf { it.isNotBlank() } ?: "{}"

        if (itemType == 3) {
            val widgetObj = itemObj.reqObject("widget")
            providerClassName = widgetObj.optString("providerClassName")
            // Stash first-party instance state inside the item until the rebinder knows the new
            // appWidgetId (AppWidgetRestoredRebinder.finalizeRestoredWidget re-keys and strips it).
            val stash = try { org.json.JSONObject(folderConfigJson) } catch (_: Exception) { org.json.JSONObject() }
            widgetObj.get("nexusConfig")?.takeIf { it.isJsonObject }?.let {
                stash.put(RESTORED_NEXUS_CONFIG_KEY, org.json.JSONObject(it.toString()))
            }
            widgetObj.get("note")?.takeIf { it.isJsonObject }?.let {
                stash.put(RESTORED_NOTE_KEY, org.json.JSONObject(it.toString()))
            }
            folderConfigJson = stash.toString()
        } else if (itemType == 1) {
            val folderObj = itemObj.reqObject("folder")
            folderTitle = folderObj.reqString("title")
            val configObj = folderObj.reqObject("config")
            
            val matchKey = folderTitle.ifBlank { oldId.toString() }
            val newCoverPath = newFolderCoverPaths[matchKey]
            if (newCoverPath != null) {
                configObj.addProperty("coverImageFile", newCoverPath)
            }
            folderConfigJson = configObj.toString()
        } else if (itemType == 4) {
            val mosaicObj = itemObj.reqObject("mosaic")
            folderConfigJson = mosaicObj.toString()
        }

        return HomeScreenItem(
            packageName = packageName,
            page = itemObj.reqInt("page"),
            column = itemObj.reqInt("column"),
            row = itemObj.reqInt("row"),
            xFraction = itemObj.reqFloat("positionXFraction"),
            yFraction = itemObj.reqFloat("positionYFraction"),
            itemType = itemType,
            appWidgetId = if (itemType == 3 || itemType == 4) -1 else -1,
            spanX = itemObj.reqInt("spanColumns"),
            spanY = itemObj.reqInt("spanRows"),
            zIndex = itemObj.reqInt("stackOrder"),
            paddingEnabled = itemObj.reqBool("paddingEnabled"),
            containerId = -1L,
            folderTitle = folderTitle,
            folderConfigJson = folderConfigJson,
            providerClassName = providerClassName
        )
    }

    /**
     * Unpacks a bundled font into app-private storage and returns its new path. Returns null when
     * the entry is not a bundled asset (an absolute path from a v2 backup whose font file was
     * missing at export) or could not be copied — the caller skips those, so the font picker
     * never lists an entry that cannot load.
     */
    private fun restoreFontFile(context: Context, stagingRoot: File, relative: String): String? {
        if (!relative.startsWith("assets/fonts/")) return null
        val src = File(stagingRoot, relative)
        if (!src.isFile) return null
        return runCatching {
            val destDir = File(context.filesDir, "custom_fonts").also { it.mkdirs() }
            val dest = File(destDir, src.name)
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        }.getOrNull()
    }

    private suspend fun restoreAssets(
        context: Context,
        stagingRoot: File,
        bundledAssets: JsonObject,
        preferenceManager: PreferenceManager,
        settingsRepository: SettingsRepository
    ): Map<String, String> {
        val newFolderCoverPaths = mutableMapOf<String, String>()
        val folderCoversArr = bundledAssets.getAsJsonArray("folderCovers")
        folderCoversArr?.forEach {
            val obj = it.asJsonObject
            val fileRel = obj.reqString("file")
            val referencedIn = obj.reqString("referencedIn")
            val src = File(stagingRoot, fileRel)
            if (src.exists()) {
                val destDir = File(context.filesDir, "folder_covers").also { d -> d.mkdirs() }
                val dest = File(destDir, src.name)
                src.copyTo(dest, overwrite = true)
                // Extract "title-or-id" from "folder:<title_or_id>"
                val prefix = "folder:"
                if (referencedIn.startsWith(prefix)) {
                    val matchKey = referencedIn.substring(prefix.length)
                    newFolderCoverPaths[matchKey] = dest.absolutePath
                }
            }
        }

        val customAppIconsArr = bundledAssets.getAsJsonArray("customAppIcons")
        customAppIconsArr?.forEach {
            val pkg = it.asJsonObject.reqString("packageName")
            val fileRel = it.asJsonObject.reqString("file")
            val src = File(stagingRoot, fileRel)
            if (src.exists()) {
                val destDir = File(context.filesDir, "app_icons").also { d -> d.mkdirs() }
                val dest = File(destDir, src.name)
                src.copyTo(dest, overwrite = true)
                preferenceManager.setCustomIcon(pkg, dest.absolutePath)
            }
        }

        val wallpaperArr = bundledAssets.getAsJsonArray("wallpaperImages")
        wallpaperArr?.forEach {
            val fileRel = it.asJsonObject.reqString("file")
            val src = File(stagingRoot, fileRel)
            if (src.exists()) {
                val destDir = File(context.filesDir, "wallpaper").also { d -> d.mkdirs() }
                val dest = File(destDir, src.name)
                src.copyTo(dest, overwrite = true)
                settingsRepository.updateWallpaperGallery(dest.absolutePath)
            }
        }
        return newFolderCoverPaths
    }
}
