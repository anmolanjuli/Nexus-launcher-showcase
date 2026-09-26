package com.nexus.launcher.ui.folder

import android.content.Context
import android.view.View
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.model.DisplayItem

object FolderWindowAppMenuLauncher {

    fun show(
        context: Context,
        anchorView: View,
        item: HomeScreenItem,
        onRemove: () -> Unit
    ) {
        var currentContext = context
        var activity: MainActivity? = null
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is MainActivity) {
                activity = currentContext
                break
            }
            currentContext = currentContext.baseContext
        }
        if (activity == null) return
        val contextMenuManager = activity.contextMenuManager

        val pm = activity.packageManager
        val intent = pm.getLaunchIntentForPackage(item.packageName) ?: return
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(item.packageName, 0)).toString()
        } catch (_: Exception) {
            item.packageName
        }
        // Resolve through IconResolver so the menu hugs the same icon the folder shows
        // (icon pack / override / themed), not the raw system one.
        val icon = try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                activity.applicationContext,
                com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
            ).iconResolver().getIcon(item.packageName)
        } catch (_: Exception) {
            try { pm.getApplicationIcon(item.packageName) } catch (_: Exception) { null }
        }
        val displayItem = DisplayItem(label, icon, intent, null)
        val main = activity.findViewById<View>(R.id.main_container) ?: return
        
        // Find the actual ImageView to perfectly hug the icon, ignoring text labels/padding
        val iconView = if (anchorView is android.view.ViewGroup && anchorView.childCount > 0) anchorView.getChildAt(0) else anchorView
        
        val anchorLoc = IntArray(2)
        val containerLoc = IntArray(2)
        iconView.getLocationOnScreen(anchorLoc)
        main.getLocationOnScreen(containerLoc)
        
        val width = iconView.width
        val height = iconView.height
        
        val localRect = android.graphics.Rect(
            anchorLoc[0] - containerLoc[0],
            anchorLoc[1] - containerLoc[1],
            anchorLoc[0] - containerLoc[0] + width,
            anchorLoc[1] - containerLoc[1] + height
        )
        contextMenuManager.showForFolderApp(
            item = displayItem,
            itemLocalRect = localRect,
            coordinateView = main
        ) { menu, dismiss ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                try {
                    val query = android.content.pm.LauncherApps.ShortcutQuery().apply {
                        setPackage(item.packageName)
                        setQueryFlags(
                            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        )
                    }
                    launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
                        ?.take(3)
                        ?.forEach { shortcut ->
                            val shortcutIcon = try {
                                launcherApps.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
                            } catch (_: Exception) {
                                null
                            }
                            menu.addShortcut(shortcut.shortLabel?.toString() ?: context.getString(com.nexus.launcher.R.string.home_edit_shortcut), shortcutIcon) {
                                try {
                                    launcherApps.startShortcut(shortcut, null, null)
                                } catch (_: Exception) {}
                                dismiss()
                            }
                        }
                } catch (_: Exception) {}
            }

            menu.addAction(context.getString(com.nexus.launcher.R.string.folder_menu_remove_from_folder), R.drawable.ic_remove, isDestructive = true) {
                onRemove()
                dismiss()
            }
        }
    }
}
