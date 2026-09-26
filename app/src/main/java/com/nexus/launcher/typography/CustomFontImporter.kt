package com.nexus.launcher.typography

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Handles reading, copying, and validating user-uploaded font files from SAF into app-private storage.
 */
object CustomFontImporter {

    suspend fun importFont(context: Context, uri: Uri): Result<CustomFontEntry> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val rawName = queryDisplayName(context, uri) ?: "Custom Font"
        val cleanName = rawName.substringBeforeLast(".").trim()
        val extension = rawName.substringAfterLast(".", "ttf").lowercase()

        val fontsDir = File(context.filesDir, "custom_fonts").apply { mkdirs() }
        val fontId = UUID.randomUUID().toString().take(8)
        val targetFile = File(fontsDir, "font_${fontId}.${extension}")

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open font file stream"))

            if (!hasValidFontHeader(targetFile)) {
                targetFile.delete()
                return@withContext Result.failure(IllegalArgumentException("Invalid font file signature"))
            }

            val validatedTypeface = try {
                Typeface.createFromFile(targetFile)
            } catch (e: Exception) {
                null
            }

            if (validatedTypeface == null) {
                targetFile.delete()
                return@withContext Result.failure(IllegalArgumentException("Invalid or corrupt font file"))
            }

            val entry = CustomFontEntry(
                id = fontId,
                name = cleanName,
                filePath = targetFile.absolutePath
            )
            Result.success(entry)
        } catch (e: Exception) {
            targetFile.delete()
            Result.failure(e)
        }
    }

    private fun hasValidFontHeader(file: File): Boolean {
        if (file.length() < 4) return false
        val header = ByteArray(4)
        try {
            file.inputStream().use { stream ->
                if (stream.read(header) < 4) return false
            }
        } catch (_: Exception) {
            return false
        }
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        val b2 = header[2].toInt() and 0xFF
        val b3 = header[3].toInt() and 0xFF

        val isTrueType = b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00
        val isOpenType = b0 == 0x4F && b1 == 0x54 && b2 == 0x54 && b3 == 0x4F
        val isMacTrue = b0 == 0x74 && b1 == 0x72 && b2 == 0x75 && b3 == 0x65
        val isPostScript = b0 == 0x74 && b1 == 0x79 && b2 == 0x70 && b3 == 0x31
        val isTtc = b0 == 0x74 && b1 == 0x74 && b2 == 0x63 && b3 == 0x66
        val isWoff = b0 == 0x77 && b1 == 0x4F && b2 == 0x46 && b3 == 0x46
        val isWoff2 = b0 == 0x77 && b1 == 0x4F && b2 == 0x46 && b3 == 0x32

        return isTrueType || isOpenType || isMacTrue || isPostScript || isTtc || isWoff || isWoff2
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) return cursor.getString(index)
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment
    }
}
