package com.nexus.launcher.ui.backup

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.ui.dock.settings.DockSettingsSnapshot
import com.nexus.launcher.ui.folder.FolderConfigCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Assembles the Backup/Restore v1 [manifest.json] object (formatVersion 1).
 * Call on Dispatchers.IO.
 */
object BackupManifestBuilder {
    private val gson = Gson()

    data class AssetRefs(
        val folderCovers: List<Map<String, String>>,
        val customAppIcons: List<Map<String, String>>,
        val wallpaperImages: List<Map<String, String>>
    )

    fun build(
        items: List<HomeScreenItem>,
        pages: List<Triple<Int, String, String?>>,
        settings: NexusSettingsData,
        dock: DockSettingsSnapshot,
        assets: AssetRefs,
        coverPathByFolderId: Map<Int, String>,
        preferenceManager: PreferenceManager,
        defaultPage: Int = 0,
        /** Needed to export first-party widget instance config (NexusWidgetConfig / Notes). */
        context: Context? = null,
        /** Real home page count. [pages] can carry extra stored metadata beyond it. */
        pageCount: Int = pages.size,
        /** Pixel size the page previews were rendered at, or null when none were written. */
        thumbSize: Pair<Int, Int>? = null,
        /** User-facing name for this backup; defaults to its creation date. */
        label: String? = null,
        /** Theme engine / typography / feed blocks — the stores outside NexusSettingsData. */
        themeEngine: JsonObject? = null,
        typography: JsonObject? = null,
        feedSources: com.google.gson.JsonElement? = null,
        locale: JsonObject? = null
    ): JsonObject {
        val root = JsonObject()
        // v2 adds settingsSnapshot (the whole NexusSettingsData), themeEngine, typography,
        // feedSources and behavior. v1 readers ignore them; the v1 blocks below still ship.
        root.addProperty("formatVersion", BackupImporter.CURRENT_FORMAT_VERSION)
        root.addProperty("createdAt", isoNow())
        root.add("sourceDevice", sourceDevice())

        val itemsArr = JsonArray()
        items.forEach { itemsArr.add(toItem(it, coverPathByFolderId, context)) }
        root.add("homeScreenItems", itemsArr)

        val pagesArr = JsonArray()
        pages.forEach { pagesArr.add(toPage(it)) }
        root.add("pages", pagesArr)

        // Everything the backups list renders on a card, grouped so it can be read without
        // parsing the rest of the manifest.
        root.add("catalog", JsonObject().apply {
            addProperty("label", label ?: "")
            addProperty("pageCount", pageCount)
            addProperty("defaultPage", defaultPage)
            addProperty("itemCount", items.size)
            addProperty("thumbWidth", thumbSize?.first ?: 0)
            addProperty("thumbHeight", thumbSize?.second ?: 0)
        })
        // The authoritative settings payload: every NexusSettingsData field, serialized whole.
        // The hand-listed blocks below are kept because they make a manifest readable by eye, but
        // restore reads this — so a field added to the data class is backed up without anyone
        // remembering to add it here, which is exactly how the old blocks fell behind.
        root.add("settingsSnapshot", gson.toJsonTree(settings))
        themeEngine?.let { root.add("themeEngine", it) }
        typography?.let { root.add("typography", it) }
        feedSources?.let { root.add("feedSources", it) }
        locale?.let { root.add("locale", it) }
        root.add("behavior", behaviorObject(preferenceManager))
        root.add("standaloneSettings", standaloneSettingsObject(context))
        root.add("settings", settingsObject(settings, dock, defaultPage))
        root.add("bundledAssets", bundledAssets(assets))
        root.add("perAppPreferences", perAppPreferencesObject(preferenceManager))
        root.add("missingDataNotes", JsonArray())
        return root
    }

