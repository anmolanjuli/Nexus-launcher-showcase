package com.nexus.launcher.ui.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface IconResolverEntryPoint {
    fun iconResolver(): IconResolver
    fun themedIconFactory(): ThemedIconFactory
}

@Singleton
class IconResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val iconPackParser: IconPackParser,
    private val preferenceManager: com.nexus.launcher.data.prefs.PreferenceManager,
    private val themedIconFactory: ThemedIconFactory
) {
    fun getIcon(packageName: String, className: String? = null, overrideShape: Int? = null, skipCustomOverride: Boolean = false): Drawable {
        val pm = context.packageManager
        
        val actualClassName = className ?: getClassName(pm, packageName)
        
        // Priority: explicit override → per-app prefs → global Theme shape
        val shapeToUse = overrideShape
            ?: preferenceManager.getAppIconShape(packageName)
            ?: currentShape

        // 1. Per-app custom override (highest priority)
        if (!skipCustomOverride) {
            val customIconData = preferenceManager.getCustomIcons()[packageName]
            if (customIconData != null) {
            if (customIconData.startsWith("/")) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(customIconData)
                if (bitmap != null) {
                    val customDrawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                    return if (shapeToUse == -1) customDrawable else IconShapeMasker(customDrawable, shapeToUse)
                }
            } else if (customIconData.contains("::")) {
                val parts = customIconData.split("::")
                if (parts.size == 2) {
                    val packPkg = parts[0]
                    val resName = parts[1]
                    try {
                        val packContext = context.createPackageContext(packPkg, 0)
                        val resId = packContext.resources.getIdentifier(resName, "drawable", packPkg)
                        if (resId != 0) {
                            val packIcon = ResourcesCompat.getDrawable(packContext.resources, resId, null)
                            if (packIcon != null) {
                                return if (shapeToUse == -1) packIcon else IconShapeMasker(packIcon, shapeToUse)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("IconResolver", "Failed to load override pack icon $customIconData", e)
                    }
                }
            }
            }
        }

        if (actualClassName != null) {
            val component = ComponentName(packageName, actualClassName)
            val drawableId = iconPackParser.getDrawableId(component)
            val packRes = iconPackParser.getPackResources()
            if (drawableId != null && packRes != null) {
                try {
                    val packIcon = ResourcesCompat.getDrawable(packRes, drawableId, null)
                    if (packIcon != null) {
                        return if (shapeToUse == -1) packIcon else IconShapeMasker(packIcon, shapeToUse)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("IconResolver", "Failed to load custom icon for $component", e)
                }
            }
        }
        
        if (themedIconFactory.enabled) {
            try {
                val density = context.resources.displayMetrics.density
                val bakePx = ThemedIconFactory.targetBitmapPx(density)
                val themed = themedIconFactory.createThemedDrawable(packageName, bakePx)
                val maskShape = if (shapeToUse == -1) {
                    ThemedIconFactory.THEMED_DEFAULT_SHAPE
                } else {
                    shapeToUse
                }
                return IconShapeMasker(themed, maskShape)
            } catch (e: Exception) {
                // A bake failure must degrade to the plain icon, never propagate — callers
                // build whole app lists from this and would drop the app otherwise.
                android.util.Log.w("IconResolver", "Themed bake failed for $packageName, falling back", e)
            }
        }

        val rawDrawable = try {
            pm.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            pm.defaultActivityIcon
        }
        
        return if (shapeToUse == -1) rawDrawable else IconShapeMasker(rawDrawable, shapeToUse)
    }
    
    companion object {
        var currentShape: Int = -1
    }
    
    private val classNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun getClassName(pm: PackageManager, packageName: String): String? {
        val cached = classNameCache[packageName]
        if (cached != null) {
            return if (cached == "") null else cached
        }
        val intent = pm.getLaunchIntentForPackage(packageName)
        val className = intent?.component?.className
        classNameCache[packageName] = className ?: ""
        return className
    }
}
