package com.nexus.launcher.reader.doc

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.nexus.launcher.reader.NexusReaderThemeHelper
import com.nexus.launcher.theme.NexusColorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Custom View hosting an optimized WebView for rendering EPUB chapters with dynamic CSS theme injection,
 * local relative asset loading, pinch-to-zoom (1x–3x), continuous scroll, and horizontal page turn swipe.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class NexusEpubPageView(
    context: Context,
    private val scope: CoroutineScope,
    private val parser: NexusEpubParser,
    private var palette: NexusReaderThemeHelper.ReaderPalette,
    private var tokens: NexusColorTokens,
    private var readingMode: PdfReadingModeStore.Mode,
    private val onChapterChanged: (chapterIndex: Int, totalChapters: Int) -> Unit,
    private val onScrollPositionChanged: (chapterIndex: Int, scrollY: Int) -> Unit,
    private val onCenterTap: () -> Unit,
    private val onScrollHideToolbars: () -> Unit
) : FrameLayout(context) {

    private val dp = resources.displayMetrics.density
    private val webView: WebView
    var currentChapterIndex: Int = 0
        private set

    private var initialScrollOffset: Int = 0
    private var isRestoringScroll: Boolean = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onCenterTap()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (readingMode != PdfReadingModeStore.Mode.PAGE_TURN || e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dx) > abs(dy) && abs(dx) > 60 * dp && abs(velocityX) > 200 * dp) {
                if (dx < 0) {
                    goToNextChapter()
                } else {
                    goToPreviousChapter()
                }
                return true
            }
            return false
        }
    })

    init {
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        webView = object : WebView(context) {
            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                super.onScrollChanged(l, t, oldl, oldt)
                if (!isRestoringScroll) {
                    onScrollPositionChanged(currentChapterIndex, t)
                    if (t > oldt && (t - oldt) > 20 * dp) {
                        onScrollHideToolbars()
                    }
                }
            }
        }.apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(palette.bg)
            isVerticalScrollBarEnabled = readingMode == PdfReadingModeStore.Mode.CONTINUOUS_SCROLL
            isHorizontalScrollBarEnabled = false

            settings.apply {
                // A book is untrusted content. It is rendered from a file:// base so its own images
                // and fonts resolve, which means any script in it would run next to the app's
                // private files; EPUB text needs no script engine, so there is none. The two
                // cross-file-origin switches are already off by default and stated here so that
                // stays deliberate.
                javaScriptEnabled = false
                domStorageEnabled = false
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                defaultTextEncodingName = "UTF-8"
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (initialScrollOffset > 0) {
                        isRestoringScroll = true
                        view?.post {
                            view.scrollTo(0, initialScrollOffset)
                            initialScrollOffset = 0
                            isRestoringScroll = false
                        }
                    }
                }
            }

            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
        }

        addView(webView)
    }

    fun loadChapter(chapterIndex: Int, scrollOffset: Int = 0) {
        val total = parser.totalChapters
        if (total == 0) return
        val clampedIndex = chapterIndex.coerceIn(0, total - 1)
        currentChapterIndex = clampedIndex
        initialScrollOffset = scrollOffset

        scope.launch {
            val rawHtml = parser.readChapterHtml(clampedIndex)
            val chapter = parser.chapters.getOrNull(clampedIndex)
            val parentDir = chapter?.file?.parentFile ?: parser.opfDirectory
            val baseUrl = if (parentDir != null) "file://${parentDir.absolutePath}/" else null
            val themedHtml = NexusEpubThemeStyler.injectIntoHtml(rawHtml, palette, tokens)

            withContext(Dispatchers.Main) {
                webView.setBackgroundColor(palette.bg)
                webView.loadDataWithBaseURL(
                    baseUrl,
                    themedHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
                onChapterChanged(currentChapterIndex, total)
            }
        }
    }

    fun applyTheme(newPalette: NexusReaderThemeHelper.ReaderPalette, newTokens: NexusColorTokens) {
        palette = newPalette
        tokens = newTokens
        webView.setBackgroundColor(palette.bg)
        val currentScrollY = webView.scrollY
        loadChapter(currentChapterIndex, currentScrollY)
    }

    fun setReadingMode(mode: PdfReadingModeStore.Mode) {
        readingMode = mode
        webView.isVerticalScrollBarEnabled = mode == PdfReadingModeStore.Mode.CONTINUOUS_SCROLL
    }

    fun goToNextChapter(): Boolean {
        if (currentChapterIndex < parser.totalChapters - 1) {
            loadChapter(currentChapterIndex + 1, 0)
            return true
        }
        return false
    }

    fun goToPreviousChapter(): Boolean {
        if (currentChapterIndex > 0) {
            loadChapter(currentChapterIndex - 1, 0)
            return true
        }
        return false
    }

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
        removeAllViews()
    }
}
