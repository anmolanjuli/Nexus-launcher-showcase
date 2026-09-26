package com.nexus.launcher.ui.canvas

import android.util.Log
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.util.NexusDiag

/**
 * TEMPORARY diagnostic for home-icon drag/drop occupancy and DB outcome.
 * Also traces Mosaic move persists and every removeItemById (data-loss hunt).
 * Do not use for product logic.
 */
object HomeGridDropDiag {
    const val TAG = "HomeGridDropDiag"

    private var lastHighlightKey: String? = null

    fun itemBrief(item: HomeScreenItem?): String {
        if (item == null) return "none"
        return "id=${item.id} type=${item.itemType} pkg=${item.packageName} " +
            "page=${item.page} cell=${item.column},${item.row} " +
            "span=${item.spanX}x${item.spanY} xf=${"%.2f".format(item.xFraction)} " +
            "yf=${"%.2f".format(item.yFraction)} container=${item.containerId}"
    }

    fun logMosaicPersist(
        trigger: String,
        item: HomeScreenItem,
        xFrac: Float,
        yFrac: Float,
        col: Int,
        row: Int
    ) {
        if (!NexusDiag.ENABLED) return
        Log.d(
            TAG,
            "MOSAIC_PERSIST trigger=$trigger ${itemBrief(item)} " +
                "newXf=${"%.3f".format(xFrac)} newYf=${"%.3f".format(yFrac)} " +
                "newOrigin=$col,$row"
        )
    }

    fun logPositionPersist(
        trigger: String,
        mover: HomeScreenItem,
        newPage: Int,
        newCol: Int,
        newRow: Int,
        allItems: List<HomeScreenItem>,
        willClearGhost: Boolean
    ) {
        if (!NexusDiag.ENABLED) return
        val originHits = occupantsAtCell(
            allItems, mover.page, mover.column, mover.row, mover.id
        )
        val ghostEligible = originHits.filter { it.itemType == 0 || it.itemType == 2 }
        val oldBoxHits = spanHits(
            allItems, mover.page, mover.column, mover.row,
            mover.spanX, mover.spanY, mover.id
        )
        val newBoxHits = spanHits(
            allItems, newPage, newCol, newRow, mover.spanX, mover.spanY, mover.id
        )
        Log.d(
            TAG,
            "POS_PERSIST trigger=$trigger moverType=${mover.itemType} " +
                "isMosaic=${mover.itemType == HomeItemTypes.MOSAIC} " +
                "mover=${itemBrief(mover)} newCell=$newPage,$newCol,$newRow " +
                "willClearGhost=$willClearGhost " +
                "originOccupants=${briefs(originHits)} " +
                "ghostEligible=${briefs(ghostEligible)} " +
                "oldBboxHits=${briefs(oldBoxHits)} " +
                "newBboxHits=${briefs(newBoxHits)}"
        )
    }

    fun logHighlight(
        draggedId: Int,
        draggedSpanX: Int,
        draggedSpanY: Int,
        col: Int,
        row: Int,
        overlap: Boolean,
        occupant: HomeScreenItem?,
        liveCols: Int,
        highlightCols: Int
    ) {
        if (!NexusDiag.ENABLED) return
        val key = "$draggedId|$draggedSpanX|$draggedSpanY|$col|$row|$overlap|${occupant?.id}|$liveCols|$highlightCols"
        if (key == lastHighlightKey) return
        lastHighlightKey = key
        Log.d(
            TAG,
            "HIGHLIGHT draggedId=$draggedId draggedSpan=${draggedSpanX}x$draggedSpanY " +
                "cell=$col,$row overlap=$overlap liveCols=$liveCols highlightCols=$highlightCols " +
                "occupant=${itemBrief(occupant)}"
        )
    }

    fun logDrop(
        source: String,
        draggedId: Int,
        beforeSpanX: Int,
        beforeSpanY: Int,
        beforeCell: String,
        dropCell: String,
        overlap: Boolean,
        occupant: HomeScreenItem?,
        outcome: String,
        extra: String = ""
    ) {
        if (!NexusDiag.ENABLED) return
        Log.d(
            TAG,
            "DROP source=$source draggedId=$draggedId " +
                "dbSpanBefore=${beforeSpanX}x$beforeSpanY beforeCell=$beforeCell " +
                "dropCell=$dropCell overlap=$overlap occupant=${itemBrief(occupant)} " +
                "outcome=$outcome $extra"
        )
    }

    fun logDb(source: String, id: Int, after: HomeScreenItem?, extra: String = "") {
        if (!NexusDiag.ENABLED) return
        Log.d(
            TAG,
            "DB source=$source id=$id exists=${after != null} after=${itemBrief(after)} $extra"
        )
    }

    fun logDelete(source: String, id: Int, extra: String = "", includeStack: Boolean = false) {
        if (!NexusDiag.ENABLED) return
        Log.d(TAG, "DELETE source=$source id=$id $extra")
        if (includeStack) {
            Log.d(TAG, "DELETE_STACK source=$source id=$id\n${stackTop()}")
        }
    }

    fun logIntersecting(
        source: String,
        page: Int,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
        items: List<HomeScreenItem>,
        excludeIds: List<Int>
    ) {
        if (!NexusDiag.ENABLED) return
        val thisRight = col + spanX - 1
        val thisBottom = row + spanY - 1
        val hits = items.filter { item ->
            item.id !in excludeIds &&
                item.page == page &&
                item.containerId == -1L &&
                col <= item.column + item.spanX - 1 &&
                thisRight >= item.column &&
                row <= item.row + item.spanY - 1 &&
                thisBottom >= item.row
        }
        Log.d(
            TAG,
            "INTERSECT source=$source drop=${col},${row} dropSpan=${spanX}x$spanY " +
                "exclude=$excludeIds hits=" +
                hits.joinToString(" ") { "id=${it.id} cell=${it.column},${it.row} span=${it.spanX}x${it.spanY} type=${it.itemType}" }
                    .ifBlank { "none" }
        )
    }

    private fun occupantsAtCell(
        items: List<HomeScreenItem>,
        page: Int,
        col: Int,
        row: Int,
        excludeId: Int
    ): List<HomeScreenItem> {
        return items.filter {
            it.id != excludeId &&
                it.page == page &&
                it.column == col &&
                it.row == row &&
                it.containerId == -1L
        }
    }

    private fun spanHits(
        items: List<HomeScreenItem>,
        page: Int,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
        excludeId: Int
    ): List<HomeScreenItem> {
        val right = col + spanX - 1
        val bottom = row + spanY - 1
        return items.filter { item ->
            item.id != excludeId &&
                item.page == page &&
                item.containerId == -1L &&
                col <= item.column + item.spanX - 1 &&
                right >= item.column &&
                row <= item.row + item.spanY - 1 &&
                bottom >= item.row
        }
    }

    private fun briefs(items: List<HomeScreenItem>): String {
        if (items.isEmpty()) return "none"
        return items.joinToString(" ") { itemBrief(it) }
    }

    private fun stackTop(): String {
        val full = Log.getStackTraceString(Throwable())
        return full.lineSequence().take(14).joinToString("\n")
    }
}
