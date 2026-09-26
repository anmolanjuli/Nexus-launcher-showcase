package com.nexus.launcher.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<FeedSource>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: FeedSource): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<FeedArticle>)

    @Query("SELECT * FROM feed_sources ORDER BY sortOrder ASC, addedAt ASC")
    fun getAllSourcesFlow(): Flow<List<FeedSource>>

    /** One-shot read for backup export/restore, which has no use for a Flow. */
    @Query("SELECT * FROM feed_sources ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getAllSourcesSnapshot(): List<FeedSource>

    @Query("SELECT * FROM feed_sources WHERE isEnabled = 1 ORDER BY sortOrder ASC, addedAt ASC")
    fun getEnabledSourcesFlow(): Flow<List<FeedSource>>

    @Query("SELECT * FROM feed_articles WHERE sourceId = :sourceId ORDER BY publishedAt DESC LIMIT 12")
    fun getArticlesForSource(sourceId: Int): Flow<List<FeedArticle>>

    @Query("SELECT a.* FROM feed_articles a INNER JOIN feed_sources s ON a.sourceId = s.id WHERE s.isEnabled = 1 ORDER BY a.publishedAt DESC, a.id DESC LIMIT 80")
    fun getAllEnabledArticlesFlow(): Flow<List<FeedArticle>>

    @Query("SELECT a.* FROM feed_articles a INNER JOIN feed_sources s ON a.sourceId = s.id WHERE s.isEnabled = 1 AND (a.title LIKE '%' || :query || '%' OR a.sourceName LIKE '%' || :query || '%') ORDER BY a.publishedAt DESC, a.id DESC LIMIT 40")
    fun searchArticlesFlow(query: String): Flow<List<FeedArticle>>

    @Query("SELECT COUNT(*) FROM feed_sources")
    suspend fun getSourceCount(): Int

    @Query("DELETE FROM feed_articles WHERE sourceId = :sourceId")
    suspend fun deleteArticlesForSource(sourceId: Int)

    @Query("DELETE FROM feed_articles WHERE cachedAt < :cutoffMs")
    suspend fun deleteArticlesOlderThan(cutoffMs: Long)

    @Update
    suspend fun updateSource(source: FeedSource)

    @Delete
    suspend fun deleteSource(source: FeedSource)

    @Query("UPDATE feed_sources SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSourceOrder(id: Int, sortOrder: Int)
}
