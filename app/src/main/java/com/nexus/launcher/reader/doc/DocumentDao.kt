package com.nexus.launcher.reader.doc

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for querying and modifying documents in the E-Ink Library.
 */
@Dao
interface DocumentDao {

    @Query("SELECT * FROM nexus_documents ORDER BY lastReadTimestamp DESC")
    fun getAllDocuments(): Flow<List<DocumentRecord>>

    @Query("SELECT * FROM nexus_documents WHERE uri = :uri LIMIT 1")
    suspend fun getDocument(uri: String): DocumentRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentRecord)

    @Query("UPDATE nexus_documents SET lastReadPosition = :position, lastReadTimestamp = :timestamp WHERE uri = :uri")
    suspend fun updateReadingPosition(uri: String, position: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE nexus_documents SET totalPages = :totalPages WHERE uri = :uri")
    suspend fun updateTotalPages(uri: String, totalPages: Int)

    @Query("UPDATE nexus_documents SET displayName = :displayName WHERE uri = :uri")
    suspend fun updateDisplayName(uri: String, displayName: String?)

    @Query("UPDATE nexus_documents SET collectionId = :collectionId WHERE uri = :uri")
    suspend fun updateCollection(uri: String, collectionId: Long?)

    @Query("UPDATE nexus_documents SET coverPath = :coverPath WHERE uri = :uri")
    suspend fun updateCoverPath(uri: String, coverPath: String?)

    @Query("UPDATE nexus_documents SET readingMillis = readingMillis + :millis WHERE uri = :uri")
    suspend fun addReadingTime(uri: String, millis: Long)

    /** PDF and EPUB documents other than [exceptUri]: what the free limit counts. Text is never counted. */
    @Query("SELECT COUNT(*) FROM nexus_documents WHERE fileType IN ('pdf', 'epub') AND uri != :exceptUri")
    suspend fun countLimitedDocuments(exceptUri: String): Int

    @Query("DELETE FROM nexus_documents WHERE uri = :uri")
    suspend fun deleteDocument(uri: String)
}
