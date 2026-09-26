package com.nexus.launcher.reader.doc

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Manages window insets, status bar/navigation bar dimension queries, and insets dispatching for document readers.
 */
object NexusDocumentInsetsHelper {

    fun getStatusBarHeight(context: Context, dp: Float): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else (24 * dp).toInt()
    }

    fun getNavigationBarHeight(context: Context, dp: Float): Int {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else (24 * dp).toInt()
    }

    fun setupInsetsListener(
        rootLayout: View,
        contentContainer: FrameLayout,
        statusBarShelf: View,
        toolbarBuilder: NexusDocumentToolbarBuilder,
        dp: Float,
        onInsetsChanged: (statusBar: Int, navBar: Int) -> Unit
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )
            val sb = statusBarInsets.top.coerceAtLeast((24 * dp).toInt())
            val nb = navBarInsets.bottom.coerceAtLeast((16 * dp).toInt())

            val lp = contentContainer.layoutParams as? FrameLayout.LayoutParams
            if (lp != null && (lp.topMargin != sb || lp.bottomMargin != nb)) {
                lp.topMargin = sb
                lp.bottomMargin = nb
                contentContainer.layoutParams = lp
            }
            val shelfLp = statusBarShelf.layoutParams as? FrameLayout.LayoutParams
            if (shelfLp != null && shelfLp.height != sb) {
                shelfLp.height = sb
                statusBarShelf.layoutParams = shelfLp
            }
            toolbarBuilder.updateInsets(sb, nb)
            onInsetsChanged(sb, nb)
            insets
        }
    }
}
