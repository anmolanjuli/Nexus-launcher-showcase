package com.nexus.launcher.reader.doc

import android.content.Context
import android.widget.FrameLayout
import com.nexus.launcher.reader.NexusReaderThemeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Binds and mounts either vertical scroll or horizontal page view for PDF documents.
 */
object NexusPdfDocumentBinder {

    data class BindingResult(
        val verticalView: NexusPdfPageView?,
        val horizontalView: NexusPdfHorizontalPageView?
    )

    fun bind(
        context: Context,
        scope: CoroutineScope,
        contentContainer: FrameLayout,
        engine: NexusPdfRendererEngine,
        isDarkPaper: Boolean,
        palette: NexusReaderThemeHelper.ReaderPalette,
        mode: PdfReadingModeStore.Mode,
        theme: PdfThemeModeStore.ThemeMode,
        fitMode: PdfPageFitStore.FitMode,
        statusBarHeight: Int,
        navBarHeight: Int,
        startPage: Int,
        dp: Float,
        onPageChanged: (pageIndex: Int, totalPages: Int) -> Unit,
        onCenterTap: () -> Unit,
        onScrollHideToolbars: () -> Unit
    ): BindingResult {
        contentContainer.removeAllViews()

        return if (mode == PdfReadingModeStore.Mode.PAGE_TURN) {
            val hView = NexusPdfHorizontalPageView(
                context = context,
                scope = scope,
                engine = engine,
                isEInk = palette.isEInk,
                isDarkPaper = isDarkPaper,
                backgroundColor = palette.bg,
                statusBarHeight = statusBarHeight,
                navBarHeight = navBarHeight,
                onPageChanged = onPageChanged,
                onCenterTap = onCenterTap,
                initialTheme = theme,
                initialFitMode = fitMode
            )
            contentContainer.addView(hView)
            hView.setup(startPage)
            BindingResult(verticalView = null, horizontalView = hView)
        } else {
            var initialScrollIgnored = false
            val vView = NexusPdfPageView(
                context = context,
                scope = scope,
                engine = engine,
                isEInk = palette.isEInk,
                isDarkPaper = isDarkPaper,
                backgroundColor = palette.bg,
                statusBarHeight = statusBarHeight,
                navBarHeight = navBarHeight,
                onPageChanged = onPageChanged,
                onScrollProgress = { scrollY, _ ->
                    if (!initialScrollIgnored) {
                        initialScrollIgnored = true
                        return@NexusPdfPageView
                    }
                    if (scrollY > 120 * dp) {
                        onScrollHideToolbars()
                    }
                },
                onTap = onCenterTap,
                initialTheme = theme,
                initialFitMode = fitMode
            )
            contentContainer.addView(vView)
            scope.launch {
                vView.setupPages(startPage)
            }
            BindingResult(verticalView = vView, horizontalView = null)
        }
    }
}
