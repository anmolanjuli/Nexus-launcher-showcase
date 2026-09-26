package com.nexus.launcher.ui.settings

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow

/**
 * Encapsulates data binding, listeners, and configuration for controls in [HomeScreenSettingsFragment].
 */
object HomeScreenSettingsBinder {

    val transitionNames = mapOf(
        "none" to "None",
        "slide" to "Slide",
        "fade" to "Fade",
        "cube" to "Cube",
        "zoom" to "Zoom",
        "flip" to "Flip",
        "gravity_fold" to "Gravity Fold"
    )

    fun getTransitionNames(context: Context): Map<String, String> = mapOf(
        "none" to context.getString(R.string.home_settings_transition_none),
        "slide" to context.getString(R.string.home_settings_transition_slide),
        "fade" to context.getString(R.string.home_settings_transition_fade),
        "cube" to context.getString(R.string.home_settings_transition_cube),
        "zoom" to context.getString(R.string.home_settings_transition_zoom),
        "flip" to context.getString(R.string.home_settings_transition_flip),
        "gravity_fold" to context.getString(R.string.home_settings_transition_gravity_fold)
    )

    fun bindControls(
        context: Context,
        pending: NexusSettingsData,
        reduceMotion: Boolean,
        animSpeed: Float,
        sliderHomeColumns: NexusSliderRow,
        sliderHomeRows: NexusSliderRow,
        sliderHomeIconSize: NexusSliderRow,
        toggleHomeLabels: NexusToggleRow,
        toggleHomeTwoLineLabels: NexusToggleRow,
        toggleHomeIndicator: NexusToggleRow,
        sliderPaddingLR: NexusSliderRow,
        sliderPaddingTB: NexusSliderRow,
        sliderGapH: NexusSliderRow,
        sliderGapV: NexusSliderRow,
        transitionsHeader: NexusNavRow,
        segmentedPageTransition: NexusSegmentedRow,
        toggleReduceMotion: NexusToggleRow,
        sliderAnimSpeed: NexusSliderRow,
        toggleShowFeed: NexusToggleRow
    ) {
        val transMap = getTransitionNames(context)
        sliderHomeColumns.configure(
            context.getString(R.string.home_settings_columns),
            NexusDefaults.HOME_COLUMNS_MIN,
            NexusDefaults.HOME_COLUMNS_MAX,
            pending.homeColumns,
            subtitle = context.getString(R.string.home_settings_columns_subtitle)
        )
        sliderHomeRows.configure(
            context.getString(R.string.home_settings_rows),
            NexusDefaults.HOME_ROWS_MIN,
            NexusDefaults.HOME_ROWS_MAX,
            pending.homeRows,
            subtitle = context.getString(R.string.home_settings_rows_subtitle)
        )
        sliderHomeIconSize.configure(
            context.getString(R.string.home_settings_icon_size),
            40,
            100,
            (pending.homeIconSizeMultiplier * 100).toInt(),
            subtitle = context.getString(R.string.home_settings_icon_size_subtitle)
        )
        toggleHomeLabels.configure(
            context.getString(R.string.home_settings_app_label),
            pending.homeShowLabels,
            subtitle = context.getString(R.string.home_settings_app_label_subtitle)
        )
        toggleHomeTwoLineLabels.configure(
            context.getString(R.string.settings_two_line_labels),
            pending.homeTwoLineLabels,
            subtitle = context.getString(R.string.settings_two_line_labels_subtitle)
        )
        toggleHomeTwoLineLabels.isEnabled = pending.homeShowLabels
        toggleHomeTwoLineLabels.alpha = if (pending.homeShowLabels) 1f else 0.4f
        toggleHomeIndicator.configure(
            context.getString(R.string.home_settings_page_indicator),
            pending.homeShowIndicator,
            subtitle = context.getString(R.string.home_settings_page_indicator_subtitle)
        )
        sliderPaddingLR.configure(
            context.getString(R.string.home_settings_padding_horizontal),
            -4,
            64,
            pending.homePaddingLeftRightDp.toInt(),
            subtitle = context.getString(R.string.home_settings_padding_horizontal_subtitle),
            formatValue = { "${it}dp" }
        )
        sliderPaddingTB.configure(
            context.getString(R.string.home_settings_padding_vertical),
            -32,
            64,
            pending.homePaddingTopBottomDp.toInt(),
            subtitle = context.getString(R.string.home_settings_padding_vertical_subtitle),
            formatValue = { "${it}dp" }
        )
        sliderGapH.configure(
            context.getString(R.string.home_settings_gap_horizontal),
            0,
            64,
            pending.homeGapHorizontalDp.toInt(),
            subtitle = context.getString(R.string.home_settings_gap_horizontal_subtitle),
            formatValue = { "${it}dp" }
        )
        sliderGapV.configure(
            context.getString(R.string.home_settings_gap_vertical),
            0,
            64,
            pending.homeGapVerticalDp.toInt(),
            subtitle = context.getString(R.string.home_settings_gap_vertical_subtitle),
            formatValue = { "${it}dp" }
        )
        transitionsHeader.setSubtitle(transMap[pending.pageTransition] ?: pending.pageTransition)
        segmentedPageTransition.configure(
            context.getString(R.string.home_settings_page_transition),
            listOf(
                "none" to (transMap["none"] ?: "None"),
                "slide" to (transMap["slide"] ?: "Slide"),
                "fade" to (transMap["fade"] ?: "Fade"),
                "cube" to (transMap["cube"] ?: "Cube"),
                "zoom" to (transMap["zoom"] ?: "Zoom"),
                "flip" to (transMap["flip"] ?: "Flip"),
                "gravity_fold" to (transMap["gravity_fold"] ?: "Gravity Fold")
            ),
            pending.pageTransition
        )
        // Slide (the default) and None stay free; the rest are Premium.
        segmentedPageTransition.setPremiumOptions(com.nexus.launcher.premium.PremiumFeature.PAGE_TRANSITIONS) { it != "slide" && it != "none" }
        toggleReduceMotion.configure(
            context.getString(R.string.home_settings_reduce_motion),
            reduceMotion,
            subtitle = context.getString(R.string.home_settings_reduce_motion_subtitle)
        )
        sliderAnimSpeed.configure(
            context.getString(R.string.home_settings_transition_speed),
            50,
            200,
            (animSpeed * 100).toInt(),
            subtitle = context.getString(R.string.home_settings_transition_speed_subtitle)
        )
        toggleShowFeed.configure(
            context.getString(R.string.home_settings_show_feed),
            pending.homeShowFeed,
            subtitle = context.getString(R.string.home_settings_show_feed_subtitle)
        )
    }

