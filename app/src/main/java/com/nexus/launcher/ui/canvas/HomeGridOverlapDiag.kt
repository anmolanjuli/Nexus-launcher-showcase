package com.nexus.launcher.ui.canvas

import android.util.Log
import com.nexus.launcher.util.NexusDiag

/**
 * TEMPORARY diagnostic for home vs preview grid geometry.
 * Remove after repro numbers are reviewed. Do not use for product logic.
 */
object HomeGridOverlapDiag {
    const val TAG = "HomeGridOverlapDiag"

    const val CALLER_PREVIEW = "preview"
    const val CALLER_HOME = "home"

    @Volatile var homeDockSource: String = "unset"
        private set
    @Volatile var homeDockMeasuredHeightPx: Int = -1
        private set
    @Volatile var homeDockTopInCanvasPx: Int = -1
        private set
    @Volatile var homeDockIndicatorBandPx: Int = -1
        private set

    private val lastGridByCaller = mutableMapOf<String, String>()
    private val lastIconByCaller = mutableMapOf<String, String>()
    private val lastDockByCaller = mutableMapOf<String, String>()
    private var lastClipKey: String = ""

    fun noteHomeDock(
        source: String,
        measuredHeightPx: Int,
        dockTopInCanvasPx: Int,
        indicatorBandPx: Int
    ) {
        if (!NexusDiag.ENABLED) return
        homeDockSource = source
        homeDockMeasuredHeightPx = measuredHeightPx
        homeDockTopInCanvasPx = dockTopInCanvasPx
        homeDockIndicatorBandPx = indicatorBandPx
    }

    fun logDock(
        caller: String,
        source: String,
        reservePx: Int,
        measuredHeightPx: Int = -1,
        dockTopInCanvasPx: Int = -1,
        indicatorBandPx: Int = -1,
        dockIconPx: Int = -1,
        labelBandPx: Int = -1
    ) {
        if (!NexusDiag.ENABLED) return
        if (caller != CALLER_PREVIEW && caller != CALLER_HOME) return
        val key = "$caller|$source|$reservePx|$measuredHeightPx|$dockTopInCanvasPx|" +
            "$indicatorBandPx|$dockIconPx|$labelBandPx"
        if (lastDockByCaller[caller] == key) return
        lastDockByCaller[caller] = key
        Log.d(
            TAG,
            "DOCK caller=$caller source=$source reservePx=$reservePx " +
                "measuredHeightPx=$measuredHeightPx dockTopInCanvasPx=$dockTopInCanvasPx " +
                "indicatorBandPx=$indicatorBandPx dockIconPx=$dockIconPx " +
                "labelBandPx=$labelBandPx"
        )
    }

    fun logGrid(
        caller: String,
        columns: Int,
        rows: Int,
        paddingLrDp: Float,
        paddingTbDp: Float,
        gapHDp: Float,
        gapVDp: Float,
        availableW: Float,
        availableH: Float,
        netW: Float,
        netH: Float,
        cellW: Float,
        cellH: Float,
        statusBarPx: Int = -1,
        topInsetPx: Int = -1,
        dockSource: String = "",
        dockReservePx: Int = -1,
        dockMeasuredHeightPx: Int = -1
    ) {
        if (!NexusDiag.ENABLED) return
        if (caller != CALLER_PREVIEW && caller != CALLER_HOME) return
        val key = "$caller|$columns|$rows|$paddingLrDp|$paddingTbDp|$gapHDp|$gapVDp|" +
            "${availableW.toInt()}|${availableH.toInt()}|${cellW.toInt()}|${cellH.toInt()}|" +
            "$statusBarPx|$topInsetPx|$dockSource|$dockReservePx|$dockMeasuredHeightPx"
        if (lastGridByCaller[caller] == key) return
        lastGridByCaller[caller] = key
        Log.d(
            TAG,
            "GRID caller=$caller cols=$columns rows=$rows " +
                "padLR=${paddingLrDp}dp padTB=${paddingTbDp}dp " +
                "gapH=${gapHDp}dp gapV=${gapVDp}dp " +
                "avail=${fmt(availableW)}x${fmt(availableH)} " +
                "netW=${fmt(netW)} netH=${fmt(netH)} " +
                "cellW=${fmt(cellW)} cellH=${fmt(cellH)} " +
                "statusBarPx=$statusBarPx topInsetPx=$topInsetPx " +
                "dockSource=$dockSource dockReservePx=$dockReservePx " +
                "dockMeasuredHeightPx=$dockMeasuredHeightPx"
        )
    }

    fun logIcon(
        caller: String,
        gridRows: Int,
        multiplier: Float,
        cellW: Float,
        cellH: Float,
        gapHPx: Float,
        gapVPx: Float,
        requestedPx: Float,
        contentW: Float,
        contentH: Float,
        maxSide: Float,
        finalW: Int,
        finalH: Int,
        iconLeft: Int,
        iconTop: Int
    ) {
        if (!NexusDiag.ENABLED) return
        if (caller != CALLER_PREVIEW && caller != CALLER_HOME) return
        val key = "$caller|$gridRows|$multiplier|${cellW.toInt()}|${cellH.toInt()}|" +
            "${gapHPx.toInt()}|${gapVPx.toInt()}|${requestedPx.toInt()}|" +
            "${contentW.toInt()}|${contentH.toInt()}|${maxSide.toInt()}|$finalW|$finalH|" +
            "$iconLeft|$iconTop"
        if (lastIconByCaller[caller] == key) return
        lastIconByCaller[caller] = key
        Log.d(
            TAG,
            "ICON caller=$caller rows=$gridRows multiplier=$multiplier " +
                "cell=${fmt(cellW)}x${fmt(cellH)} gapPx=${fmt(gapHPx)}x${fmt(gapVPx)} " +
                "requestedPx=${fmt(requestedPx)} contentW=${fmt(contentW)} " +
                "contentH=${fmt(contentH)} maxSide=${fmt(maxSide)} " +
                "final=${finalW}x$finalH origin=${iconLeft},${iconTop} " +
                "exceedsCellW=${finalW > cellW} exceedsCellH=${finalH > cellH}"
        )
    }

    fun logHomeClip(
        cellTop: Float,
        iconTopAfterGap: Int,
        iconTopClamped: Int,
        clipTop: Int,
        topInsetPx: Int
    ) {
        if (!NexusDiag.ENABLED) return
        val overflowPx = clipTop - iconTopAfterGap
        val key = "${cellTop.toInt()}|$iconTopAfterGap|$iconTopClamped|$clipTop|$topInsetPx"
        if (lastClipKey == key) return
        lastClipKey = key
        Log.d(
            TAG,
            "CLIP caller=$CALLER_HOME cellTop=${fmt(cellTop)} " +
                "iconTopAfterGap=$iconTopAfterGap iconTopClamped=$iconTopClamped " +
                "clipTop=$clipTop topInsetPx=$topInsetPx " +
                "overflowAboveClipPx=$overflowPx"
        )
    }

    private fun fmt(value: Float): String = "%.1f".format(value)
}
