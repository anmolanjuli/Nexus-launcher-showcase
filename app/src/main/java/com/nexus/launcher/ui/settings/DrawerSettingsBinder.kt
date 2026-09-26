package com.nexus.launcher.ui.settings

import android.content.Context
import android.view.View
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow

object DrawerSettingsBinder {

    fun bindControls(
        context: Context,
        s: NexusSettingsData,
        sliderDrawerIconSize: NexusSliderRow,
        segmentedLayout: NexusSegmentedRow,
        segmentedCategoryLayout: NexusSegmentedRow,
        sliderDrawerColumns: NexusSliderRow,
        toggleDrawerLabels: NexusToggleRow,
        toggleDrawerTwoLineLabels: NexusToggleRow,
        gridHeader: NexusNavRow,
        segmentedSortOrder: NexusSegmentedRow,
        transitionsHeader: NexusNavRow,
        segmentedDrawerTransition: NexusSegmentedRow,
        categoriesHeader: NexusNavRow,
        toggleSearchPill: NexusToggleRow,
        segmentedSearchPosition: NexusSegmentedRow,
        toggleDrawerCategoryBar: NexusToggleRow,
        segmentedCategoryStyle: NexusSegmentedRow,
        segmentedCategoryPosition: NexusSegmentedRow,
        toggleDrawerRail: NexusToggleRow
    ) {
        sliderDrawerIconSize.configure(
            context.getString(R.string.home_settings_icon_size),
            40,
            85,
            (s.drawerIconSizeMultiplier * 100).toInt(),
            subtitle = context.getString(R.string.home_settings_icon_size_subtitle)
        )

        segmentedLayout.configure(
            context.getString(R.string.drawer_grid_or_list_label),
            listOf(
                "grid" to context.getString(R.string.drawer_grid_or_list_grid),
                "list" to context.getString(R.string.drawer_grid_or_list_list),
                "categories" to context.getString(R.string.drawer_grid_or_list_categories)
            ),
            s.drawerGridOrList,
            inline = true,
            iconRes = 0,
            subtitle = DrawerSettingsFormatters.getGridOrListSubtitle(context, s.drawerGridOrList)
        )
        segmentedCategoryLayout.configure(
            context.getString(R.string.drawer_category_layout_label),
            DrawerSettingsFormatters.categoryLayoutOptions(context),
            s.drawerCategoryLayout,
            inline = true,
            iconRes = 0,
            subtitle = context.getString(R.string.drawer_category_layout_subtitle)
        )
        segmentedCategoryLayout.visibility =
            if (s.drawerGridOrList == com.nexus.launcher.data.prefs.DrawerLayoutModes.CATEGORIES) View.VISIBLE else View.GONE
        sliderDrawerColumns.configure(
            context.getString(R.string.home_settings_columns),
            2,
            10,
            s.drawerColumns,
            subtitle = context.getString(R.string.home_settings_columns_subtitle)
        )
        toggleDrawerLabels.configure(
            context.getString(R.string.home_settings_app_label),
            s.drawerShowLabels,
            subtitle = context.getString(R.string.home_settings_app_label_subtitle)
        )
        toggleDrawerTwoLineLabels.configure(
            context.getString(R.string.settings_two_line_labels),
            s.drawerTwoLineLabels,
            subtitle = context.getString(R.string.settings_two_line_labels_subtitle)
        )
        toggleDrawerTwoLineLabels.isEnabled = s.drawerShowLabels
        toggleDrawerTwoLineLabels.alpha = if (s.drawerShowLabels) 1f else 0.4f
        sliderDrawerColumns.alpha = if (s.drawerGridOrList == "grid") 1f else 0.4f
        sliderDrawerColumns.isEnabled = s.drawerGridOrList == "grid"

        gridHeader.setSubtitle(DrawerSettingsFormatters.getGridSubtitle(context, s))

        segmentedSortOrder.configure(
            context.getString(R.string.drawer_sort_order_label),
            DrawerSettingsFormatters.sortOptions(context),
            s.drawerSortOrder,
            inline = true,
            iconRes = 0,
            subtitle = DrawerSettingsFormatters.getSortSubtitle(context, s.drawerSortOrder)
        )

        transitionsHeader.setSubtitle(DrawerSettingsFormatters.getTransitionName(context, s.drawerTransition))

        segmentedDrawerTransition.configure(
            context.getString(R.string.drawer_transition_label),
            DrawerSettingsFormatters.transitionOptions(context),
            s.drawerTransition,
            inline = false,
            iconRes = 0,
            subtitle = DrawerSettingsFormatters.getTransitionSubtitle(context, s.drawerTransition)
        )

        categoriesHeader.setSubtitle(DrawerSettingsFormatters.getNavigationSubtitle(context, s))
        toggleSearchPill.configure(
            context.getString(R.string.drawer_show_search_pill_label),
            s.drawerShowSearchPill,
            subtitle = context.getString(R.string.drawer_search_pill_subtitle)
        )
        segmentedSearchPosition.configure(
            context.getString(R.string.drawer_search_bar_position_label),
            DrawerSettingsFormatters.searchPositionOptions(context),
            s.drawerSearchBarPosition,
            inline = true,
            iconRes = 0,
            subtitle = context.getString(R.string.drawer_search_pos_subtitle)
        )
        toggleDrawerCategoryBar.configure(
            context.getString(R.string.drawer_categories_label),
            s.drawerShowCategoryBar,
            subtitle = context.getString(R.string.drawer_categories_subtitle)
        )
        segmentedCategoryStyle.configure(
            context.getString(R.string.drawer_category_style_label),
            DrawerSettingsFormatters.categoryStyleOptions(context, s.drawerShowSearchPill),
            DrawerSettingsFormatters.effectiveCategoryMode(s),
            inline = true,
            iconRes = 0,
            subtitle = context.getString(R.string.drawer_category_style_subtitle)
        )
        segmentedCategoryPosition.configure(
            context.getString(R.string.drawer_category_position_label),
            DrawerSettingsFormatters.searchPositionOptions(context),
            s.drawerCategoryPosition,
            inline = true,
            iconRes = 0,
            subtitle = context.getString(R.string.drawer_category_pos_subtitle)
        )
        segmentedSearchPosition.visibility =
            if (s.drawerShowSearchPill) View.VISIBLE else View.GONE
        segmentedCategoryStyle.visibility =
            if (s.drawerShowCategoryBar) View.VISIBLE else View.GONE
        segmentedCategoryPosition.visibility =
            if (s.drawerShowCategoryBar &&
                DrawerSettingsFormatters.hasOwnCategoryBar(s)
            ) View.VISIBLE else View.GONE
        toggleDrawerRail.configure(
            context.getString(R.string.drawer_side_rail_label),
            s.drawerShowRail,
            subtitle = context.getString(R.string.drawer_side_rail_subtitle)
        )
    }
}
