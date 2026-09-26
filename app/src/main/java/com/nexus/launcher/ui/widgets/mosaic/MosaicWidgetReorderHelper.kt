package com.nexus.launcher.ui.widgets.mosaic

import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.data.HomeScreenItem
import kotlin.math.abs

object MosaicWidgetReorderHelper {

    fun wireReorder(
        handle: TextView,
        row: LinearLayout,
        index: Int,
        item: HomeScreenItem,
        mosaicView: LivingMosaicView,
        scroll: ScrollView,
        dp: Float,
        touchSlop: Int,
        onReorder: (from: Int, to: Int) -> Unit
    ) {
        var startY = 0f
        var startScrollY = 0
        var dragging = false
        var autoScrollDirection = 0
        var lastRawY = 0f
        val autoScrollTick = object : Runnable {
            override fun run() {
                if (!dragging || autoScrollDirection == 0) return
                val before = scroll.scrollY
                scroll.scrollBy(0, autoScrollDirection * (18 * dp).toInt())
                if (scroll.scrollY == before) return
                row.translationY = lastRawY - startY + scroll.scrollY - startScrollY
                row.postDelayed(this, 16L)
            }
        }
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startScrollY = scroll.scrollY
                    dragging = false
                    autoScrollDirection = 0
                    scroll.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && abs(event.rawY - startY) > touchSlop) {
                        dragging = true
                        LivingMosaicHaptics.tick(row)
                        row.alpha = 0.7f
                        row.elevation = 8f * dp
                    }
                    if (dragging) {
                        lastRawY = event.rawY
                        val direction = scrollDirectionFor(scroll, lastRawY, dp)
                        if (direction != autoScrollDirection) {
                            autoScrollDirection = direction
                            if (direction != 0) {
                                LivingMosaicHaptics.tick(row)
                                row.post(autoScrollTick)
                            }
                        }
                        row.translationY = event.rawY - startY + scroll.scrollY - startScrollY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    scroll.requestDisallowInterceptTouchEvent(false)
                    autoScrollDirection = 0
                    row.removeCallbacks(autoScrollTick)
                    if (dragging) {
                        row.alpha = 1f
                        row.elevation = 0f
                        row.translationY = 0f
                        val target = if (event.actionMasked == MotionEvent.ACTION_UP) {
                            targetIndexAt(row.parent as? LinearLayout, scroll, event.rawY, index)
                        } else index
                        if (target != index) {
                            LivingMosaicHaptics.confirm(row)
                            onReorder(index, target)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun scrollDirectionFor(scroll: ScrollView, rawY: Float, dp: Float): Int {
        val location = IntArray(2)
        scroll.getLocationOnScreen(location)
        val edge = (48 * dp).toInt()
        return when {
            rawY < location[1] + edge -> -1
            rawY > location[1] + scroll.height - edge -> 1
            else -> 0
        }
    }

    private fun targetIndexAt(listHost: LinearLayout?, scroll: ScrollView, rawY: Float, fallback: Int): Int {
        if (listHost == null) return fallback
        val scrollLocation = IntArray(2)
        scroll.getLocationOnScreen(scrollLocation)
        val contentY = rawY - scrollLocation[1] + scroll.scrollY
        return (0 until listHost.childCount).mapNotNull { childPosition ->
            val child = listHost.getChildAt(childPosition)
            (child.tag as? Int)?.let { childIndex ->
                childIndex to abs(contentY - (child.top + child.bottom) / 2f)
            }
        }.minByOrNull { it.second }?.first ?: fallback
    }
}
