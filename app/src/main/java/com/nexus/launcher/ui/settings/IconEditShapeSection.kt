package com.nexus.launcher.ui.settings

import android.content.Context
import android.view.View
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

/**
 * Per-app shape strip for [IconEditSheet].
 * Includes [IconShapeTileRow.FOLLOW_GLOBAL] so the icon can follow Theme shape.
 */
object IconEditShapeSection {

    fun build(
        context: Context,
        density: Float,
        selectedId: Int,
        onSelect: (Int) -> Unit
    ): View {
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        return IconShapeTileRow.build(
            context = context,
            density = density,
            selectedId = selectedId,
            options = IconShapeTileRow.PER_APP_OPTIONS,
            tokens = tokens,
            onSelect = onSelect
        )
    }

    fun refresh(section: View, selectedId: Int) {
        val tokens = try {
            ThemeObserver.currentTokens(section.context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        IconShapeTileRow.refreshSelection(section, selectedId, tokens)
    }
}
