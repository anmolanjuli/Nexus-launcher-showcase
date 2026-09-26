package com.nexus.launcher.ui.widgets
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHost
import androidx.activity.ComponentActivity
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import android.app.Activity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView
import com.nexus.launcher.ui.widgets.mosaic.MosaicChild
class AppWidgetController(
    private val activity: ComponentActivity,
    private val appWidgetHost: AppWidgetHost,
    private val appWidgetManager: AppWidgetManager,
    private val canvasView: LauncherCanvasView,
    private val homeScreenViewModel: HomeScreenViewModel,
    private val widgetProviderCatalog: WidgetProviderCatalog
) {
    private val restoredRebinder = AppWidgetRestoredRebinder(
        activity, appWidgetHost, appWidgetManager, homeScreenViewModel
    )
    private var pendingReplaceItem: com.nexus.launcher.data.HomeScreenItem? = null
    private var pendingReplaceXFraction = 0.5f
    private var pendingReplaceYFraction = 0.5f
    private var pendingReplaceSpanX     = 2
    private var pendingReplaceSpanY     = 2
    private var pendingReplacePage      = 0
    private var pendingMosaicItem: com.nexus.launcher.data.HomeScreenItem? = null
    private var pendingMosaicItemTargetSlot: Int? = null
    private var pendingConfigureWidgetId = -1 // the widget whose setup screen is open
    fun replaceWidget(item: com.nexus.launcher.data.HomeScreenItem) {
        pendingReplaceItem      = item
        pendingReplaceXFraction = item.xFraction
        pendingReplaceYFraction = item.yFraction
        pendingReplaceSpanX     = item.spanX
        pendingReplaceSpanY     = item.spanY
        pendingReplacePage      = item.page
        launchWidgetPicker()
    }
    private var pendingMosaicWidgetAddedCallback: ((Int, String, String) -> Unit)? = null
    fun launchWidgetPickerForMosaic(
        mosaic: com.nexus.launcher.data.HomeScreenItem,
        targetSlot: Int? = null,
        onPickerDismissed: (() -> Unit)? = null,
        onWidgetAdded: ((appWidgetId: Int, providerPackage: String, providerClassName: String) -> Unit)? = null
    ) {
        pendingMosaicItem = mosaic
        pendingMosaicItemTargetSlot = targetSlot
        pendingMosaicWidgetAddedCallback = onWidgetAdded
        pendingReplaceItem = null
        launchWidgetPicker(excludeNexusMosaic = true, onPickerDismissed = onPickerDismissed)
    }
    fun launchWidgetPicker(
        excludeNexusMosaic: Boolean = false,
        onPickerDismissed: (() -> Unit)? = null
    ) {
        if (!excludeNexusMosaic) {
            pendingMosaicItem = null
            pendingMosaicItemTargetSlot = null
        }
        val overlay = WidgetPickerOverlay(activity)
        overlay.onDismissed = onPickerDismissed
        overlay.excludeNexusMosaic = excludeNexusMosaic
        overlay.onWidgetSelected = { _: WidgetAppGroup, entry: WidgetProviderEntry ->
            val dropX = if (pendingReplaceItem != null)
                WidgetCoordinateSpace.centerXFromFraction(pendingReplaceXFraction, canvasView.width.toFloat())
            else canvasView.width / 2f
            val dropY = if (pendingReplaceItem != null)
                pendingReplaceYFraction * canvasView.height
            else canvasView.height / 2f
            handlePickerEntry(entry, dropX, dropY)
            overlay.dismiss()
        }
        overlay.onWidgetDropped = { entry: WidgetProviderEntry, x: Float, y: Float ->
            handlePickerEntry(entry, x, y)
            overlay.dismiss()
        }
        val cached = WidgetProviderCatalog.getCached()
        if (cached != null) {
            overlay.bind(cached)
            overlay.show()
        } else {
            overlay.show()
            overlay.post {
                activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val groups = widgetProviderCatalog.loadGroupedProviders()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        overlay.bind(groups)
                    }
                }
            }
        }
    }
    var pendingWidgetDropX: Float = 0f
    var pendingWidgetDropY: Float = 0f
    private fun handlePickerEntry(entry: WidgetProviderEntry, x: Float, y: Float) {
        if (entry.isLivingMosaic || entry.isShortcutBox || entry.isAppBox || entry.isLiveApps) {
            pendingMosaicItem = null
            placeSyntheticWidget(entry.nexusKind, x, y)
            return
        }
        val info = entry.info ?: NexusWidgetProviderResolver.resolve(appWidgetManager, entry) ?: return
        handleWidgetDrop(info, entry.minWidthDp, entry.minHeightDp, x, y)
    }

    private fun placeSyntheticWidget(nexusKind: String?, x: Float, y: Float) {
        val targetPage = canvasView.currentPage
        pendingWidgetDropX = x
        pendingWidgetDropY = y
        val density = activity.resources.displayMetrics.density
        val (spanX, spanY) = AppWidgetGeometryOps.computeWidgetSpans(
            null, density, canvasView, pendingReplaceItem, pendingReplaceSpanX, pendingReplaceSpanY
        )
        val (cellWidthDp, cellHeightDp) = AppWidgetGeometryOps.computeCellSizeDp(density, canvasView.viewHeight, canvasView)
        val minWidthDp = (spanX * cellWidthDp).toInt()
        val minHeightDp = (spanY * cellHeightDp).toInt()
        val (xFrac, yFrac) = AppWidgetGeometryOps.getClampedFractions(
            minWidthDp, minHeightDp, density, canvasView, pendingWidgetDropX, pendingWidgetDropY
        )
        if (NexusWidgetKinds.isLiveApps(nexusKind)) {
            com.nexus.launcher.ui.widgets.liveapp.LiveAppPlacement.placeEmpty(
                homeScreenViewModel, nexusKind, targetPage, xFrac, yFrac
            )
        } else if (NexusWidgetKinds.isAppBox(nexusKind)) {
            com.nexus.launcher.ui.widgets.appbox.AppBoxPlacement.placeEmpty(
                homeScreenViewModel, nexusKind, targetPage, xFrac, yFrac
            )
        } else if (NexusWidgetKinds.isShortcutBox(nexusKind)) {
            com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxPlacement.placeEmpty(
                homeScreenViewModel, nexusKind, targetPage, xFrac, yFrac
            )
        } else {
            com.nexus.launcher.ui.widgets.mosaic.LivingMosaicPlacement.placeEmpty(
                homeScreenViewModel, nexusKind, targetPage, xFrac, yFrac
            )
        }
    }
    fun handleWidgetDrop(info: android.appwidget.AppWidgetProviderInfo, minWidthDp: Int, minHeightDp: Int, x: Float, y: Float) {
        pendingWidgetDropX = x
        pendingWidgetDropY = y
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val allowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
        if (allowed) {
            configureDroppedWidget(appWidgetId, info)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            activity.startActivityForResult(intent, REQUEST_BIND_APPWIDGET)
        }
    }
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CREATE_APPWIDGET && requestCode != REQUEST_BIND_APPWIDGET) {
            return false
        }
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CREATE_APPWIDGET -> finalizeWidget(data)
                REQUEST_BIND_APPWIDGET -> {
                    val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return true
                    val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    configureDroppedWidget(appWidgetId, info)
                }
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            // A setup screen may cancel without data; the id is still ours to give back.
            val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)?.takeIf { it != -1 }
                ?: pendingConfigureWidgetId.takeIf { requestCode == REQUEST_CREATE_APPWIDGET }
                ?: -1
            if (appWidgetId != -1) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
        }
        if (requestCode == REQUEST_CREATE_APPWIDGET) pendingConfigureWidgetId = -1
        return true
    }
    suspend fun rebindRestoredWidgets(pendingItems: List<com.nexus.launcher.data.HomeScreenItem>) =
        restoredRebinder.rebindRestoredWidgets(pendingItems)
    suspend fun rebindRestoredMosaics(pendingItems: List<com.nexus.launcher.data.HomeScreenItem>) =
        restoredRebinder.rebindRestoredMosaics(pendingItems)
    private fun configureDroppedWidget(appWidgetId: Int, appWidgetInfo: android.appwidget.AppWidgetProviderInfo?) {
        if (appWidgetInfo?.configure != null) {
            savePendingState()
            pendingConfigureWidgetId = appWidgetId
            try {
                // Through the host, as the platform documents — a direct launch of the setup
                // screen fails when the app does not export it (enforced from Android 12).
                appWidgetHost.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, REQUEST_CREATE_APPWIDGET, null)
            } catch (e: android.content.ActivityNotFoundException) {
                android.util.Log.w("AppWidgetController", "No setup screen for ${appWidgetInfo.provider}", e)
                pendingConfigureWidgetId = -1
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
        } else {
            val provider = appWidgetInfo?.provider ?: return
            val providerPackage = provider.packageName
            val providerClassName = provider.className
            val (spanX, spanY) = AppWidgetGeometryOps.computeWidgetSpans(
                appWidgetInfo, activity.resources.displayMetrics.density, canvasView, pendingReplaceItem, pendingReplaceSpanX, pendingReplaceSpanY
            )
            val density = activity.resources.displayMetrics.density
            val (cellWidthDp, cellHeightDp) = AppWidgetGeometryOps.computeCellSizeDp(density, canvasView.height, canvasView)
            val minWidthDp = (spanX * cellWidthDp).toInt()
            val minHeightDp = (spanY * cellHeightDp).toInt()
            val (xFrac, yFrac) = AppWidgetGeometryOps.resolvePlacementFractions(
                minWidthDp, minHeightDp, density, canvasView, pendingWidgetDropX, pendingWidgetDropY,
                pendingReplaceItem, pendingReplaceXFraction, pendingReplaceYFraction
            )
            commitWidgetOrMosaicChild(
                appWidgetId, providerPackage, providerClassName,
                pendingReplaceItem?.page ?: canvasView.currentPage, spanX, spanY, xFrac, yFrac
            )
        }
    }
    private fun finalizeWidget(data: Intent?) {
        // Setup screens are meant to return the id, but not all do; fall back to the one we sent.
        val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)?.takeIf { it != -1 }
            ?: pendingConfigureWidgetId.takeIf { it != -1 } ?: return
        activity.lifecycleScope.launch {
            restorePendingState()
            val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            val provider = appWidgetInfo?.provider ?: return@launch
        val providerPackage = provider.packageName
        val providerClassName = provider.className
        val (spanX, spanY) = AppWidgetGeometryOps.computeWidgetSpans(
            appWidgetInfo, activity.resources.displayMetrics.density, canvasView, pendingReplaceItem, pendingReplaceSpanX, pendingReplaceSpanY
        )
        val density = activity.resources.displayMetrics.density
        val (cellWidthDp, cellHeightDp) = AppWidgetGeometryOps.computeCellSizeDp(density, canvasView.height, canvasView)
        val minWidthDp = (spanX * cellWidthDp).toInt()
        val minHeightDp = (spanY * cellHeightDp).toInt()
        val (xFrac, yFrac) = AppWidgetGeometryOps.resolvePlacementFractions(
            minWidthDp, minHeightDp, density, canvasView, pendingWidgetDropX, pendingWidgetDropY,
            pendingReplaceItem, pendingReplaceXFraction, pendingReplaceYFraction
        )
        commitWidgetOrMosaicChild(
            appWidgetId, providerPackage, providerClassName,
            pendingReplaceItem?.page ?: canvasView.currentPage, spanX, spanY, xFrac, yFrac
        )
        }
    }
    private fun commitWidgetOrMosaicChild(
        appWidgetId: Int,
        providerPackage: String,
        providerClassName: String,
        targetPage: Int,
        spanX: Int,
        spanY: Int,
        xFrac: Float,
        yFrac: Float
    ) {
        val mosaicTarget = pendingMosaicItem
        if (mosaicTarget != null) {
            val slot = pendingMosaicItemTargetSlot
            homeScreenViewModel.addWidgetToMosaic(
                mosaicTarget, appWidgetId, providerPackage, providerClassName, slot
            )
            val mosaicView = findWidgetOverlayLayout()?.findMosaic(mosaicTarget.id)
            if (mosaicView != null) {
                val current = mosaicView.currentConfig()
                val currentKids = current.currentChildren().toMutableList()
                val newChild = MosaicChild(appWidgetId, providerPackage, providerClassName)
                if (slot != null) {
                    while (currentKids.size <= slot) currentKids.add(MosaicChild(-1))
                    currentKids[slot] = newChild
                } else {
                    currentKids.add(newChild)
                }
                mosaicView.pulseAndRelayout(current.withCurrentChildren(currentKids))
            }
            pendingMosaicWidgetAddedCallback?.invoke(appWidgetId, providerPackage, providerClassName)
            pendingMosaicWidgetAddedCallback = null
            pendingMosaicItem = null
            pendingMosaicItemTargetSlot = null
            return
        }
        // Drop onto an existing mosaic from the widget picker
        val overlay = findWidgetOverlayLayout()
        if (overlay != null) {
            val canvasLoc = IntArray(2)
            canvasView.getLocationOnScreen(canvasLoc)
            val screenX = canvasLoc[0] + pendingWidgetDropX
            val screenY = canvasLoc[1] + pendingWidgetDropY
            val mosaicHit = com.nexus.launcher.ui.widgets.mosaic.LivingMosaicDropHelper
                .findMosaicAtScreenPoint(overlay, screenX, screenY)
            if (mosaicHit != null) {
                if (com.nexus.launcher.ui.widgets.mosaic.LivingMosaicDropHelper.canAcceptChild(mosaicHit.first)) {
                    homeScreenViewModel.addWidgetToMosaic(
                        mosaicHit.first, appWidgetId, providerPackage, providerClassName
                    )
                    val mCfg = mosaicHit.second.currentConfig()
                    val mKids = mCfg.currentChildren().toMutableList()
                    mKids.add(MosaicChild(appWidgetId, providerPackage, providerClassName))
                    mosaicHit.second.pulseAndRelayout(mCfg.withCurrentChildren(mKids))
                    com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics.confirm(mosaicHit.second)
                } else {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    android.widget.Toast.makeText(activity, activity.getString(com.nexus.launcher.R.string.toast_mosaic_page_is_full), android.widget.Toast.LENGTH_SHORT).show()
                    com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics.confirm(mosaicHit.second)
                }
                return
            }
        }
        val replacing = pendingReplaceItem
        homeScreenViewModel.addWidgetToHomeScreen(
            appWidgetId = appWidgetId,
            providerPackage = providerPackage,
            page = pendingReplaceItem?.page ?: targetPage,
            spanX = spanX,
            spanY = spanY,
            xFraction = xFrac,
            yFraction = yFrac,
            providerClassName = providerClassName
        )
        if (replacing != null) {
            appWidgetHost.deleteAppWidgetId(replacing.appWidgetId)
            activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                homeScreenViewModel.deleteItem(replacing)
            }
            pendingReplaceItem = null
        }
    }
    private fun savePendingState() {
        AppWidgetPendingStateHelper.save(
            activity, pendingReplaceItem, pendingReplaceXFraction, pendingReplaceYFraction,
            pendingReplaceSpanX, pendingReplaceSpanY, pendingReplacePage,
            pendingMosaicItem, pendingMosaicItemTargetSlot, pendingWidgetDropX, pendingWidgetDropY
        )
    }
    private suspend fun restorePendingState() {
        val state = AppWidgetPendingStateHelper.restore(activity, homeScreenViewModel)
        pendingReplaceItem = state.replaceItem
        pendingReplaceXFraction = state.replaceX
        pendingReplaceYFraction = state.replaceY
        pendingReplaceSpanX = state.replaceSpanX
        pendingReplaceSpanY = state.replaceSpanY
        pendingReplacePage = state.replacePage
        pendingMosaicItem = state.mosaicItem
        pendingMosaicItemTargetSlot = state.mosaicSlot
        pendingWidgetDropX = state.dropX
        pendingWidgetDropY = state.dropY
    }

    fun removeWidget(item: com.nexus.launcher.data.HomeScreenItem) {
        if (item.appWidgetId != -1) {
            appWidgetHost.deleteAppWidgetId(item.appWidgetId)
        }
        homeScreenViewModel.deleteItem(item)
    }
    private fun findWidgetOverlayLayout(): WidgetOverlayLayout? {
        (activity as? MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.let { return it }
        val main = activity.findViewById<android.widget.FrameLayout>(com.nexus.launcher.R.id.main_container) ?: return null
        for (i in 0 until main.childCount) {
            val child = main.getChildAt(i)
            if (child is WidgetOverlayLayout) return child
        }
        return null
    }

    companion object {
        const val REQUEST_CREATE_APPWIDGET = 1002
        const val REQUEST_BIND_APPWIDGET = 1003
    }
}
