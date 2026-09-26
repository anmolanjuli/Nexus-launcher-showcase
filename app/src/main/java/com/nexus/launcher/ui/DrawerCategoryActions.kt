package com.nexus.launcher.ui

import android.app.Activity
import com.nexus.launcher.R
import com.nexus.launcher.ui.folder.FolderAuroraDialogs

/**
 * The dialogs behind the category dropdown's manage actions: add (name and icon), rename, change
 * icon and delete. Kept apart from [DrawerChromeController], which only decides where the
 * dropdown and bars go.
 */
object DrawerCategoryActions {

    /** Offers the suggested categories first; "Create your own" falls through to [createNew]. */
    fun add(activity: Activity, viewModel: MainViewModel, onAdded: (String) -> Unit) {
        DrawerCategorySuggestions.show(
            activity, viewModel, onAdded,
            createYourOwn = { createNew(activity, viewModel, onAdded) },
        )
    }

    fun createNew(activity: Activity, viewModel: MainViewModel, onAdded: (String) -> Unit) {
        val icons = CategoryIconPicker.row(activity, CategoryIconCatalog.defaultNameFor("custom"))
        FolderAuroraDialogs.showRename(
            activity, "",
            title = activity.getString(R.string.category_new),
            hint = activity.getString(R.string.category_name_hint),
            blankFallback = null,
            extraContent = icons.view,
        ) { name ->
            val key = viewModel.addCategory(name) ?: return@showRename
            viewModel.drawerCategories.setIcon(key, icons.current)
            onAdded(key)
        }
    }

    /**
     * A built-in's hint is its translated name and an empty entry restores it; an added
     * category has no default, so an empty entry is ignored.
     */
    fun rename(activity: Activity, viewModel: MainViewModel, key: String, onClosed: () -> Unit) {
        val defaultName = DrawerSearchPillBuilder.defaultCategoryLabel(activity, key)
        val builtIn = !DrawerCategories.isCustom(key)
        FolderAuroraDialogs.showRename(
            activity, DrawerSearchPillBuilder.categoryLabelText(activity, key),
            title = activity.getString(R.string.category_rename_title),
            hint = if (builtIn) defaultName else activity.getString(R.string.category_name_hint),
            blankFallback = if (builtIn) defaultName else null,
            onClosed = onClosed,
        ) { name -> viewModel.drawerCategories.rename(key, name, defaultName) }
    }

    fun changeIcon(activity: Activity, viewModel: MainViewModel, key: String, onClosed: () -> Unit) {
        CategoryIconPicker.show(
            activity,
            title = activity.getString(R.string.category_icon_title),
            selected = DrawerCategories.iconNameFor(activity, key),
            defaultName = CategoryIconCatalog.defaultNameFor(key),
            onClosed = onClosed,
        ) { name -> viewModel.drawerCategories.setIcon(key, name) }
    }

    fun delete(activity: Activity, viewModel: MainViewModel, key: String, onClosed: () -> Unit) {
        FolderAuroraDialogs.showConfirmation(
            context = activity,
            title = activity.getString(R.string.category_delete_title, DrawerSearchPillBuilder.categoryLabelText(activity, key)),
            body = activity.getString(R.string.category_delete_body),
            cancelText = activity.getString(R.string.action_cancel),
            confirmText = activity.getString(R.string.action_delete),
            onConfirm = { viewModel.deleteCategory(key) },
            onClosed = onClosed,
        )
    }
}