    fun setupListeners(
        sliderHomeColumns: NexusSliderRow,
        sliderHomeRows: NexusSliderRow,
        sliderHomeIconSize: NexusSliderRow,
        toggleHomeLabels: NexusToggleRow,
        toggleHomeTwoLineLabels: NexusToggleRow,
        toggleHomeIndicator: NexusToggleRow,
        sliderPaddingLR: NexusSliderRow,
        sliderPaddingTB: NexusSliderRow,
        sliderGapH: NexusSliderRow,
        sliderGapV: NexusSliderRow,
        transitionsHeader: NexusNavRow,
        segmentedPageTransition: NexusSegmentedRow,
        toggleReduceMotion: NexusToggleRow,
        sliderAnimSpeed: NexusSliderRow,
        toggleShowFeed: NexusToggleRow,
        onPatch: ((NexusSettingsData) -> NexusSettingsData) -> Unit,
        /** Used only by the three grid-geometry sliders. Applying a new column or row count
         *  reflows the home screen and can move the user's icons, so those stay behind the Apply
         *  bar while everything else on this page commits as it changes. */
        onPatchStaged: ((NexusSettingsData) -> NexusSettingsData) -> Unit,
        onReduceMotionChanged: (Boolean) -> Unit,
        onAnimSpeedChanged: (Float) -> Unit,
        onRebindRequired: () -> Unit,
        isIgnoreCallbacks: () -> Boolean,
        isLabelsShowing: () -> Boolean
    ) {
        sliderHomeColumns.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatchStaged { s -> s.copy(homeColumns = value) }
        }
        sliderHomeRows.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatchStaged { s -> s.copy(homeRows = value) }
        }
        sliderHomeIconSize.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatchStaged { s -> s.copy(homeIconSizeMultiplier = value / 100f) }
        }
        toggleHomeLabels.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(homeShowLabels = checked) }
        }
        toggleHomeTwoLineLabels.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) {
                if (isLabelsShowing()) {
                    onPatch { s -> s.copy(homeTwoLineLabels = checked) }
                } else {
                    onRebindRequired()
                }
            }
        }
        toggleHomeIndicator.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(homeShowIndicator = checked) }
        }
        sliderPaddingLR.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(homePaddingLeftRightDp = value.toFloat()) }
        }
        sliderPaddingTB.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(homePaddingTopBottomDp = value.toFloat()) }
        }
        sliderGapH.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(homeGapHorizontalDp = value.toFloat()) }
        }
        sliderGapV.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(homeGapVerticalDp = value.toFloat()) }
        }
        segmentedPageTransition.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) {
                onPatch { s -> s.copy(pageTransition = value) }
                transitionsHeader.setSubtitle(getTransitionNames(transitionsHeader.context)[value] ?: value)
            }
        }
        toggleReduceMotion.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onReduceMotionChanged(checked)
        }
        sliderAnimSpeed.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onAnimSpeedChanged(value / 100f)
        }
        toggleShowFeed.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(homeShowFeed = checked) }
        }
    }
}
