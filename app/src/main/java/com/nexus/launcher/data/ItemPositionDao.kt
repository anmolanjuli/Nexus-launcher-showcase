package com.nexus.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemPositionDao {

    @Query("SELECT * FROM item_positions WHERE shape = :shape")
    fun getPositionsForShapeFlow(shape: String): Flow<List<ItemPosition>>

    @Query("SELECT * FROM item_positions")
    fun getAllPositionsFlow(): Flow<List<ItemPosition>>

    @Query("SELECT * FROM item_positions WHERE shape = :shape")
    suspend fun getPositionsForShape(shape: String): List<ItemPosition>

    @Query("SELECT * FROM item_positions WHERE itemId = :itemId AND shape = :shape LIMIT 1")
    suspend fun getPosition(itemId: Int, shape: String): ItemPosition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosition(position: ItemPosition)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPositions(positions: List<ItemPosition>)

    @Query("DELETE FROM item_positions WHERE itemId = :itemId")
    suspend fun deletePositionsForItem(itemId: Int)

    @Query("DELETE FROM item_positions WHERE itemId = :itemId AND shape = :shape")
    suspend fun deletePosition(itemId: Int, shape: String)

    @Query("DELETE FROM item_positions WHERE shape = :shape")
    suspend fun deleteAllForShape(shape: String)

    @Query("UPDATE item_positions SET page = page + :delta WHERE page >= :fromPage")
    suspend fun shiftPages(fromPage: Int, delta: Int)

    @Query("DELETE FROM item_positions WHERE page = :page")
    suspend fun deletePositionsOnPage(page: Int)

    @Query("""
        DELETE FROM item_positions
        WHERE itemId NOT IN (SELECT id FROM home_screen_items)
           OR itemId IN (SELECT id FROM home_screen_items WHERE containerId != -1 OR page < 0)
    """)
    suspend fun cleanupOrphans(): Int

    @Query("SELECT * FROM item_positions")
    suspend fun getAllPositionsDebug(): List<ItemPosition>
}
