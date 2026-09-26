package com.nexus.launcher.ui.widgets.shortcutbox

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import org.json.JSONObject

/** Resolves app and shortcut drawables and labels for [ShortcutBoxView]. */
object ShortcutBoxIconResolver {

    fun resolve(
        context: Context,
        members: List<HomeScreenItem>,
        iconsBySlot: MutableMap<Int, Drawable?>,
        labelsBySlot: MutableMap<Int, String>
    ) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return
        val pm = context.packageManager
        val densityDpi = context.resources.displayMetrics.densityDpi

        for (member in members) {
            val slot = member.column
            if (member.itemType == HomeItemTypes.SHORTCUT) {
                try {
                    val shortcutId = JSONObject(member.folderConfigJson).optString("shortcutId")
                    if (member.packageName == context.packageName || NexusBuiltinShortcuts.isBuiltin(shortcutId)) {
                        iconsBySlot[slot] = NexusBuiltinShortcuts.getDrawable(context, shortcutId)
                        labelsBySlot[slot] = NexusBuiltinShortcuts.get(shortcutId)?.let { context.getString(it.titleRes) } ?: member.folderTitle
                    } else {
                        val query = LauncherApps.ShortcutQuery().apply {
                            setPackage(member.packageName)
                            setShortcutIds(listOf(shortcutId))
                            setQueryFlags(
                                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                            )
                        }
                        val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                        val match = shortcuts?.firstOrNull()
                        val icon = match?.let { launcherApps.getShortcutIconDrawable(it, densityDpi) }
                            ?: pm.defaultActivityIcon
                        iconsBySlot[slot] = icon
                        labelsBySlot[slot] = match?.shortLabel?.toString() ?: member.folderTitle
                    }
                } catch (_: Exception) {
                    iconsBySlot[slot] = pm.defaultActivityIcon
                    labelsBySlot[slot] = member.folderTitle
                }
            } else {
                try {
                    iconsBySlot[slot] = pm.getApplicationIcon(member.packageName)
                    val appInfo = pm.getApplicationInfo(member.packageName, 0)
                    labelsBySlot[slot] = pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    iconsBySlot[slot] = pm.defaultActivityIcon
                    labelsBySlot[slot] = member.packageName
                }
            }
        }
    }
}
