package com.nexus.launcher.ui.widgets.shortcutbox

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
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.folder.FolderAuroraDialogs
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSettingsButtons
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import com.nexus.launcher.ui.widgets.NexusEditBottomSheetHelper
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetSettingsSheetLayout

/** Bottom settings sheet for Shortcut / App Box container. */
class ShortcutBoxSettingsSheet(
    private val activity: MainActivity,
    private val item: HomeScreenItem,
    private val widgetRect: Rect,
    private val boxView: ShortcutBoxView? = null
) {
    private val mainContainer by lazy { activity.findViewById<FrameLayout>(R.id.main_container) }
    private val dp get() = activity.resources.displayMetrics.density
    private val tokens: NexusColorTokens
        get() = try {
            ThemeObserver.currentTokens(activity)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

    private var dialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private var originalConfig: ShortcutBoxConfig? = null
    private var previewImage: ImageView? = null
    private val renderer = ShortcutBoxRenderer(activity)

    fun show() {
        dismiss()
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, true)
        val config = ShortcutBoxConfig.parse(item.folderConfigJson)
        originalConfig = config
        var draft = config

        val place = NexusWidgetSettingsSheetLayout.placement(activity, widgetRect, dp)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground()
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { }
        }
        card.addView(NexusEditBottomSheetHelper.createDragHandle(activity, tokens, dp))

        // Header Row
        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * dp).toInt()
            }
        }
        val titleText = TextView(activity).apply {
            text = activity.getString(R.string.shortcut_box_title)
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

        // Live Preview Box
        val preview = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (128 * dp).toInt()
            ).apply { bottomMargin = (12 * dp).toInt() }
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        previewImage = preview
        card.addView(preview)
        refreshPreview(draft)

        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = com.nexus.launcher.ui.widgets.CappedScrollView(activity).apply {
            capPx = (place.capPx - (120 * dp).toInt()).coerceAtLeast((180 * dp).toInt())
            addView(content)
        }
        card.addView(scroll)

        // Group: Appearance
        val appearanceGroup = SettingsSectionGroupView(activity)

        // 2. Opacity Slider
        val opacityRow = NexusSliderRow(activity).apply {
            configure(activity.getString(com.nexus.launcher.R.string.dock_settings_opacity), 0, 100, (draft.surfaceOpacity * 100).toInt().coerceIn(0, 100)) { "$it%" }
            applyAccentColor(tokens.textPrimary)
            onValueChanged = { value ->
                draft = draft.copy(surfaceOpacity = value / 100f)
                applyDraft(draft, persist = false)
            }
        }

        // 2b. Glass Refraction Slider — visible whenever the global UI Style is Frosted Glass.
        // The per-item Glass/Soft-UI row that used to gate this was removed (dead at render time
        // — NexusWidgetConfig.isGlassSurface()/isNeumorphicSurface() already only follow the
        // global toggle), so this now follows that same global toggle directly.
        val refractionRow = NexusSliderRow(activity).apply {
            configure(activity.getString(com.nexus.launcher.R.string.folder_edit_glass_refraction), 0, 100, (draft.glassRefraction * 100).toInt().coerceIn(0, 100)) { "$it%" }
            applyAccentColor(tokens.textPrimary)
            visibility = if (com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled) View.VISIBLE else View.GONE
            onValueChanged = { value ->
                draft = draft.copy(glassRefraction = value / 100f)
                applyDraft(draft, persist = false)
            }
        }

        // 3. Flush / Borderless Toggle
        val borderlessToggle = NexusToggleRow(activity).apply {
            configure(activity.getString(com.nexus.launcher.R.string.widget_flush_borderless), draft.isFlushBorder)
            applyAccentColor(tokens.textPrimary)
            onCheckedChanged = { isChecked ->
                draft = draft.copy(isFlushBorder = isChecked)
                applyDraft(draft, persist = false)
            }
        }

        // 4. Expressive Gradient & Picker
        val bgPickerContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
        }
        val bgPicker = com.nexus.launcher.ui.widgets.mosaic.MosaicBackgroundPickerBinder(bgPickerContainer) { mode, index ->
            draft = draft.copy(backgroundMode = mode, frostedGradientIndex = index)
            applyDraft(draft, persist = false)
        }

        val expressiveToggle = NexusToggleRow(activity).apply {
            configure(activity.getString(com.nexus.launcher.R.string.expressive_gradient), draft.isExpressive)
            applyAccentColor(tokens.textPrimary)
            onCheckedChanged = { isChecked ->
                val nextMode = if (isChecked) NexusWidgetConfig.BG_FROSTED else NexusWidgetConfig.BG_NEUMORPHIC
                draft = draft.copy(isExpressive = isChecked, backgroundMode = nextMode)
                bgPicker.setVisible(isChecked)
                bgPicker.bind(draft.backgroundMode, draft.frostedGradientIndex)
                applyDraft(draft, persist = false)
            }
        }

        appearanceGroup.addChildRow(opacityRow)
        appearanceGroup.addChildRow(refractionRow)
        appearanceGroup.addChildRow(borderlessToggle)
        appearanceGroup.addChildRow(expressiveToggle)
        appearanceGroup.addChildRow(bgPickerContainer)

        bgPicker.bind(draft.backgroundMode, draft.frostedGradientIndex)
        bgPicker.setVisible(draft.isExpressive)
        content.addView(appearanceGroup)

        // Group: Grid Layout
        val gridGroup = SettingsSectionGroupView(activity)

        val gridStepperRow = ShortcutGridStepperRow(activity).apply {
            configure(draft.gridCols, draft.gridRows)
            onGridChanged = { cols, rows ->
                draft = draft.copy(gridCols = cols, gridRows = rows)
                applyDraft(draft, persist = false)
            }
        }

        val spacingRow = com.nexus.launcher.ui.widgets.BoxIconSpacingRow.create(activity, tokens.textPrimary, draft.iconSpacing) {
            draft = draft.copy(iconSpacing = it)
            applyDraft(draft, persist = false)
        }

        gridGroup.addChildRow(gridStepperRow)
        gridGroup.addChildRow(spacingRow)
        content.addView(gridGroup)

        // Footer Actions
        val footer = NexusSettingsButtons.buildFooter(
            context = activity,
            dp = dp,
            onReset = {
                originalConfig?.let { applyDraft(it, persist = true) }
                dismiss()
            },
            onApply = {
                applyDraft(draft, persist = true)
                dismiss()
            }
        )
        card.addView(footer.container)
        val dlg = NexusEditBottomSheetHelper.create(activity, card) { requestDismiss() }
        dialog = dlg
        dlg.show()
    }

    private fun refreshPreview(config: ShortcutBoxConfig) {
        val pw = (240 * dp).toInt().coerceAtLeast(1)
        val ph = (128 * dp).toInt().coerceAtLeast(1)
        val bmp = android.graphics.Bitmap.createBitmap(pw, ph, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        renderer.draw(
            canvas = canvas,
            width = pw.toFloat(),
            height = ph.toFloat(),
            spanX = item.spanX,
            spanY = item.spanY,
            config = config,
            membersBySlot = boxView?.membersMap().orEmpty(),
            iconsBySlot = boxView?.iconsMap().orEmpty(),
            labelsBySlot = boxView?.labelsMap().orEmpty()
        )
        previewImage?.setImageBitmap(bmp)
    }

    private fun applyDraft(config: ShortcutBoxConfig, persist: Boolean = false) {
        boxView?.applyLiveConfig(config)
        refreshPreview(config)
        if (persist) {
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                activity.applicationContext,
                com.nexus.launcher.di.DaoEntryPoint::class.java
            )
            ShortcutBoxOperations.updateConfig(
                activity.lifecycleScope,
                entryPoint.homeScreenDao(),
                item,
                config
            )
        }
    }

    private fun cardBackground() = NexusEditBottomSheetHelper.buildCardBackground(tokens, dp)

    private fun requestDismiss() {
        val current = ShortcutBoxConfig.parse(item.folderConfigJson)
        if (originalConfig != null && current != originalConfig) {
            FolderAuroraDialogs.showDiscard(activity) {
                originalConfig?.let { applyDraft(it, persist = true) }
                dismiss()
            }
        } else {
            dismiss()
        }
    }

    fun dismiss() {
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, false)
        dialog?.dismiss()
        dialog = null
    }

    private companion object { const val TAG = "shortcut_box_settings_sheet" }
}
