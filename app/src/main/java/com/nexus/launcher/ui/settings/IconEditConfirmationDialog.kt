package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

object IconEditConfirmationDialog {

    fun show(context: Context, onExit: () -> Unit) {
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        val dp = context.resources.displayMetrics.density

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Was its own hand-rolled FLAG_DIM_BEHIND(0.72) + FLAG_BLUR_BEHIND(25dp), never
            // converted to the shared frosted-glass component. No separate workspace-blur
            // activation needed here — this dialog is always shown on top of the (already
            // frosted, already blurred) Icon Edit sheet, not directly over the home screen.
            com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(window)
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
        }

        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            val margin = (24 * dp).toInt()
            setPadding(margin, margin, margin, margin)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
            background = GradientDrawable().apply {
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 24f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            val pad = (24 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val titleView = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.icon_edit_unsaved_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }
        card.addView(titleView)

        val messageView = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.icon_edit_unsaved_message)
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (24 * dp).toInt() }
        }
        card.addView(messageView)

        val continueBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() }
            text = context.getString(com.nexus.launcher.R.string.icon_edit_continue)
            isAllCaps = false
            setTextColor(tokens.surface)
            background = GradientDrawable().apply {
                setColor(tokens.textPrimary)
                cornerRadius = 14 * dp
            }
            NexusTypeScale.bodyStrong.bindTo(this, tokens.surface)
            setOnClickListener {
                it.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
                dialog.dismiss()
            }
        }
        card.addView(continueBtn)

        val exitBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = context.getString(com.nexus.launcher.R.string.action_exit)
            isAllCaps = false
            setTextColor(tokens.textSecondary)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textSecondary)
            setOnClickListener {
                it.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
                dialog.dismiss()
                onExit()
            }
        }
        card.addView(exitBtn)

        root.addView(card)
        dialog.setContentView(root)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.show()
    }

    fun showResetConfirmation(context: Context, onReset: () -> Unit) {
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        val dp = context.resources.displayMetrics.density

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Was its own hand-rolled FLAG_DIM_BEHIND(0.72) + FLAG_BLUR_BEHIND(25dp), never
            // converted to the shared frosted-glass component. No separate workspace-blur
            // activation needed here — this dialog is always shown on top of the (already
            // frosted, already blurred) Icon Edit sheet, not directly over the home screen.
            com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(window)
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
        }

        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            val margin = (24 * dp).toInt()
            setPadding(margin, margin, margin, margin)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
            background = GradientDrawable().apply {
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 24f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            val pad = (24 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val titleView = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.icon_edit_reset_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }
        card.addView(titleView)

        val messageView = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.icon_edit_reset_message)
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (24 * dp).toInt() }
        }
        card.addView(messageView)

        val cancelBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() }
            text = context.getString(com.nexus.launcher.R.string.action_cancel)
            isAllCaps = false
            setTextColor(tokens.surface)
            background = GradientDrawable().apply {
                setColor(tokens.textPrimary)
                cornerRadius = 14 * dp
            }
            NexusTypeScale.bodyStrong.bindTo(this, tokens.surface)
            setOnClickListener {
                it.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
                dialog.dismiss()
            }
        }
        card.addView(cancelBtn)

        val confirmBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = context.getString(com.nexus.launcher.R.string.action_reset)
            isAllCaps = false
            setTextColor(tokens.textSecondary)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textSecondary)
            setOnClickListener {
                it.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
                dialog.dismiss()
                onReset()
            }
        }
        card.addView(confirmBtn)

        root.addView(card)
        dialog.setContentView(root)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.show()
    }
}
