package com.nexus.launcher.feed

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Handles anchored dropdown menu presentation and trigger highlight styling for NexusFeedPage.
 */
object NexusFeedMenuHelper {

    fun showMenu(
        context: Context,
        menuBtn: ImageView,
        currentTokens: NexusColorTokens,
        dp: Float,
        isEInk: Boolean,
        chromeFillColor: Int,
        isHeadlineOnly: Boolean,
        onHeadlineOnlyChanged: (Boolean) -> Unit,
        onOpenManageSources: () -> Unit,
        onEInkModeChanged: () -> Unit
    ) {
        menuBtn.background = GradientDrawable().apply {
            setColor(if (isEInk) currentTokens.surfaceRaised else chromeFillColor)
            cornerRadius = if (isEInk) 0f else 999f
            if (isEInk) {
                setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
            }
        }
        val menuPadding = (7 * dp).toInt()
        menuBtn.setPadding(menuPadding, menuPadding, menuPadding, menuPadding)
        NexusFeedHeaderMenuDialog(
            context = context,
            anchorView = menuBtn,
            isHeadlineOnly = isHeadlineOnly,
            onHeadlineOnlyChanged = onHeadlineOnlyChanged,
            onOpenManageSources = onOpenManageSources,
            onEInkModeChanged = onEInkModeChanged,
            onDismissed = {
                menuBtn.background = null
                menuBtn.setPadding(0, 0, 0, 0)
            }
        ).show()
    }
}
