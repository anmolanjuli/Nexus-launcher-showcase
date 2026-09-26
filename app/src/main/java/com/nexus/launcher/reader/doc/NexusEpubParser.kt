package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Lightweight, zero-dependency pure Kotlin/Java EPUB parser.
 * Handles ZIP extraction, OPF parsing, spine ordering, TOC extraction, cover extraction,
 * and DRM/fixed-layout rejection.
 */
class NexusEpubParser(
    private val context: Context,
    private val uri: Uri
) {
    class DrmProtectedException(message: String = "This book is DRM-protected and cannot be opened.") : Exception(message)
    class FixedLayoutException(message: String = "Fixed-layout EPUBs are not yet supported.") : Exception(message)
    class InvalidEpubException(message: String = "This file does not appear to be a valid EPUB.") : Exception(message)
    class CorruptedFileException(message: String = "Unable to open this file.") : Exception(message)

    data class Chapter(
        val index: Int,
        val id: String,
        val file: File,
        val relativeHref: String,
        val title: String
    )

    var bookTitle: String = "EPUB Document"
        private set
    var author: String = ""
        private set
    var chapters: List<Chapter> = emptyList()
        private set
    var tableOfContents: List<NexusEpubTocExtractor.TocItem> = emptyList()
        private set
    var coverImageFile: File? = null
        private set
    var opfDirectory: File? = null
        private set

    val totalChapters: Int
        get() = chapters.size

    suspend fun open(): Int = withContext(Dispatchers.IO) {
        val hash = hashUri(uri.toString())
        val cacheDir = File(context.cacheDir, "epub_cache/$hash")

        if (!cacheDir.exists() || cacheDir.list().isNullOrEmpty()) {
            pruneExtractedBooks(File(context.cacheDir, "epub_cache"), keep = hash)
            extractEpubArchive(uri, cacheDir)
        }

        // 1. DRM check
        if (File(cacheDir, "META-INF/encryption.xml").exists()) {
            throw DrmProtectedException()
        }

        // 2. Container.xml check
        val containerFile = File(cacheDir, "META-INF/container.xml")
        if (!containerFile.exists()) {
            throw InvalidEpubException()
        }

        val opfRelativePath = parseContainerOpfPath(containerFile)
            ?: throw InvalidEpubException()

        val opfFile = File(cacheDir, opfRelativePath)
        if (!opfFile.exists()) {
            throw CorruptedFileException("The EPUB structure is invalid.")
        }
        val opfDir = opfFile.parentFile ?: cacheDir
        opfDirectory = opfDir

        // 3. Parse OPF package
        parseOpf(opfFile, opfDir)

        chapters.size
    }

    suspend fun readChapterHtml(chapterIndex: Int): String = withContext(Dispatchers.IO) {
        val chapter = chapters.getOrNull(chapterIndex) ?: return@withContext ""
        if (!chapter.file.exists()) return@withContext ""
        try {
            chapter.file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun getChapterTitle(index: Int): String {
        return chapters.getOrNull(index)?.title ?: context.getString(com.nexus.launcher.R.string.reader_chapter_fallback, index + 1)
    }

    private fun extractEpubArchive(sourceUri: Uri, targetDir: File) {
        if (!targetDir.exists()) targetDir.mkdirs()
        var written = 0L

        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw CorruptedFileException()

        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryPath = entry.name.replace("\\", "/")
                val outFile = File(targetDir, entryPath)

                // Guard against Zip Slip vulnerability
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw CorruptedFileException("Invalid path in archive")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        written += zis.copyTo(fos)
                    }
                    // A zip can claim to hold far more than it is: without a ceiling, one crafted
                    // (or merely enormous) book fills the device's storage while it extracts.
                    if (written > MAX_EXTRACTED_BYTES) {
                        targetDir.deleteRecursively()
                        throw CorruptedFileException("Archive too large")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Keeps the most recently opened books extracted and deletes the rest.
     *
     * An EPUB is unpacked whole into the cache and left there so reopening it is instant. Nothing
     * ever removed one, so a shelf of books quietly became a second copy of itself on disk.
     */
    private fun pruneExtractedBooks(root: File, keep: String) {
        val books = root.listFiles()?.filter { it.isDirectory && it.name != keep } ?: return
        if (books.size < MAX_EXTRACTED_BOOKS) return
        books.sortedBy { it.lastModified() }
            .take(books.size - MAX_EXTRACTED_BOOKS + 1)
            .forEach { it.deleteRecursively() }
    }

    private fun parseContainerOpfPath(containerFile: File): String? {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(InputStreamReader(FileInputStream(containerFile), Charsets.UTF_8))
        }

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (!fullPath.isNullOrBlank()) {
                    return fullPath.trim()
                }
            }
            eventType = parser.next()
        }
        return null
    }

    private fun parseOpf(opfFile: File, opfDir: File) {
        val manifestHrefs = mutableMapOf<String, String>()
        val manifestMediaTypes = mutableMapOf<String, String>()
        val manifestProperties = mutableMapOf<String, String>()
        val spineIdRefs = mutableListOf<String>()
        var coverIdFromMeta: String? = null
        var opfTocId: String? = null

        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(InputStreamReader(FileInputStream(opfFile), Charsets.UTF_8))
        }

        var eventType = parser.eventType
        var inMetadata = false
        var inTitle = false
        var inCreator = false
        var inRenditionLayout = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase() ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "metadata" -> inMetadata = true
                        "dc:title", "title" -> if (inMetadata) inTitle = true
                        "dc:creator", "creator" -> if (inMetadata) inCreator = true
                        "meta" -> {
                            val metaName = parser.getAttributeValue(null, "name")
                            val metaProp = parser.getAttributeValue(null, "property")
                            val content = parser.getAttributeValue(null, "content")
                            if (metaName.equals("cover", ignoreCase = true) && !content.isNullOrBlank()) {
                                coverIdFromMeta = content.trim()
                            }
                            if (metaProp.equals("rendition:layout", ignoreCase = true)) {
                                inRenditionLayout = true
                            }
                        }
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val mediaType = parser.getAttributeValue(null, "media-type")
                            val properties = parser.getAttributeValue(null, "properties") ?: ""
                            if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                                manifestHrefs[id] = href.trim()
                                if (!mediaType.isNullOrBlank()) manifestMediaTypes[id] = mediaType.trim()
                                if (properties.isNotBlank()) manifestProperties[id] = properties.trim()
                            }
                        }
                        "spine" -> {
                            opfTocId = parser.getAttributeValue(null, "toc")
                            val layout = parser.getAttributeValue(null, "rendition:layout")
                            if (layout.equals("pre-paginated", ignoreCase = true)) {
                                throw FixedLayoutException()
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (!idref.isNullOrBlank()) {
                                spineIdRefs.add(idref.trim())
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val txt = parser.text?.trim() ?: ""
                    if (inTitle && txt.isNotBlank()) bookTitle = txt
                    if (inCreator && txt.isNotBlank()) author = txt
                    if (inRenditionLayout && txt.equals("pre-paginated", ignoreCase = true)) {
                        throw FixedLayoutException()
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "metadata" -> inMetadata = false
                        "dc:title", "title" -> inTitle = false
                        "dc:creator", "creator" -> inCreator = false
                        "meta" -> inRenditionLayout = false
                    }
                }
            }
            eventType = parser.next()
        }

        // 4. Resolve spine chapters
        val spineHrefs = spineIdRefs.mapNotNull { manifestHrefs[it] }
        val rawToc = NexusEpubTocExtractor.extractToc(opfDir, manifestHrefs, manifestProperties, spineHrefs, opfTocId) {
            context.getString(com.nexus.launcher.R.string.reader_chapter_fallback, it + 1)
        }
        tableOfContents = rawToc

        val chapterList = mutableListOf<Chapter>()
        spineIdRefs.forEachIndexed { index, idref ->
            val href = manifestHrefs[idref] ?: return@forEachIndexed
            val file = File(opfDir, href.substringBefore("#").substringBefore("?"))
            val title = rawToc.find { it.chapterIndex == index }?.title ?: context.getString(com.nexus.launcher.R.string.reader_chapter_fallback, index + 1)
            chapterList.add(Chapter(index, idref, file, href, title))
        }
        chapters = chapterList

        // 5. Resolve cover image
        val coverItemId = coverIdFromMeta
            ?: manifestProperties.entries.firstOrNull { it.value.contains("cover-image", ignoreCase = true) }?.key
            ?: manifestHrefs.keys.firstOrNull { it.contains("cover", ignoreCase = true) && isImageMedia(manifestMediaTypes[it]) }

        coverImageFile = coverItemId?.let { id ->
            manifestHrefs[id]?.let { href ->
                File(opfDir, href.substringBefore("#").substringBefore("?"))
            }
        }?.takeIf { it.exists() }
    }

    private fun isImageMedia(mediaType: String?): Boolean {
        if (mediaType == null) return false
        return mediaType.startsWith("image/", ignoreCase = true)
    }

    suspend fun extractCoverBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        val file = coverImageFile ?: return@withContext null
        try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun hashUri(uriString: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5").digest(uriString.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            uriString.hashCode().toString()
        }
    }

    companion object {
        /** Unpacked books kept in the cache; the oldest go first. */
        private const val MAX_EXTRACTED_BOOKS = 4

        /** Ceiling on one book's unpacked size. */
        private const val MAX_EXTRACTED_BYTES = 400L * 1024 * 1024

        suspend fun extractCover(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
            val parser = NexusEpubParser(context, uri)
            try {
                parser.open()
                parser.extractCoverBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
}
