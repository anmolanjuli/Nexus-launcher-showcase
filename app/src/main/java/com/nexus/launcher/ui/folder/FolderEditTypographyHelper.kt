package com.nexus.launcher.ui.folder

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow

/** Binds dynamic typography and color tokens to static XML layout headers and descriptions in Folder Edit Sheet. */
object FolderEditTypographyHelper {

    fun bindSectionHeaders(root: View) {
        val tokens = try {
            ThemeObserver.currentTokens(root.context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is TextView && child !is EditText && child.id != com.nexus.launcher.R.id.btn_save_folder && child.id != com.nexus.launcher.R.id.btn_reset_folder && child.id != com.nexus.launcher.R.id.save_button) {
                    val density = root.resources.displayMetrics.scaledDensity
                    val sp = if (density > 0) child.textSize / density else 14f
                    when {
                        sp >= 18f -> NexusTypeScale.title.bindTo(child, tokens.textPrimary)
                        sp <= 12.5f -> NexusTypeScale.labelSmall.bindTo(child, tokens.textSecondary)
                        else -> NexusTypeScale.body.bindTo(child, tokens.textPrimary)
                    }
                } else if (child is ViewGroup && child !is NexusSegmentedRow && child !is NexusSliderRow && child !is NexusToggleRow && child !is FolderShapeThumbnailRow) {
                    bindSectionHeaders(child)
                }
            }
        }
    }
}
