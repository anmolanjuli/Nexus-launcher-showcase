package com.nexus.launcher.reader

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.R
import com.nexus.launcher.feed.FeedBookmarkStore
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumGate

/**
 * UI helper routines for status bar heights, window insets, sharing, and bookmarks in NexusReaderActivity.
 */
object NexusReaderUiHelper {

    fun getStatusBarHeight(window: Window, resources: Resources, dp: Float): Int {
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val top = insets?.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        )?.top
        if (top != null && top > 0) return top
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val dimen = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
        return if (dimen >= (24 * dp).toInt()) dimen else (48 * dp).toInt()
    }

    fun setupWindowInsets(
        rootLayout: View,
        statusBarShelf: View,
        toolbarBuilder: NexusReaderToolbarBuilder,
        scrollView: ScrollView,
        webView: View,
        scrollProgress: ProgressBar,
        dp: Float
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val sb = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top.coerceAtLeast((24 * dp).toInt())
            val nb = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom.coerceAtLeast((16 * dp).toInt())

            val shelfLp = statusBarShelf.layoutParams as? FrameLayout.LayoutParams
            if (shelfLp != null && shelfLp.height != sb) {
                shelfLp.height = sb
                statusBarShelf.layoutParams = shelfLp
            }
            toolbarBuilder.updateInsets(sb)
            scrollView.setPadding(0, sb + (56 * dp).toInt(), 0, nb + (40 * dp).toInt())
            val webLp = webView.layoutParams as? FrameLayout.LayoutParams
            if (webLp != null) {
                webLp.topMargin = sb + (56 * dp).toInt()
                webView.layoutParams = webLp
            }
            val progLp = scrollProgress.layoutParams as? FrameLayout.LayoutParams
            if (progLp != null) {
                progLp.topMargin = sb + (54 * dp).toInt()
                scrollProgress.layoutParams = progLp
            }
            insets
        }
    }

    fun handleBookmarkToggle(
        context: Context,
        articleUrl: String,
        toolbarBuilder: NexusReaderToolbarBuilder
    ) {
        if (!PremiumGate.allow(context, PremiumFeature.EINK_FEED)) {
            return
        }
        val isSaved = FeedBookmarkStore.toggleBookmark(context, articleUrl)
        toolbarBuilder.setBookmarked(isSaved)
    }

    fun handleShare(
        context: Context,
        articleTitle: String,
        articleUrl: String
    ) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "$articleTitle\n$articleUrl")
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.nexus_reader_action_share)))
    }

    fun calculateScrollProgress(scrollView: ScrollView): Int {
        val child = scrollView.getChildAt(0) ?: return 0
        val totalScroll = (child.height - scrollView.height).coerceAtLeast(1)
        return ((scrollView.scrollY.toFloat() / totalScroll) * 100).toInt().coerceIn(0, 100)
    }
}
