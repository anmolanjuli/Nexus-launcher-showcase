package com.nexus.launcher.feed

import android.content.Context
import android.widget.ScrollView
import com.nexus.launcher.data.FeedDao
import com.nexus.launcher.reader.doc.DocumentLibraryRepository
import com.nexus.launcher.reader.doc.DocumentLibraryView
import com.nexus.launcher.reader.doc.DocumentPickerHelper
import com.nexus.launcher.reader.doc.NexusDocumentReaderActivity
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages lazily instantiated tabs (Settings, Library) within the Feed panel.
 */
class NexusFeedTabManager(
    private val context: Context,
    private val activity: MainActivity,
    private val scope: CoroutineScope,
    private val tabHost: NexusFeedTabContentHost,
    private val displayManager: NexusFeedArticleDisplayManager,
    private val scrollView: ScrollView,
    private val feedDao: FeedDao,
    private val sourceManager: NexusFeedSourceManager,
    private val onEInkChanged: () -> Unit
) {
    var settingsView: NexusFeedSettingsView? = null
        private set
    var libraryView: DocumentLibraryView? = null
        private set

    fun ensureSettingsTab(onCancelArticles: () -> Unit) {
        onCancelArticles()
        val settingsContainer = tabHost.container(NexusFeedBottomBar.Tab.SETTINGS)
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val currentTokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        if (settingsView == null) {
            settingsView = NexusFeedSettingsView(
                context = context,
                scope = scope,
                feedDao = feedDao,
                sourceManager = sourceManager
            ).apply {
                onEInkModeChanged = { onEInkChanged() }
                applyTokens(currentTokens)
            }.also { settingsContainer.addView(it) }
        } else {
            settingsView?.applyTokens(currentTokens)
        }
        displayManager.setStripsForTab(NexusFeedBottomBar.Tab.SETTINGS, settingsView!!.horizontalScrollStrips(), isCurrentTab = true)
        scrollView.scrollTo(0, 0)
    }

    fun ensureLibraryTab(onCancelArticles: () -> Unit) {
        onCancelArticles()
        val libraryContainer = tabHost.container(NexusFeedBottomBar.Tab.LIBRARY)
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val currentTokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        if (libraryView == null) {
            libraryView = DocumentLibraryView(
                context = context,
                scope = scope,
                onImportClick = {
                    DocumentPickerHelper.pickDocument(activity) { uri ->
                        if (uri != null) {
                            scope.launch(Dispatchers.IO) {
                                val repository = DocumentLibraryRepository(context)
                                val feature = com.nexus.launcher.premium.PremiumFeature.DOCUMENT_LIBRARY
                                // Past the free limit a PDF or EPUB is not added: say why, then offer Premium.
                                val limited = repository.needsPremiumToAdd(uri) &&
                                    com.nexus.launcher.ui.premium.PremiumBadges.isShown(feature)
                                if (!limited) {
                                    repository.addDocument(uri)
                                    return@launch
                                }
                                runCatching {
                                    context.contentResolver.releasePersistableUriPermission(
                                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                    )
                                }
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    com.nexus.launcher.reader.doc.DocumentLimitDialog.show(activity) {
                                        com.nexus.launcher.premium.PremiumGate.allow(activity, feature)
                                    }
                                }
                            }
                        }
                    }
                },
                onDocumentClick = { doc ->
                    NexusDocumentReaderActivity.start(context, doc.uri, doc.fileName, doc.fileType)
                }
            ).apply {
                applyTokens(currentTokens)
            }.also { libraryContainer.addView(it) }
        } else {
            libraryView?.applyTokens(currentTokens)
        }
        displayManager.setStripsForTab(NexusFeedBottomBar.Tab.LIBRARY, emptyList(), isCurrentTab = true)
        scrollView.scrollTo(0, 0)
    }

    fun applyTokens(tokens: NexusColorTokens) {
        settingsView?.applyTokens(tokens)
        libraryView?.applyTokens(tokens)
    }
}
