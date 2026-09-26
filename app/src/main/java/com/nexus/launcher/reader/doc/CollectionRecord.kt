package com.nexus.launcher.reader.doc

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a user-defined collection/folder for documents.
 */
@Entity(tableName = "nexus_collections")
data class CollectionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorTag: String = "#B0BEC5",
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)
