package com.nexus.launcher.reader.doc

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for document collections.
 */
@Dao
interface CollectionDao {

    @Query("SELECT * FROM nexus_collections ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllCollections(): Flow<List<CollectionRecord>>

    @Query("SELECT * FROM nexus_collections WHERE id = :id LIMIT 1")
    suspend fun getCollection(id: Long): CollectionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionRecord): Long

    @Update
    suspend fun updateCollection(collection: CollectionRecord)

    @Query("DELETE FROM nexus_collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)
}
