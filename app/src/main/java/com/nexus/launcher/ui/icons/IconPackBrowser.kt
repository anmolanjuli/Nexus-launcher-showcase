package com.nexus.launcher.ui.icons

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

data class BrowserIcon(
    val drawableName: String,
    val resId: Int
)

typealias PackIcon = BrowserIcon

class IconPackBrowser(private val context: Context) {

    suspend fun loadIcons(packageName: String): List<BrowserIcon> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<BrowserIcon>()
        try {
            val pm = context.packageManager
            val res = pm.getResourcesForApplication(packageName)
            val appFilterId = res.getIdentifier("appfilter", "xml", packageName)
            
            if (appFilterId == 0) {
                Log.w("IconPackBrowser", "No appfilter.xml found in $packageName")
                return@withContext emptyList()
            }

            val reflectionMap = mutableMapOf<String, Int>()
            try {
                val packContext = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE)
                val rClass = Class.forName("$packageName.R\$drawable", true, packContext.classLoader)
                for (field in rClass.fields) {
                    if (field.type == Int::class.javaPrimitiveType || field.type == Int::class.java) {
                        reflectionMap[field.name] = field.getInt(null)
                    }
                }
            } catch (e: Exception) {
                Log.w("IconPackBrowser", "Reflection fast-path unavailable, using getIdentifier", e)
            }

            val seenDrawables = mutableSetOf<String>()
            val xpp = res.getXml(appFilterId)
            var eventType = xpp.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && xpp.name == "item") {
                    val drawableValue = xpp.getAttributeValue(null, "drawable")
                    if (drawableValue != null && !seenDrawables.contains(drawableValue)) {
                        seenDrawables.add(drawableValue)
                        val drawableId = if (reflectionMap.isNotEmpty()) {
                            reflectionMap[drawableValue] ?: 0
                        } else {
                            res.getIdentifier(drawableValue, "drawable", packageName)
                        }
                        
                        if (drawableId != 0) {
                            resultList.add(BrowserIcon(drawableValue, drawableId))
                        }
                    }
                }
                eventType = xpp.next()
            }
        } catch (e: Exception) {
            Log.e("IconPackBrowser", "Error loading pack $packageName", e)
        }
        return@withContext resultList
    }
}
