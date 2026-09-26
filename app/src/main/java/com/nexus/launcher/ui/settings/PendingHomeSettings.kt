package com.nexus.launcher.ui.settings

import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.data.prefs.NexusSettingsData

data class PendingHomeSettings(
    var homeColumns: Int = NexusDefaults.HOME_COLUMNS,
    var homeRows: Int = NexusDefaults.HOME_ROWS,
    var homeIconSizeMultiplier: Float = NexusDefaults.HOME_ICON_SIZE_MULTIPLIER,
    var animSpeedMultiplier: Float = 1.0f,
    var homeShowLabels: Boolean = NexusDefaults.HOME_SHOW_LABELS,
    var homeTwoLineLabels: Boolean = NexusDefaults.HOME_TWO_LINE_LABELS,
    var homeShowIndicator: Boolean = NexusDefaults.HOME_SHOW_INDICATOR,
    var pageTransition: String = "slide",
    var homePaddingLeftRightDp: Float = NexusDefaults.HOME_PADDING_LEFT_RIGHT,
    var homePaddingTopBottomDp: Float = NexusDefaults.HOME_PADDING_TOP_BOTTOM,
    var homeGapHorizontalDp: Float = NexusDefaults.HOME_GAP_HORIZONTAL,
    var homeGapVerticalDp: Float = NexusDefaults.HOME_GAP_VERTICAL,
    var iconShape: Int = 11
) {
    companion object {
        fun from(settings: NexusSettingsData, animSpeedMultiplier: Float): PendingHomeSettings {
            return PendingHomeSettings(
                homeColumns = settings.homeColumns,
                homeRows = settings.homeRows,
                homeIconSizeMultiplier = settings.homeIconSizeMultiplier,
                animSpeedMultiplier = animSpeedMultiplier,
                homeShowLabels = settings.homeShowLabels,
                homeTwoLineLabels = settings.homeTwoLineLabels,
                homeShowIndicator = settings.homeShowIndicator,
                pageTransition = settings.pageTransition,
                homePaddingLeftRightDp = settings.homePaddingLeftRightDp,
                homePaddingTopBottomDp = settings.homePaddingTopBottomDp,
                homeGapHorizontalDp = settings.homeGapHorizontalDp,
                homeGapVerticalDp = settings.homeGapVerticalDp,
                iconShape = settings.iconShape
            )
        }
    }

    fun isDefault(): Boolean {
        return homeColumns == NexusDefaults.HOME_COLUMNS &&
               homeRows == NexusDefaults.HOME_ROWS &&
               homeIconSizeMultiplier == NexusDefaults.HOME_ICON_SIZE_MULTIPLIER &&
               animSpeedMultiplier == 1.0f &&
               homeShowLabels == NexusDefaults.HOME_SHOW_LABELS &&
               homeTwoLineLabels == NexusDefaults.HOME_TWO_LINE_LABELS &&
               homeShowIndicator == NexusDefaults.HOME_SHOW_INDICATOR &&
               pageTransition == "slide" &&
               homePaddingLeftRightDp == NexusDefaults.HOME_PADDING_LEFT_RIGHT &&
               homePaddingTopBottomDp == NexusDefaults.HOME_PADDING_TOP_BOTTOM &&
               homeGapHorizontalDp == NexusDefaults.HOME_GAP_HORIZONTAL &&
               homeGapVerticalDp == NexusDefaults.HOME_GAP_VERTICAL
    }
}
