package com.nexus.launcher.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.folder.FolderAuroraDialogs

class SettingsApplyBarBinder(
    private val activity: SettingsActivity,
) {
    lateinit var applyButton: MaterialButton
        private set

    fun bind(
        pagerIndex: () -> Int,
        tokens: () -> NexusColorTokens,
        onApply: () -> Unit,
    ) {
        val applyBar = activity.findViewById<LinearLayout>(R.id.settings_apply_bar)
        applyBar.gravity = android.view.Gravity.CENTER
        applyBar.orientation = LinearLayout.HORIZONTAL
        val density = activity.resources.displayMetrics.density
        val current = tokens()
        applyButton = NexusDesignSystem.buildApplyButton(
            activity,
            label = activity.getString(R.string.action_apply),
            accentArgb = current.textPrimary,
        )
        applyButton.setIconResource(R.drawable.ic_check)
        applyButton.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        applyButton.iconPadding = (8 * density).toInt()
        NexusDesignSystem.styleSettingsChromePill(applyButton, density)
        NexusTypeScale.bodyStrong.bindTo(activity, applyButton)
        applyButton.isEnabled = false
        updateColors(current)
        applyButton.setOnClickListener {
            it.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
            onApply()
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_settings_applied),
                Toast.LENGTH_SHORT,
            ).show()
        }
        applyBar.addView(applyButton)
        ViewCompat.setOnApplyWindowInsetsListener(applyBar) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val extra = (8 * density).toInt()
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, nav + extra)
            insets
        }
    }

    /** Kept so callers need not change; the bar has nothing page-specific left to update now
     *  that Reset is gone and Apply's enabled state is driven by whether anything is staged. */
    fun onPageSelected(position: Int) = Unit

    fun updateColors(tokens: NexusColorTokens) {
        if (!::applyButton.isInitialized) return
        val disabled = ColorUtils.setAlphaComponent(tokens.textPrimary, 0x4D)
        val enabledStates = arrayOf(
            intArrayOf(android.R.attr.state_enabled),
            intArrayOf(-android.R.attr.state_enabled),
        )
        applyButton.backgroundTintList = ColorStateList(
            enabledStates,
            intArrayOf(tokens.surface, Color.TRANSPARENT),
        )
        applyButton.strokeColor = ColorStateList(
            enabledStates,
            intArrayOf(tokens.divider, Color.TRANSPARENT),
        )
        applyButton.setTextColor(ColorStateList(enabledStates, intArrayOf(tokens.textPrimary, disabled)))
        applyButton.iconTint = ColorStateList(enabledStates, intArrayOf(tokens.textPrimary, disabled))
    }
}
