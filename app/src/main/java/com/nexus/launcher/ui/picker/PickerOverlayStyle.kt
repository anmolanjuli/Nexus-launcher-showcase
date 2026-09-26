package com.nexus.launcher.ui.picker

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.folder.FolderBlurCoordinator
import com.nexus.launcher.ui.folder.FolderWindowLifecycle
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.glass.NeumorphicSurfaces

/**
 * How the pickers look in each UI Style — the overlay's own ground, and the controls every picker
 * shares. Kept apart from [PickerOverlayShell] so the shell stays about layout and lifecycle.
 *
 * In Neumorphism the overlay stays an opaque ground and the controls on it take relief: the
 * search field sunken, buttons raised.
 */
internal object PickerOverlayStyle {

    /**
     * Opaque `tokens.bg` in Default and Neumorphism (the pickers' deliberate default: no blur or
     * dim compositing); a translucent frosted fill over the live workspace blur in Frosted Glass.
     * Returns whether the workspace blur was switched on, for [release] to undo.
     *
     * The picker can open from an open folder's "+" as well as from the folder icon's long-press
     * menu. Those two paths put different things behind a translucent picker: the blurred home
     * screen, or the open folder card with its own scrim over it. Blurring that card harder was not
     * enough — the picker, and its search field most visibly, still read fainter over the card than
     * over the home screen. So the folder is hidden while the picker covers it, and both paths sit
     * over exactly the same backdrop.
     */
    fun applyBackground(shell: View, tokens: NexusColorTokens, activity: Activity): Boolean {
        if (!FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            shell.setBackgroundColor(tokens.bg)
            return false
        }
        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
        val fillAlpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
        shell.setBackgroundColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
        FolderBlurCoordinator.setWorkspaceBlur(activity, true)
        FolderWindowLifecycle.activeOverlayRoot?.let { folder ->
            if (folder.visibility == View.VISIBLE) {
                folder.visibility = View.INVISIBLE
                hiddenFolder = java.lang.ref.WeakReference(folder)
            }
        }
        return true
    }

    /** The open folder this picker hid, to show again when it closes. */
    private var hiddenFolder: java.lang.ref.WeakReference<View>? = null

    /** Undoes [applyBackground]'s blur, and brings back the folder it hid. */
    fun release(activity: Activity?) {
        activity?.let { FolderBlurCoordinator.setWorkspaceBlur(it, false) }
        val folder = hiddenFolder?.get()
        hiddenFolder = null
        // Only if it is still the open folder and still hidden by us: saving apps can close or
        // rebuild the folder while the picker is up, and a folder that has gone must stay gone.
        if (folder != null && folder === FolderWindowLifecycle.activeOverlayRoot &&
            folder.isAttachedToWindow && folder.visibility == View.INVISIBLE
        ) {
            folder.visibility = View.VISIBLE
        }
    }

    fun closeButton(view: View, tokens: NexusColorTokens, dp: Float) {
        view.background = NeumorphicSurfaces.raisedOr(view, tokens, 16 * dp) {
            GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 16 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
    }

    fun searchField(view: View, tokens: NexusColorTokens, dp: Float) {
        view.background = NeumorphicSurfaces.sunkenOr(view, tokens, 14 * dp) {
            GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
    }

    /** A list row the user taps to choose — raised in Neumorphism. */
    fun choiceCard(view: View, tokens: NexusColorTokens, dp: Float, cornerDp: Float = 14f) {
        view.background = NeumorphicSurfaces.raisedOr(view, tokens, cornerDp * dp) {
            GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = cornerDp * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
    }

    /**
     * The picker's Save button: an inverted pill (ink fill, surface text) in Default and Frosted
     * Glass. In Neumorphism an inverted slab has no relief to give, so it is a raised pill with
     * ink text instead — the same as the edit sheets' Apply.
     */
    fun primaryButton(view: TextView, tokens: NexusColorTokens, dp: Float) {
        if (NeumorphicSurfaces.isActive) {
            view.background = NeumorphicSurfaces.card(view, tokens, 24 * dp)
            view.setTextColor(tokens.textPrimary)
        } else {
            view.background = GradientDrawable().apply {
                setColor(tokens.textPrimary)
                cornerRadius = 24f * dp
            }
            view.setTextColor(tokens.surface)
        }
    }

    /**
     * Enabled or disabled look for a [primaryButton], by colour alone — the view stays opaque so
     * the list behind never shows through a dimmed button.
     */
    fun setPrimaryEnabled(view: TextView, tokens: NexusColorTokens, enabled: Boolean) {
        if (NeumorphicSurfaces.isActive) {
            view.setTextColor(if (enabled) tokens.textPrimary else tokens.textSecondary)
        } else {
            (view.background as? GradientDrawable)?.setColor(if (enabled) tokens.textPrimary else tokens.surfaceRaised)
            view.setTextColor(if (enabled) tokens.surface else tokens.textSecondary)
        }
        view.isEnabled = enabled
    }
}
