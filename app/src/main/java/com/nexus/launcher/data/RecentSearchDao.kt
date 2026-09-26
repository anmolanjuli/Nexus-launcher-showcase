package com.nexus.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {
    @Query("SELECT MAX(id) as id, packageName, MAX(timestamp) as timestamp FROM recent_searches GROUP BY packageName ORDER BY timestamp DESC LIMIT 5")
    fun getRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE id NOT IN (SELECT id FROM recent_searches ORDER BY timestamp DESC LIMIT 50)")
    suspend fun pruneOldEntries()

    @Query("DELETE FROM recent_searches")
    suspend fun clearHistory()
}
