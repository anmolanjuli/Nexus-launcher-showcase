package com.nexus.launcher.ui.dock.settings

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow

/** Binds dynamic typography to static XML layout views in the Dock Settings Dialog. */
object DockSettingsTypographyHelper {

    fun bindViews(root: View) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is TextView && child !is EditText && child.id != com.nexus.launcher.R.id.btn_reset_dock && child.id != com.nexus.launcher.R.id.btn_apply_dock) {
                    val density = root.resources.displayMetrics.scaledDensity
                    val sp = if (density > 0) child.textSize / density else 14f
                    when {
                        sp >= 17f -> NexusTypeScale.title.bindTo(child)
                        sp <= 12.5f -> NexusTypeScale.caption.bindTo(child)
                        else -> NexusTypeScale.bodyStrong.bindTo(child)
                    }
                } else if (child is ViewGroup && child !is NexusSegmentedRow && child !is NexusSliderRow && child !is NexusToggleRow) {
                    bindViews(child)
                }
            }
        }
    }
}