    private fun toItem(item: HomeScreenItem, coverById: Map<Int, String>, context: Context?): JsonObject {
        val type = com.nexus.launcher.data.HomeItemTypes.backupName(item.itemType)
        val o = JsonObject()
        if (type == "FOLDER") {
            o.add("packageName", JsonNull.INSTANCE)
        } else {
            o.addProperty("packageName", item.packageName)
        }
        o.addProperty("id", item.id)
        o.addProperty("page", item.page)
        o.addProperty("column", item.column)
        o.addProperty("row", item.row)
        o.addProperty("positionXFraction", item.xFraction)
        o.addProperty("positionYFraction", item.yFraction)
        o.addProperty("itemType", type)
        o.addProperty("spanColumns", item.spanX)
        o.addProperty("spanRows", item.spanY)
        o.addProperty("stackOrder", item.zIndex)
        o.addProperty("paddingEnabled", item.paddingEnabled)
        // Raw per-item config: shortcut id (SHORTCUT), box layout (SHORTCUT_BOX / APP_BOX), widget
        // free-size fractions (WIDGET). Folder / mosaic configs are exported structured below.
        o.addProperty("configJson", item.folderConfigJson)
        if (item.containerId == -1L) {
            o.add("parentFolderId", JsonNull.INSTANCE)
        } else {
            o.addProperty("parentFolderId", item.containerId)
        }
        if (type == "WIDGET") {
            o.add("widget", widgetObj(item, context))
            o.add("folder", JsonNull.INSTANCE)
            o.add("mosaic", JsonNull.INSTANCE)
        } else if (type == "FOLDER") {
            o.add("widget", JsonNull.INSTANCE)
            o.add("folder", folderObj(item, coverById[item.id]))
            o.add("mosaic", JsonNull.INSTANCE)
        } else if (type == "MOSAIC") {
            o.add("widget", JsonNull.INSTANCE)
            o.add("folder", JsonNull.INSTANCE)
            o.add("mosaic", mosaicObj(item))
        } else {
            o.add("widget", JsonNull.INSTANCE)
            o.add("folder", JsonNull.INSTANCE)
            o.add("mosaic", JsonNull.INSTANCE)
        }
        return o
    }

    private fun widgetObj(item: HomeScreenItem, context: Context?): JsonObject {
        val w = JsonObject()
        w.addProperty("providerPackageName", item.packageName)
        val resolvedCls = item.providerClassName ?: if (context != null && item.appWidgetId > 0) {
            try {
                android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetInfo(item.appWidgetId)?.provider?.className
            } catch (_: Exception) { null }
        } else null

        if (resolvedCls != null) {
            w.addProperty("providerClassName", resolvedCls)
        } else {
            w.add("providerClassName", JsonNull.INSTANCE)
        }
        // First-party Nexus widgets keep per-instance state in SharedPreferences keyed by
        // appWidgetId, which changes on restore — export it so the rebinder can re-key it.
        val isNexus = context != null && (item.packageName == context.packageName || (resolvedCls != null && resolvedCls.contains("com.nexus.launcher")))
        if (context != null && item.appWidgetId > 0 && isNexus) {
            w.add("nexusConfig", gson.toJsonTree(
                com.nexus.launcher.ui.widgets.NexusWidgetConfig.read(context, item.appWidgetId)
            ))
            if (resolvedCls != null && resolvedCls.contains("NexusNotesWidgetProvider")) {
                w.add("note", gson.toJsonTree(
                    com.nexus.launcher.ui.widgets.notes.NotesDataStore.read(context, item.appWidgetId)
                ))
            }
        }
        return w
    }

    private fun folderObj(item: HomeScreenItem, coverRel: String?): JsonObject {
        val f = JsonObject()
        f.addProperty("title", item.folderTitle)
        val config = FolderConfigCodec.parse(item.folderConfigJson)
        f.add("config", JsonParser.parseString(gson.toJson(config)).asJsonObject)
        if (coverRel != null) {
            f.addProperty("coverImageFile", coverRel)
        } else {
            f.add("coverImageFile", JsonNull.INSTANCE)
        }
        return f
    }

    private fun mosaicObj(item: HomeScreenItem): JsonObject {
        val m = JsonObject()
        val config = com.nexus.launcher.ui.widgets.mosaic.MosaicConfig.parse(item.folderConfigJson)
        m.addProperty("mode", config.mode)
        m.addProperty("focusIndex", config.focusIndex)
        m.addProperty("pageIndex", config.pageIndex)
        m.addProperty("surfaceOpacity", config.surfaceOpacity)
        m.addProperty("backgroundMode", config.backgroundMode)
        m.addProperty("frostedGradientIndex", config.frostedGradientIndex)
        
        val pagesArr = JsonArray()
        config.pages.forEach { page ->
            val pageObj = JsonObject()
            if (page.layoutTemplate != null) {
                pageObj.addProperty("layoutTemplate", page.layoutTemplate)
            } else {
                pageObj.add("layoutTemplate", JsonNull.INSTANCE)
            }
            
            val kidsArr = JsonArray()
            page.children.forEach { child ->
                val childObj = JsonObject()
                childObj.addProperty("providerPackage", child.providerPackage)
                if (child.providerClassName.isNotBlank()) {
                    childObj.addProperty("providerClassName", child.providerClassName)
                } else {
                    childObj.add("providerClassName", JsonNull.INSTANCE)
                }
                kidsArr.add(childObj)
            }
            pageObj.add("children", kidsArr)
            pagesArr.add(pageObj)
        }
        m.add("pages", pagesArr)
        return m
    }

