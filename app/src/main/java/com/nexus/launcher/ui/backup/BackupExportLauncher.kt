package com.nexus.launcher.ui.backup

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SAF save flow for backup export — mirrors [com.nexus.launcher.ui.WallpaperGallerySource]
 * activity-result registry pattern with ACTION_CREATE_DOCUMENT.
 */
object BackupExportLauncher {
    private const val REGISTRY_KEY = "nexus_backup_export_create"

    fun createExporter(activity: ComponentActivity): BackupExporter {
        val ep = EntryPointAccessors.fromApplication(
            activity.applicationContext, BackupExportEntryPoint::class.java
        )
        return BackupExporter(
            context = activity.applicationContext,
            homeScreenDao = ep.homeScreenDao(),
            settingsRepository = ep.settingsRepository(),
            dockSettingsRepository = ep.dockSettingsRepository(),
            preferenceManager = ep.preferenceManager(),
            themeController = ep.themeController(),
            fontFamilyController = ep.fontFamilyController(),
            feedDao = ep.feedDao(),
            localeController = ep.localeController()
        )
    }

    /**
     * Builds a backup and writes it straight into the user's chosen folder — no picker, no
     * per-backup prompt. [onDone] reports success on the main thread.
     */
    fun exportToFolder(
        activity: ComponentActivity,
        treeUri: String,
        label: String = BackupFolderLauncher.defaultLabel(),
        onDone: (Boolean) -> Unit
    ) {
        val exporter = createExporter(activity)
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val zip = exporter.buildZipFile(label)
            val target = if (zip != null) {
                BackupFolderLauncher.createBackupFile(activity, treeUri)
            } else null
            val ok = if (zip != null && target != null) {
                exporter.writeZipToUri(zip, target)
            } else {
                zip?.delete()
                false
            }
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    /** Convenience: resolve deps via Hilt, then [launch]. */
    fun launch(activity: ComponentActivity, onDone: (Boolean) -> Unit) {
        launch(activity, createExporter(activity), onDone)
    }

    /**
     * Builds the backup zip on IO, then opens the system create-document picker
     * (application/zip). Invokes [onDone] on the main thread.
     */
    fun launch(
        activity: ComponentActivity,
        exporter: BackupExporter,
        onDone: (Boolean) -> Unit
    ) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val zip = exporter.buildZipFile()
            withContext(Dispatchers.Main) {
                if (zip == null) {
                    onDone(false)
                    return@withContext
                }
                var launcher: ActivityResultLauncher<Intent>? = null
                launcher = activity.activityResultRegistry.register(
                    REGISTRY_KEY, ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    launcher?.unregister()
                    if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            val ok = exporter.writeZipToUri(zip, result.data!!.data!!)
                            withContext(Dispatchers.Main) { onDone(ok) }
                        }
                    } else {
                        zip.delete()
                        onDone(false)
                    }
                }
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, "nexus_$timestamp.zip")
                }
                launcher.launch(intent)
            }
        }
    }
}
