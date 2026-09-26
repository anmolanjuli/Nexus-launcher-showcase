package com.nexus.launcher.ui.widgets.mosaic

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.settings.views.NexusSettingsButtons
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

/** Compact per-widget appearance editor used from Mosaic Studio's widget list. */
class MosaicChildStyleSheet(
    private val activity: MainActivity,
    child: MosaicChild,
    /** Only Nexus's own widgets (Calendar/Weather/Music/...) can resolve BG_INHERIT — third-party
     *  widgets render their own opaque content and can never show a mosaic-level background, so
     *  the toggle is hidden for them entirely rather than shown-but-nonfunctional. */
    private val isNexusOwned: Boolean,
    private val onPreview: (MosaicChild) -> Unit,
    private val onApply: (MosaicChild) -> Unit
) {
    private val dp get() = activity.resources.displayMetrics.density
    private val original = child
    private var draft = child
    private var applied = false
    private var dialog: Dialog? = null

    private val tokens: NexusColorTokens
        get() = try {
            ThemeObserver.currentTokens(activity)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

    fun show() {
        dismiss()
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, true, windowBlurRadiusPx = 100)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground()
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt())
        }
        content.addView(TextView(activity).apply {
            text = activity.getString(com.nexus.launcher.R.string.mosaic_child_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
        })
        content.addView(TextView(activity).apply {
            text = if (isNexusOwned) {
                activity.getString(com.nexus.launcher.R.string.mosaic_child_override_desc)
            } else {
                activity.getString(com.nexus.launcher.R.string.mosaic_child_foreign_desc)
            }
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
        })

        val previewCard = android.widget.FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (54 * dp).toInt()
            ).apply {
                bottomMargin = (12 * dp).toInt()
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 14f * dp)
                }
            }
        }
        val previewLabel = TextView(activity).apply {
            text = activity.getString(com.nexus.launcher.R.string.mosaic_child_surface_preview)
            gravity = Gravity.CENTER
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
        }
        previewCard.addView(previewLabel)
        content.addView(previewCard)

        this.updatePreviewCard = {
            val isFrosted = draft.backgroundMode == MosaicConfig.BG_FROSTED
            val isInheritMode = draft.backgroundMode == MosaicConfig.BG_INHERIT
            previewCard.background = LivingMosaicGlass.build(
                activity,
                MosaicConfig(
                    surfaceOpacity = draft.backgroundOpacity,
                    backgroundMode = if (isInheritMode) MosaicConfig.BG_GLASS else draft.backgroundMode,
                    frostedGradientIndex = draft.frostedGradientIndex,
                    isExpressive = isFrosted || draft.isExpressive
                )
            ).drawable
        }
        this.updatePreviewCard?.invoke()

        // Third-party widgets render their own opaque content — BG_INHERIT can never be visible
        // for them (see LivingMosaicChildHost). Force off any stale INHERIT value on open so the
        // rest of this sheet never has to reason about an impossible state.
        if (!isNexusOwned && draft.backgroundMode == MosaicConfig.BG_INHERIT) {
            draft = draft.copy(backgroundMode = MosaicConfig.BG_GLASS)
        }

        val group = SettingsSectionGroupView(activity)

        val isInherit = isNexusOwned && draft.backgroundMode == MosaicConfig.BG_INHERIT

        val bgContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
            visibility = if (isInherit) View.GONE else View.VISIBLE
        }
        val bgPicker = MosaicBackgroundPickerBinder(bgContainer) { mode, index ->
            draft = draft.copy(backgroundMode = mode, frostedGradientIndex = index)
            preview()
            LivingMosaicHaptics.click(content)
        }
        bgPicker.bind(
            if (draft.backgroundMode == MosaicConfig.BG_INHERIT) MosaicConfig.BG_GLASS else draft.backgroundMode,
            draft.frostedGradientIndex
        )
        bgPicker.setVisible(!isInherit)

        val opacitySlider = NexusSliderRow(activity).apply {
            configure(activity.getString(com.nexus.launcher.R.string.dock_settings_opacity), 0, 100, (draft.backgroundOpacity * 100).toInt()) { "$it%" }
            applyAccentColor(tokens.textPrimary)
            visibility = if (isInherit) View.GONE else View.VISIBLE
            onValueChanged = { value ->
                draft = draft.copy(backgroundOpacity = value / 100f)
                preview()
            }
        }

        val inheritToggle = NexusToggleRow(activity).apply {
            configure(activity.getString(com.nexus.launcher.R.string.mosaic_child_inherit), isInherit)
            applyAccentColor(tokens.textPrimary)
            onCheckedChanged = { isChecked ->
                val nextMode = if (isChecked) MosaicConfig.BG_INHERIT else MosaicConfig.BG_GLASS
                draft = draft.copy(backgroundMode = nextMode)
                bgContainer.visibility = if (isChecked) View.GONE else View.VISIBLE
                bgPicker.setVisible(!isChecked)
                opacitySlider.visibility = if (isChecked) View.GONE else View.VISIBLE
                preview()
            }
        }
        // Third-party widgets can't be overridden either way (see LivingMosaicChildHost) — hide
        // Inherit AND Custom Background entirely rather than showing controls that do nothing.
        if (isNexusOwned) {
            group.addChildRow(inheritToggle)
            group.addChildRow(bgContainer)
            group.addChildRow(opacitySlider)
            content.addView(group)
        }

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (12 * dp).toInt(), 0, 0)
        }
        actions.addView(NexusSettingsButtons.buildResetButton(activity, tokens, dp, haptic = false) {
            LivingMosaicHaptics.click(actions)
            dismiss()
        }.apply {
            text = activity.getString(android.R.string.cancel)
        })
        actions.addView(NexusSettingsButtons.buildApplyButton(activity, tokens, dp, activity.getString(com.nexus.launcher.R.string.action_apply), haptic = false) {
            LivingMosaicHaptics.confirm(actions)
            applied = true
            onApply(draft)
            dismiss()
        })
        content.addView(actions)

        dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCanceledOnTouchOutside(true)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout((340 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
            show()
            window?.apply {
                setLayout((340 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                // Gives this dialog's own window a real blur-behind pass (stacking on top of
                // the workspace blur already active behind it) instead of relying on a single
                // blur pass alone — same shared component the folder/widget/icon edit sheets
                // use. Still explicitly clear the dim: the default Dialog theme dims its
                // background by ~0.6 unless told not to, and left in place that compounds with
                // this card's own translucent fill and crushes it toward flat black (see
                // FrostedGlassEngine.applyDialogWindowChrome's note on this exact bug).
                com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(this)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0f)
            }
            setOnDismissListener {
                if (!applied) onPreview(original)
            }
        }
    }

    private var updatePreviewCard: (() -> Unit)? = null

    private fun cardBackground() = GradientDrawable().apply {
        val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
        val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
        cornerRadius = 20f * dp
        setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
        setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
    }

    private fun preview() {
        updatePreviewCard?.invoke()
        onPreview(draft)
    }

    private fun dismiss() {
        dialog?.dismiss()
        dialog = null
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, false)
    }
}
