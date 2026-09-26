package com.nexus.launcher.feed

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/** One persistent content container per bottom-bar tab — switching tabs toggles visibility
 *  instead of tearing down and rebuilding the whole scroll area on every tap. */
class NexusFeedTabContentHost(
    context: Context,
    dp: Float
) : FrameLayout(context) {

    private val padH = (20 * dp).toInt()
    private val padBottom = (110 * dp).toInt()
    private val containers = NexusFeedBottomBar.Tab.entries.associateWith { tab ->
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, (4 * dp).toInt(), padH, padBottom)
            visibility = if (tab == NexusFeedBottomBar.Tab.FEED) View.VISIBLE else View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }.also { addView(it) }
    }

    var activeTab: NexusFeedBottomBar.Tab = NexusFeedBottomBar.Tab.FEED
        private set

    fun container(tab: NexusFeedBottomBar.Tab): LinearLayout = containers.getValue(tab)

    fun show(tab: NexusFeedBottomBar.Tab) {
        if (tab == activeTab) return
        containers[activeTab]?.visibility = View.GONE
        containers[tab]?.visibility = View.VISIBLE
        activeTab = tab
    }
}
