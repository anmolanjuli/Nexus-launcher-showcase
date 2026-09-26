package com.nexus.launcher.reader.doc

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.reader.NexusReaderSkeletonView
import com.nexus.launcher.reader.NexusReaderThemeHelper
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Full-screen E-Ink Document Reader activity supporting PDF (continuous vertical scroll or horizontal page turn),
 * EPUB (spine, TOC, full theme adaptation, pinch zoom), and TXT files with Room position tracking.
 */
class NexusDocumentReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "extra_doc_uri"
        const val EXTRA_TITLE = "extra_doc_title"
        const val EXTRA_TYPE = "extra_doc_type"

        fun start(context: Context, uri: String, title: String, type: String) {
            val intent = Intent(context, NexusDocumentReaderActivity::class.java).apply {
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TYPE, type)
            }
            context.startActivity(intent)
        }
    }

    private val dp get() = resources.displayMetrics.density
    private var docUriString: String = ""
    private var docTitle: String = ""
    private var docType: String = ""

    private val isPdf: Boolean
        get() = docType.contains("pdf", ignoreCase = true) || docUriString.endsWith(".pdf", ignoreCase = true)

    private val isEpub: Boolean
        get() = docType.contains("epub", ignoreCase = true) || docUriString.endsWith(".epub", ignoreCase = true)

    private var currentStatusBarHeight: Int = 0
    private var currentNavBarHeight: Int = 0

    private lateinit var repository: DocumentLibraryRepository
    private lateinit var palette: NexusReaderThemeHelper.ReaderPalette
    private lateinit var rootLayout: FrameLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var statusBarShelf: View
    private lateinit var toolbarBuilder: NexusDocumentToolbarBuilder
    private lateinit var immersiveHelper: NexusDocumentImmersiveHelper
    private lateinit var skeletonView: NexusReaderSkeletonView

    private var pdfCoordinator: NexusPdfReaderCoordinator? = null
    private var epubBinder: NexusEpubDocumentBinder? = null
    private var lastRecordedPosition: Int = 0
    private var lastRecordedTotal: Int = 0
    private var sessionTracker: ReadingSessionTracker? = null
    private var einkRefresh: com.nexus.launcher.feed.NexusEInkRefreshGesture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        docUriString = intent.getStringExtra(EXTRA_URI) ?: ""
        docTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Document"
        docType = intent.getStringExtra(EXTRA_TYPE) ?: "pdf"

        if (docUriString.isBlank()) {
            finish()
            return
        }

        // No paywall here: the Library's limit is on adding a document, never on opening one
        // (DocumentLibraryRepository.FREE_DOCUMENT_LIMIT).

        repository = DocumentLibraryRepository(this)
        sessionTracker = ReadingSessionTracker(repository, docUriString)
        val tokens = ThemeObserver.currentTokens(this)
        palette = NexusReaderThemeHelper.resolvePalette(this, tokens, forcedEInk = false)
        NexusReaderThemeHelper.applyWindowChrome(this, window, palette)

        buildUi()
        loadDocument()
    }

    fun getActiveFileBackground(): Int {
        return when {
            isPdf -> {
                val theme = pdfCoordinator?.currentPdfTheme ?: PdfThemeModeStore.getTheme(this, docUriString, palette.isDarkPaper)
                PdfThemeFilterApplier.surfaceColor(theme, palette.bg)
            }
            else -> palette.bg
        }
    }

    private fun buildUi() {
        val initialFileBg = getActiveFileBackground()
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(initialFileBg)
        }
        window.decorView.setBackgroundColor(initialFileBg)
        NexusReaderThemeHelper.applyEInkSaturationLayer(rootLayout, palette.isEInk)

        currentStatusBarHeight = NexusDocumentInsetsHelper.getStatusBarHeight(this, dp)
        currentNavBarHeight = NexusDocumentInsetsHelper.getNavigationBarHeight(this, dp)

        contentContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = currentStatusBarHeight
                bottomMargin = currentNavBarHeight
            }
        }
        rootLayout.addView(contentContainer)

        statusBarShelf = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                currentStatusBarHeight,
                Gravity.TOP
            )
            background = ColorDrawable(initialFileBg)
        }
        rootLayout.addView(statusBarShelf)

        skeletonView = NexusReaderSkeletonView(this).apply {
            build(palette.surfaceRaised, palette.isEInk)
            setPadding((20 * dp).toInt(), (56 * dp).toInt(), (20 * dp).toInt(), 0)
        }
        contentContainer.addView(skeletonView)

        toolbarBuilder = NexusDocumentToolbarBuilder(
            context = this,
            onBackClick = { finish() },
            onCloseClick = { finish() },
            onReadingModeChange = { newMode -> switchMode(newMode) },
            onThemeChange = { newTheme ->
                pdfCoordinator?.switchPdfTheme(newTheme)
                val newBg = PdfThemeFilterApplier.surfaceColor(newTheme, palette.bg)
                immersiveHelper.updateBackground(newBg)
            },
            onFitModeChange = { newFit -> pdfCoordinator?.switchPdfFitMode(newFit) },
            onTocClick = { epubBinder?.showTableOfContents() }
        )
        immersiveHelper = NexusDocumentImmersiveHelper(
            activity = this,
            rootLayout = rootLayout,
            statusBarShelf = statusBarShelf,
            palette = palette,
            toolbarBuilder = toolbarBuilder,
            getFileBackground = { getActiveFileBackground() }
        )

        if (isPdf) {
            initPdfCoordinator()
        }

        rebuildTopToolbar()
        val bottomToolbar = toolbarBuilder.buildBottomToolbar(
            navigationBarHeight = currentNavBarHeight,
            palette = palette,
            initialStatus = if (isPdf || isEpub) getString(R.string.nexus_reader_loading_article) else getString(R.string.nexus_doc_reading_status)
        )
        rootLayout.addView(bottomToolbar)
        toolbarBuilder.immersiveStatus?.let { rootLayout.addView(it) }

        NexusDocumentInsetsHelper.setupInsetsListener(
            rootLayout = rootLayout,
            contentContainer = contentContainer,
            statusBarShelf = statusBarShelf,
            toolbarBuilder = toolbarBuilder,
            dp = dp
        ) { sb, nb ->
            currentStatusBarHeight = sb
            currentNavBarHeight = nb
        }

        setContentView(rootLayout)
        einkRefresh = com.nexus.launcher.feed.NexusEInkRefreshGesture(rootLayout) { palette.isEInk }
    }

    private fun initPdfCoordinator() {
        val coord = NexusPdfReaderCoordinator(
            activity = this,
            scope = lifecycleScope,
            repository = repository,
            contentContainer = contentContainer,
            toolbarBuilder = toolbarBuilder,
            immersiveHelper = immersiveHelper,
            palette = palette,
            docUriString = docUriString,
            dp = dp,
            onReady = { skeletonView.visibility = View.GONE },
            onError = { msg ->
                skeletonView.visibility = View.GONE
                showError(msg)
            }
        )
        coord.initPreferences()
        pdfCoordinator = coord
        immersiveHelper.updateBackground(getActiveFileBackground())
    }

    private fun rebuildTopToolbar() {
        if (toolbarBuilder.areToolbarsVisible && ::rootLayout.isInitialized) {
            val oldTop = try { toolbarBuilder.topToolbar } catch (_: Exception) { null }
            if (oldTop != null) rootLayout.removeView(oldTop)
        }
        val currentMode = pdfCoordinator?.currentPdfMode ?: PdfReadingModeStore.getMode(this)
        val newTop = toolbarBuilder.buildTopToolbar(
            statusBarHeight = currentStatusBarHeight,
            palette = palette,
            documentTitle = docTitle,
            currentPdfMode = if (isPdf || isEpub) currentMode else null,
            currentPdfTheme = if (isPdf) pdfCoordinator?.currentPdfTheme else null,
            currentPdfFitMode = if (isPdf) pdfCoordinator?.currentPdfFitMode else null,
            showToc = isEpub,
            isEpub = isEpub
        )
        rootLayout.addView(newTop)
    }

    private fun switchMode(newMode: PdfReadingModeStore.Mode) {
        if (isPdf) {
            pdfCoordinator?.switchPdfMode(newMode, currentStatusBarHeight, currentNavBarHeight)
        } else if (isEpub) {
            PdfReadingModeStore.setMode(this, newMode)
            epubBinder?.setReadingMode(newMode)
        }
        rebuildTopToolbar()
    }

    private fun loadDocument() {
        val uri = Uri.parse(docUriString)
        lifecycleScope.launch {
            val record = repository.getDocument(docUriString)
            val initialPosition = record?.lastReadPosition ?: 0
            lastRecordedPosition = initialPosition

            when {
                isPdf -> pdfCoordinator?.loadPdf(uri, initialPosition, currentStatusBarHeight, currentNavBarHeight)
                isEpub -> loadEpubDocument(uri, initialPosition)
                else -> loadTxtDocument(uri, initialPosition)
            }
        }
    }

    private fun loadEpubDocument(uri: Uri, initialPosition: Int) {
        val tokens = ThemeObserver.currentTokens(this)
        val mode = PdfReadingModeStore.getMode(this)
        val binder = NexusEpubDocumentBinder(
            context = this,
            scope = lifecycleScope,
            contentContainer = contentContainer,
            uri = uri,
            docUriString = docUriString,
            repository = repository,
            palette = palette,
            tokens = tokens,
            readingMode = mode,
            onUpdateStatus = { status, progress ->
                toolbarBuilder.updateStatus(status, progress)
            },
            onCenterTap = { immersiveHelper.toggleImmersive() },
            onScrollHideToolbars = {
                if (toolbarBuilder.areToolbarsVisible) toolbarBuilder.hideToolbars()
            },
            onReady = { skeletonView.visibility = View.GONE },
            onError = { msg ->
                skeletonView.visibility = View.GONE
                showError(msg)
            }
        )
        epubBinder = binder
        binder.load(initialPosition)
    }

    private suspend fun loadTxtDocument(uri: Uri, startScrollOffset: Int) {
        NexusTxtDocumentBinder.loadAndBind(
            activity = this,
            scope = lifecycleScope,
            contentContainer = contentContainer,
            skeletonView = skeletonView,
            uri = uri,
            docUriString = docUriString,
            docTitle = docTitle,
            palette = palette,
            startScrollOffset = startScrollOffset,
            dp = dp,
            statusBarHeight = currentStatusBarHeight,
            onScroll = { scrollY, total ->
                lastRecordedPosition = scrollY
                // The library reads progress as position/total, whatever the units are; for text
                // both are scroll extents. Without this a text file had no total at all, and its
                // card fell back to printing the raw scroll offset as a percentage.
                if (total > 0 && total != lastRecordedTotal) {
                    lastRecordedTotal = total
                    lifecycleScope.launch(Dispatchers.IO) {
                        repository.updateTotalPages(docUriString, total)
                    }
                }
                val progress = ((scrollY.toFloat() / total) * 100).toInt()
                toolbarBuilder.updateStatus(getString(R.string.nexus_doc_reading_status), progress)
                if (toolbarBuilder.areToolbarsVisible && scrollY > 60 * dp) {
                    toolbarBuilder.hideToolbars()
                }
            },
            onTap = { toolbarBuilder.toggleToolbars() },
            onError = { showError(it) }
        )
    }

    private fun showError(message: String) {
        NexusDocumentErrorHelper.showError(this, contentContainer, message, palette, dp)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        einkRefresh?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        sessionTracker?.start()
    }

    override fun onPause() {
        super.onPause()
        sessionTracker?.stop(lifecycleScope)
        pdfCoordinator?.savePosition()
        epubBinder?.saveCurrentPosition()
        if (!isPdf && !isEpub) {
            lifecycleScope.launch(Dispatchers.IO) {
                repository.updateReadingPosition(docUriString, lastRecordedPosition)
            }
        }
    }

    override fun onDestroy() {
        pdfCoordinator?.close()
        pdfCoordinator = null
        epubBinder?.destroy()
        epubBinder = null
        super.onDestroy()
    }
}
