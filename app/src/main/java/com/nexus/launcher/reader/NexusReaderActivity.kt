package com.nexus.launcher.reader

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.feed.FeedBookmarkStore
import com.nexus.launcher.feed.NexusFeedEInkCoordinator
import com.nexus.launcher.feed.NexusFeedImageLoader
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Full-screen in-app News Reader activity displaying distraction-free extracted article content.
 * Supports theme adaptation, E-Ink Paper physics, skeleton loading, and fallback WebView rendering.
 */
class NexusReaderActivity : AppCompatActivity() {

    private val dp get() = resources.displayMetrics.density
    private var articleUrl: String = ""
    private var articleTitle: String = ""
    private var sourceName: String = ""
    private var heroImageUrl: String? = null

    private var currentMode = NexusReaderThemeHelper.ReaderDisplayMode.READER

    /** The stand-in web page is hidden until its paper styling has been applied. */
    private var awaitingWebViewPaper = false

    /** Clean article extraction deadline before attempting DOM extraction or fallback. */
    private val EXTRACT_TIMEOUT_MS = 3_800L
    private lateinit var tokens: NexusColorTokens
    private lateinit var palette: NexusReaderThemeHelper.ReaderPalette

    private lateinit var rootLayout: FrameLayout
    private lateinit var toolbarBuilder: NexusReaderToolbarBuilder
    private lateinit var scrollProgress: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var articleContentLayout: LinearLayout
    private lateinit var skeletonView: NexusReaderSkeletonView
    private lateinit var webView: WebView

    private var currentArticle: ExtractedArticle? = null
    private var einkRefresh: com.nexus.launcher.feed.NexusEInkRefreshGesture? = null

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        einkRefresh?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        articleUrl = intent.getStringExtra(NexusReaderLauncher.EXTRA_URL) ?: ""
        articleTitle = intent.getStringExtra(NexusReaderLauncher.EXTRA_TITLE) ?: ""
        sourceName = intent.getStringExtra(NexusReaderLauncher.EXTRA_SOURCE_NAME) ?: ""
        heroImageUrl = intent.getStringExtra(NexusReaderLauncher.EXTRA_IMAGE_URL)

        if (articleUrl.isBlank()) {
            finish()
            return
        }

        tokens = ThemeObserver.currentTokens(this)
        if (NexusFeedEInkCoordinator.isEInkMode(this)) {
            currentMode = NexusReaderThemeHelper.ReaderDisplayMode.EINK
        }
        palette = NexusReaderThemeHelper.resolvePalette(this, tokens, currentMode == NexusReaderThemeHelper.ReaderDisplayMode.EINK)
        NexusReaderThemeHelper.applyWindowChrome(this, window, palette)

