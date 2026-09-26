package com.nexus.launcher.reader.doc

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Repository coordinating Room database operations and SAF metadata resolution for the Document Library.
 */
class DocumentLibraryRepository(private val context: Context) {

    companion object {
        /**
         * PDF and EPUB documents a free user can keep in the library, each fully readable. The
         * limit is on adding: documents already in the library always open.
         */
        const val FREE_DOCUMENT_LIMIT = 5
    }

    private val db = DocumentDatabase.getInstance(context)
    private val dao = db.documentDao()
    private val collectionDao = db.collectionDao()

    val documents: Flow<List<DocumentRecord>> = dao.getAllDocuments()
    val collections: Flow<List<CollectionRecord>> = collectionDao.getAllCollections()

    suspend fun getDocument(uri: String): DocumentRecord? = withContext(Dispatchers.IO) {
        dao.getDocument(uri)
    }

    suspend fun addDocument(uri: Uri): DocumentRecord? = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        val (fileName, fileSize) = queryFileMeta(uri)
        val fileType = detectFileType(fileName, uri) ?: return@withContext null

        val record = DocumentRecord(
            uri = uriString,
            fileName = fileName,
            fileType = fileType,
            lastReadPosition = 0,
            lastReadTimestamp = System.currentTimeMillis(),
            totalPages = 0,
            fileSize = fileSize,
            addedTimestamp = System.currentTimeMillis()
        )
        dao.insertDocument(record)
        record
    }

    /**
     * Whether adding [uri] needs Premium: it is a PDF or EPUB and the library already holds
     * [FREE_DOCUMENT_LIMIT] of them. Re-adding a document already there never does.
     */
    suspend fun needsPremiumToAdd(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val type = detectFileType(queryFileMeta(uri).first, uri)
        (type == "pdf" || type == "epub") && dao.countLimitedDocuments(uri.toString()) >= FREE_DOCUMENT_LIMIT
    }

    suspend fun updateReadingPosition(uri: String, position: Int) = withContext(Dispatchers.IO) {
        dao.updateReadingPosition(uri, position, System.currentTimeMillis())
    }

    suspend fun updateTotalPages(uri: String, totalPages: Int) = withContext(Dispatchers.IO) {
        dao.updateTotalPages(uri, totalPages)
    }

    /** Adds one reading session's length; see [ReadingSessionTracker]. */
    suspend fun addReadingTime(uri: String, millis: Long) = withContext(Dispatchers.IO) {
        dao.addReadingTime(uri, millis)
    }

    suspend fun updateDisplayName(uri: String, displayName: String?) = withContext(Dispatchers.IO) {
        dao.updateDisplayName(uri, displayName)
    }

    suspend fun updateCollection(uri: String, collectionId: Long?) = withContext(Dispatchers.IO) {
        dao.updateCollection(uri, collectionId)
    }

    suspend fun updateCoverPath(uri: String, coverPath: String?) = withContext(Dispatchers.IO) {
        dao.updateCoverPath(uri, coverPath)
    }

    suspend fun createCollection(name: String, colorTag: String): Long = withContext(Dispatchers.IO) {
        collectionDao.insertCollection(CollectionRecord(name = name, colorTag = colorTag))
    }

    suspend fun deleteCollection(id: Long) = withContext(Dispatchers.IO) {
        collectionDao.deleteCollection(id)
    }

    suspend fun removeDocument(uri: String) = withContext(Dispatchers.IO) {
        dao.deleteDocument(uri)
    }

    private fun queryFileMeta(uri: Uri): Pair<String, Long> {
        var name = "Document"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}
        return Pair(name, size)
    }

    private fun detectFileType(fileName: String, uri: Uri): String? {
        val lowerName = fileName.lowercase(Locale.ROOT)
        if (lowerName.endsWith(".pdf")) return "pdf"
        if (lowerName.endsWith(".txt")) return "txt"
        if (lowerName.endsWith(".epub")) return "epub"

        val mimeType = try {
            context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            null
        }
        return when {
            mimeType == "application/pdf" -> "pdf"
            mimeType == "text/plain" -> "txt"
            mimeType == "application/epub+zip" -> "epub"
            else -> null
        }
    }
}
