package com.nexus.launcher.ui.folder

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

object FolderAuroraDialogs {

    /**
     * These dialogs can be triggered from the home screen, from within an open folder, OR
     * (via `showDiscard`) stacked on top of an ALREADY-OPEN widget/box/mosaic/folder settings
     * sheet — setWorkspaceBlur (home canvas) and the activeCard RenderEffect only cover the
     * first two cases; neither reaches the third, since the settings sheet's own window sits
     * between the home canvas and this dialog. Without its own window-level blur-behind, THAT
     * case showed this dialog's already-correctly-translucent card fill (`dialogCardBackground`)
     * over the sharp, unblurred sheet behind it — reading as plain see-through rather than
     * frosted, exactly the "discard changes" dialog reported as "not frosted, it's transparent"
     * for the folder/widget/mosaic settings sheets (the icon edit sheet's own equivalent dialog
     * was fine because it sets this directly on itself, unlike this shared one). Giving this
     * dialog its own `applyDialogWindowChrome` makes it correct regardless of what's stacked
     * beneath it, on top of (not instead of) the existing workspace/card blur for the other two
     * cases.
     */
    internal fun applyDialogBlur(context: Context, dialog: Dialog, onClosed: (() -> Unit)? = null) {
        dialog.window?.let { com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(it) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            FolderBlurCoordinator.setWorkspaceBlur(context, true)
            // Blur activeOverlayRoot (the whole card+scrim wrapper), NOT activeCard directly —
            // FolderCardFrostApplier.applyBlur()/removeBlur() unconditionally call
            // `cardContainer.setRenderEffect(null)` on activeCard every time they run (its own
            // wallpaper-blur lives on a separate child, `blurBg`, not the card itself — that
            // null-reset was written assuming nothing else ever touches this property). Any
            // routine refresh while this dialog is open (theme change, config save, ...) would
            // silently wipe this exact RenderEffect, and did — reported as "I can see the opened
            // folder in the background," no blur at all. activeOverlayRoot isn't managed by
            // anything else, so nothing can silently reset it out from under this dialog.
            FolderWindowLifecycle.activeOverlayRoot?.setRenderEffect(
                com.nexus.launcher.ui.glass.ChromeBackdrop.effect(
                    com.nexus.launcher.ui.glass.ChromeBackdrop.OVER_FOLDER_RADIUS_PX
                )
            )
        }
        dialog.setOnDismissListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                FolderBlurCoordinator.setWorkspaceBlur(context, false)
                FolderWindowLifecycle.activeOverlayRoot?.setRenderEffect(null)
            }
            onClosed?.invoke()
        }
    }

    /**
     * Name entry dialog. Defaults to renaming a folder; [title] and [hint] reuse it for other
     * names. A blank entry falls back to [blankFallback], or is ignored when that is null.
     */
    fun showRename(
        context: Context,
        initialName: String,
        title: String? = null,
        hint: String? = null,
        blankFallback: String? = context.getString(com.nexus.launcher.R.string.folder_default_name),
        /** Shown between the name field and the buttons, e.g. an icon choice. */
        extraContent: View? = null,
        /** Runs however the dialog closes: saved, cancelled, back or outside tap. */
        onClosed: (() -> Unit)? = null,
        onConfirm: (String) -> Unit
    ) {
        val tokens = ThemeObserver.currentTokens(context)
        val dp = context.resources.displayMetrics.density
        val dialog = Dialog(context)
        applyDialogBlur(context, dialog, onClosed)
        val view = LayoutInflater.from(context).inflate(R.layout.view_folder_rename_dialog, null)
        view.background = dialogCardBackground(tokens, dp)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        val input = view.findViewById<EditText>(R.id.folder_rename_input).apply {
            setText(initialName)
            setSelection(initialName.length)
            hint?.let { this.hint = it }
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            setHintTextColor(tokens.textSecondary)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 12 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val cancelBtn = view.findViewById<TextView>(R.id.folder_rename_cancel).apply {
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textSecondary)
            setOnClickListener { dialog.dismiss() }
        }
        val confirmBtn = view.findViewById<TextView>(R.id.folder_rename_confirm).apply {
            NexusTypeScale.bodyStrong.bindTo(this, tokens.accent)
            setOnClickListener {
                val name = input.text.toString().trim().ifBlank { blankFallback.orEmpty() }
                if (name.isNotEmpty()) onConfirm(name)
                dialog.dismiss()
            }
        }
        if (extraContent != null) (view as? ViewGroup)?.let { it.addView(extraContent, it.childCount - 1) }
        val titleView = (view as? ViewGroup)?.getChildAt(0) as? TextView
        titleView?.let {
            title?.let { t -> it.text = t }
            NexusTypeScale.title.bindTo(it, tokens.textPrimary)
        }
        dialog.show()
    }

    fun showRemove(
        context: Context,
        appCount: Int,
        onConfirm: () -> Unit
    ) {
        val tokens = ThemeObserver.currentTokens(context)
        val dp = context.resources.displayMetrics.density
        val dialog = Dialog(context)
        applyDialogBlur(context, dialog)
        val view = LayoutInflater.from(context).inflate(R.layout.view_folder_remove_dialog, null)
        view.background = dialogCardBackground(tokens, dp)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        bindRemoveDialogViews(view, tokens, dp)
        view.findViewById<TextView>(R.id.folder_remove_body).text =
            context.getString(com.nexus.launcher.R.string.folder_remove_body_count, appCount)
        view.findViewById<TextView>(R.id.folder_remove_cancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.folder_remove_confirm).setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        dialog.show()
    }

    fun showDiscard(
        context: Context,
        onConfirm: () -> Unit
    ) {
        val tokens = ThemeObserver.currentTokens(context)
        val dp = context.resources.displayMetrics.density
        val dialog = Dialog(context)
        applyDialogBlur(context, dialog)
        val view = LayoutInflater.from(context).inflate(R.layout.view_folder_remove_dialog, null)
        view.background = dialogCardBackground(tokens, dp)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        bindRemoveDialogViews(view, tokens, dp)
        view.findViewById<TextView>(R.id.folder_remove_title)?.text = view.context.getString(com.nexus.launcher.R.string.folder_discard_title)
        view.findViewById<TextView>(R.id.folder_remove_body).text = view.context.getString(com.nexus.launcher.R.string.folder_discard_body)
        view.findViewById<TextView>(R.id.folder_remove_cancel).apply {
            text = view.context.getString(com.nexus.launcher.R.string.folder_keep_editing)
            setOnClickListener {
                it.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
                dialog.dismiss()
            }
        }
        view.findViewById<TextView>(R.id.folder_remove_confirm).apply {
            text = view.context.getString(com.nexus.launcher.R.string.folder_discard)
            setOnClickListener {
                it.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
                onConfirm()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun showConfirmation(
        context: Context,
        title: String,
        body: String,
        cancelText: String,
        confirmText: String,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null,
        autoDismissMs: Long? = null,
        onClosed: (() -> Unit)? = null,
    ) {
        val tokens = ThemeObserver.currentTokens(context)
        val dp = context.resources.displayMetrics.density
        val dialog = Dialog(context)
        applyDialogBlur(context, dialog, onClosed)
        val view = LayoutInflater.from(context).inflate(R.layout.view_folder_remove_dialog, null)
        view.background = dialogCardBackground(tokens, dp)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        bindRemoveDialogViews(view, tokens, dp)
        view.findViewById<TextView>(R.id.folder_remove_title)?.text = title
        view.findViewById<TextView>(R.id.folder_remove_body).text = body
        val cancelBtn = view.findViewById<TextView>(R.id.folder_remove_cancel).apply {
            text = cancelText
            setOnClickListener {
                onCancel?.invoke()
                dialog.dismiss()
            }
        }
        val confirmBtn = view.findViewById<TextView>(R.id.folder_remove_confirm).apply {
            text = confirmText
            setOnClickListener {
                onConfirm()
                dialog.dismiss()
            }
        }
        if (autoDismissMs != null) {
            cancelBtn.visibility = View.GONE
            confirmBtn.visibility = View.GONE
            view.postDelayed({
                if (dialog.isShowing) {
                    onConfirm()
                    dialog.dismiss()
                }
            }, autoDismissMs)
        }
        dialog.show()
    }

    fun showReport(
        context: Context,
        title: String,
        body: String
    ) {
        val tokens = ThemeObserver.currentTokens(context)
        val dp = context.resources.displayMetrics.density
        val dialog = Dialog(context)
        applyDialogBlur(context, dialog)
        val view = LayoutInflater.from(context).inflate(R.layout.view_folder_remove_dialog, null)
        view.background = dialogCardBackground(tokens, dp)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        bindRemoveDialogViews(view, tokens, dp)
        view.findViewById<TextView>(R.id.folder_remove_title)?.text = title
        view.findViewById<TextView>(R.id.folder_remove_body).text = body
        view.findViewById<TextView>(R.id.folder_remove_cancel).visibility = View.GONE
        view.findViewById<TextView>(R.id.folder_remove_confirm).apply {
            text = view.context.getString(com.nexus.launcher.R.string.action_ok)
            NexusTypeScale.bodyStrong.bindTo(this, tokens.accent)
            setOnClickListener { dialog.dismiss() }
        }
        dialog.show()
    }

    internal fun dialogCardBackground(tokens: NexusColorTokens, dp: Float) = GradientDrawable().apply {
        val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
        val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
        cornerRadius = 18f * dp
        setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
        setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
    }

    private fun bindRemoveDialogViews(view: View, tokens: NexusColorTokens, dp: Float) {
        val colorBlind = ThemeObserver.currentColorBlindMode(view.context)
        view.findViewById<TextView>(R.id.folder_remove_title)?.let {
            NexusTypeScale.title.bindTo(it, tokens.textPrimary)
        }
        view.findViewById<TextView>(R.id.folder_remove_body)?.let {
            NexusTypeScale.body.bindTo(it, tokens.textSecondary)
        }
        view.findViewById<TextView>(R.id.folder_remove_cancel)?.let {
            NexusTypeScale.bodyStrong.bindTo(it, tokens.textSecondary)
        }
        view.findViewById<TextView>(R.id.folder_remove_confirm)?.let {
            val dangerColor = tokens.danger
            NexusTypeScale.bodyStrong.bindTo(it, dangerColor)
            if (colorBlind != com.nexus.launcher.theme.ColorBlindMode.NONE) {
                it.background = GradientDrawable().apply {
                    cornerRadius = 8f * dp
                    setStroke((1.5f * dp).toInt(), dangerColor)
                    setColor(Color.TRANSPARENT)
                }
            }
        }
    }
}
