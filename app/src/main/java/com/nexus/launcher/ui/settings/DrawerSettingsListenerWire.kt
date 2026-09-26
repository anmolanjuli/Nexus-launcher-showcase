package com.nexus.launcher.ui.settings

import android.content.Context
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.premium.PremiumGate
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow

/**
 * Wires user-interaction callbacks for App Drawer settings rows.
 * Extracted from DrawerSettingsFragment to keep that class well below 400 lines.
 */
object DrawerSettingsListenerWire {

    fun wireListeners(
        context: Context,
        segmentedLayout: NexusSegmentedRow,
        segmentedCategoryLayout: NexusSegmentedRow,
        sliderDrawerColumns: NexusSliderRow,
        sliderDrawerIconSize: NexusSliderRow,
        toggleDrawerLabels: NexusToggleRow,
        toggleDrawerTwoLineLabels: NexusToggleRow,
        segmentedSortOrder: NexusSegmentedRow,
        transitionsHeader: NexusNavRow,
        segmentedDrawerTransition: NexusSegmentedRow,
        toggleSearchPill: NexusToggleRow,
        segmentedSearchPosition: NexusSegmentedRow,
        segmentedCategoryStyle: NexusSegmentedRow,
        segmentedCategoryPosition: NexusSegmentedRow,
        toggleDrawerCategoryBar: NexusToggleRow,
        toggleDrawerRail: NexusToggleRow,
        onPatch: ((NexusSettingsData) -> NexusSettingsData) -> Unit,
        onUpdateDrawerTransition: (String) -> Unit,
        onRebindRequired: () -> Unit,
        isIgnoreCallbacks: () -> Boolean,
        isLabelsShowing: () -> Boolean,
        isSearchPillShowing: () -> Boolean,
        setIgnoreCallbacks: (Boolean) -> Unit
    ) {
        // Grid is free; every other drawer mode is Premium. The row refuses a locked mode itself.
        segmentedLayout.setPremiumOptions(DrawerSettingsPremium.FEATURE) { it != "grid" }
        segmentedLayout.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) {
                sliderDrawerColumns.alpha = if (value == "grid") 1f else 0.4f
                sliderDrawerColumns.isEnabled = value == "grid"
                onPatch { s -> s.copy(drawerGridOrList = value) }
            }
        }
        segmentedCategoryLayout.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(drawerCategoryLayout = value) }
        }
        DrawerSettingsPremium.bind(
            segmentedSearchPosition, segmentedCategoryStyle, segmentedCategoryPosition, toggleDrawerRail,
        ) { isSearchPillShowing() }

        sliderDrawerColumns.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(drawerColumns = value) }
        }

        sliderDrawerIconSize.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(drawerIconSizeMultiplier = value / 100f) }
        }

        toggleDrawerLabels.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(drawerShowLabels = checked) }
        }
        toggleDrawerTwoLineLabels.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) {
                if (isLabelsShowing()) {
                    onPatch { s -> s.copy(drawerTwoLineLabels = checked) }
                } else {
                    onRebindRequired()
                }
            }
        }

        segmentedSortOrder.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(drawerSortOrder = value) }
        }

        segmentedDrawerTransition.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) {
                onPatch { s -> s.copy(drawerTransition = value) }
                onUpdateDrawerTransition(value)
                transitionsHeader.setSubtitle(DrawerSettingsFormatters.getTransitionName(context, value))
                segmentedDrawerTransition.setSubtitle(DrawerSettingsFormatters.getTransitionSubtitle(context, value))
            }
        }

        toggleSearchPill.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(drawerShowSearchPill = checked) }
        }

        segmentedSearchPosition.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(drawerSearchBarPosition = value) }
        }

        segmentedCategoryStyle.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(drawerCategoryMode = value) }
        }

        segmentedCategoryPosition.onValueChanged = { value ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(drawerCategoryPosition = value) }
        }

        toggleDrawerCategoryBar.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(drawerShowCategoryBar = checked) }
        }

        toggleDrawerRail.onCheckedChanged = { checked ->
            // The A–Z rail is on by default; switching it off is Premium, back on never is.
            if (!isIgnoreCallbacks()) {
                if (checked || PremiumGate.allow(context, DrawerSettingsPremium.FEATURE)) {
                    onPatch { s -> s.copy(drawerShowRail = checked) }
                } else {
                    setIgnoreCallbacks(true)
                    toggleDrawerRail.setChecked(true)
                    setIgnoreCallbacks(false)
                }
            }
        }
    }
}
