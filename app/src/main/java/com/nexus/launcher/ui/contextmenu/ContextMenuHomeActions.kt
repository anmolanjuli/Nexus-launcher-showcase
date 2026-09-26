package com.nexus.launcher.ui.contextmenu

import com.nexus.launcher.R
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.model.DisplayItem
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ContextMenuHomeActions {
    fun populate(
        menu: ContextMenuView,
        item: DisplayItem,
        activity: MainActivity,
        homeScreenViewModel: com.nexus.launcher.ui.HomeScreenViewModel,
        currentItemId: Int,
        currentPage: Int,
        isDockMenu: Boolean,
        excludedTitles: Set<String>,
        onEnterHomeSelectionMode: ((Int, String) -> Unit)?,
        onDismiss: () -> Unit
    ) {
        val packageName = item.intent?.component?.packageName
            ?: item.intent?.getStringExtra("packageName") ?: return
        val skipSelect = isDockMenu || "Select" in excludedTitles || activity.getString(R.string.action_select) in excludedTitles
        val skipRemove = "Remove from Home" in excludedTitles || "Remove" in excludedTitles || activity.getString(R.string.action_remove) in excludedTitles

        if (!skipSelect) {
            menu.addAction(activity.getString(R.string.action_select), R.drawable.ic_select) {
                onEnterHomeSelectionMode?.invoke(currentItemId, packageName)
                onDismiss()
            }
        }

        menu.addAction(activity.getString(R.string.action_edit), R.drawable.ic_edit) {
            val mainViewModel = androidx.lifecycle.ViewModelProvider(activity)[
                com.nexus.launcher.ui.MainViewModel::class.java
            ]
            val sheet = com.nexus.launcher.ui.settings.IconEditSheet(
                packageName, item.label, mainViewModel
            )
            sheet.show(activity.supportFragmentManager, "icon_edit")
            onDismiss()
        }

        if (!isDockMenu && currentItemId != -1) {
            menu.addAction(activity.getString(R.string.action_resize), R.drawable.ic_resize) {
                onDismiss()
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val dao = dagger.hilt.android.EntryPointAccessors.fromApplication(
                        activity.applicationContext,
                        com.nexus.launcher.di.DaoEntryPoint::class.java
                    ).homeScreenDao()
                    val homeItem = dao.getItemById(currentItemId) ?: return@launch
                    if (homeItem.itemType != 0) return@launch
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        com.nexus.launcher.ui.canvas.IconResizeModeLauncher(
                            activity, activity.canvasView, homeScreenViewModel
                        ).show(homeItem)
                    }
                }
            }
        }

        if (!skipRemove) {
            menu.addAction(activity.getString(R.string.action_remove), R.drawable.ic_remove, isDestructive = true) {
                if (currentItemId != -1) {
                    homeScreenViewModel.removeFromHomeScreenById(currentItemId)
                } else {
                    homeScreenViewModel.removeFromHomeScreen(packageName, currentPage)
                }
                onDismiss()
            }
        }
    }
}
