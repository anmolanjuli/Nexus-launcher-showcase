package com.nexus.launcher.ui.folder

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.core.view.doOnLayout
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

object FolderWindowLifecycle {
    private const val TAG = "FolderWindowLifecycle"

    internal var activeFolderId: Long? = null
    internal var activeOverlayRoot: View? = null
    internal var activeIconX: Float = 0f
    internal var activeIconY: Float = 0f
    internal var activeIconSize: Float = 0f


    fun showFolder(
        context: Context,
        rootView: android.view.ViewGroup,
        folderItem: HomeScreenItem,
        iconX: Float,
        iconY: Float,
        contents: List<HomeScreenItem>,
        iconCache: Map<String, Drawable>,
        iconSize: Float = 0f,
        highlightBounds: FolderScrimHighlight.Bounds? = null
    ) {
        val activity = context as? Activity ?: return

        FolderWindowManager.pendingReopenFolderId = folderItem.id.toLong()
        FolderWindowManager.pendingReopenIconX = iconX
        FolderWindowManager.pendingReopenIconY = iconY

        if (iconX == 0f && iconY == 0f) {
            Log.w(TAG, "showFolder received (0,0) iconScreen — check FolderOpenAnchor upstream")
        } else {
            Log.d(TAG, "showFolder iconScreen=($iconX, $iconY)")
        }

        android.util.Log.d("FolderReopen", "showFolder: isShowing=${FolderOverlayController.isShowing()} isClosing=${FolderWindowManager.isClosing}")
        if (FolderOverlayController.isShowing()) dismissFolder()
        FolderContextMenuLauncher.dismiss()

        val overlayRoot = FolderOverlayController.getOrInflateOverlay(activity) { dismissFolder() }
        val scrim = overlayRoot.findViewById<FolderWindowScrimView>(R.id.folder_scrim)
        val folderCard = overlayRoot.findViewById<LinearLayout>(R.id.folder_card_container)
        val cardHolder = overlayRoot.findViewById<FrameLayout>(R.id.folder_card_holder)
        val gridScroll = overlayRoot.findViewById<View>(R.id.folder_grid_scroll)
        val config = FolderConfigCodec.parse(folderItem.folderConfigJson)
        Log.d(
            "FolderWidth",
            "folderId=${folderItem.id} configColumns=${config.gridColumns} " +
                "rawJson=${folderItem.folderConfigJson}"
        )
        var windowConfig = config
        val density = activity.resources.displayMetrics.density
        val resolvedIconSize = if (iconSize > 0f) iconSize else 48f * density

        folderCard.visibility = View.INVISIBLE

        findCanvas(context)?.let { canvas ->
            canvas.folderOverlayFrozen = true
            canvas.frozenScrollY = canvas.scrollY
            canvas.isFolderClosing = false
            FolderWindowManager.activeCanvas = WeakReference(canvas)
        }



        scrim.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                dismissFolder()
            }
            true
        }
        folderCard.setOnClickListener { /* consume */ }

        FolderWindowChromeBinder.bind(
            context = context,
            folderCard = folderCard,
            overlayRoot = overlayRoot as FrameLayout,
            folderItem = folderItem,
            windowConfig = windowConfig,
            onConfigChanged = { newConfig ->
                windowConfig = newConfig
                FolderWindowManager.activeConfig = newConfig
                FolderCardFrostApplier.apply(overlayRoot, newConfig)
                FolderCardFrostApplier.applyBlur(folderCard, newConfig.glassRefraction, newConfig.windowBackgroundOpacity)
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                scope.launch(Dispatchers.IO) {
                    val dao = EntryPointAccessors.fromApplication(
                        context.applicationContext, DaoEntryPoint::class.java
                    ).homeScreenDao()
                    FolderActionEngine.saveFolderConfig(
                        folderItem.id.toLong(),
                        FolderContextMenuLauncher.folderDisplayName(
                            FolderWindowManager.activeFolderItem ?: folderItem
                        ),
                        newConfig, dao
                    )
                }
                val grid = FolderWindowManager.activeGrid ?: return@bind
                val scroll = folderCard.findViewById<View>(R.id.folder_grid_scroll)
                val widths = FolderWindowRefresh.repopulateGrid(
                    context, folderCard, grid, scroll, FolderWindowManager.activeContents, newConfig, iconCache
                )
                FolderWindowRefresh.repositionOpenCard(widths.cardWidthPx)
            },
            onRefreshApps = { FolderWindowRefresh.refreshOpenWindowIfShowing(context) },
            onItemUpdated = { FolderWindowManager.activeFolderItem = it }
        )

        FolderCardFrostApplier.apply(overlayRoot, config)
        FolderCardFrostApplier.applyBlur(folderCard, config.glassRefraction, config.windowBackgroundOpacity)

        FolderWindowManager.activeCard = folderCard
        FolderWindowManager.activeCardHolder = cardHolder
        activeOverlayRoot = overlayRoot
        FolderWindowManager.activeScrim = scrim
        activeFolderId = folderItem.id.toLong()
        FolderWindowManager.activeFolderItem = folderItem
        FolderWindowManager.activeIconCache = iconCache
        FolderWindowManager.activeConfig = config
        activeIconX = iconX
        activeIconY = iconY
        activeIconSize = resolvedIconSize

        val gridLayout = overlayRoot.findViewById<GridLayout>(R.id.folder_grid)
        FolderWindowManager.activeGrid = gridLayout

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            val queryId = folderItem.resolveFolderContentsId()
            val finalContents = withContext(Dispatchers.IO) {
                val dao = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    DaoEntryPoint::class.java
                ).homeScreenDao()
                val fromDb = dao.getItemsInFolderSync(queryId)
                if (fromDb.isNotEmpty()) fromDb else contents
            }
            FolderWindowManager.activeContents = finalContents
            val populateAndPresent = {
                val widths = FolderWindowRefresh.repopulateGrid(
                    context, folderCard, gridLayout, gridScroll,
                    finalContents, windowConfig, iconCache
                )
                val maxScrollH =
                    (activity.resources.displayMetrics.heightPixels * 0.55f).toInt()
                FolderWindowPlacer.capScrollHeight(gridScroll, maxScrollH)
                presentFolderCard(
                    holder = cardHolder,
                    card = folderCard,
                    root = overlayRoot,
                    iconX = iconX,
                    iconY = iconY,
                    iconSize = resolvedIconSize,
                    cardWidthPx = widths.cardWidthPx,
                    scrim = scrim,
                    highlightBounds = highlightBounds,
                    grid = gridLayout
                )
            }
            // Populate only after posting to the next frame to avoid timing races
            // with visibility toggles, without forcing a full DecorView layout pass.
            folderCard.post {
                if (folderCard.isLaidOut && folderCard.width > 0 && !folderCard.isLayoutRequested) {
                    populateAndPresent()
                } else {
                    folderCard.doOnLayout { populateAndPresent() }
                }
            }
        }
    }

    private fun presentFolderCard(
        holder: FrameLayout,
        card: LinearLayout,
        root: View,
        iconX: Float,
        iconY: Float,
        iconSize: Float,
        cardWidthPx: Int,
        scrim: FolderWindowScrimView,
        highlightBounds: FolderScrimHighlight.Bounds?,
        grid: GridLayout
    ) {
        afterHolderLayout(holder) {
            FolderWindowPlacer.positionCardNearIcon(
                holder, root, iconX, iconY, iconSize, cardWidthPx
            )
            scrim.setHighlight(highlightBounds)
            val config = FolderWindowManager.activeConfig ?: FolderConfigCodec.parse(
                FolderWindowManager.activeFolderItem?.folderConfigJson ?: "{}"
            )
            scrim.setOpenFolderBadge(
                appCount = FolderWindowManager.activeContents.size,
                folderId = FolderWindowManager.activeFolderItem?.id ?: 0,
                screenX = iconX,
                screenY = iconY,
                iconSize = iconSize,
                shapeStyle = FolderScrimHighlight.shapeStyle(config),
                accentColor = FolderScrimHighlight.resolvedAccentColor()
            )
            val canvas = FolderWindowManager.activeCanvas?.get()
            val folderId = FolderWindowManager.activeFolderItem?.id ?: return@afterHolderLayout
            FolderWindowMorphAnimator.playOpen(
                canvas = canvas,
                card = card,
                scrim = scrim,
                holder = holder,
                iconScreenX = iconX,
                iconScreenY = iconY,
                iconSize = iconSize,
                folderItemId = folderId,
                shapeStyle = FolderScrimHighlight.shapeStyle(config),
              onGridReveal = { FolderWindowGridStagger.reveal(grid) }
            )
        }
    }

    private fun afterHolderLayout(holder: FrameLayout, block: () -> Unit) {
        // Layout is already guaranteed by folderCard.doOnLayout upstream.
        // Post the block to ensure it runs after any pending traversals
        // without forcing a new layout pass.
        holder.post { block() }
    }

    fun dismissFolder() {
        if (FolderWindowManager.isClosing) return
        val overlay = FolderOverlayController.getOverlay() ?: return
        val ctx = overlay.context
        val card = FolderWindowManager.activeCard
        val scrim = FolderWindowManager.activeScrim
        val canvas = FolderWindowManager.activeCanvas?.get()
        val folderId = FolderWindowManager.activeFolderItem?.id

        FolderWindowManager.activeCard?.findViewById<EditText>(R.id.folder_title_text)?.let { edit ->
            FolderWindowTitleBinder.saveIfNeeded(edit) { FolderWindowManager.activeFolderItem = it }
        }

        if (card != null && scrim != null && folderId != null && canvas != null) {
            FolderWindowManager.isClosing = true
            canvas.isFolderClosing = true
            FolderWindowMorphAnimator.playClose(canvas, card, scrim) {
                detachAndClear(ctx)
            }
            return
        }
        detachAndClear(ctx)
    }

    private fun detachAndClear(ctx: android.content.Context) {
        android.util.Log.d("FolderReopen", "detachAndClear called isClosing=${FolderWindowManager.isClosing}")
        FolderOverlayController.hide()
        FolderWindowManager.isClosing = false
        clearActive(ctx)
    }

    private fun clearActive(context: Context) {
        FolderWindowManager.activeCanvas?.get()?.let { canvas ->
            canvas.folderOverlayFrozen = false
            canvas.isFolderClosing = false
            FolderBlurCoordinator.restoreSourceIcon(canvas)
        }
        // Safety net: edit-from-open-folder can leave workspace RenderEffect blur on.
        if (!FolderContextMenuLauncher.isShowing()) {
            FolderBlurCoordinator.setWorkspaceBlur(context, false)
        }
        FolderWindowManager.activeCanvas = null
        FolderWindowManager.activeCard = null
        FolderWindowManager.activeCardHolder = null
        activeOverlayRoot = null
        FolderWindowManager.activeGrid = null
        FolderWindowManager.activeScrim = null
        activeFolderId = null
        FolderWindowManager.activeFolderItem = null
        FolderWindowManager.activeContents = emptyList()
        FolderWindowManager.activeIconCache = emptyMap()
        FolderWindowManager.activeConfig = null
        activeIconX = 0f
        activeIconY = 0f
        activeIconSize = 0f
    }

    private fun findCanvas(context: Context): LauncherCanvasView? {
        val activity = context as? Activity ?: return null
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return null
        return FolderCanvasInvalidator.findCanvasInTree(content)
    }
}
