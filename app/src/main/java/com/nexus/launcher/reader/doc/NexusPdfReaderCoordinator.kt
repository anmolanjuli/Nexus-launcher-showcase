package com.nexus.launcher.reader.doc

import android.app.Activity
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.R
import com.nexus.launcher.feed.NexusFeedEInkCoordinator
import com.nexus.launcher.reader.NexusReaderThemeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Coordinates PDF loading, mode switching (continuous scroll vs page turn), theme filters,
 * fit scaling, and reading position updates for NexusDocumentReaderActivity.
 */
class NexusPdfReaderCoordinator(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val repository: DocumentLibraryRepository,
    private val contentContainer: FrameLayout,
    private val toolbarBuilder: NexusDocumentToolbarBuilder,
    private val immersiveHelper: NexusDocumentImmersiveHelper,
    private val palette: NexusReaderThemeHelper.ReaderPalette,
    private val docUriString: String,
    private val dp: Float,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit
) {
    var pdfEngine: NexusPdfRendererEngine? = null
        private set
    var pdfPageView: NexusPdfPageView? = null
        private set
    var pdfHorizontalPageView: NexusPdfHorizontalPageView? = null
        private set

    var currentPdfMode = PdfReadingModeStore.Mode.CONTINUOUS_SCROLL
        private set
    var currentPdfTheme = PdfThemeModeStore.ThemeMode.ORIGINAL
        private set
    var currentPdfFitMode = PdfPageFitStore.FitMode.SMART_CROP
        private set

    var lastRecordedPosition: Int = 0
        private set

    fun initPreferences() {
        currentPdfMode = PdfReadingModeStore.getMode(activity)
        val isDarkPaper = NexusFeedEInkCoordinator.isEInkDark(activity)
        currentPdfTheme = PdfThemeModeStore.getTheme(activity, docUriString, isDarkPaper)
        currentPdfFitMode = PdfPageFitStore.getFitMode(activity, docUriString)
    }

    suspend fun loadPdf(uri: Uri, startPage: Int, statusBarHeight: Int, navBarHeight: Int) {
        val engine = NexusPdfRendererEngine(activity, uri)
        pdfEngine = engine

        try {
            val count = engine.open()
            repository.updateTotalPages(docUriString, count)
            onReady()
            attachPdfModeView(startPage, statusBarHeight, navBarHeight)
        } catch (e: NexusPdfRendererEngine.PdfPasswordException) {
            onError(activity.getString(R.string.nexus_doc_error_protected))
        } catch (e: Exception) {
            onError(activity.getString(R.string.nexus_doc_error_corrupted))
        }
    }

    fun attachPdfModeView(page: Int, statusBarHeight: Int, navBarHeight: Int) {
        val engine = pdfEngine ?: return
        val isDarkPaper = NexusFeedEInkCoordinator.isEInkDark(activity)
        val result = NexusPdfDocumentBinder.bind(
            context = activity,
            scope = scope,
            contentContainer = contentContainer,
            engine = engine,
            isDarkPaper = isDarkPaper,
            palette = palette,
            mode = currentPdfMode,
            theme = currentPdfTheme,
            fitMode = currentPdfFitMode,
            statusBarHeight = statusBarHeight,
            navBarHeight = navBarHeight,
            startPage = page,
            dp = dp,
            onPageChanged = { pageIndex, totalPages ->
                lastRecordedPosition = pageIndex
                updatePdfStatus(pageIndex, totalPages)
            },
            onCenterTap = { immersiveHelper.toggleImmersive() },
            onScrollHideToolbars = {
                if (toolbarBuilder.areToolbarsVisible) {
                    toolbarBuilder.hideToolbars()
                }
            }
        )
        pdfPageView = result.verticalView
        pdfHorizontalPageView = result.horizontalView
    }

    fun switchPdfMode(newMode: PdfReadingModeStore.Mode, statusBarHeight: Int, navBarHeight: Int) {
        if (currentPdfMode == newMode) return
        currentPdfMode = newMode
        PdfReadingModeStore.setMode(activity, newMode)
        attachPdfModeView(lastRecordedPosition, statusBarHeight, navBarHeight)
    }

    fun switchPdfTheme(newTheme: PdfThemeModeStore.ThemeMode) {
        currentPdfTheme = newTheme
        PdfThemeModeStore.setTheme(activity, docUriString, newTheme)
        toolbarBuilder.updatePdfTheme(newTheme)
        pdfPageView?.applyTheme(newTheme)
        pdfHorizontalPageView?.applyTheme(newTheme)
    }

    fun switchPdfFitMode(newFit: PdfPageFitStore.FitMode) {
        currentPdfFitMode = newFit
        PdfPageFitStore.setFitMode(activity, docUriString, newFit)
        toolbarBuilder.updatePdfFitMode(newFit)
        pdfPageView?.applyFitMode(newFit)
        pdfHorizontalPageView?.applyFitMode(newFit)
    }

    private fun updatePdfStatus(pageIndex: Int, totalPages: Int) {
        val status = activity.getString(R.string.nexus_doc_page_status, pageIndex + 1, totalPages)
        val progress = if (totalPages > 1) (((pageIndex + 1).toFloat() / totalPages) * 100).toInt() else 100
        toolbarBuilder.updateStatus(status, progress)
        scope.launch(Dispatchers.IO) {
            repository.updateReadingPosition(docUriString, pageIndex)
        }
    }

    fun savePosition() {
        scope.launch(Dispatchers.IO) {
            repository.updateReadingPosition(docUriString, lastRecordedPosition)
        }
    }

    fun close() {
        pdfEngine?.close()
        pdfEngine = null
        pdfPageView = null
        pdfHorizontalPageView = null
    }
}
