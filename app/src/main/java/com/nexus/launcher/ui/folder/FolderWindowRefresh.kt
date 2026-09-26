package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import com.nexus.launcher.R
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FolderWindowRefresh {

    fun refreshOpenWindow(
        context: Context,
        folderId: Long,
        config: FolderConfig,
        iconCache: Map<String, Drawable>
    ) {
        if (FolderWindowLifecycle.activeFolderId != folderId) return
        val card = FolderWindowManager.activeCard ?: return
        val grid = FolderWindowManager.activeGrid ?: return
        val scroll = card.findViewById<View>(R.id.folder_grid_scroll)
        FolderWindowManager.activeConfig = config
        FolderWindowManager.activeIconCache = iconCache
        refreshBackground(context, folderId, config)
        val widths = repopulateGrid(context, card, grid, scroll, FolderWindowManager.activeContents, config, FolderWindowManager.activeIconCache)
        card.findViewById<EditText>(R.id.folder_title_text)?.setText(
            FolderContextMenuLauncher.folderDisplayName(context, FolderWindowManager.activeFolderItem ?: return)
        )
        repositionOpenCard(widths.cardWidthPx)
    }

    fun refreshOpenWindowIfShowing(context: Context) {
        val folderItem = FolderWindowManager.activeFolderItem ?: return
        val card = FolderWindowManager.activeCard ?: return
        val grid = FolderWindowManager.activeGrid ?: return
        val config = FolderWindowManager.activeConfig ?: FolderConfigCodec.parse(folderItem.folderConfigJson)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            val queryId = folderItem.resolveFolderContentsId()
            val freshContents = withContext(Dispatchers.IO) {
                val dao = EntryPointAccessors.fromApplication(
                    context.applicationContext, DaoEntryPoint::class.java
                ).homeScreenDao()
                dao.getItemsInFolderSync(queryId)
            }
            FolderWindowManager.activeContents = freshContents
            FolderWindowManager.activeScrim?.setOpenFolderBadge(
                appCount = freshContents.size,
                folderId = folderItem.id,
                screenX = FolderWindowLifecycle.activeIconX,
                screenY = FolderWindowLifecycle.activeIconY,
                iconSize = FolderWindowLifecycle.activeIconSize,
                shapeStyle = FolderScrimHighlight.shapeStyle(config),
                accentColor = FolderScrimHighlight.resolvedAccentColor()
            )
            val scroll = card.findViewById<View>(R.id.folder_grid_scroll)
            val widths = repopulateGrid(
                context, card, grid, scroll, freshContents, config, FolderWindowManager.activeIconCache
            )
            card.findViewById<EditText>(R.id.folder_title_text)?.setText(
                FolderContextMenuLauncher.folderDisplayName(context, folderItem)
            )
            repositionOpenCard(widths.cardWidthPx)
        }
    }

    fun applySavedEdits(
        context: Context,
        folderId: Long,
        title: String,
        config: FolderConfig
    ) {
        if (FolderWindowLifecycle.activeFolderId != folderId) return
        FolderWindowManager.activeConfig = config
        FolderWindowManager.activeFolderItem = FolderWindowManager.activeFolderItem?.copy(
            folderTitle = title,
            folderConfigJson = FolderConfigCodec.toJson(config)
        )
        FolderWindowManager.activeCard?.findViewById<EditText>(R.id.folder_title_text)?.setText(title)
        refreshBackground(context, folderId, config)
    }

    /** Re-applies the open folder card's background against its OWN currently-active config —
     *  used when something external (e.g. the global Frosted Glass toggle) changes whether that
     *  config's Glass mode actually renders as glass, without any change to the config itself. */
    fun refreshActiveBackgroundIfShowing(context: Context) {
        val folderId = FolderWindowLifecycle.activeFolderId ?: return
        val config = FolderWindowManager.activeConfig ?: return
        refreshBackground(context, folderId, config)
    }

    fun refreshBackground(context: Context, folderId: Long, config: FolderConfig) {
        if (FolderWindowLifecycle.activeFolderId != folderId) return
        FolderWindowManager.activeConfig = config
        FolderWindowManager.activeFolderItem = FolderWindowManager.activeFolderItem?.copy(
            folderConfigJson = FolderConfigCodec.toJson(config)
        )
        FolderWindowLifecycle.activeOverlayRoot?.let { FolderCardFrostApplier.apply(it, config) }
        FolderWindowManager.activeCard?.let { FolderCardFrostApplier.applyBlur(it, config.glassRefraction, config.windowBackgroundOpacity) }
    }

    fun setOpenFolderChromeHidden(hidden: Boolean) {
        val visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        FolderWindowManager.activeCard?.visibility = visibility
        FolderWindowManager.activeScrim?.visibility = visibility
    }

    fun repopulateGrid(
        context: Context,
        card: LinearLayout,
        grid: GridLayout,
        scroll: View,
        items: List<HomeScreenItem>,
        config: FolderConfig,
        iconCache: Map<String, Drawable>
    ): FolderWindowCardSizer.LayoutWidths {
        val homeIconPx = FolderWindowLifecycle.activeIconSize.toInt().takeIf { it > 0 } ?: 0
        return FolderWindowGridRefresh.repopulate(
            context, card, grid, scroll, items, config, iconCache,
            homeIconPx, FolderWindowLifecycle.activeFolderId, onLaunchDismiss = { FolderWindowLifecycle.dismissFolder() },
            onLongPress = { item, anchor ->
                FolderWindowAppLongPress.handle(context, item, anchor) {
                    refreshOpenWindowIfShowing(context)
                }
            }
        )
    }

    fun repositionOpenCard(cardWidthPx: Int) {
        val holder = FolderWindowManager.activeCardHolder ?: return
        val root = FolderWindowLifecycle.activeOverlayRoot ?: return
        
        holder.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                holder.viewTreeObserver.removeOnPreDrawListener(this)
                FolderWindowPlacer.positionCardNearIcon(
                    holder, root, FolderWindowLifecycle.activeIconX, FolderWindowLifecycle.activeIconY, FolderWindowLifecycle.activeIconSize, cardWidthPx
                )
                return true
            }
        })
        holder.requestLayout()
    }
}
