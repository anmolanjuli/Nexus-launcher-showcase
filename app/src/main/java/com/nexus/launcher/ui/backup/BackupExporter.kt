package com.nexus.launcher.ui.backup

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import java.io.File
import java.io.FileInputStream

/**
 * Backup/Restore v1 export — builds staging dir, manifest.json, assets/, zip.
 * All work must run on Dispatchers.IO.
 */
class BackupExporter(
    private val context: Context,
    private val homeScreenDao: HomeScreenDao,
    private val settingsRepository: SettingsRepository,
    private val dockSettingsRepository: DockSettingsRepository,
    private val preferenceManager: PreferenceManager,
    private val themeController: com.nexus.launcher.theme.ThemeController? = null,
    private val fontFamilyController: com.nexus.launcher.typography.FontFamilyController? = null,
    private val feedDao: com.nexus.launcher.data.FeedDao? = null,
    private val localeController: com.nexus.launcher.locale.LocaleController? = null
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Builds a zip in cache and returns the file, or null on failure.
     */
    suspend fun buildZipFile(label: String? = null): File? {
        return try {
            val staging = File(context.cacheDir, "nexus_backup_staging").also {
                it.deleteRecursively()
                it.mkdirs()
            }
            val settings = settingsRepository.snapshot()
            val dock = dockSettingsRepository.snapshot()
            val items = homeScreenDao.getAllItemsForExportSync()
            val pageCount = maxOf(
                settings.homePageCount,
                context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
                    .getInt("home_page_count", 1),
                1
            )
            // Every page is a grid page; the array keeps its manifest shape so the importer and
            // the catalog need no special case.
            val pages = (0 until pageCount.coerceAtLeast(1))
                .map { Triple(it, "GRID", null as String?) }
            val defaultPage = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
                .getInt("default_page", 0)
            val bundled = BackupAssetBundler.bundle(staging, items, settings, preferenceManager)
            val thumbSize = BackupThumbnailWriter.write(context, staging, items, settings, pageCount)

            // Stores that live outside NexusSettingsData and so cannot ride the settings snapshot.
            val fontPathById = fontFamilyController?.let {
                BackupAssetBundler.bundleFonts(staging, it.customFonts.value)
            } ?: emptyMap()
            val themeEngine = themeController?.let { BackupThemeState.themeEngineObject(it) }
            val typography = fontFamilyController?.let { fonts ->
                BackupThemeState.typographyObject(fonts) { entry -> fontPathById[entry.id] }
            }
            val feedSources = feedDao?.let {
                BackupThemeState.feedSourcesArray(it.getAllSourcesSnapshot())
            }
            val locale = localeController?.let { BackupThemeState.localeObject(it) }
            val manifest = BackupManifestBuilder.build(
                items = items,
                pages = pages,
                settings = settings,
                dock = dock,
                assets = bundled.assets,
                coverPathByFolderId = bundled.coverPathByFolderId,
                preferenceManager = preferenceManager,
                defaultPage = defaultPage,
                context = context,
                pageCount = pageCount,
                thumbSize = thumbSize,
                label = label,
                themeEngine = themeEngine,
                typography = typography,
                feedSources = feedSources,
                locale = locale
            )
            BackupItemPositions.addTo(manifest, homeScreenDao)
            File(staging, "manifest.json").writeText(gson.toJson(manifest))
            val zip = File(context.cacheDir, "nexus_backup_${System.currentTimeMillis()}.zip")
            BackupZipWriter.zipDirectory(staging, zip)
            staging.deleteRecursively()
            zip
        } catch (_: Exception) {
            null
        }
    }

    /** Streams [zipFile] into a SAF destination [uri]. */
    fun writeZipToUri(zipFile: File, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(zipFile).use { input -> input.copyTo(out) }
            } ?: return false
            true
        } catch (_: Exception) {
            false
        } finally {
            zipFile.delete()
        }
    }
}
