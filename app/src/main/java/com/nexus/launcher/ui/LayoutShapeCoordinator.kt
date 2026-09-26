package com.nexus.launcher.ui

import android.content.res.Configuration
import android.graphics.RectF
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.data.ItemPosition
import com.nexus.launcher.data.LayoutShapeState
import com.nexus.launcher.data.ShapeAwareHomeScreenDao
import com.nexus.launcher.ui.canvas.LandscapeGridSpec
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.canvas.LayoutProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tells [LayoutShapeState] which layout shape the home screen is showing, and keeps the live
 * derivation of that shape's positions ([ShapeLayoutDeriver]) fed with its measured grid.
 *
 * Positions in a non-base shape are derived on every read until the user places an item by
 * hand there; only those manual placements are stored (see ShapeAwareHomeScreenDao).
 */
class LayoutShapeCoordinator(
    private val activity: ComponentActivity,
    private val canvasView: () -> LauncherCanvasView
) {

    private var settleJob: Job? = null

    /**
     * Created on first use, not at construction: MainActivity builds this coordinator in a field
     * initializer, before the activity has a context. Holds only the application context.
     */
    private val deriver by lazy { ShapeLayoutDeriver(activity.applicationContext) }

    private val dao by lazy {
        dagger.hilt.EntryPoints.get(
            activity.applicationContext, com.nexus.launcher.di.DaoEntryPoint::class.java
        ).homeScreenDao()
    }

    fun onStart() {
        LayoutShapeState.deriver = deriver::derive
        refresh()
    }

    fun onConfigurationChanged() {
        refresh()
        handOverMappedItems()
    }

    /**
     * Puts items in their positions for the new shape before the first frame after a rotation.
     * Waiting for the item stream (DAO -> view model -> canvas / widget collectors) let a frame
     * or two draw the old shape's positions in the new orientation.
     */
    private fun handOverMappedItems() {
        val items = (dao as? ShapeAwareHomeScreenDao)?.mappedNow()
            ?.filter { it.page != HomeScreenViewModel.DOCK_CONTAINER } ?: return
        canvasView().updateHomeScreenItems(items)
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.findWidgetOverlay(activity)?.let {
            it.replaceCachedItems(items)
            // Replacing the cache does not move anything on its own, and the widgets are still
            // laid out for the shape we just left.
            it.rebindCachedWidgets()
        }
    }

    /** Background writers (e.g. a restore from Settings) must write base positions. */
    fun onStop() {
        settleJob?.cancel()
        LayoutShapeState.setActive(ItemPosition.SHAPE_PHONE_PORTRAIT)
    }

    private fun refresh() {
        val shape = shapeFor(activity.resources.configuration)
        LayoutShapeState.setActive(shape)
        if (shape != ItemPosition.SHAPE_PHONE_PORTRAIT) settleWhenLaidOut(shape, attemptsLeft = 20)
    }

    /** Waits until the canvas has been laid out in the new orientation, then records its grid. */
    private fun settleWhenLaidOut(shape: String, attemptsLeft: Int) {
        val view = canvasView()
        view.post {
            if (LayoutShapeState.active.value != shape) return@post
            val sizeMatches = !isLandscapeShape(shape) || view.viewWidth > view.viewHeight
            val laidOut = view.viewWidth > 0 && view.viewHeight > 0 && sizeMatches &&
                isLandscapeShape(shape) == view.isLandscape &&
                view.homeGridCells.size == view.currentGridCols * view.currentGridRows
            if (!laidOut) {
                if (attemptsLeft > 0) settleWhenLaidOut(shape, attemptsLeft - 1)
                return@post
            }
            settleJob?.cancel()
            if (isLandscapeShape(shape) && LandscapeGridSpec.ensureMeasured(view)) {
                // The landscape grid size just changed (first measure, or the portrait grid
                // changed). Hand-placed landscape positions that no longer fit the new size go;
                // the rest are kept, and everything else is derived again for the new size.
                val size = LandscapeGridSpec.sizeIfMeasured(view.context)
                Log.d(TAG, "landscape grid measured: $size")
                settleJob = activity.lifecycleScope.launch {
                    val positions = ShapeAwareHomeScreenDao.positionsOf(dao)
                    if (positions != null && size != null) withContext(Dispatchers.IO) {
                        positions.getPositionsForShape(shape)
                            .filter { it.column + it.spanX > size.first || it.row + it.spanY > size.second }
                            .forEach { positions.deletePosition(it.itemId, shape) }
                    }
                    view.recalculateLayout()
                    view.invalidate()
                    settleWhenLaidOut(shape, attemptsLeft)
                }
                return@post
            }
            recordGeometry(shape, view)
        }
    }

    /**
     * Gives the deriver the shape's real cell rectangles, so derived widget fractions put
     * widgets exactly on their cells, and re-derives if they changed.
     */
    private fun recordGeometry(shape: String, view: LauncherCanvasView) {
        val overlayHeight = com.nexus.launcher.ui.folder.FolderBlurCoordinator
            .findWidgetOverlay(activity)?.height?.takeIf { it > 0 } ?: view.viewHeight
        val geometry = ShapeLayoutDeriver.Geometry(
            columns = view.currentGridCols,
            rows = view.currentGridRows,
            cells = view.homeGridCells.map { RectF(it) },
            frameLeft = view.gridAreaLeft.toFloat(),
            frameWidth = view.gridAreaWidth.toFloat(),
            overlayHeight = overlayHeight.toFloat(),
            viewWidth = view.viewWidth.toFloat(),
        )
        if (deriver.recordGeometry(shape, geometry)) {
            Log.d(TAG, "shape=$shape grid ${geometry.columns}x${geometry.rows} recorded; re-deriving")
            LayoutShapeState.invalidateDerived()
            // The rotation's own rebind ran before this grid existed, so every widget is still
            // sitting where the old shape put it. Hand the newly derived positions over now.
            handOverMappedItems()
        }
    }

    private fun isLandscapeShape(shape: String) = shape == ItemPosition.SHAPE_PHONE_LANDSCAPE

    companion object {
        private const val TAG = "LayoutShape"

        fun shapeFor(config: Configuration): String = when {
            LayoutProfile.of(config) == LayoutProfile.LARGE -> ItemPosition.SHAPE_LARGE
            config.orientation == Configuration.ORIENTATION_LANDSCAPE -> ItemPosition.SHAPE_PHONE_LANDSCAPE
            else -> ItemPosition.SHAPE_PHONE_PORTRAIT
        }
    }
}
