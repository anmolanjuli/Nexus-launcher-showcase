package com.nexus.launcher.reader

/**
 * Data model representing an article whose readable content and metadata have been isolated.
 */
data class ExtractedArticle(
    val url: String,
    val title: String,
    val author: String? = null,
    val publishDate: String? = null,
    val heroImageUrl: String? = null,
    val elements: List<ArticleElement> = emptyList(),
    val wordCount: Int = 0,
    val readingTimeMinutes: Int = 1,
    val extractedAt: Long = System.currentTimeMillis()
)