    private fun toPage(meta: Triple<Int, String, String?>): JsonObject {
        val type = when (meta.second.lowercase(Locale.US)) {
            else -> "GRID"
        }
        val o = JsonObject()
        o.addProperty("index", meta.first)
        o.addProperty("type", type)
        return o
    }

    private fun settingsObject(s: NexusSettingsData, dock: DockSettingsSnapshot, defaultPage: Int): JsonObject {
        val o = JsonObject()
        o.add("homeScreen", gson.toJsonTree(linkedMapOf(
            "columns" to s.homeColumns,
            "rows" to s.homeRows,
            "paddingLeftRightDp" to s.homePaddingLeftRightDp,
            "paddingTopBottomDp" to s.homePaddingTopBottomDp,
            "gapHorizontalDp" to s.homeGapHorizontalDp,
            "gapVerticalDp" to s.homeGapVerticalDp,
            "iconSizeMultiplier" to s.homeIconSizeMultiplier,
            "showLabels" to s.homeShowLabels,
            "twoLineLabels" to s.homeTwoLineLabels,
            "showPageIndicator" to s.homeShowIndicator,
            "pageCount" to s.homePageCount,
            "defaultPage" to defaultPage,
            "pageTransition" to s.pageTransition,
            "badgeStyleApp" to s.badgeStyleApp,
            "badgeStyleFolder" to s.badgeStyleFolder
        )))
        o.add("dock", gson.toJsonTree(linkedMapOf(
            "maxIcons" to dock.maxIcons,
            "heightDp" to dock.dockHeightDp,
            "iconSizeDp" to dock.iconSizeDp,
            "shuffleMotionPercent" to dock.shuffleMotionPercent,
            "backgroundMode" to dock.backgroundMode,
            "solidColorArgb" to dock.solidColorArgb,
            "frostedGradientIndex" to dock.frostedGradientIndex,
            "showLabels" to dock.showLabels,
            "labelFontSizeSp" to dock.labelFontSizeSp,
            "searchInDock" to dock.searchInDock,
            "searchSlotIndex" to dock.searchSlotIndex,
            "cornerRadiusDp" to dock.cornerRadiusDp,
            "backgroundOpacity" to dock.dockBackgroundOpacity,
            "glassRefraction" to dock.dockGlassRefraction
        )))
        o.add("appDrawer", gson.toJsonTree(linkedMapOf(
            "columns" to s.drawerColumns,
            "iconSizeMultiplier" to s.drawerIconSizeMultiplier,
            "showLabels" to s.drawerShowLabels,
            "twoLineLabels" to s.drawerTwoLineLabels,
            "showCategoryBar" to s.drawerShowCategoryBar,
            "showRail" to s.drawerShowRail,
            "sortOrder" to s.drawerSortOrder,
            "showSearchPill" to s.drawerShowSearchPill,
            "searchBarPosition" to s.drawerSearchBarPosition,
            "categoryMode" to s.drawerCategoryMode,
            "categoryPosition" to s.drawerCategoryPosition,
            "layout" to s.drawerLayout
        )))
        o.add("theme", gson.toJsonTree(linkedMapOf(
            "accentColor" to s.accentColor,
            "matchWallpaperColor" to s.matchWallpaperColor,
            "wallpaperType" to s.wallpaperType,
            "wallpaperSolidColor" to s.wallpaperSolidColor,
            "wallpaperGradientStart" to s.wallpaperGradientStart,
            "wallpaperGradientEnd" to s.wallpaperGradientEnd,
            "wallpaperGradientDirection" to s.wallpaperGradientDirection,
            "wallpaperBlur" to s.wallpaperBlur,
            "wallpaperTintColor" to s.wallpaperTintColor,
            "wallpaperTintStrength" to s.wallpaperTintStrength,
            "iconPack" to s.iconPack,
            "iconShape" to s.iconShape
        )))
        return o
    }

