package com.nexus.launcher.reader.doc

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Launches the system Storage Access Framework document picker for PDF and TXT files
 * and takes persistable read URI permissions.
 */
object DocumentPickerHelper {

    private const val REGISTRY_KEY = "nexus_doc_picker"
    private val MIME_TYPES = arrayOf("application/pdf", "text/plain", "application/epub+zip")

    fun pickDocument(activity: ComponentActivity, onPicked: (Uri?) -> Unit) {
        var launcher: ActivityResultLauncher<Array<String>>? = null
        launcher = activity.activityResultRegistry.register(
            REGISTRY_KEY,
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            launcher?.unregister()
            if (uri == null) {
                onPicked(null)
                return@register
            }
            runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onPicked(uri)
        }
        launcher.launch(MIME_TYPES)
    }
}