        buildUi()
        loadArticle()
    }

    private fun buildUi() {
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        NexusReaderThemeHelper.applyBackground(rootLayout, palette)
        NexusReaderThemeHelper.applyEInkSaturationLayer(rootLayout, palette.isEInk)
        einkRefresh = com.nexus.launcher.feed.NexusEInkRefreshGesture(rootLayout) { palette.isEInk }

        val statusBarHeight = NexusReaderUiHelper.getStatusBarHeight(window, resources, dp)

        // Content Scroll View
        scrollView = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, statusBarHeight + (56 * dp).toInt(), 0, (40 * dp).toInt())
            clipToPadding = true
            setOnScrollChangeListener { _, _, _, _, _ ->
                updateScrollProgress()
            }
        }

        val maxWidth = (680 * dp).toInt().coerceAtMost(resources.displayMetrics.widthPixels)
        val contentWrapper = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL)
        }

        articleContentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (20 * dp).toInt()
            setPadding(padH, (16 * dp).toInt(), padH, (24 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        skeletonView = NexusReaderSkeletonView(this)
        contentWrapper.addView(articleContentLayout)
        contentWrapper.addView(skeletonView)
        scrollView.addView(contentWrapper)
        rootLayout.addView(scrollView)

        // Integrated Fallback WebView
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                topMargin = statusBarHeight + (56 * dp).toInt()
            }
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
        }

        NexusReaderWebViewHelper.attachClients(
            webView = webView,
            rootLayout = rootLayout,
            isEInk = { palette.isEInk },
            onPageReady = {
                scrollProgress.visibility = View.GONE
                if (awaitingWebViewPaper) {
                    awaitingWebViewPaper = false
                    skeletonView.visibility = View.GONE
                    webView.animate().alpha(1f).setDuration(140).start()
                }
            },
            onProgress = { newProgress ->
                if (currentMode == NexusReaderThemeHelper.ReaderDisplayMode.ORIGINAL) {
                    scrollProgress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                    scrollProgress.progress = newProgress
                }
            },
            onDomExtractReady = {
                if (currentArticle == null) {
                    tryDomExtraction()
                }
            }
        )
        rootLayout.addView(webView)

        // Permanent status bar shelf ensuring scrolling article never bleeds into notification panel
        val statusBarShelf = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                statusBarHeight,
                Gravity.TOP
            )
            background = ColorDrawable(palette.surface)
        }
        rootLayout.addView(statusBarShelf)

        // Top Toolbar
        toolbarBuilder = NexusReaderToolbarBuilder(
            context = this,
            onBackClick = { finish() },
            onModeSelected = { switchDisplayMode(it) },
            onBookmarkClick = { NexusReaderUiHelper.handleBookmarkToggle(this, articleUrl, toolbarBuilder) },
            onShareClick = { NexusReaderUiHelper.handleShare(this, articleTitle, articleUrl) },
            onBrowserClick = { NexusFeedImageLoader.launchCustomTab(this, articleUrl) }
        )
        val toolbarView = toolbarBuilder.build(
            statusBarHeight = statusBarHeight,
            palette = palette,
            sourceName = sourceName,
            initialMode = currentMode,
            isBookmarked = FeedBookmarkStore.isBookmarked(this, articleUrl)
        )
        rootLayout.addView(toolbarView)

        // Progress line below toolbar
        scrollProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            progressDrawable = ColorDrawable(if (palette.isEInk) palette.textPrimary else tokens.accent)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (2 * dp).toInt()).apply {
                topMargin = statusBarHeight + (54 * dp).toInt()
            }
        }
        rootLayout.addView(scrollProgress)

        NexusReaderUiHelper.setupWindowInsets(
            rootLayout = rootLayout,
            statusBarShelf = statusBarShelf,
            toolbarBuilder = toolbarBuilder,
            scrollView = scrollView,
            webView = webView,
            scrollProgress = scrollProgress,
            dp = dp
        )

        setContentView(rootLayout)
    }

    private fun switchDisplayMode(newMode: NexusReaderThemeHelper.ReaderDisplayMode) {
        if (currentMode == newMode) return
        currentMode = newMode
        palette = NexusReaderThemeHelper.resolvePalette(this, tokens, currentMode == NexusReaderThemeHelper.ReaderDisplayMode.EINK)
        NexusReaderThemeHelper.applyWindowChrome(this, window, palette)
        NexusReaderThemeHelper.applyBackground(rootLayout, palette)
        NexusReaderThemeHelper.applyEInkSaturationLayer(rootLayout, palette.isEInk)

        scrollProgress.progressDrawable = ColorDrawable(if (palette.isEInk) palette.textPrimary else tokens.accent)
        toolbarBuilder.updatePalette(palette, currentMode)

        val article = currentArticle
        if (newMode == NexusReaderThemeHelper.ReaderDisplayMode.ORIGINAL) {
            scrollView.visibility = View.GONE
            webView.visibility = View.VISIBLE
            applyWebViewEInkStyling(false)
            if (webView.url.isNullOrBlank()) webView.loadUrl(articleUrl)
        } else {
            if (article != null) {
                webView.visibility = View.GONE
                scrollView.visibility = View.VISIBLE
                articleContentLayout.visibility = View.VISIBLE
                renderArticle(article)
            } else {
                scrollView.visibility = View.GONE
                webView.visibility = View.VISIBLE
                applyWebViewEInkStyling(newMode == NexusReaderThemeHelper.ReaderDisplayMode.EINK)
                if (webView.url.isNullOrBlank()) webView.loadUrl(articleUrl)
            }
        }
    }

    private fun loadArticle() {
        skeletonView.build(palette.surfaceRaised, palette.isEInk)
        skeletonView.visibility = View.VISIBLE
        articleContentLayout.visibility = View.GONE

        lifecycleScope.launch {
            val cached = NexusReaderCache.get(this@NexusReaderActivity, articleUrl)
            if (cached != null) {
                currentArticle = cached
                skeletonView.visibility = View.GONE
                if (currentMode != NexusReaderThemeHelper.ReaderDisplayMode.ORIGINAL) {
                    articleContentLayout.visibility = View.VISIBLE
                    scrollView.visibility = View.VISIBLE
                    webView.visibility = View.GONE
                    renderArticle(cached)
                }
                return@launch
            }

            // Warm WebView concurrently so if extraction fails or times out, page is already loaded
            if (webView.url.isNullOrBlank()) {
                webView.loadUrl(articleUrl)
            }

            val extracted = withTimeoutOrNull(EXTRACT_TIMEOUT_MS) {
                NexusReaderContentExtractor.extract(articleUrl, articleTitle, heroImageUrl)
            }

            if (extracted != null) {
                currentArticle = extracted
                NexusReaderCache.put(this@NexusReaderActivity, extracted)
                skeletonView.visibility = View.GONE
                if (currentMode != NexusReaderThemeHelper.ReaderDisplayMode.ORIGINAL) {
                    webView.stopLoading()
                    webView.visibility = View.GONE
                    articleContentLayout.visibility = View.VISIBLE
                    scrollView.visibility = View.VISIBLE
                    renderArticle(extracted)
                }
            } else {
                if (currentArticle != null) return@launch
                // Background extraction timed out or failed. Attempt DOM extraction from preloaded WebView.
                tryDomExtraction { domSuccess ->
                    if (!domSuccess && currentArticle == null) {
                        scrollView.visibility = View.GONE
                        webView.visibility = View.VISIBLE
                        awaitingWebViewPaper = palette.isEInk
                        if (!awaitingWebViewPaper) {
                            skeletonView.visibility = View.GONE
                            webView.animate().alpha(1f).setDuration(140).start()
                        } else {
                            // E-Ink fallback timeout: ensure skeleton is never stuck if paper CSS callback is delayed
                            lifecycleScope.launch {
                                delay(1500L)
                                if (awaitingWebViewPaper) {
                                    awaitingWebViewPaper = false
                                    skeletonView.visibility = View.GONE
                                    webView.animate().alpha(1f).setDuration(140).start()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyWebViewEInkStyling(isEInk: Boolean) {
        NexusReaderWebViewHelper.applyWebViewEInkStyling(rootLayout, webView, isEInk)
    }

    private fun tryDomExtraction(onResult: ((Boolean) -> Unit)? = null) {
        if (currentArticle != null) {
            onResult?.invoke(true)
            return
        }
        NexusReaderWebViewHelper.extractFromDom(
            webView = webView,
            articleUrl = articleUrl,
            articleTitle = articleTitle,
            sourceName = sourceName,
            heroImageUrl = heroImageUrl
        ) { article ->
            if (currentArticle != null) return@extractFromDom
            currentArticle = article
            lifecycleScope.launch {
                NexusReaderCache.put(this@NexusReaderActivity, article)
            }
            if (currentMode != NexusReaderThemeHelper.ReaderDisplayMode.ORIGINAL) {
                webView.stopLoading()
                webView.visibility = View.GONE
                skeletonView.visibility = View.GONE
                scrollView.visibility = View.VISIBLE
                articleContentLayout.visibility = View.VISIBLE
                renderArticle(article)
            }
            onResult?.invoke(true)
        }
    }

    private fun renderArticle(article: ExtractedArticle) {
        NexusReaderViewBinder.bind(
            context = this,
            scope = lifecycleScope,
            container = articleContentLayout,
            article = article,
            palette = palette,
            dp = dp
        )
    }

    private fun updateScrollProgress() {
        if (currentMode == NexusReaderThemeHelper.ReaderDisplayMode.ORIGINAL) return
        scrollProgress.progress = NexusReaderUiHelper.calculateScrollProgress(scrollView)
    }
}
