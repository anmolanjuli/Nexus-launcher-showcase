package com.nexus.launcher.ui.widgets

import android.content.Context
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel

/**
 * Persists and restores transient pending widget placement/replace state across configuration activities.
 */
object AppWidgetPendingStateHelper {

    private const val PREFS_NAME = "widget_pending_state"

    fun save(
        context: Context,
        replaceItem: HomeScreenItem?,
        replaceX: Float,
        replaceY: Float,
        replaceSpanX: Int,
        replaceSpanY: Int,
        replacePage: Int,
        mosaicItem: HomeScreenItem?,
        mosaicSlot: Int?,
        dropX: Float,
        dropY: Float
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("replace_item_id", replaceItem?.id ?: -1)
            putFloat("replace_x", replaceX)
            putFloat("replace_y", replaceY)
            putInt("replace_span_x", replaceSpanX)
            putInt("replace_span_y", replaceSpanY)
            putInt("replace_page", replacePage)
            putInt("mosaic_item_id", mosaicItem?.id ?: -1)
            putInt("mosaic_slot", mosaicSlot ?: -1)
            putFloat("drop_x", dropX)
            putFloat("drop_y", dropY)
        }.apply()
    }

    data class RestoredState(
        val replaceItem: HomeScreenItem?,
        val replaceX: Float,
        val replaceY: Float,
        val replaceSpanX: Int,
        val replaceSpanY: Int,
        val replacePage: Int,
        val mosaicItem: HomeScreenItem?,
        val mosaicSlot: Int?,
        val dropX: Float,
        val dropY: Float
    )

    suspend fun restore(context: Context, viewModel: HomeScreenViewModel): RestoredState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val replaceId = prefs.getInt("replace_item_id", -1)
        val mosaicId = prefs.getInt("mosaic_item_id", -1)
        var repItem: HomeScreenItem? = null
        var mosItem: HomeScreenItem? = null
        if (replaceId != -1 || mosaicId != -1) {
            val allItems = viewModel.getAllItemsSnapshot()
            if (replaceId != -1) repItem = allItems.find { it.id == replaceId }
            if (mosaicId != -1) mosItem = allItems.find { it.id == mosaicId }
        }
        val slot = prefs.getInt("mosaic_slot", -1)
        return RestoredState(
            replaceItem = repItem,
            replaceX = prefs.getFloat("replace_x", 0.5f),
            replaceY = prefs.getFloat("replace_y", 0.5f),
            replaceSpanX = prefs.getInt("replace_span_x", 2),
            replaceSpanY = prefs.getInt("replace_span_y", 2),
            replacePage = prefs.getInt("replace_page", 0),
            mosaicItem = mosItem,
            mosaicSlot = if (slot != -1) slot else null,
            dropX = prefs.getFloat("drop_x", 0f),
            dropY = prefs.getFloat("drop_y", 0f)
        )
    }
}
