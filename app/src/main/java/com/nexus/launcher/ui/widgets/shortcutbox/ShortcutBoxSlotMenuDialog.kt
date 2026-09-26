package com.nexus.launcher.ui.widgets.shortcutbox

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/** Nexus-styled options dialog for a slot inside a Shortcut Box. */
class ShortcutBoxSlotMenuDialog(
    private val context: Context,
    private val slotTitle: String,
    private val onChangeShortcut: () -> Unit,
    private val onRemoveFromBox: () -> Unit,
    private val onOpenWidgetSettings: (() -> Unit)? = null
) {
    private val dp = context.resources.displayMetrics.density

    private var activeDialog: Dialog? = null

    fun isShowing(): Boolean = activeDialog?.isShowing == true

    fun dismiss() {
        activeDialog?.dismiss()
        activeDialog = null
    }

    fun show() {
        dismiss()
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(context, true)
        val tokens = ThemeObserver.currentTokens(context)
        val dialog = Dialog(context)
        activeDialog = dialog
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground(tokens)
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt())
        }

        content.addView(TextView(context).apply {
            text = slotTitle.ifBlank { context.getString(R.string.shortcut_box_title) }
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
        })

        content.addView(choice(context.getString(R.string.shortcut_box_menu_change), tokens.textPrimary) {
            LivingMosaicHaptics.confirm(content)
            dialog.dismiss()
            activeDialog = null
            onChangeShortcut()
        }, rowParams())

        if (onOpenWidgetSettings != null) {
            content.addView(choice(context.getString(R.string.menu_move), tokens.textPrimary) {
                LivingMosaicHaptics.confirm(content)
                dialog.dismiss()
                activeDialog = null
                onOpenWidgetSettings()
            }, rowParams())
        }

        content.addView(choice(context.getString(R.string.shortcut_box_menu_remove), tokens.danger) {
            LivingMosaicHaptics.confirm(content)
            dialog.dismiss()
            activeDialog = null
            onRemoveFromBox()
        }, rowParams())

        content.addView(choice(context.getString(android.R.string.cancel), tokens.textSecondary) {
            LivingMosaicHaptics.click(content)
            dialog.dismiss()
            activeDialog = null
        }, rowParams())

        dialog.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCanceledOnTouchOutside(true)
            setOnDismissListener {
                activeDialog = null
                com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(context, false)
            }
            show()
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout((context.resources.displayMetrics.widthPixels * 0.85f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
        }
    }

    private fun choice(label: String, textColor: Int, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        val tokens = ThemeObserver.currentTokens(context)
        NexusTypeScale.bodyStrong.bindTo(this, textColor)
        background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 14f * dp
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }
        setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
        setOnClickListener { onClick() }
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
