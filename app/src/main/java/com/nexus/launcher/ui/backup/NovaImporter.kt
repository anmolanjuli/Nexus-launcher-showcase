package com.nexus.launcher.ui.backup

import android.content.ComponentName
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

object NovaImporter {
    private const val TAG = "NovaImporter"

    class ImportException(message: String) : Exception(message)

    suspend fun restore(
        context: Context,
        uri: Uri,
        dao: HomeScreenDao,
        settingsRepository: SettingsRepository,
        preferenceManager: PreferenceManager
    ) = withContext(Dispatchers.IO) {
        val stagingRoot = File(context.cacheDir, "nova_restore_staging")
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
                            outFile.outputStream().use { os -> zis.copyTo(os) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: throw ImportException("Could not open InputStream for URI")

            val dbFile = File(stagingRoot, "nova.db")
            if (!dbFile.exists()) throw ImportException("nova.db not found in backup")

            val currentSettings = settingsRepository.snapshot()
            val ourCols = currentSettings.homeColumns
            val ourRows = currentSettings.homeRows
            var novaSubgridCols = ourCols
            var novaSubgridRows = ourRows

            val xmlFile = File(stagingRoot, "nova.xml")
            if (xmlFile.exists()) {
                val match = Regex("""name="desktop_grid"[^>]*>(\d+)x(\d+)""").find(xmlFile.readText())
                if (match != null) {
                    novaSubgridRows = match.groupValues[1].toInt()
                    novaSubgridCols = match.groupValues[2].toInt()
                }
            }

            Log.d(TAG, "Opening nova.db...")
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("SELECT * FROM favorites", null)
            val rows = mutableListOf<NovaImportPersist.NovaRow>()

            Log.d(TAG, "Validating favorites table...")
            while (cursor.moveToNext()) {
                try {
                    val idIdx = cursor.getColumnIndexOrThrow("_id")
                    val titleIdx = cursor.getColumnIndexOrThrow("title")
                    val intentIdx = cursor.getColumnIndexOrThrow("intent")
                    val containerIdx = cursor.getColumnIndexOrThrow("container")
                    val screenIdx = cursor.getColumnIndexOrThrow("screen")
                    val cellXIdx = cursor.getColumnIndexOrThrow("cellX")
                    val cellYIdx = cursor.getColumnIndexOrThrow("cellY")
                    val spanXIdx = cursor.getColumnIndexOrThrow("spanX")
                    val spanYIdx = cursor.getColumnIndexOrThrow("spanY")
                    val itemTypeIdx = cursor.getColumnIndexOrThrow("itemType")
                    val appWidgetProviderIdx = cursor.getColumnIndexOrThrow("appWidgetProvider")

                    if (cursor.isNull(idIdx) || cursor.isNull(containerIdx) || cursor.isNull(itemTypeIdx)) {
                        continue
                    }

                    val container = cursor.getInt(containerIdx)
                    val isGridItem = container == -100 || container == -101
                    if (isGridItem && (cursor.isNull(screenIdx) || cursor.isNull(cellXIdx) || cursor.isNull(cellYIdx))) {
                        continue
                    }

                    val _id = cursor.getInt(idIdx)
                    val title = if (!cursor.isNull(titleIdx)) cursor.getString(titleIdx) else ""
                    val intentStr = if (!cursor.isNull(intentIdx)) cursor.getString(intentIdx) else null
                    val screen = if (!cursor.isNull(screenIdx)) cursor.getInt(screenIdx) else 0
                    val rawCellX = if (!cursor.isNull(cellXIdx)) cursor.getInt(cellXIdx) else 0
                    val rawCellY = if (!cursor.isNull(cellYIdx)) cursor.getInt(cellYIdx) else 0
                    val rawSpanX = if (!cursor.isNull(spanXIdx)) cursor.getInt(spanXIdx) else 1
                    val rawSpanY = if (!cursor.isNull(spanYIdx)) cursor.getInt(spanYIdx) else 1

                    val isDesktop = container == -100
                    val cellX = if (isDesktop && novaSubgridCols > 0) (rawCellX * ourCols) / novaSubgridCols else rawCellX
                    val cellY = if (isDesktop && novaSubgridRows > 0) (rawCellY * ourRows) / novaSubgridRows else rawCellY
                    val spanX = if (isDesktop && novaSubgridCols > 0) Math.max(1, (rawSpanX * ourCols) / novaSubgridCols) else 1
                    val spanY = if (isDesktop && novaSubgridRows > 0) Math.max(1, (rawSpanY * ourRows) / novaSubgridRows) else 1

                    val itemType = cursor.getInt(itemTypeIdx)
                    val appWidgetProvider =
                        if (!cursor.isNull(appWidgetProviderIdx)) cursor.getString(appWidgetProviderIdx) else null

                    val mappedItemType = when (itemType) {
                        0, 1, 6 -> 0
                        2 -> 1
                        4 -> 3
                        else -> -1
                    }
                    if (mappedItemType == -1) continue

                    var packageName = ""
                    var folderKey: Int? = null
                    if (mappedItemType == 1) {
                        folderKey = parseNovaFolderKey(intentStr)
                    } else if (mappedItemType == 0 && !intentStr.isNullOrBlank()) {
                        packageName = parseNovaPackageName(intentStr)
                    }

                    var providerCls: String? = null
                    if (mappedItemType == 3 && !appWidgetProvider.isNullOrBlank()) {
                        val component = ComponentName.unflattenFromString(appWidgetProvider)
                        if (component != null) {
                            packageName = component.packageName
                            providerCls = component.className
                        }
                    }

                    rows.add(
                        NovaImportPersist.NovaRow(
                            _id, title, packageName, container, screen,
                            cellX, cellY, spanX, spanY, mappedItemType, providerCls, folderKey
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed row", e)
                }
            }
            cursor.close()
            db.close()

            val folderKeys = rows.mapNotNull { it.folderKey }.toSet()
            rows.retainAll { r ->
                r.container == -100 || r.container == -101 ||
                    r.folderKey != null || r.container in folderKeys
            }

            Log.d(TAG, "Persisting items (single Room transaction)...")
            NovaImportPersist.persist(context, dao, rows, ourCols, ourRows)

            Log.d(TAG, "Importing drawer folders...")
            NovaDrawerImporter.importDrawerFolders(context, dbFile, dao, rows)

            preferenceManager.setPendingWidgetRebind(true)
            Log.d(TAG, "Nova restore completed successfully!")
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    /** Nova favorites use toUri(0) (#Intent;…;end). URI_INTENT_SCHEME only for intent: URIs. */
    private fun parseNovaPackageName(intentStr: String): String {
        val cleaned = intentStr.replace(Regex("""extendedLaunchFlags=0x[0-9a-fA-F]+;"""), "")
        try {
            if (cleaned.contains("/") &&
                !cleaned.startsWith("intent:") &&
                !cleaned.startsWith("#Intent")
            ) {
                ComponentName.unflattenFromString(cleaned)?.packageName?.let { if (it.isNotEmpty()) return it }
            } else {
                val flags = if (cleaned.startsWith("intent:")) {
                    android.content.Intent.URI_INTENT_SCHEME
                } else {
                    0
                }
                val intent = android.content.Intent.parseUri(cleaned, flags)
                val pkg = intent.component?.packageName ?: intent.`package`.orEmpty()
                if (pkg.isNotEmpty()) return pkg
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse intent: $intentStr", e)
        }
        val match = Regex("""component=([^;]+)""").find(cleaned) ?: return ""
        return ComponentName.unflattenFromString(match.groupValues[1])?.packageName.orEmpty()
    }

    /** Nova folder membership key from intent, e.g. FOLDER%3A-207 or FOLDER:-207 → -207. */
    private fun parseNovaFolderKey(intentStr: String?): Int? {
        if (intentStr.isNullOrBlank()) return null
        val decoded = try {
            java.net.URLDecoder.decode(intentStr, Charsets.UTF_8.name())
        } catch (_: Exception) {
            intentStr
        }
        return Regex("""FOLDER:(-?\d+)""").find(decoded)?.groupValues?.get(1)?.toIntOrNull()
    }
}
