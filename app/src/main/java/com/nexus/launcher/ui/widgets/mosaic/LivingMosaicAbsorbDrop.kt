package com.nexus.launcher.ui.widgets.mosaic

import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout

/** Absorb a home-screen widget into a Living Mosaic on drop. */
object LivingMosaicAbsorbDrop {

    enum class Result { NO_TARGET, ABSORBED, REJECTED }

    /**
     * A target that cannot accept is rejected rather than falling through behind the Mosaic.
     */
    fun tryAbsorb(
        activity: MainActivity,
        overlay: WidgetOverlayLayout,
        widgetView: View,
        widgetItem: HomeScreenItem,
        highlightedTarget: Pair<HomeScreenItem, LivingMosaicView>? = null,
        onAbsorbed: () -> Unit
    ): Result {
        val hit = highlightedTarget ?: LivingMosaicDropHelper.findMosaicOverlappingView(overlay, widgetView)
            ?: return Result.NO_TARGET
        val (mosaicItem, mosaicView) = hit
        if (!LivingMosaicDropHelper.canAcceptChild(mosaicItem)) {
            Toast.makeText(activity, activity.getString(com.nexus.launcher.R.string.toast_mosaic_page_is_full), Toast.LENGTH_SHORT).show()
            LivingMosaicHaptics.confirm(mosaicView)
            return Result.REJECTED
        }
        val hsv = ViewModelProvider(activity)[HomeScreenViewModel::class.java]
        hsv.absorbWidgetIntoMosaic(mosaicItem, widgetItem)
        widgetView.visibility = View.GONE
        val cfg = MosaicConfig.parse(mosaicItem.folderConfigJson)
        mosaicView.pulseAndRelayout(
            cfg.withCurrentChildren(
                cfg.currentChildren() + MosaicChild(
                    widgetItem.appWidgetId,
                    widgetItem.packageName,
                    widgetItem.providerClassName.orEmpty(),
                    spanX = widgetItem.spanX,
                    spanY = widgetItem.spanY
                )
            )
        )
        LivingMosaicHaptics.confirm(mosaicView)
        onAbsorbed()
        return Result.ABSORBED
    }
}
