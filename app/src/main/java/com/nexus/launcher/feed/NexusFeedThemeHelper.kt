package com.nexus.launcher.feed

import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Handles background fills, chrome translucency calculation, and status bar metrics for NexusFeedPage.
 */
object NexusFeedThemeHelper {

    fun applyBackground(view: View, tokens: NexusColorTokens, isEInk: Boolean) {
        if (isEInk) {
            view.background = null
            view.setBackgroundColor(tokens.bg)
            return
        }
        val alpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.55f) * 255).toInt()
        if (tokens.bgTop != null && tokens.bgBottom != null) {
            view.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    (alpha shl 24) or (tokens.bgTop and 0x00FFFFFF),
                    (alpha shl 24) or (tokens.bgBottom and 0x00FFFFFF)
                )
            )
        } else {
            view.background = null
            view.setBackgroundColor((alpha shl 24) or (tokens.bg and 0x00FFFFFF))
        }
    }

    fun chromeFillColor(baseColor: Int): Int {
        val alpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255).toInt()
        return (alpha shl 24) or (baseColor and 0x00FFFFFF)
    }

    fun getStatusBarHeight(context: android.content.Context, resources: Resources, dp: Float): Int {
        if (context is android.app.Activity) {
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(context.window.decorView)
            val top = insets?.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )?.top
            if (top != null && top > 0) return top
        }
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val dimen = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
        return if (dimen >= (24 * dp).toInt()) dimen else (48 * dp).toInt()
    }

    fun getStatusBarHeight(resources: Resources, dp: Float): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val dimen = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
        return if (dimen >= (24 * dp).toInt()) dimen else (48 * dp).toInt()
    }

    fun applyHeaderTokens(
        menuBtn: android.widget.ImageView,
        searchBtn: android.widget.ImageView,
        titleView: android.widget.TextView,
        emptyView: android.widget.TextView,
        tokens: NexusColorTokens,
        isEInk: Boolean
    ) {
        menuBtn.imageTintList = android.content.res.ColorStateList.valueOf(tokens.textPrimary)
        searchBtn.imageTintList = android.content.res.ColorStateList.valueOf(tokens.textPrimary)
        if (isEInk) {
            titleView.typeface = NexusFeedEInkStyler.serifBold
            titleView.setTextColor(tokens.textPrimary)
            emptyView.typeface = NexusFeedEInkStyler.serifRegular
            emptyView.setTextColor(tokens.textSecondary)
        } else {
            titleView.typeface = android.graphics.Typeface.DEFAULT
            com.nexus.launcher.typography.NexusTypeScale.title.bindTo(titleView, tokens.textPrimary)
            emptyView.typeface = android.graphics.Typeface.DEFAULT
            com.nexus.launcher.typography.NexusTypeScale.body.bindTo(emptyView, tokens.textSecondary)
        }
    }

    fun createEmptyView(context: android.content.Context, dp: Float, tokens: NexusColorTokens): android.widget.TextView {
        return android.widget.TextView(context).apply {
            com.nexus.launcher.typography.NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            gravity = android.view.Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding((32 * dp).toInt(), (100 * dp).toInt(), (32 * dp).toInt(), (32 * dp).toInt())
            visibility = View.GONE
        }
    }

    fun getTabTitleRes(tab: NexusFeedBottomBar.Tab): Int = when (tab) {
        NexusFeedBottomBar.Tab.FEED -> com.nexus.launcher.R.string.nexus_feed_tab_feed
        NexusFeedBottomBar.Tab.EXPLORE -> com.nexus.launcher.R.string.nexus_feed_tab_explore
        NexusFeedBottomBar.Tab.SAVED -> com.nexus.launcher.R.string.nexus_feed_tab_saved
        NexusFeedBottomBar.Tab.LIBRARY -> com.nexus.launcher.R.string.nexus_feed_tab_library
        NexusFeedBottomBar.Tab.SETTINGS -> com.nexus.launcher.R.string.nexus_feed_tab_settings
    }
}
