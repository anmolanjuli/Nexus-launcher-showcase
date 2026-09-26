package com.nexus.launcher.ui.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Reads the user's backup folder into the cards the Backups screen renders.
 *
 * Each zip is opened once; its manifest header and page previews are copied into a per-backup
 * cache directory keyed by identity + mtime + size, and every later listing reads the cache. The
 * zips are written manifest-first, previews-second ([BackupZipWriter]), so a listing scan stops
 * before the bulky `assets/` entries.
 *
 * Call on Dispatchers.IO.
 */
object BackupCatalog {

    private const val CACHE_DIR = "backup_catalog"
    private const val META_FILE = "meta.json"

    data class Entry(
        val uri: Uri,
        val fileName: String,
        val label: String,
        val createdAt: String,
        val lastModified: Long,
        val sizeBytes: Long,
        val pageCount: Int,
        val defaultPage: Int,
        val itemCount: Int,
        /** Extracted preview PNGs, page-ordered. Empty when the backup carried none. */
        val thumbFiles: List<File>,
        /** This backup's extracted-preview directory, removed alongside it on delete. */
        val cacheDir: File
    )

    /** Resolves the persisted tree URI to a folder, or null when unset/revoked. */
    fun folder(context: Context, treeUri: String): DocumentFile? {
        if (treeUri.isBlank()) return null
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.takeIf { it.canRead() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lists the folder's Nexus backups, newest first.
     *
     * Zips this build did not write — no `catalog` block in their manifest — are skipped rather
     * than listed as broken cards.
     */
    fun list(context: Context, treeUri: String): List<Entry> {
        val dir = folder(context, treeUri) ?: return emptyList()
        return dir.listFiles()
            .filter { it.isFile && (it.name?.endsWith(".zip", ignoreCase = true) == true) }
            .mapNotNull { read(context, it) }
            .sortedByDescending { it.lastModified }
    }

    private fun read(context: Context, doc: DocumentFile): Entry? {
        val name = doc.name ?: return null
        val key = cacheKey(doc)
        val cacheDir = File(File(context.cacheDir, CACHE_DIR), key)
        val metaFile = File(cacheDir, META_FILE)

        val meta = if (metaFile.isFile) {
            runCatching { JsonParser.parseString(metaFile.readText()).asJsonObject }.getOrNull()
        } else {
            extract(context, doc, cacheDir)
        } ?: return null

        val catalog = meta.getAsJsonObject("catalog") ?: return null
        val pageCount = catalog.get("pageCount")?.asInt ?: 0
        val thumbs = (0 until pageCount)
            .map { File(cacheDir, BackupThumbnailWriter.fileName(it)) }
            .filter { it.isFile }

        return Entry(
            uri = doc.uri,
            fileName = name,
            label = catalog.get("label")?.asString?.takeIf { it.isNotBlank() } ?: name.removeSuffix(".zip"),
            createdAt = meta.get("createdAt")?.asString ?: "",
            lastModified = doc.lastModified(),
            sizeBytes = doc.length(),
            pageCount = pageCount,
            defaultPage = catalog.get("defaultPage")?.asInt ?: 0,
            itemCount = catalog.get("itemCount")?.asInt ?: 0,
            thumbFiles = thumbs,
            cacheDir = cacheDir
        )
    }

    /**
     * Pulls the manifest header and preview PNGs out of one zip into [cacheDir].
     * Returns the parsed manifest, or null if this is not a backup this build can list.
     */
    private fun extract(context: Context, doc: DocumentFile, cacheDir: File): JsonObject? {
        return try {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            var manifest: JsonObject? = null
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val entryName = entry.name
                        when {
                            entryName == "manifest.json" -> {
                                manifest = runCatching {
                                    JsonParser.parseString(zis.readBytes().decodeToString()).asJsonObject
                                }.getOrNull()
                            }
                            entryName.startsWith("${BackupThumbnailWriter.THUMBS_DIR}/") -> {
                                val leaf = entryName.substringAfterLast('/')
                                if (leaf.isNotBlank()) {
                                    FileOutputStream(File(cacheDir, leaf)).use { out ->
                                        zis.copyTo(out)
                                    }
                                }
                            }
                            // Everything past the previews is payload the list never needs.
                            else -> if (manifest != null) return@use
                        }
                        zis.closeEntry()
                    }
                }
            }
            val parsed = manifest ?: run { cacheDir.deleteRecursively(); return null }
            if (!parsed.has("catalog")) {
                cacheDir.deleteRecursively()
                return null
            }
            File(cacheDir, META_FILE).writeText(parsed.toString())
            parsed
        } catch (_: Exception) {
            cacheDir.deleteRecursively()
            null
        }
    }

    /** Identity plus mtime and size, so an overwritten backup re-extracts instead of going stale. */
    private fun cacheKey(doc: DocumentFile): String {
        val raw = "${doc.uri}|${doc.lastModified()}|${doc.length()}"
        return raw.hashCode().toUInt().toString(16)
    }

    fun delete(context: Context, entry: Entry): Boolean {
        return try {
            val ok = DocumentFile.fromSingleUri(context, entry.uri)?.delete() ?: false
            if (ok) entry.cacheDir.deleteRecursively()
            ok
        } catch (_: Exception) {
            false
        }
    }
}
