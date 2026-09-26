package com.nexus.launcher.ui.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

@Singleton
class IconPackDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getAvailableIconPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val themes = mutableMapOf<String, IconPackInfo>()
        
        val intentActions = listOf(
            "com.novalauncher.THEME",
            "org.adw.launcher.THEMES",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK"
        )
        
        for (action in intentActions) {
            val intent = Intent(action)
            val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }
            
            for (ri in resolveInfos) {
                val packageName = ri.activityInfo.packageName
                if (!themes.containsKey(packageName)) {
                    try {
                        val label = ri.loadLabel(pm).toString()
                        val icon = ri.loadIcon(pm)
                        themes[packageName] = IconPackInfo(packageName, label, icon)
                    } catch (e: Exception) {
                        android.util.Log.e("IconPackDetector", "Error loading pack $packageName", e)
                    }
                }
            }
        }
        
        themes.values.toList().sortedBy { it.label }
    }
}
