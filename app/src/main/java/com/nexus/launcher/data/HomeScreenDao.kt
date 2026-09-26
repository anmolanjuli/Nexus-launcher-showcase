package com.nexus.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeScreenDao {

    @Query("SELECT * FROM home_screen_items WHERE containerId = -1 ORDER BY page, row, column")
    fun getAllItems(): Flow<List<HomeScreenItem>>

    @Query("SELECT * FROM home_screen_items WHERE containerId != -1 ORDER BY `row` ASC, `column` ASC")
    fun getAllFolderContents(): Flow<List<HomeScreenItem>>

    @Query("SELECT * FROM home_screen_items WHERE page = :page AND containerId = -1")
    suspend fun getItemsForPage(page: Int): List<HomeScreenItem>

    @Query("SELECT * FROM home_screen_items WHERE containerId = :folderId ORDER BY `row` ASC, `column` ASC")
    fun getItemsInFolder(folderId: Long): Flow<List<HomeScreenItem>>

    @Query("SELECT * FROM home_screen_items WHERE containerId = :folderId ORDER BY `row` ASC, `column` ASC")
    suspend fun getItemsInFolderSync(folderId: Long): List<HomeScreenItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: HomeScreenItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemAndGetId(item: HomeScreenItem): Long

    @Query("DELETE FROM home_screen_items WHERE packageName = :packageName AND page = :page")
    suspend fun removeItem(packageName: String, page: Int)

    @Query("SELECT COUNT(*) FROM home_screen_items WHERE page = :page AND column = :col AND row = :row AND containerId = -1 AND NOT (appWidgetId = -1 AND itemType IN (3, 4))")
    suspend fun isCellOccupied(page: Int, col: Int, row: Int): Int

    @Query("""SELECT COUNT(*) FROM home_screen_items
              WHERE page = :page
              AND containerId = -1
              AND ABS(xFraction - :xF) < 0.1
              AND ABS(yFraction - :yF) < 0.1""")
    suspend fun isFractionOccupied(page: Int, xF: Float, yF: Float): Int


    @Query("DELETE FROM home_screen_items WHERE id = :id")
    suspend fun removeItemByIdRaw(id: Int)

    /**
     * TEMPORARY — wrap every id-delete so Mosaic data-loss logs include a stack.
     * Remove with [com.nexus.launcher.ui.canvas.HomeGridDropDiag].
     */
    @androidx.room.Transaction
    suspend fun removeItemById(id: Int) {
        val victim = getItemById(id)
        android.util.Log.d(
            "HomeGridDropDiag",
            "DELETE source=dao.removeItemById id=$id " +
                "victim=${com.nexus.launcher.ui.canvas.HomeGridDropDiag.itemBrief(victim)}"
        )
        android.util.Log.d(
            "HomeGridDropDiag",
            "DELETE_STACK source=dao.removeItemById id=$id\n" +
                android.util.Log.getStackTraceString(Throwable())
                    .lineSequence().take(14).joinToString("\n")
        )
        removeItemByIdRaw(id)
    }

    @androidx.room.Query("DELETE FROM home_screen_items WHERE id = :folderId OR containerId = :folderId")
    suspend fun deleteFolderAndContents(folderId: Long)

    @androidx.room.Query("UPDATE home_screen_items SET folderTitle = :title, folderConfigJson = :configJson WHERE id = :folderId")
    suspend fun updateFolderConfig(folderId: Long, title: String, configJson: String)

    @Query("SELECT id FROM home_screen_items WHERE packageName = :packageName AND page = :page LIMIT 1")
    suspend fun findItemId(packageName: String, page: Int): Int?

    @Query("SELECT id FROM home_screen_items WHERE packageName = :packageName LIMIT 1")
    suspend fun findItemIdAnyPage(packageName: String): Int?

    @Query("DELETE FROM home_screen_items WHERE packageName = :packageName")
    suspend fun removeAllByPackage(packageName: String)

    @Query("DELETE FROM home_screen_items WHERE page = :page")
    suspend fun deleteItemsOnPage(page: Int)

    @Query("DELETE FROM home_screen_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM home_screen_items WHERE page > :page AND containerId = -1")
    suspend fun getItemsAbovePage(page: Int): List<HomeScreenItem>

    @Query("SELECT * FROM home_screen_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): HomeScreenItem?

    @Update
    suspend fun updateItem(item: HomeScreenItem)

    @Query("UPDATE home_screen_items SET page = :newPage WHERE id = :id")
    suspend fun updateItemPage(id: Int, newPage: Int)

    @Query("UPDATE home_screen_items SET page = :page, xFraction = :xFraction, yFraction = :yFraction, column = :column, row = :row WHERE id = :id")
    suspend fun updateItemPosition(id: Int, page: Int, xFraction: Float, yFraction: Float, column: Int, row: Int)

    @Query("UPDATE home_screen_items SET spanX = :spanX, spanY = :spanY, xFraction = :xFraction, yFraction = :yFraction, column = :column, row = :row WHERE id = :id")
    suspend fun updateWidgetBounds(id: Int, spanX: Int, spanY: Int, xFraction: Float, yFraction: Float, column: Int, row: Int)

    @Query("UPDATE home_screen_items SET zIndex = :zIndex WHERE id = :id")
    suspend fun updateWidgetZIndex(id: Int, zIndex: Int)



    // DIAGNOSTIC — remove after debugging
    @Query("SELECT * FROM home_screen_items WHERE containerId = -1 ORDER BY page, row, column")
    suspend fun getAllItemsDebug(): List<HomeScreenItem>

    /** All rows including folder members — for backup export only. */
    @Query("SELECT * FROM home_screen_items ORDER BY page, containerId, row, column")
    suspend fun getAllItemsForExportSync(): List<HomeScreenItem>

    @Query("SELECT packageName FROM home_screen_items WHERE containerId != -1")
    suspend fun getFolderedPackageNames(): List<String>

    /** The `page` of one row, used to tell which space a folder belongs to (-2 = drawer). */
    @Query("SELECT page FROM home_screen_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemPage(itemId: Long): Int?

    /**
     * Packages inside a **drawer** folder — the suspend counterpart of
     * [getFolderedPackageNamesFlow], for the app picker rather than the drawer list.
     */
    @Query(
        """
        SELECT m.packageName FROM home_screen_items m
        INNER JOIN home_screen_items f ON f.id = m.containerId
        WHERE f.page = -2 AND f.itemType = 1
        """
    )
    suspend fun getDrawerFolderedPackageNamesSync(): List<String>

    /**
     * Packages inside a **home-screen** folder. The mirror of
     * [getDrawerFolderedPackageNamesSync]: the two spaces are independent, so an app can sit in
     * a home-screen folder and a drawer folder at once and each picker should only hide what its
     * own space already holds.
     */
    @Query(
        """
        SELECT m.packageName FROM home_screen_items m
        INNER JOIN home_screen_items f ON f.id = m.containerId
        WHERE f.page != -2 AND f.itemType = 1
        """
    )
    suspend fun getHomeFolderedPackageNamesSync(): List<String>

    /**
     * Packages that live inside a **drawer** folder (container is `page = -2, itemType = 1`) —
     * drives the drawer's own foldered-app hiding. Deliberately narrower than
     * [getFolderedPackageNames]: an app placed in a *home-screen* folder must still appear in
     * the drawer, exactly like an app placed loose on the home screen does.
     */
    @Query(
        """
        SELECT m.packageName FROM home_screen_items m
        INNER JOIN home_screen_items f ON f.id = m.containerId
        WHERE f.page = -2 AND f.itemType = 1
        """
    )
    fun getFolderedPackageNamesFlow(): Flow<List<String>>

    /**
     * Rows whose containerId points at a missing parent (folder was deleted without cascade).
     * Diagnostic / report-only — do not purge without explicit confirmation.
     */
    @Query(
        """
        SELECT COUNT(*) FROM home_screen_items
        WHERE containerId != -1
          AND NOT EXISTS (
            SELECT 1 FROM home_screen_items AS f WHERE f.id = home_screen_items.containerId
          )
        """
    )
    suspend fun countOrphanedFolderMembers(): Int

    @Query(
        """
        SELECT * FROM home_screen_items
        WHERE containerId != -1
          AND NOT EXISTS (
            SELECT 1 FROM home_screen_items AS f WHERE f.id = home_screen_items.containerId
          )
        """
    )
    suspend fun getOrphanedFolderMembers(): List<HomeScreenItem>

    /** Drawer group folders only (page = -2, itemType = 1). Used to avoid subscribing to all home items in filteredDrawerItems. */
    @Query("SELECT * FROM home_screen_items WHERE page = -2 AND itemType = 1 AND containerId = -1")
    fun getDrawerFolders(): Flow<List<HomeScreenItem>>
}
