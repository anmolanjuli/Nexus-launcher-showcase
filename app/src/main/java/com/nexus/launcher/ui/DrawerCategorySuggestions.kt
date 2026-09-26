package com.nexus.launcher.ui

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import com.nexus.launcher.R
import com.nexus.launcher.domain.search.AppCategoryCatalog
import com.nexus.launcher.ui.settings.NexusSelectionSheet
import com.nexus.launcher.ui.settings.SelectionOption

/**
 * The list of categories offered when adding one: Shopping, Travel, Health and the rest of
 * [AppCategoryCatalog.SUGGESTED] that are not on screen yet, plus "Create your own" for a
 * hand-named one.
 *
 * Picking one fills it immediately — the engine has already sorted every app into the most
 * specific category it fits, so the apps for it simply stop falling back to the broader ones
 * (see [DrawerCategories.visibleIdFor]).
 */
object DrawerCategorySuggestions {

    private const val CREATE_YOUR_OWN = -1

    /** Shows the picker, or goes straight to naming one when nothing is left to suggest. */
    fun show(
        activity: Activity,
        viewModel: MainViewModel,
        onAdded: (String) -> Unit,
        createYourOwn: () -> Unit,
    ) {
        val manager = (activity as? FragmentActivity)?.supportFragmentManager
        val suggestions = viewModel.drawerCategories.suggestedToAdd()
        if (manager == null || suggestions.isEmpty()) {
            createYourOwn()
            return
        }
        val options = suggestions.mapNotNull { id ->
            val key = DrawerCategories.BUILT_IN.getOrNull(id) ?: return@mapNotNull null
            SelectionOption(
                key = id,
                title = DrawerSearchPillBuilder.defaultCategoryLabel(activity, key),
                iconRes = DrawerCategories.iconFor(activity, key),
            )
        } + SelectionOption(
            key = CREATE_YOUR_OWN,
            title = activity.getString(R.string.category_create_your_own),
            iconRes = R.drawable.ic_category_custom,
        )
        NexusSelectionSheet(
            sheetTitle = activity.getString(R.string.category_add_title),
            sheetSubtitle = activity.getString(R.string.category_add_subtitle),
            options = options,
            selectedKey = Int.MIN_VALUE,
            onSelected = { id ->
                if (id == CREATE_YOUR_OWN) createYourOwn()
                else viewModel.drawerCategories.addSuggested(id)?.let(onAdded)
            },
        ).show(manager, "category_suggestions")
    }
}
