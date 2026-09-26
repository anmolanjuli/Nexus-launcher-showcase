package com.nexus.launcher.ui.widgets.mosaic

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

/** Builds the "Mosaic" tab content for [LivingMosaicSettingsSheet] — appearance/layout/mode
 *  controls only. Apply/Reset live in the sheet's shared persistent bottom bar. */
object LivingMosaicTabBuilder {

    fun build(
        activity: MainActivity,
        tokens: NexusColorTokens,
        dp: Float,
        mosaicView: LivingMosaicView,
        getDraft: () -> MosaicConfig,
        setDraft: (MosaicConfig) -> Unit
    ): View {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        content.addView(sectionHeader(activity, tokens, dp, activity.getString(com.nexus.launcher.R.string.folder_edit_section_appearance)))
        val appearanceGroup = SettingsSectionGroupView(activity)

        val opacity = NexusSliderRow(activity).apply {
            configure(activity.getString(com.nexus.launcher.R.string.dock_settings_opacity), 0, 100, (getDraft().surfaceOpacity * 100).toInt().coerceIn(0, 100)) { "$it%" }
            applyAccentColor(tokens.textPrimary)
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                getDraft().copy(surfaceOpacity = value / 100f).also { setDraft(it); mosaicView.applyDraftSurface(it) }
            }
        }
        appearanceGroup.addChildRow(opacity)

