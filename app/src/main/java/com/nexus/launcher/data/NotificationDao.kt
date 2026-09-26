package com.nexus.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    /** Newest first; the sheet groups them by app itself. */
    @Query("SELECT * FROM notification_records WHERE postedAt >= :since ORDER BY postedAt DESC LIMIT 500")
    fun observeSince(since: Long): Flow<List<NotificationRecord>>

    @Query("SELECT * FROM notification_records WHERE key = :key AND clearedAt = 0 LIMIT 1")
    suspend fun findPosted(key: String): NotificationRecord?

    @Insert
    suspend fun insert(record: NotificationRecord): Long

    @Query("UPDATE notification_records SET clearedAt = :at WHERE key = :key AND clearedAt = 0")
    suspend fun markCleared(key: String, at: Long)

    /** On a restart nothing we stored is posted any more until the listener says otherwise. */
    @Query("UPDATE notification_records SET clearedAt = :at WHERE clearedAt = 0")
    suspend fun markAllCleared(at: Long)

    /**
     * Clears every stored-as-posted row except [keys] — the ones the shade still holds, which
     * keep their row (a restart must not turn each of them into a cleared duplicate).
     */
    @Query("UPDATE notification_records SET clearedAt = :at WHERE clearedAt = 0 AND `key` NOT IN (:keys)")
    suspend fun markClearedExcept(keys: List<String>, at: Long)

    /** Drops the still-posted row for [key], so an update in place replaces it, not adds a copy. */
    @Query("DELETE FROM notification_records WHERE `key` = :key AND clearedAt = 0")
    suspend fun deletePosted(key: String)

    @Query("UPDATE notification_records SET clearedAt = 0 WHERE key IN (:keys)")
    suspend fun markPosted(keys: List<String>)

    @Query("DELETE FROM notification_records WHERE postedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM notification_records WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM notification_records")
    suspend fun clear()
}
