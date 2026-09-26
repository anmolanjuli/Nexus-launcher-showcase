package com.nexus.launcher.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feed_articles",
    foreignKeys = [
        ForeignKey(
            entity = FeedSource::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["publishedAt"])
    ]
)
data class FeedArticle(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sourceId: Int,
    val title: String,
    val link: String,
    val imageUrl: String? = null,
    val imageUrlFallback: String? = null,
    val sourceName: String,
    val publishedAt: Long,
    val cachedAt: Long = System.currentTimeMillis()
)