        // Was previously hardcoded to 0.70f everywhere (LivingMosaicView, LivingMosaicChildHost)
        // with no way to adjust it — the actual missing control the wallpaper-blur strength was
        // permanently fixed at, unlike every widget/box/folder's own Glass Refraction slider.
        val refraction = NexusSliderRow(activity).apply {
            configure(activity.getString(com.nexus.launcher.R.string.folder_edit_glass_refraction), 0, 100, (getDraft().glassRefraction * 100).toInt().coerceIn(0, 100)) { "$it%" }
            applyAccentColor(tokens.textPrimary)
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                getDraft().copy(glassRefraction = value / 100f).also { setDraft(it); mosaicView.applyDraftSurface(it) }
            }
        }
        appearanceGroup.addChildRow(refraction)

        val shapeRow = NexusSegmentedRow(activity).apply {
            configure(
                activity.getString(com.nexus.launcher.R.string.mosaic_shape),
                listOf("1" to activity.getString(com.nexus.launcher.R.string.icon_shape_squircle), "2" to activity.getString(com.nexus.launcher.R.string.icon_shape_square)),
                getDraft().shapeStyle.toString()
            )
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                val shape = value.toInt()
                getDraft().copy(shapeStyle = shape).also {
                    setDraft(it)
                    mosaicView.applyDraftSurface(it)
                }
            }
        }
        appearanceGroup.addChildRow(shapeRow)

        val bgContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
        }
        val bgPicker = MosaicBackgroundPickerBinder(bgContainer) { mode, index ->
            LivingMosaicHaptics.click(content)
            getDraft().copy(backgroundMode = mode, frostedGradientIndex = index).also { setDraft(it); mosaicView.applyDraftSurface(it) }
        }

        val expressiveToggle = NexusToggleRow(activity).apply {
            configure(activity.getString(com.nexus.launcher.R.string.mosaic_expressive_mode), getDraft().isExpressive)
            applyAccentColor(tokens.textPrimary)
            onCheckedChanged = { isChecked ->
                LivingMosaicHaptics.tick(this)
                getDraft().copy(isExpressive = isChecked).also {
                    setDraft(it)
                    bgPicker.setVisible(isChecked)
                    mosaicView.applyDraftSurface(it)
                }
            }
        }
        appearanceGroup.addChildRow(expressiveToggle)
        appearanceGroup.addChildRow(bgContainer)
        bgPicker.bind(getDraft().backgroundMode, getDraft().frostedGradientIndex)
        bgPicker.setVisible(getDraft().isExpressive)
        content.addView(appearanceGroup)

        content.addView(sectionHeader(activity, tokens, dp, activity.getString(com.nexus.launcher.R.string.mosaic_section_layout_pages)))
        val layoutGroup = SettingsSectionGroupView(activity)

        val templateRow = NexusSegmentedRow(activity).apply {
            configure(
                activity.getString(com.nexus.launcher.R.string.mosaic_layout_template),
                LivingMosaicSheetDecor.templateOptions(activity),
                getDraft().currentPage().layoutTemplate ?: ""
            )
            onValueChanged = { templateId ->
                LivingMosaicHaptics.tick(this)
                updatePage(getDraft, setDraft, mosaicView) {
                    it.copy(layoutTemplate = templateId.takeIf { t -> t.isNotEmpty() })
                }
            }
        }
        val pageLabel = TextView(activity).apply {
            gravity = Gravity.CENTER
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun refreshPage() {
            pageLabel.text = pageText(activity, getDraft())
            templateRow.configure(
                activity.getString(com.nexus.launcher.R.string.mosaic_layout_template),
                LivingMosaicSheetDecor.templateOptions(activity),
                getDraft().currentPage().layoutTemplate ?: ""
            )
        }
        refreshPage()
        layoutGroup.addChildRow(templateRow)

        val pageNavRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            val prevBtn = LivingMosaicSheetDecor.pillButton(activity, tokens, dp, "‹") {
                val cfg = getDraft()
                val next = (cfg.pageIndex - 1).coerceIn(0, cfg.pages.lastIndex)
                if (next != cfg.pageIndex) {
                    setDraft(cfg.copy(pageIndex = next, focusIndex = 0))
                    LivingMosaicHaptics.confirm(mosaicView)
                    refreshPage()
                    mosaicView.pulseAndRelayout(getDraft())
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val nextBtn = LivingMosaicSheetDecor.pillButton(activity, tokens, dp, "›") {
                val cfg = getDraft()
                val next = (cfg.pageIndex + 1).coerceIn(0, cfg.pages.lastIndex)
                if (next != cfg.pageIndex) {
                    setDraft(cfg.copy(pageIndex = next, focusIndex = 0))
                    LivingMosaicHaptics.confirm(mosaicView)
                    refreshPage()
                    mosaicView.pulseAndRelayout(getDraft())
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            addView(prevBtn)
            addView(pageLabel)
            addView(nextBtn)
        }
        layoutGroup.addChildRow(pageNavRow)

        val pageActionRow = LivingMosaicSheetDecor.buttonRow(activity, tokens, dp, activity.getString(com.nexus.launcher.R.string.mosaic_add_page), activity.getString(com.nexus.launcher.R.string.mosaic_remove_page)) { direction ->
            if (direction < 0) {
                setDraft(getDraft().addPage())
                LivingMosaicHaptics.confirm(mosaicView)
                refreshPage()
                mosaicView.pulseAndRelayout(getDraft())
            } else if (getDraft().pages.size > 1) {
                setDraft(getDraft().removePage(getDraft().pageIndex))
                LivingMosaicHaptics.confirm(mosaicView)
                refreshPage()
                mosaicView.pulseAndRelayout(getDraft())
            } else {
                Toast.makeText(activity, activity.getString(R.string.toast_at_least_one_page_required), Toast.LENGTH_SHORT).show()
            }
        }
        layoutGroup.addChildRow(pageActionRow)
        content.addView(layoutGroup)

        content.addView(sectionHeader(activity, tokens, dp, activity.getString(com.nexus.launcher.R.string.mosaic_section_default_mode)))
        val modeGroup = SettingsSectionGroupView(activity)
        val modeRow = NexusSegmentedRow(activity).apply {
            configure(
                activity.getString(com.nexus.launcher.R.string.mosaic_default_mode),
                listOf(MosaicConfig.MODE_MOSAIC to "Mosaic", MosaicConfig.MODE_SINGLE to activity.getString(com.nexus.launcher.R.string.mosaic_mode_focus)),
                getDraft().mode,
                inline = true
            )
            onValueChanged = { value ->
                getDraft().copy(mode = value, focusIndex = 0).also {
                    setDraft(it)
                    LivingMosaicHaptics.confirm(mosaicView)
                    mosaicView.pulseAndRelayout(it)
                }
            }
        }
        modeGroup.addChildRow(modeRow)
        content.addView(modeGroup)

        // Apply/Reset live in the sheet's persistent bottom bar, shared with the Widgets tab.
        return ScrollView(activity).apply { addView(content) }
    }

    private fun updatePage(
        get: () -> MosaicConfig,
        set: (MosaicConfig) -> Unit,
        mosaicView: LivingMosaicView,
        transform: (MosaicPage) -> MosaicPage
    ) {
        val cfg = get()
        val pages = cfg.pages.toMutableList()
        pages[cfg.pageIndex] = transform(pages[cfg.pageIndex])
        cfg.copy(pages = pages).also {
            set(it)
            LivingMosaicHaptics.confirm(mosaicView)
            mosaicView.pulseAndRelayout(it)
        }
    }

    private fun sectionHeader(activity: MainActivity, tokens: NexusColorTokens, dp: Float, label: String) =
        TextView(activity).apply {
            text = label
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            setPadding(0, (10 * dp).toInt(), 0, (4 * dp).toInt())
        }

    private fun pageText(context: android.content.Context, config: MosaicConfig) =
        context.getString(com.nexus.launcher.R.string.mosaic_page_of, config.pageIndex + 1, config.pages.size)
}
