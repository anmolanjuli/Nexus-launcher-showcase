package com.nexus.launcher.ui.backup

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.ItemPosition
import com.nexus.launcher.data.ShapeAwareHomeScreenDao
import com.nexus.launcher.ui.canvas.LandscapeGridSpec

/**
 * Hand-placed positions for non-portrait shapes (`item_positions` rows) in backups — the manifest's
 * `itemPositions` array. Portrait positions ride the items themselves; everything not placed by
 * hand in another shape is derived again from them after a restore, so only manual rows are kept.
 *
 * Rows are keyed by the item's backed-up `id` and re-keyed through the restore's old -> new id map.
 * A backup written before this existed has no array and restores exactly as before.
 */
internal object BackupItemPositions {

    private const val TAG = "BackupItemPositions"
    private const val KEY = "itemPositions"

    /** Adds the manual rows for [dao]'s items to [manifest]; no-op if there are none. */
    suspend fun addTo(manifest: JsonObject, dao: HomeScreenDao) {
        val positions = ShapeAwareHomeScreenDao.positionsOf(dao)?.getAllPositionsDebug()
            ?.filter { it.isManual }.orEmpty()
        if (positions.isEmpty()) return
        val arr = JsonArray()
        positions.forEach { p ->
            arr.add(JsonObject().apply {
                addProperty("itemId", p.itemId)
                addProperty("shape", p.shape)
                addProperty("page", p.page)
                addProperty("column", p.column)
                addProperty("row", p.row)
                addProperty("xFraction", p.xFraction)
                addProperty("yFraction", p.yFraction)
                addProperty("spanX", p.spanX)
                addProperty("spanY", p.spanY)
            })
        }
        manifest.add(KEY, arr)
    }

    /**
     * Writes the manifest's rows back under the restored items' new ids. Rows for items that were
     * not restored are dropped, and so are landscape rows that do not fit this device's landscape
     * grid (a backup from another phone) — those items are simply derived instead.
     */
    suspend fun restore(
        context: Context,
        manifest: JsonObject,
        oldIdToNewId: Map<Int, Long>,
        dao: HomeScreenDao
    ) {
        val arr = manifest.getAsJsonArray(KEY) ?: return
        val positions = ShapeAwareHomeScreenDao.positionsOf(dao) ?: return
        val landscapeSize = LandscapeGridSpec.sizeIfMeasured(context)
        val rows = arr.mapNotNull { el ->
            try {
                val o = el.asJsonObject
                val newId = oldIdToNewId[o.get("itemId").asInt] ?: return@mapNotNull null
                val p = ItemPosition(
                    itemId = newId.toInt(),
                    shape = o.get("shape").asString,
                    page = o.get("page").asInt,
                    column = o.get("column").asInt,
                    row = o.get("row").asInt,
                    xFraction = o.get("xFraction").asFloat,
                    yFraction = o.get("yFraction").asFloat,
                    spanX = o.get("spanX").asInt,
                    spanY = o.get("spanY").asInt,
                    isManual = true
                )
                val fits = p.shape != ItemPosition.SHAPE_PHONE_LANDSCAPE || landscapeSize == null ||
                    (p.column + p.spanX <= landscapeSize.first && p.row + p.spanY <= landscapeSize.second)
                if (fits) p else null
            } catch (e: Exception) {
                Log.w(TAG, "Skipping unreadable position entry", e)
                null
            }
        }
        if (rows.isNotEmpty()) positions.upsertPositions(rows)
        Log.d(TAG, "Restored ${rows.size} of ${arr.size()} hand-placed positions")
    }
}
