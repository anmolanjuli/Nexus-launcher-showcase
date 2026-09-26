package com.nexus.launcher.ui.contextmenu

import android.app.AlertDialog
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.widget.EditText
import com.nexus.launcher.R
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.MainViewModel
import com.nexus.launcher.ui.model.DisplayItem

object ContextMenuDrawerActions {

    fun populateShortcuts(
        activity: MainActivity,
        menu: ContextMenuView,
        item: DisplayItem,
        onDismiss: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val launcherApps = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val component = item.intent?.component ?: return
        try {
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(component.packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                )
            }
            launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
                ?.take(4)
                ?.forEach { shortcut ->
                    val icon = try {
                        launcherApps.getShortcutIconDrawable(
                            shortcut, activity.resources.displayMetrics.densityDpi
                        )
                    } catch (_: Exception) {
                        null
                    }
                    menu.addShortcut(shortcut.shortLabel?.toString() ?: activity.getString(com.nexus.launcher.R.string.home_edit_shortcut), icon) {
                        try {
                            launcherApps.startShortcut(shortcut, null, null)
                        } catch (_: Exception) {
                        }
                        onDismiss()
                    }
                }
        } catch (_: Exception) {
        }
    }

    fun populateActions(
        activity: MainActivity,
        menu: ContextMenuView,
        item: DisplayItem,
        viewModel: MainViewModel,
        onDismiss: () -> Unit,
        onEnterSelectionMode: ((String, com.nexus.launcher.ui.model.SelectionSource) -> Unit)?
    ) {
        val packageName = item.intent?.component?.packageName ?: return
        menu.addAction(activity.getString(R.string.action_select), R.drawable.ic_select) {
            onEnterSelectionMode?.invoke(packageName, com.nexus.launcher.ui.model.SelectionSource.DRAWER)
            onDismiss()
        }
        try {
            val providers = android.appwidget.AppWidgetManager.getInstance(activity)
                .getInstalledProvidersForPackage(packageName, null)
            if (providers.isNotEmpty()) {
                menu.addAction(activity.getString(R.string.menu_widgets), R.drawable.ic_widgets) { onDismiss() }
            }
        } catch (_: Exception) {
        }
        menu.addAction(activity.getString(R.string.menu_change_category), R.drawable.ic_category) {
            onDismiss()
            val currentCategoryId = item.categoryName?.let { com.nexus.launcher.ui.DrawerCategories.idOf(it) }?.coerceAtLeast(0) ?: 0
            com.nexus.launcher.ui.settings.CategoryPickerSheet.show(
                activity.supportFragmentManager, viewModel.categories, currentCategoryId,
                title = activity.getString(R.string.menu_change_category),
                context = activity
            ) { categoryId ->
                viewModel.setAppCategory(packageName, categoryId)
            }
        }
        menu.addAction(activity.getString(R.string.action_edit), R.drawable.ic_edit) {
            val sheet = com.nexus.launcher.ui.settings.IconEditSheet(packageName, item.label, viewModel)
            sheet.show(activity.supportFragmentManager, "icon_edit")
            onDismiss()
        }
        menu.addAction(activity.getString(R.string.menu_hide_app), R.drawable.ic_visibility_off, isDestructive = true) {
            onDismiss()
            if (com.nexus.launcher.premium.PremiumGate.allow(activity, com.nexus.launcher.premium.PremiumFeature.HIDDEN_APPS)) viewModel.hideApp(packageName)
        }

    }

    // Rename dialog removed in favor of IconEditSheet
}
