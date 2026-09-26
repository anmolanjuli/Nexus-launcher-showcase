package com.nexus.launcher.ui.drawercategories

import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.data.prefs.DrawerLayoutModes
import com.nexus.launcher.domain.model.AppModel
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.MainViewModel
import com.nexus.launcher.ui.canvas.DrawerProgress
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.canvas.SideInsets
import com.nexus.launcher.ui.model.DisplayItem
import com.nexus.launcher.ui.model.LauncherState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CategoriesDrawerController(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val viewModel: MainViewModel,
) {
    private var host: CategoriesDrawerHost? = null
    private var lastBindKey: String? = null
    private var lastSnapshot: BindSnapshot? = null

    fun attach(container: FrameLayout) {
        if (host != null) return
        val overlay = CategoriesDrawerHost(activity).apply {
            visibility = View.GONE
            val canvas = this@CategoriesDrawerController.canvasView
            onAppClick = { app -> CategoriesDrawerActions.launch(activity, canvas, app) }
            onAppLongPress = { app, tile ->
                CategoriesDrawerActions.onLongPress(activity, canvas, app, tile)
            }
            canvasView = canvas
        }
        val canvasIndex = container.indexOfChild(canvasView).coerceAtLeast(0)
        container.addView(
            overlay,
            canvasIndex + 1,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        host = overlay
        CategoriesDrawerJump.setListener { id -> overlay.jumpToCategory(id) }
        CategoriesDrawerMenu.setListener { app, tile ->
            CategoriesDrawerActions.showMenu(activity, canvasView, app, tile)
        }
        activity.drawerChromeViews().forEach { it.bringToFront() }
        val previousDrag = canvasView.onDragStarted
        canvasView.onDragStarted = { item, x, y ->
            overlay.visibility = View.INVISIBLE
            previousDrag?.invoke(item, x, y)
        }
        // A theme change moves no app and no category, so nothing else here would emit — but
        // every card, plate and label is painted from the theme's tokens.
        com.nexus.launcher.theme.ThemeObserver.observe(activity, activity.lifecycleScope) {
            val snapshot = lastSnapshot ?: return@observe
            lastBindKey = null
            activity.lifecycleScope.launch { bind(snapshot) }
        }
        chainDrawerProgress(overlay)
        chainDrawerState(overlay)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val categoryTick = combine(
                    viewModel.filteredDrawerItems,
                    viewModel.drawerCategories.revision,
                ) { _, revision -> revision }
                combine(
                    viewModel.nexusSettings,
                    viewModel.apps,
                    viewModel.recentApps,
                    viewModel.searchQuery,
                    categoryTick,
                ) { settings, apps, recents, query, revision ->
                    BindSnapshot(
                        enabled = settings.drawerGridOrList == DrawerLayoutModes.CATEGORIES,
                        iconSize = settings.drawerIconSizeMultiplier,
                        columns = settings.drawerColumns,
                        style = settings.drawerCategoryLayout,
                        searching = query.isNotBlank() || canvasView.isSearchMode,
                        showRail = settings.drawerShowRail && settings.drawerSortOrder == "az",
                        bottomChrome = canvasView.drawerChrome.bottomReservePx > 0,
                        apps = apps,
                        recents = recents,
                        folders = viewModel.filteredDrawerItems.value.filter {
                            it.intent?.action == "nexus.folder.OPEN"
                        },
                        revision = revision,
                    )
                }.collect { snapshot ->
                    lastSnapshot = snapshot
                    bind(snapshot)
                }
            }
        }
    }

    private fun chainDrawerProgress(overlay: CategoriesDrawerHost) {
        val previous = canvasView.onDrawerAnimationProgress
        canvasView.onDrawerAnimationProgress = { progress ->
            previous?.invoke(progress)
            applyDrawerState(overlay, progress)
        }
    }

    /**
     * Shows, fades and transforms the overlay for the drawer at [progress]. Called on every drawer
     * move and also after every bind: switching layout from the overflow card happens with the
     * drawer standing still, and when only movement re-decided visibility, Spatial stayed drawn
     * (and took the taps) over Grid, and a fresh Categories stayed blank until the next swipe.
     */
    private fun applyDrawerState(overlay: CategoriesDrawerHost, progress: Float) {
        overlay.translationY = 0f
        overlay.alpha = progress
        // In Categories mode this overlay *is* the drawer's contents (the canvas skips its
        // icons), so without this Cube or Tilt would roll an empty sheet.
        com.nexus.launcher.ui.canvas.Drawer3DViewTransform.applyDrawer(overlay, canvasView, progress)
        // Fading a whole view tree is cheaper drawn once into a layer, as the dock does.
        val layer = if (progress > 0f && progress < 1f) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        if (overlay.layerType != layer) overlay.setLayerType(layer, null)
        val settings = viewModel.nexusSettings.value
        overlay.visibility = CategoriesDrawerActions.overlayVisibility(
            enabled = settings.drawerGridOrList == DrawerLayoutModes.CATEGORIES,
            progress = progress,
            forwarding = overlay.isForwarding,
            dragging = canvasView.dragHandler.isIconDragActive,
        )
        applyPadding(overlay)
    }

    private fun chainDrawerState(overlay: CategoriesDrawerHost) {
        val previous = canvasView.onStateChanged
        canvasView.onStateChanged = { state ->
            previous?.invoke(state)
            if (state == LauncherState.HOME && !canvasView.dragHandler.isIconDragActive) {
                overlay.visibility = View.GONE
                overlay.translationY = 0f
            }
        }
    }

    private suspend fun bind(snapshot: BindSnapshot) {
        val overlay = host ?: return
        if (!snapshot.enabled) {
            lastBindKey = null
            canvasView.railRenderer.isVisible = snapshot.showRail
            applyDrawerState(overlay, DrawerProgress.of(canvasView)) // hides it: mode is off
            return
        }
        // The canvas keeps drawing its own A-Z rail beneath this overlay, where it shows through
        // between the cards; the Categories list brings its own (StripAlphabetRail).
        canvasView.railRenderer.isVisible = false
        CategoryIconSize.set(
            snapshot.iconSize, snapshot.columns,
            com.nexus.launcher.ui.canvas.HomeGridArea.usableWidth(canvasView),
            activity.resources.displayMetrics.density,
        )
        if (snapshot.searching) {
            applyPadding(overlay)
            return
        }
        val hidden = activity.preferenceManager.getHiddenApps()
        val renamed = viewModel.renamedApps.value
        val visible = snapshot.apps
            .filter { it.packageName !in hidden }
            .map { app -> renamed[app.packageName]?.let { app.copy(label = it) } ?: app }
        // The app list is rebuilt whenever icons change (icon pack, theme) — its identity is
        // what tells us to redraw, since the labels and categories in the key have not moved.
        val key = "${snapshot.style}|${snapshot.revision}|${snapshot.showRail}|${snapshot.bottomChrome}|" +
            System.identityHashCode(snapshot.apps).toString() + "|" +
            snapshot.iconSize + "|" + snapshot.columns + "|" +
            com.nexus.launcher.theme.ThemeObserver.currentTokens(activity).hashCode() + "|" +
            snapshot.recents.joinToString { it.packageName } + "|" +
            visible.joinToString { "${it.packageName}:${it.categoryId}:${it.label}" } +
            snapshot.folders.joinToString { "${it.intent?.getLongExtra("folderId", -1L)}:${it.categoryName}" } +
            viewModel.drawerCategories.keys.value.joinToString()
        if (key == lastBindKey) {
            applyDrawerState(overlay, DrawerProgress.of(canvasView))
            return
        }
        lastBindKey = key
        // Reads app shortcuts through LauncherApps and loads their icons — never on the
        // main thread, where it stuttered the drawer on every rebuild.
        val model = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CategoriesDrawerMapper.build(
                context = activity,
                apps = visible,
                recents = snapshot.recents,
                folders = snapshot.folders,
                drawerCategories = viewModel.drawerCategories,
                canvas = canvasView,
            )
        }
        overlay.bind(
            style = snapshot.style,
            categories = model.categories,
            frequent = CategoriesDrawerMapper.frequent(model.categories, snapshot.recents),
            untaggedFolders = model.untaggedFolders,
            showRail = snapshot.showRail,
            bottomChrome = snapshot.bottomChrome,
        )
        applyDrawerState(overlay, DrawerProgress.of(canvasView)) // show it if the drawer is open
        activity.drawerChromeViews().forEach { it.bringToFront() }
    }

    private var lastPadding: IntArray? = null

    /**
     * Setting padding lays the whole overlay out again, and this used to run on every frame of
     * the drawer slide — which is what made opening and closing stutter. Only a real change now.
     */
    private fun applyPadding(overlay: CategoriesDrawerHost) {
        val density = overlay.resources.displayMetrics.density
        val gap = (8f * density).toInt()
        val extraTop = if (canvasView.drawerChrome.topReservePx > 0) gap else 0
        val extraBottom = if (canvasView.drawerChrome.bottomReservePx > 0) gap else 0
        val next = intArrayOf(
            SideInsets.left,
            canvasView.topInset + canvasView.drawerChrome.topReservePx + extraTop,
            SideInsets.right,
            canvasView.bottomBarHeight + canvasView.drawerChrome.bottomReservePx + extraBottom,
        )
        if (lastPadding?.contentEquals(next) == true) return
        lastPadding = next
        overlay.setPadding(next[0], next[1], next[2], next[3])
    }

    private data class BindSnapshot(
        val enabled: Boolean,
        val iconSize: Float,
        val columns: Int,
        val style: String,
        val searching: Boolean,
        val showRail: Boolean,
        val bottomChrome: Boolean,
        val apps: List<AppModel>,
        val recents: List<AppModel>,
        val folders: List<DisplayItem>,
        val revision: Int,
    )
}
