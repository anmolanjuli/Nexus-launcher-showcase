package com.nexus.launcher.ui.folder

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

object FolderWindowGridPopulator {

    fun populate(
        context: Context,
        gridLayout: GridLayout,
        items: List<HomeScreenItem>,
        config: FolderConfig,
        iconCache: Map<String, Drawable>,
        onLaunch: (HomeScreenItem) -> Unit,
        cardContainer: LinearLayout? = null,
        homeIconSizePx: Int = 0,
        onLongPress: ((HomeScreenItem, View) -> Unit)? = null
    ): FolderWindowGridMetrics.Layout {
        val canvasView = com.nexus.launcher.ui.folder.FolderCanvasInvalidator.findCanvasInTree(gridLayout)
            ?: com.nexus.launcher.ui.folder.FolderWindowManager.activeCanvas?.get()
        val density = context.resources.displayMetrics.density
        val displayWidth = context.resources.displayMetrics.widthPixels
        val displayHeight = context.resources.displayMetrics.heightPixels

        // Drawer folders (page=-2) must use drawer icon size, not homeIconSizeMultiplier.
        val fromDrawer = FolderWindowManager.activeFolderItem?.page == -2
        val baseIconSizePx = if (fromDrawer && canvasView != null) {
            resolveDrawerIconSizePx(canvasView, density)
        } else {
            val userIconSizeMultiplier = canvasView?.homeIconSizeMultiplier ?: 1.0f
            val cell = canvasView?.homeGridCells?.firstOrNull()
                ?: android.graphics.RectF(0f, 0f, 72f * density, 72f * density)
            val gridRows = canvasView?.currentGridRows ?: 8
            val gapHorizontalPx = canvasView?.homeScreenRenderer?.gapHorizontalPx ?: 0f
            val gapVerticalPx = canvasView?.homeScreenRenderer?.gapVerticalPx ?: 0f
            com.nexus.launcher.ui.canvas.IconLayoutMetrics.compute(
                cell = cell,
                gridRows = gridRows,
                gapHorizontalPx = gapHorizontalPx,
                gapVerticalPx = gapVerticalPx,
                spanX = 1,
                spanY = 1,
                showLabels = false,
                userIconSizeMultiplier = userIconSizeMultiplier,
                density = density,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                labelText = null,
                labelPaint = null
            ).iconRect.width()
        }

        val sorted = FolderContentsResolver.sortedForDisplay(items).toMutableList()
        val metrics = FolderWindowGridMetrics.compute(context, config, sorted.size, baseIconSizePx)
        val layoutGridWidthPx = FolderGridWidthResolver.layoutWidthPx(metrics.widthCols, metrics.iconSizePx, metrics.gapPx, density)
        
        FolderWindowCardSizer.applyGridAndScrollWidth(gridLayout, layoutGridWidthPx)
        
        if (gridLayout.childCount > 0) gridLayout.removeAllViews()
        
        gridLayout.columnCount = metrics.widthCols
        gridLayout.rowCount = GridLayout.UNDEFINED
        gridLayout.setPadding(0, 0, 0, 0)
        gridLayout.clipChildren = false
        gridLayout.clipToPadding = false
        cardContainer?.clipChildren = false
        cardContainer?.clipToPadding = false
        
        val spacingPx = metrics.gapPx
        val marginPx = spacingPx / 2
        val baseIconSize = clampIconToCardInner(cardContainer, layoutGridWidthPx, metrics, spacingPx, density)
        val iconSizePx = baseIconSize
        val labelSp = 11f

        val dao = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext, com.nexus.launcher.di.DaoEntryPoint::class.java
        ).homeScreenDao()
        val coroutineScope = CoroutineScope(Dispatchers.Main)

        val badgeCounts = com.nexus.launcher.service.NexusNotificationService.badgeCounts.value
        val badgeStyleApp = canvasView?.badgeStyleApp ?: 1

        sorted.forEachIndexed { index, item ->
            val col = index % metrics.widthCols
            val row = index / metrics.widthCols
            
            val bCount = badgeCounts[item.packageName] ?: 0
            val itemContainer = FolderIconViewBuilder.build(
                context, item, config, col, row, iconSizePx, marginPx, density, iconCache, bCount, badgeStyleApp
            )

            FolderIconTouchHandler.attach(
                view = itemContainer,
                context = context,
                item = item,
                density = density,
                gridLayout = gridLayout,
                cardContainer = cardContainer,
                sorted = sorted,
                metrics = metrics,
                dao = dao,
                coroutineScope = coroutineScope
            )

            gridLayout.addView(itemContainer)
        }

        // Pad with invisible placeholders so the grid always shows at least 2×2 cells
        val minCells = metrics.widthCols * metrics.rowCount
        val paddingNeeded = minCells - sorted.size
        if (paddingNeeded > 0) {
            val placeholderSpec = GridLayout.LayoutParams().apply {
                width = iconSizePx
                height = iconSizePx
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            repeat(paddingNeeded) {
                val placeholder = View(context).apply {
                    visibility = View.INVISIBLE
                    layoutParams = placeholderSpec
                }
                gridLayout.addView(placeholder)
            }
        }

        FolderWindowCardSizer.forceGridRemeasure(gridLayout, layoutGridWidthPx)
        cardContainer?.let {
            FolderWindowCardSizer.applyCardFromGridWidth(it, layoutGridWidthPx)
        }
        return metrics
    }

    private fun clampIconToCardInner(cardContainer: LinearLayout?, layoutGridWidthPx: Int, metrics: FolderWindowGridMetrics.Layout, spacingPx: Int, density: Float): Int {
        if (cardContainer == null) return metrics.iconSizePx
        val cardWidthPx = FolderWindowCardSizer.cardWidthForGrid(layoutGridWidthPx, density)
        val cardInnerWidth = cardWidthPx - cardContainer.paddingLeft - cardContainer.paddingRight
        val cols = metrics.widthCols
        val actualGridWidth = cols * metrics.iconSizePx + (cols - 1) * spacingPx
        if (actualGridWidth <= cardInnerWidth) return metrics.iconSizePx
        val minIconSizePx = (36 * density).toInt()
        return ((cardInnerWidth - (cols - 1) * spacingPx) / cols).coerceAtLeast(minIconSizePx)
    }

    /** Prefer a real drawer app cell; fall back to folder cell or multiplier estimate. */
    private fun resolveDrawerIconSizePx(
        canvasView: com.nexus.launcher.ui.canvas.LauncherCanvasView,
        density: Float
    ): Int {
        val fromApp = canvasView.drawerItems.firstOrNull {
            it.intent?.action != "nexus.folder.OPEN" && it.drawRect.width() > 0
        }?.drawRect?.width()
        if (fromApp != null && fromApp > 0) return fromApp
        val fromAny = canvasView.drawerItems.firstOrNull { it.drawRect.width() > 0 }?.drawRect?.width()
        if (fromAny != null && fromAny > 0) return fromAny
        val cols = canvasView.gridRenderer.columnCount.coerceAtLeast(1)
        val cellW = canvasView.gridAreaWidth / cols
        return (cellW * canvasView.drawerIconSizeMultiplier).toInt().coerceAtLeast((36 * density).toInt())
    }
}
