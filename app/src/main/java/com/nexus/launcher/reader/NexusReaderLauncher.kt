package com.nexus.launcher.reader

import android.content.Context
import android.content.Intent
import com.nexus.launcher.data.FeedArticle

/**
 * Launch router for opening articles inside the in-app Reader Mode instead of external browser.
 */
object NexusReaderLauncher {

    const val EXTRA_URL = "extra_reader_url"
    const val EXTRA_TITLE = "extra_reader_title"
    const val EXTRA_SOURCE_NAME = "extra_reader_source_name"
    const val EXTRA_IMAGE_URL = "extra_reader_image_url"
    const val EXTRA_PUBLISHED_AT = "extra_reader_published_at"

    fun openArticle(context: Context, article: FeedArticle) {
        val intent = Intent(context, NexusReaderActivity::class.java).apply {
            putExtra(EXTRA_URL, article.link)
            putExtra(EXTRA_TITLE, article.title)
            putExtra(EXTRA_SOURCE_NAME, article.sourceName)
            putExtra(EXTRA_IMAGE_URL, article.imageUrl ?: article.imageUrlFallback)
            putExtra(EXTRA_PUBLISHED_AT, article.publishedAt)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    fun openUrl(context: Context, url: String, title: String? = null, sourceName: String? = null) {
        val intent = Intent(context, NexusReaderActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            title?.let { putExtra(EXTRA_TITLE, it) }
            sourceName?.let { putExtra(EXTRA_SOURCE_NAME, it) }
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }
}
