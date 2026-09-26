package com.nexus.launcher.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Picking and using the folder backups live in.
 *
 * The user chooses it once; the persisted tree permission is what lets later sessions both write
 * new backups there and enumerate the existing ones. That enumeration is the whole point — the
 * previous flow saved through `ACTION_CREATE_DOCUMENT` to a location the launcher immediately
 * forgot, so there was never a list to show.
 */
object BackupFolderLauncher {
    private const val REGISTRY_KEY = "nexus_backup_folder_pick"

    /** Opens the folder picker and reports the persisted tree URI, or null if cancelled. */
    fun pick(activity: ComponentActivity, onPicked: (String?) -> Unit) {
        var launcher: ActivityResultLauncher<Uri?>? = null
        launcher = activity.activityResultRegistry.register(
            REGISTRY_KEY, ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            launcher?.unregister()
            if (uri == null) {
                onPicked(null)
                return@register
            }
            val ok = runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.isSuccess
            onPicked(if (ok) uri.toString() else null)
        }
        launcher.launch(null)
    }

    /** Human-readable folder name for the settings row, or null when unset/revoked. */
    fun displayName(context: Context, treeUri: String): String? =
        BackupCatalog.folder(context, treeUri)?.name

    /**
     * Where the folder is, for the Backup Location row — "Internal storage/Nexus backup" — or
     * null when unset or no longer readable. A tree from the device's own storage carries its
     * path in the document id ("primary:Nexus backup"); any other provider (a cloud drive) only
     * has a name to show, so that is what it gets.
     */
    fun displayPath(context: Context, treeUri: String): String? {
        val name = displayName(context, treeUri) ?: return null
        val uri = Uri.parse(treeUri)
        if (uri.authority != EXTERNAL_STORAGE_AUTHORITY) return name
        val docId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }
            .getOrNull() ?: return name
        val volume = docId.substringBefore(':')
        val relative = docId.substringAfter(':', "")
        val root = context.getString(
            if (volume.equals("primary", ignoreCase = true)) com.nexus.launcher.R.string.backup_location_internal
            else com.nexus.launcher.R.string.backup_location_sd_card
        )
        return if (relative.isEmpty()) root else "$root/$relative"
    }

    /** The Backup Location row's subtitle: the path, or why there is none. */
    fun locationSubtitle(context: Context, treeUri: String): String = when {
        treeUri.isBlank() -> context.getString(com.nexus.launcher.R.string.backup_location_none)
        else -> displayPath(context, treeUri)
            ?: context.getString(com.nexus.launcher.R.string.backup_location_missing)
    }

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * Creates the destination document for a new backup inside the chosen folder.
     * Returns null when the folder is gone or the file could not be created.
     */
    fun createBackupFile(context: Context, treeUri: String): Uri? {
        val dir = BackupCatalog.folder(context, treeUri) ?: return null
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        return try {
            dir.createFile("application/zip", "nexus_$stamp")?.uri
        } catch (_: Exception) {
            null
        }
    }

    /** A backup's default user-facing name — the moment it was taken. */
    fun defaultLabel(): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())

    /** True when the persisted permission still holds. */
    fun isUsable(context: Context, treeUri: String): Boolean {
        val dir = BackupCatalog.folder(context, treeUri) ?: return false
        return dir.canWrite()
    }

    @Suppress("unused")
    fun asDocument(context: Context, treeUri: String): DocumentFile? =
        BackupCatalog.folder(context, treeUri)
}
