package com.nexus.launcher.reader.doc

import android.content.Context
import android.net.Uri
import android.widget.FrameLayout
import com.nexus.launcher.R
import com.nexus.launcher.reader.NexusReaderThemeHelper
import com.nexus.launcher.theme.NexusColorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates EPUB parsing, lifecycle management, theme updates, TOC dialog presentation,
 * and reading position persistence for NexusDocumentReaderActivity using lightweight NexusEpubParser.
 */
class NexusEpubDocumentBinder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val contentContainer: FrameLayout,
    private val uri: Uri,
    private val docUriString: String,
    private val repository: DocumentLibraryRepository,
    private var palette: NexusReaderThemeHelper.ReaderPalette,
    private var tokens: NexusColorTokens,
    private var readingMode: PdfReadingModeStore.Mode,
    private val onUpdateStatus: (status: String, progressPercent: Int) -> Unit,
    private val onCenterTap: () -> Unit,
    private val onScrollHideToolbars: () -> Unit,
    private val onReady: () -> Unit,
    private val onError: (message: String) -> Unit
) {
    private var epubParser: NexusEpubParser? = null
    var pageView: NexusEpubPageView? = null
        private set

    private var lastRecordedChapter: Int = 0
    private var lastRecordedScrollY: Int = 0

    fun load(initialPosition: Int) {
        val parser = NexusEpubParser(context, uri)
        epubParser = parser

        scope.launch {
            try {
                val totalChapters = parser.open()
                if (totalChapters == 0) {
                    withContext(Dispatchers.Main) {
                        onError(context.getString(R.string.nexus_doc_error_corrupted))
                    }
                    return@launch
                }

                repository.updateTotalPages(docUriString, totalChapters)

                val restoredChapter = (initialPosition / 10000).coerceIn(0, totalChapters - 1)
                val restoredScroll = (initialPosition % 10000).coerceAtLeast(0)
                lastRecordedChapter = restoredChapter
                lastRecordedScrollY = restoredScroll

                withContext(Dispatchers.Main) {
                    attachPageView(parser, restoredChapter, restoredScroll)
                    onReady()
                }
            } catch (e: NexusEpubParser.DrmProtectedException) {
                withContext(Dispatchers.Main) {
                    onError(context.getString(R.string.nexus_doc_error_epub_drm))
                }
            } catch (e: NexusEpubParser.FixedLayoutException) {
                withContext(Dispatchers.Main) {
                    onError(context.getString(R.string.nexus_doc_error_epub_fixed_layout))
                }
            } catch (e: NexusEpubParser.InvalidEpubException) {
                withContext(Dispatchers.Main) {
                    onError(context.getString(R.string.nexus_doc_error_epub_invalid))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(context.getString(R.string.nexus_doc_error_corrupted))
                }
            }
        }
    }

    private fun attachPageView(parser: NexusEpubParser, startChapter: Int, startScroll: Int) {
        contentContainer.removeAllViews()

        val view = NexusEpubPageView(
            context = context,
            scope = scope,
            parser = parser,
            palette = palette,
            tokens = tokens,
            readingMode = readingMode,
            onChapterChanged = { chapterIndex, total ->
                lastRecordedChapter = chapterIndex
                lastRecordedScrollY = 0
                persistPosition(chapterIndex, 0)
                updateToolbarStatus(chapterIndex, 0, total)
            },
            onScrollPositionChanged = { chapterIndex, scrollY ->
                lastRecordedChapter = chapterIndex
                lastRecordedScrollY = scrollY
                persistPosition(chapterIndex, scrollY)
                updateToolbarStatus(chapterIndex, scrollY, parser.totalChapters)
            },
            onCenterTap = onCenterTap,
            onScrollHideToolbars = onScrollHideToolbars
        )
        contentContainer.addView(view)
        pageView = view

        view.loadChapter(startChapter, startScroll)
    }

    fun showTableOfContents() {
        val parser = epubParser ?: return

        val chapters = if (parser.tableOfContents.isNotEmpty()) {
            parser.tableOfContents.map { Pair(it.chapterIndex, it.title) }
        } else {
            (0 until parser.totalChapters).map { i ->
                Pair(i, parser.getChapterTitle(i))
            }
        }

        NexusEpubTocDialog(
            context = context,
            chapters = chapters,
            currentChapterIndex = lastRecordedChapter,
            isEInk = palette.isEInk,
            onChapterSelected = { chosenChapter ->
                pageView?.loadChapter(chosenChapter, 0)
            }
        ).show()
    }

    fun setReadingMode(mode: PdfReadingModeStore.Mode) {
        readingMode = mode
        pageView?.setReadingMode(mode)
    }

    fun applyTheme(newPalette: NexusReaderThemeHelper.ReaderPalette, newTokens: NexusColorTokens) {
        palette = newPalette
        tokens = newTokens
        pageView?.applyTheme(newPalette, newTokens)
    }

    private fun updateToolbarStatus(chapterIndex: Int, scrollY: Int, totalChapters: Int) {
        val status = context.getString(R.string.nexus_epub_chapter_status, chapterIndex + 1, totalChapters)
        val progress = if (totalChapters > 0) {
            val scrollFraction = (scrollY.toFloat() / 3000f).coerceIn(0f, 0.95f)
            (((chapterIndex.toFloat() + scrollFraction) / totalChapters.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        onUpdateStatus(status, progress)
    }

    private fun persistPosition(chapterIndex: Int, scrollY: Int) {
        val encodedPosition = (chapterIndex * 10000) + scrollY.coerceIn(0, 9999)
        scope.launch(Dispatchers.IO) {
            repository.updateReadingPosition(docUriString, encodedPosition)
        }
    }

    fun saveCurrentPosition() {
        val encodedPosition = (lastRecordedChapter * 10000) + lastRecordedScrollY.coerceIn(0, 9999)
        scope.launch(Dispatchers.IO) {
            repository.updateReadingPosition(docUriString, encodedPosition)
        }
    }

    fun destroy() {
        saveCurrentPosition()
        pageView?.destroy()
        pageView = null
        epubParser = null
    }
}
