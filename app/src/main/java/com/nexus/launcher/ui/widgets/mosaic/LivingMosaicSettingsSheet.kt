package com.nexus.launcher.ui.widgets.mosaic

import android.appwidget.AppWidgetHost
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.folder.FolderAuroraDialogs
import com.nexus.launcher.ui.settings.views.NexusSettingsButtons
import com.nexus.launcher.ui.widgets.AppWidgetController

/** One Mosaic Studio sheet with settings and widget-management tabs. */
class LivingMosaicSettingsSheet(
    private val activity: MainActivity,
    private val homeScreenViewModel: HomeScreenViewModel,
    private val appWidgetController: AppWidgetController,
    private val appWidgetHost: AppWidgetHost
) {
    private val mainContainer by lazy { activity.findViewById<FrameLayout>(R.id.main_container) }
    private val dp get() = activity.resources.displayMetrics.density
    private val tokens: NexusColorTokens
        get() = try {
            ThemeObserver.currentTokens(activity)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
    private var activeExitRequest: (() -> Unit)? = null
    private var activeRoot: FrameLayout? = null
    private var dialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null

    fun show(item: HomeScreenItem, mosaicView: LivingMosaicView) {
        dismiss()
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, true)
        var draft = mosaicView.currentConfig()
        var original = draft

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = LivingMosaicSheetDecor.cardBackground(tokens, dp)
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { }
        }
        card.addView(com.nexus.launcher.ui.widgets.NexusEditBottomSheetHelper.createDragHandle(activity, tokens, dp))
        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        val titleText = TextView(activity).apply {
            text = activity.getString(com.nexus.launcher.R.string.mosaic_studio_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageView(activity).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            val pad = (6 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.surfaceRaised)
            }
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                requestDismiss()
            }
        }
        headerRow.addView(titleText)
        headerRow.addView(closeBtn)
        card.addView(headerRow)
        // Larger than the 100dp strip it was — the preview is what the sheet is editing — but
        // capped so the controls below keep most of the sheet. It is a live mirror of the real
        // Mosaic (MosaicLivePreviewView), so its glass, opacity and refraction are the widget's
        // own. Neumorphism adds a raised card behind it, like every other panel in the sheet.
        val dpInt = { v: Float -> (v * dp).toInt() }
        val previewFrame: android.view.ViewGroup = when {
            com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive -> android.widget.FrameLayout(activity).apply {
                background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.card(this, tokens, 18 * dp)
                setPadding(dpInt(12f), dpInt(12f), dpInt(12f), dpInt(12f))
            }
            else -> android.widget.FrameLayout(activity)
        }
        previewFrame.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpInt(168f)).apply {
            bottomMargin = dpInt(12f)
            if (com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive) {
                topMargin = dpInt(4f)
                marginStart = dpInt(2f)
                marginEnd = dpInt(2f)
            }
        }
        val preview = MosaicLivePreviewView(activity, mosaicView).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            contentDescription = activity.getString(com.nexus.launcher.R.string.mosaic_live_preview_desc)
        }
        previewFrame.addView(preview)
        card.addView(previewFrame)

        fun refreshMosaicPreview() {
            preview.refresh()
        }
        mosaicView.post { refreshMosaicPreview() }

        val contentHost = FrameLayout(activity)
        card.addView(contentHost)
        val tabs = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (12 * dp).toInt(), 0, 0)
        }
        card.addView(tabs)

        // Shared across both tabs — tapping Apply from either tab commits everything staged here.
        val getDraft: () -> MosaicConfig = { draft }
        val setDraft: (MosaicConfig) -> Unit = { draft = it }
        val hasChanges: () -> Boolean = { draft.toJson() != original.toJson() }

        lateinit var footer: NexusSettingsButtons.Footer
        val refreshApplyEnabled: () -> Unit = { footer.applyButton.isEnabled = hasChanges() }

        footer = NexusSettingsButtons.buildFooter(
            context = activity,
            dp = dp,
            onReset = {
                // Appearance-only reset (matches Dock/Folder "reset stages defaults"), pages/
                // children are deliberately preserved — a full factory wipe would delete the
                // user's widget arrangement, which is destructive and out of scope here.
                val defaults = MosaicConfig()
                setDraft(
                    draft.copy(
                        mode = defaults.mode,
                        surfaceOpacity = defaults.surfaceOpacity,
                        backgroundMode = defaults.backgroundMode,
                        frostedGradientIndex = defaults.frostedGradientIndex,
                        isExpressive = defaults.isExpressive
                    )
                )
                mosaicView.pulseAndRelayout(draft)
                mosaicView.post { refreshMosaicPreview() }
                selectTabRefresh?.invoke()
                refreshApplyEnabled()
            },
            onApply = {
                val fresh = homeScreenViewModel.homeScreenItems.value.find { it.id == item.id } ?: item
                homeScreenViewModel.updateMosaicConfig(fresh, draft)
                mosaicView.pulseAndRelayout(draft)
                dismiss()
            }
        )
        card.addView(footer.container)
        val dlg = com.nexus.launcher.ui.widgets.NexusEditBottomSheetHelper.create(activity, card) {
            activeExitRequest?.invoke()
        }
        dialog = dlg
        dlg.show()

        lateinit var mosaicTab: LinearLayout
        lateinit var widgetsTab: LinearLayout
        fun selectTab(widgets: Boolean) {
            contentHost.removeAllViews()
            // The Mosaic tab's cards need shadow room; the Widgets tab keeps its own alignment.
            com.nexus.launcher.ui.glass.NeumorphicSurfaces.clearShadowRoom(contentHost)
            if (widgets) {
                contentHost.addView(
                    MosaicWidgetManagerContent(
                        activity = activity,
                        appWidgetController = appWidgetController,
                        appWidgetHost = appWidgetHost,
                        getDraft = getDraft,
                        setDraft = { setDraft(it); refreshApplyEnabled() },
                        onPickerVisibilityChanged = { pickerVisible: Boolean ->
                            if (pickerVisible) {
                                com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, false)
                                activeRoot?.visibility = View.INVISIBLE
                                if (dlg.isShowing) dlg.hide()
                            } else {
                                if (!activity.isFinishing && !activity.isDestroyed) {
                                    dlg.show()
                                    com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, true)
                                }
                                activeRoot?.visibility = View.VISIBLE
                                mosaicView.post { refreshMosaicPreview() }
                            }
                        },
                        onConfigChanged = { updatedConfig ->
                            original = original.withCurrentChildren(updatedConfig.currentChildren())
                            mosaicView.post { refreshMosaicPreview() }
                        }
                    ).build(item, mosaicView)
                )
            } else {
                val mosaicScroll = LivingMosaicTabBuilder.build(activity, tokens, dp, mosaicView, getDraft) {
                    setDraft(it)
                    refreshApplyEnabled()
                    mosaicView.post { refreshMosaicPreview() }
                }
                contentHost.addView(mosaicScroll)
                (mosaicScroll as? android.view.ViewGroup)?.let { com.nexus.launcher.ui.glass.NeumorphicSurfaces.makeShadowRoom(it) }
            }
            mosaicTab.isSelected = !widgets
            widgetsTab.isSelected = widgets
            styleTab(mosaicTab)
            styleTab(widgetsTab)
        }
        mosaicTab = tabButton(R.drawable.ic_settings, "Mosaic") { selectTab(false) }
        widgetsTab = tabButton(R.drawable.ic_widgets, activity.getString(com.nexus.launcher.R.string.home_edit_widgets)) { selectTab(true) }
        tabs.addView(mosaicTab)
        tabs.addView(widgetsTab)
        selectTabRefresh = { selectTab(widgetsTab.isSelected) }
        selectTab(false)
        refreshApplyEnabled()
        activeExitRequest = {
            val discard = {
                mosaicView.pulseAndRelayout(original)
                dismiss()
            }
            if (!hasChanges()) discard()
            else FolderAuroraDialogs.showDiscard(activity, discard)
        }
    }

    private var selectTabRefresh: (() -> Unit)? = null

    private fun tabButton(icon: Int, label: String, onClick: () -> Unit) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, (44 * dp).toInt(), 1f).apply { marginEnd = (4 * dp).toInt(); marginStart = (4 * dp).toInt() }
        addView(ImageView(activity).apply { setImageResource(icon); contentDescription = null }, LinearLayout.LayoutParams((18 * dp).toInt(), (18 * dp).toInt()).apply { marginEnd = (6 * dp).toInt() })
        addView(TextView(activity).apply {
            text = label
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
        })
        setOnClickListener { LivingMosaicHaptics.click(this); onClick() }
    }

    private fun styleTab(tab: LinearLayout) {
        val tint = if (tab.isSelected) tokens.textPrimary else tokens.textSecondary
        (tab.getChildAt(0) as ImageView).imageTintList = android.content.res.ColorStateList.valueOf(tint)
        (tab.getChildAt(1) as TextView).setTextColor(tint)
        tab.background = if (tab.isSelected) {
            GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 12 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        } else null
    }

    fun requestDismiss() { activeExitRequest?.invoke() ?: dismiss() }
    fun dismiss() {
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, false)
        activeExitRequest = null
        activeRoot = null
        dialog?.dismiss()
        dialog = null
    }
    fun isShowing(): Boolean = dialog?.isShowing == true
    private companion object { const val TAG = "mosaic_settings_sheet" }
}
