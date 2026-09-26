package com.nexus.launcher.ui.icons

import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Icon-pack lookup with a cheap load and lazy resolution.
 *
 * [loadPack] only parses `appfilter.xml` into a component → drawable *name* map — a pure XML
 * walk that takes tens of milliseconds even for packs with thousands of entries. Drawable
 * resource ids are resolved on first [getDrawableId] for that component and memoised, so the
 * `getIdentifier` IPC is paid only for apps actually installed (~200), not for every entry in
 * the pack (~5–10k).
 *
 * The previous implementation loaded the pack's dex (`CONTEXT_INCLUDE_CODE` + reflection on
 * `R$drawable`) and, when that failed — which it does on any pack with R-class inlining — fell
 * back to `getIdentifier` for every entry up front. That was the multi-second stall on apply
 * and on cold start.
 */
@Singleton
class IconPackParser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var currentPack: String = "none"
    @Volatile private var nameMap: Map<ComponentName, String> = emptyMap()
    @Volatile private var packResources: Resources? = null
    private val idCache = ConcurrentHashMap<String, Int>()

    val loadedPack: String get() = currentPack

    suspend fun loadPack(packageName: String) = withContext(Dispatchers.IO) {
        if (packageName == currentPack) return@withContext
        if (packageName == "none" || packageName.isBlank()) {
            clear()
            return@withContext
        }

        try {
            val pm = context.packageManager
            val res = pm.getResourcesForApplication(packageName)
            val newMap = HashMap<ComponentName, String>(4096)

            // IPS packs ship appfilter under res/raw; Apex/Nova packs use res/xml.
            parseRawAppFilter(res, packageName, newMap)
            if (newMap.isEmpty()) {
                parseXmlAppFilter(res, packageName, newMap)
            }
            if (newMap.isEmpty()) {
                parseAssetsAppFilter(packageName, newMap)
            }

            if (newMap.isEmpty()) {
                Log.w("IconPackParser", "No appfilter.xml found in raw/xml/assets of $packageName")
            }

            idCache.clear()
            nameMap = newMap
            packResources = res
            currentPack = packageName
            Log.d("IconPackParser", "Indexed ${newMap.size} entries from $packageName")
        } catch (e: Exception) {
            Log.e("IconPackParser", "Error loading pack $packageName", e)
            clear()
        }
    }

    private fun parseRawAppFilter(
        res: Resources,
        packageName: String,
        newMap: HashMap<ComponentName, String>
    ) {
        val rawId = res.getIdentifier("appfilter", "raw", packageName)
        if (rawId == 0) return
        try {
            res.openRawResource(rawId).use { stream ->
                val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(stream, "UTF-8")
                parseAppFilterXml(parser, newMap)
            }
        } catch (e: Exception) {
            Log.w("IconPackParser", "Failed parsing res raw appfilter in $packageName", e)
        }
    }

    private fun parseXmlAppFilter(
        res: Resources,
        packageName: String,
        newMap: HashMap<ComponentName, String>
    ) {
        val appFilterId = res.getIdentifier("appfilter", "xml", packageName)
        if (appFilterId == 0) return
        try {
            parseAppFilterXml(res.getXml(appFilterId), newMap)
        } catch (e: Exception) {
            Log.w("IconPackParser", "Failed parsing res xml appfilter in $packageName", e)
        }
    }

    private fun parseAssetsAppFilter(
        packageName: String,
        newMap: HashMap<ComponentName, String>
    ) {
        try {
            val packContext = context.createPackageContext(packageName, 0)
            val assetNames = listOf("appfilter.xml", "app_filter.xml", "theme_resources.xml")
            for (assetName in assetNames) {
                try {
                    packContext.assets.open(assetName).use { stream ->
                        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                        val parser = factory.newPullParser()
                        parser.setInput(stream, "UTF-8")
                        parseAppFilterXml(parser, newMap)
                    }
                    if (newMap.isNotEmpty()) break
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w("IconPackParser", "Failed opening assets in $packageName", e)
        }
    }

    private fun parseAppFilterXml(xpp: XmlPullParser, newMap: HashMap<ComponentName, String>) {
        var eventType = xpp.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && xpp.name == "item") {
                val componentValue = xpp.getAttributeValue(null, "component")
                val drawableValue = xpp.getAttributeValue(null, "drawable")
                if (componentValue != null && !drawableValue.isNullOrBlank()) {
                    parseComponent(componentValue)?.let { newMap[it] = drawableValue }
                }
            }
            eventType = xpp.next()
        }
    }

    /** Resolved drawable id matching [keyword] in component package, class, or drawable name. */
    fun findDrawableIdByKeyword(keyword: String): Int? {
        val res = packResources ?: return null
        val pack = currentPack
        for ((comp, drawableName) in nameMap) {
            if (comp.packageName.contains(keyword, ignoreCase = true) ||
                comp.className.contains(keyword, ignoreCase = true) ||
                drawableName.contains(keyword, ignoreCase = true)
            ) {
                val id = idCache.getOrPut(drawableName) {
                    try { res.getIdentifier(drawableName, "drawable", pack) } catch (_: Exception) { 0 }
                }
                if (id != 0) return id
            }
        }
        return null
    }

    /** Resolved drawable id for [componentName], or null if the pack has no entry / it's missing. */
    fun getDrawableId(componentName: ComponentName): Int? {
        val name = nameMap[componentName] ?: return null
        val res = packResources ?: return null
        val pack = currentPack
        val id = idCache.getOrPut(name) {
            try { res.getIdentifier(name, "drawable", pack) } catch (_: Exception) { 0 }
        }
        return if (id != 0) id else null
    }

    fun getPackResources(): Resources? = packResources

    private fun clear() {
        currentPack = "none"
        nameMap = emptyMap()
        packResources = null
        idCache.clear()
    }

    private fun parseComponent(componentString: String): ComponentName? {
        var cleaned = componentString
        if (cleaned.startsWith("ComponentInfo{") && cleaned.endsWith("}")) {
            cleaned = cleaned.substring(14, cleaned.length - 1)
        }
        val parts = cleaned.split("/")
        return if (parts.size == 2) ComponentName(parts[0], parts[1]) else null
    }
}