    private fun bundledAssets(a: AssetRefs): JsonObject {
        val o = JsonObject()
        o.add("folderCovers", gson.toJsonTree(a.folderCovers))
        o.add("customAppIcons", gson.toJsonTree(a.customAppIcons))
        o.add("wallpaperImages", gson.toJsonTree(a.wallpaperImages))
        return o
    }

    private fun perAppPreferencesObject(prefs: PreferenceManager): JsonObject {
        val o = JsonObject()
        o.add("hiddenApps", gson.toJsonTree(prefs.getHiddenApps()))
        o.add("renamedApps", gson.toJsonTree(prefs.getRenamedApps()))
        
        val gestureActions = JsonObject()
        val swipeUp = prefs.getSwipeUpActions()
        val swipeDown = prefs.getSwipeDownActions()
        val doubleTap = prefs.getDoubleTapActions()
        
        val allActionPkgs = swipeUp.keys + swipeDown.keys + doubleTap.keys
        for (pkg in allActionPkgs) {
            val pkgActions = JsonObject()
            swipeUp[pkg]?.let { pkgActions.addProperty("swipeUp", it) }
            swipeDown[pkg]?.let { pkgActions.addProperty("swipeDown", it) }
            doubleTap[pkg]?.let { pkgActions.addProperty("doubleTap", it) }
            gestureActions.add(pkg, pkgActions)
        }
        o.add("gestureActions", gestureActions)
        
        o.add("iconShapeOverrides", gson.toJsonTree(prefs.getAppIconShapes()))
        return o
    }

    /** Launcher behaviour kept in [PreferenceManager] rather than the settings DataStore. */
    private fun behaviorObject(prefs: PreferenceManager): JsonObject {
        val o = JsonObject()
        o.add("folderCategories", gson.toJsonTree(prefs.getFolderCategories().mapKeys { it.key.toString() }))
        o.add("appCategoryOverrides", gson.toJsonTree(prefs.getAppCategoryOverrides()))
        o.addProperty("customCategories", prefs.getDrawerCategoryJson())
        o.addProperty("categoryIcons", prefs.getDrawerCategoryIconsJson())
        o.addProperty("categoryNames", prefs.getDrawerCategoryNamesJson())
        o.add("hiddenBuiltinCategories", gson.toJsonTree(prefs.getHiddenDrawerCategories()))
        o.add("addedSuggestedCategories", gson.toJsonTree(prefs.getAddedDrawerCategories()))
        o.addProperty("animSpeedMultiplier", prefs.getAnimSpeedMultiplier())
        o.addProperty("reduceMotion", prefs.getReduceMotion())
        return o
    }

    /**
     * Settings kept in their own SharedPreferences files rather than the settings DataStore:
     * the search result-source toggles ("nexus_search_prefs") and the weather temperature unit
     * ("nexus_weather_cache"). Only the unit is taken from the weather file — the rest of it is
     * fetched data with no place in a backup.
     */
    private fun standaloneSettingsObject(context: Context?): JsonObject {
        val o = JsonObject()
        if (context == null) return o
        val search = com.nexus.launcher.search.NexusSearchSettings(context)
        o.add("search", JsonObject().apply {
            addProperty("contacts", search.searchContactsEnabled)
            addProperty("web", search.searchWebEnabled)
            addProperty("calculator", search.searchCalculatorEnabled)
            addProperty("conversion", search.searchConversionEnabled)
            addProperty("maps", search.searchMapsEnabled)
        })
        o.add("weather", JsonObject().apply {
            addProperty(
                "unitOverride",
                context.getSharedPreferences("nexus_weather_cache", Context.MODE_PRIVATE)
                    .getString("weather_unit_override", "auto") ?: "auto"
            )
        })
        return o
    }

    private fun sourceDevice(): JsonObject {
        val o = JsonObject()
        o.addProperty("manufacturer", Build.MANUFACTURER ?: "")
        o.addProperty("model", Build.MODEL ?: "")
        o.addProperty("androidVersion", Build.VERSION.RELEASE ?: "")
        return o
    }

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}
