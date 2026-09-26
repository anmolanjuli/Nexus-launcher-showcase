package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import com.nexus.launcher.domain.model.AppModel

object CategoriesShortcutLoader {

    fun load(
        context: Context,
        members: List<AppModel>,
        recents: List<AppModel>,
        palette: CategoryPalette,
        limit: Int = 4,
    ): List<CategoryApp> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return emptyList()
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return emptyList()
        val memberPkgs = members.map { it.packageName }.toSet()
        val ordered = linkedSetOf<AppModel>()
        recents.filterTo(ordered) { it.packageName in memberPkgs }
        members.forEach { ordered.add(it) }
        val out = mutableListOf<CategoryApp>()
        for (app in ordered) {
            if (out.size >= limit) break
            val shortcuts = query(launcherApps, app.packageName) ?: continue
            for (shortcut in shortcuts) {
                if (out.size >= limit) break
                val id = shortcut.id ?: continue
                val icon = try {
                    launcherApps.getShortcutIconDrawable(
                        shortcut, context.resources.displayMetrics.densityDpi
                    )
                } catch (_: Exception) {
                    app.icon
                }
                val label = shortcut.shortLabel?.toString()
                    ?: shortcut.longLabel?.toString()
                    ?: app.label
                out.add(
                    CategoryApp(
                        label = label,
                        color = palette.mistBlue,
                        packageName = app.packageName,
                        className = app.className,
                        icon = icon ?: app.icon,
                        shortcutId = id,
                    )
                )
            }
        }
        return out
    }

    private fun query(launcherApps: LauncherApps, packageName: String) = try {
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
        }
        launcherApps.getShortcuts(query, Process.myUserHandle())
            ?.filter { !it.id.isNullOrBlank() }
            ?.take(4)
    } catch (_: Exception) {
        null
    }
}
