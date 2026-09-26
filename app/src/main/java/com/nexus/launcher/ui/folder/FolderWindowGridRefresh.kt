package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem

object FolderWindowGridRefresh {

    private const val TAG = "FolderWindow"

    fun repopulate(
        context: Context,
        card: LinearLayout,
        grid: GridLayout,
        scroll: View,
        items: List<HomeScreenItem>,
        config: FolderConfig,
        iconCache: Map<String, Drawable>,
        homeIconSizePx: Int,
        folderId: Long?,
        onLaunchDismiss: () -> Unit,
        onLongPress: ((HomeScreenItem, View) -> Unit)? = null
    ): FolderWindowCardSizer.LayoutWidths {
        Log.d(
            "FolderWidth",
            "repopulateGrid folderId=$folderId configColumns=${config.gridColumns} itemCount=${items.size}"
        )
        val metrics = FolderWindowGridPopulator.populate(
            context = context,
            gridLayout = grid,
            items = items,
            config = config,
            iconCache = iconCache,
            onLaunch = { item: HomeScreenItem ->
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                    if (intent != null) {
                        context.startActivity(intent)
                        onLaunchDismiss()
                    }
                } catch (_: Exception) {
                    Log.e(TAG, "Failed to launch ${item.packageName}")
                }
            },
            cardContainer = card,
            homeIconSizePx = homeIconSizePx,
            onLongPress = onLongPress
        )
        val density = card.resources.displayMetrics.density
        val layoutGridWidthPx = FolderGridWidthResolver.layoutWidthPx(
            metrics.widthCols, metrics.iconSizePx, metrics.gapPx, density
        )
        val widths = FolderWindowCardSizer.LayoutWidths(
            layoutGridWidthPx,
            FolderWindowCardSizer.cardWidthForGrid(layoutGridWidthPx, density)
        )
        val maxScrollH = (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
        FolderWindowPlacer.capScrollHeight(scroll, maxScrollH)
        return widths
    }
}
