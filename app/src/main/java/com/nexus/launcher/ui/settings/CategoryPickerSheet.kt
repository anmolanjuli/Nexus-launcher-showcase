package com.nexus.launcher.ui.settings

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.nexus.launcher.ui.DrawerCategories
import com.nexus.launcher.ui.DrawerSearchPillBuilder

/**
 * Thin wrapper around [NexusSelectionSheet] for picking a drawer category — shared by the app
 * long-press menu's "Change Category" and the drawer folder menu's "Set Category", so both open
 * the identical Nexus-styled sheet rather than each rolling their own picker UI.
 */
object CategoryPickerSheet {
    /** [categories] are the drawer's category keys with "All" first; callers pass their current
     *  category id (0 = uncategorized/All) and get back the chosen id. */
    fun show(
        fragmentManager: FragmentManager,
        categories: List<String>,
        currentCategoryId: Int,
        title: String = "Change Category",
        context: Context? = null,
        onSelected: (Int) -> Unit
    ) {
        val options = categories.drop(1).map { key ->
            val label = context?.let { DrawerSearchPillBuilder.categoryLabelText(it, key) } ?: key
            SelectionOption(key = DrawerCategories.idOf(key), title = label, iconRes = context?.let { DrawerCategories.iconFor(it, key) })
        }
        NexusSelectionSheet(
            sheetTitle = title,
            options = options,
            selectedKey = currentCategoryId,
            onSelected = onSelected
        ).show(fragmentManager, "category_picker")
    }
}
