package com.nexus.launcher.reader.doc

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a user-imported document in the E-Ink Reader Library.
 */
@Entity(tableName = "nexus_documents")
data class DocumentRecord(
    @PrimaryKey val uri: String,
    val fileName: String,
    val fileType: String,
    val lastReadPosition: Int = 0,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val totalPages: Int = 0,
    val fileSize: Long = 0L,
    val collectionId: Long? = null,
    val displayName: String? = null,
    val coverPath: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis(),
    /** Time spent reading this document, in the foreground, summed across sessions. */
    val readingMillis: Long = 0L
) {
    val effectiveTitle: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: fileName
}
