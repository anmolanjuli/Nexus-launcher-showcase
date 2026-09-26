package com.nexus.launcher.ui.widgets.mosaic

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.ColorBlindMode
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity

/** Nexus-styled remove choice dialog for a widget hosted inside a Living Mosaic. */
class MosaicWidgetRemovalDialog(
    private val activity: MainActivity,
    private val canRestore: Boolean,
    private val onRestore: () -> Unit,
    private val onDelete: () -> Unit
) {
    private val dp get() = activity.resources.displayMetrics.density

    fun show() {
        val tokens = ThemeObserver.currentTokens(activity)
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, true, windowBlurRadiusPx = 100)
        val dialog = Dialog(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground(tokens)
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt())
        }
        content.addView(TextView(activity).apply {
            text = activity.getString(com.nexus.launcher.R.string.mosaic_remove_widget_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
        })
        content.addView(TextView(activity).apply {
            text = if (canRestore) {
                activity.getString(com.nexus.launcher.R.string.mosaic_remove_restore_desc)
            } else {
                activity.getString(com.nexus.launcher.R.string.mosaic_remove_no_space_desc)
            }
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            setPadding(0, (5 * dp).toInt(), 0, (14 * dp).toInt())
        })
        content.addView(choice(activity.getString(com.nexus.launcher.R.string.mosaic_remove_restore_action), canRestore) {
            LivingMosaicHaptics.confirm(content)
            onRestore()
            dialog.dismiss()
        })
        content.addView(choice(activity.getString(com.nexus.launcher.R.string.action_delete), true, isDelete = true) {
            LivingMosaicHaptics.confirm(content)
            onDelete()
            dialog.dismiss()
        }, rowParams())
        content.addView(choice(activity.getString(com.nexus.launcher.R.string.action_cancel), true) {
            LivingMosaicHaptics.click(content)
            dialog.dismiss()
        }, rowParams())
        dialog.setOnDismissListener {
            com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, false)
        }
        dialog.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCanceledOnTouchOutside(true)
            show()
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout((activity.resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
                // Real blur-behind pass on this dialog's own window, stacking on top of the
                // workspace blur — same shared component the other edit sheets use. Still
                // explicitly clear the dim: the default Dialog theme dims its background by
                // ~0.6 unless told not to, which would compound with content's own translucent
                // fill and crush it toward flat black.
                com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(this)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0f)
            }
        }
    }

    /** [isDelete] marks the destructive choice; the label is translated, so it cannot be matched on. */
    private fun choice(label: String, enabled: Boolean, isDelete: Boolean = false, onClick: () -> Unit) = TextView(activity).apply {
        text = label
        gravity = Gravity.CENTER
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.45f
        val tokens = ThemeObserver.currentTokens(activity)
        val colorBlind = ThemeObserver.currentColorBlindMode(activity)
        val btnColor = if (isDelete) tokens.danger else tokens.textPrimary
        NexusTypeScale.bodyStrong.bindTo(this, btnColor)
        if (isDelete && colorBlind != ColorBlindMode.NONE) {
            background = GradientDrawable().apply {
                cornerRadius = 14f * dp
                setStroke((1.5f * dp).toInt().coerceAtLeast(1), btnColor)
                setColor(tokens.surfaceRaised)
            }
        } else {
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 14f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
        setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
        setOnClickListener { if (enabled) onClick() }
    }

    private fun rowParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = (8 * dp).toInt() }

    private fun cardBackground(tokens: NexusColorTokens) = GradientDrawable().apply {
        val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
        val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
        cornerRadius = 20f * dp
        setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
        setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
    }
}
