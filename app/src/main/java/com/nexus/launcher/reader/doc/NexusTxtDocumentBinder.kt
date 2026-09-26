package com.nexus.launcher.reader.doc

import android.app.Activity
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlinx.coroutines.CoroutineScope
import com.nexus.launcher.R
import com.nexus.launcher.reader.ExtractedArticle
import com.nexus.launcher.reader.NexusReaderSkeletonView
import com.nexus.launcher.reader.NexusReaderThemeHelper
import com.nexus.launcher.reader.NexusReaderViewBinder

/**
 * Handles plain-text document loading, paragraph rendering, and scroll setup.
 *
 * Text arrives a chunk at a time. A paragraph is a TextView, and a text file is allowed to be 5MB
 * — tens of thousands of paragraphs — so laying every one of them out at once froze the reader and
 * then ran it out of memory. [CHUNK] paragraphs are laid out up front and the next chunk is added
 * as the end of the document comes into view, which is how far anyone can read at a time anyway.
 */
object NexusTxtDocumentBinder {

    private const val CHUNK = 300

    suspend fun loadAndBind(
        activity: Activity,
        scope: CoroutineScope,
        contentContainer: FrameLayout,
        skeletonView: NexusReaderSkeletonView,
        uri: Uri,
        docUriString: String,
        docTitle: String,
        palette: NexusReaderThemeHelper.ReaderPalette,
        startScrollOffset: Int,
        dp: Float,
        statusBarHeight: Int,
        onScroll: (scrollY: Int, totalHeight: Int) -> Unit,
        onTap: () -> Unit,
        onError: (String) -> Unit
    ) {
        val engine = NexusTxtRendererEngine(activity, uri)
        try {
            val paragraphs = engine.loadParagraphs()
            skeletonView.visibility = View.GONE

            var appendIfNearEnd: ((Int) -> Unit)? = null
            val scrollView = ScrollView(activity).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                setPadding(0, statusBarHeight + (56 * dp).toInt(), 0, (60 * dp).toInt())
                clipToPadding = false
                setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    val total = (getChildAt(0).height - height).coerceAtLeast(1)
                    onScroll(scrollY, total)
                    appendIfNearEnd?.invoke(scrollY)
                }
                setOnClickListener { onTap() }
            }

            val maxWidth = (680 * dp).toInt().coerceAtMost(activity.resources.displayMetrics.widthPixels)
            val contentWrapper = FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL)
            }

            val articleLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                val padH = (20 * dp).toInt()
                setPadding(padH, (16 * dp).toInt(), padH, (24 * dp).toInt())
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener { onTap() }
            }

            val syntheticArticle = ExtractedArticle(
                url = docUriString,
                title = docTitle,
                elements = paragraphs.take(CHUNK),
                readingTimeMinutes = (paragraphs.size / 3).coerceAtLeast(1)
            )

            NexusReaderViewBinder.bind(
                context = activity,
                scope = scope,
                container = articleLayout,
                article = syntheticArticle,
                palette = palette,
                dp = dp
            )

            contentWrapper.addView(articleLayout)
            scrollView.addView(contentWrapper)
            contentContainer.addView(scrollView)

            var rendered = minOf(CHUNK, paragraphs.size)
            appendIfNearEnd = { scrollY ->
                val remaining = articleLayout.height - scrollY - scrollView.height
                if (rendered < paragraphs.size && remaining < scrollView.height * 2) {
                    val next = minOf(rendered + CHUNK, paragraphs.size)
                    for (i in rendered until next) {
                        articleLayout.addView(
                            NexusReaderViewBinder.paragraphView(activity, paragraphs[i].text, palette, dp)
                        )
                    }
                    rendered = next
                }
            }

            scrollView.post {
                if (startScrollOffset > 0) {
                    scrollView.scrollTo(0, startScrollOffset)
                }
            }

        } catch (e: NexusTxtRendererEngine.TxtFileTooLargeException) {
            skeletonView.visibility = View.GONE
            onError(activity.getString(R.string.nexus_doc_error_too_large))
        } catch (e: Exception) {
            skeletonView.visibility = View.GONE
            onError(activity.getString(R.string.nexus_doc_error_corrupted))
        }
    }
}
