package com.nexus.launcher.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "item_positions",
    primaryKeys = ["itemId", "shape"],
    indices = [Index(value = ["itemId"])]
)
data class ItemPosition(
    val itemId: Int,
    val shape: String,
    val page: Int,
    val column: Int,
    val row: Int,
    val xFraction: Float,
    val yFraction: Float,
    val spanX: Int,
    val spanY: Int,
    /**
     * True once the user has placed this item by hand in this shape (drag, resize, add). Only
     * such rows are stored: every other item's position in a non-base shape is derived live
     * from its base position (see ShapeLayoutDeriver), so it keeps tracking portrait changes.
     */
    @ColumnInfo(defaultValue = "0")
    val isManual: Boolean = false
) {
    companion object {
        const val SHAPE_PHONE_PORTRAIT = "phone_portrait"
        const val SHAPE_PHONE_LANDSCAPE = "phone_landscape"
        const val SHAPE_LARGE = "large"
    }
}
