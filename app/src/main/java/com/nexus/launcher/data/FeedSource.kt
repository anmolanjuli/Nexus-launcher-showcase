package com.nexus.launcher.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feed_sources",
    indices = [Index(value = ["url"], unique = true)]
)
data class FeedSource(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val url: String,
    val category: String,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastFetchedAt: Long = 0L,
    val lastError: String? = null
)
