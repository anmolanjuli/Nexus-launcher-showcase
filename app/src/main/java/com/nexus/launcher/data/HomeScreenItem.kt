package com.nexus.launcher.data

import android.content.Intent
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "home_screen_items")
data class HomeScreenItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val packageName: String,
    val page: Int,
    val column: Int,
    val row: Int,
    val xFraction: Float = 0f,
    val yFraction: Float = 0f,
    val itemType: Int = 0,
    val appWidgetId: Int = -1,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val zIndex: Int = 0,
    val paddingEnabled: Boolean = true,
    @androidx.room.ColumnInfo(defaultValue = "-1")
    val containerId: Long = -1L,
    @androidx.room.ColumnInfo(defaultValue = "")
    val folderTitle: String = "",
    @androidx.room.ColumnInfo(defaultValue = "{}")
    val folderConfigJson: String = "{}",
    /** Widget provider class name (`ComponentName.className`); null for apps/folders or pre-v13 widgets. */
    val providerClassName: String? = null,
    /** Dead schema from the removed Minimal UI feature — the column still exists in Room v15
     *  (kept to avoid a destructive migration) and every row is 'classic'. Nothing reads it. */
    @androidx.room.ColumnInfo(defaultValue = "classic")
    val uiMode: String = "classic"
) {
    constructor(
        id: Long,
        packageName: String,
        page: Int,
        column: Int,
        row: Int,
        itemType: Int,
        containerId: Long,
        folderTitle: String
    ) : this(
        id = id.toInt(),
        packageName = packageName,
        page = page,
        column = column,
        row = row,
        itemType = itemType,
        containerId = containerId,
        folderTitle = folderTitle
    )
    /** Non-persisted launch payload for PWAs / web shortcuts (preserved across drag-drop). */
    @Ignore
    var launchIntent: Intent? = null

    /** Folder id used to load contained apps (supports home shortcuts to drawer folders). */
    fun resolveFolderContentsId(): Long {
        if (itemType != 1) return id.toLong()
        return try {
            val ref = org.json.JSONObject(folderConfigJson.ifBlank { "{}" })
                .optLong("referenceFolderId", -1L)
            if (ref > 0L) ref else id.toLong()
        } catch (_: Exception) {
            id.toLong()
        }
    }
}
