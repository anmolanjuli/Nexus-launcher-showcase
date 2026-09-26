package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.FolderConfig
import com.google.gson.Gson
import org.json.JSONObject

object FolderConfigCodec {

    private val gson = Gson()

    fun parse(json: String?): FolderConfig {
        if (json.isNullOrBlank()) return FolderConfig()
        return try {
            val parsed = gson.fromJson(json, FolderConfig::class.java) ?: FolderConfig()
            val withWindowOpacity = if (!JSONObject(json).has("windowBackgroundOpacity")) {
                parsed.copy(windowBackgroundOpacity = parsed.backgroundOpacity)
            } else {
                parsed
            }
            normalizePlateFields(FolderShapeStyle.normalize(withWindowOpacity))
        } catch (_: Exception) {
            normalizePlateFields(parseLegacy(json))
        }
    }

    private fun normalizePlateFields(config: FolderConfig): FolderConfig = config.copy(
        iconBackgroundMode = FolderIconPlateDraw.normalizeBackgroundMode(config.iconBackgroundMode),
        themeMode = when (config.themeMode.uppercase()) {
            com.nexus.launcher.ui.widgets.NexusWidgetConfig.THEME_LIGHT -> com.nexus.launcher.ui.widgets.NexusWidgetConfig.THEME_LIGHT
            com.nexus.launcher.ui.widgets.NexusWidgetConfig.THEME_DARK -> com.nexus.launcher.ui.widgets.NexusWidgetConfig.THEME_DARK
            else -> com.nexus.launcher.ui.widgets.NexusWidgetConfig.THEME_FOLLOW
        }
    )

    fun toJson(config: FolderConfig): String = gson.toJson(config)

    private fun parseLegacy(json: String): FolderConfig {
        return try {
            val o = JSONObject(json)
            FolderConfig(
                backgroundColor = if (o.has("backgroundColor")) o.getString("backgroundColor") else null,
                shapeStyle = o.optInt("shapeStyle", 0),
                previewStyle = o.optInt("previewStyle", 0),
                customIconPackage = if (o.has("customIconPackage")) o.getString("customIconPackage") else null,
                openAnimation = o.optInt("openAnimation", 1),
                sortMode = o.optInt("sortMode", 0),
                gridColumns = o.optInt("gridColumns", 5),
                showLabels = o.optBoolean("showLabels", true),
                showAccentRing = o.optBoolean("showAccentRing", false),
                windowBackgroundMode = o.optString("windowBackgroundMode", "TRANSPARENT"),
                frostedGradientIndex = o.optInt("frostedGradientIndex", 0),
                solidBackgroundColor = if (o.has("solidBackgroundColor")) {
                    o.getString("solidBackgroundColor")
                } else {
                    null
                },
                flipHorizontal = o.optBoolean("flipHorizontal", false),
                flipVertical = o.optBoolean("flipVertical", false),
                backgroundOpacity = o.optDouble("backgroundOpacity", 1.0).toFloat(),
                windowBackgroundOpacity = if (o.has("windowBackgroundOpacity")) {
                    o.getDouble("windowBackgroundOpacity").toFloat()
                } else {
                    o.optDouble("windowBackgroundOpacity", 1.0).toFloat()
                },
                glassRefraction = o.optDouble("glassRefraction", 0.70).toFloat(),
                previewStyleExplicitlySet = o.optBoolean("previewStyleExplicitlySet", false),
                isExpressive = o.optBoolean("isExpressive", false),
                iconBackgroundMode = FolderIconPlateDraw.normalizeBackgroundMode(
                    o.optString("iconBackgroundMode", com.nexus.launcher.ui.widgets.NexusWidgetConfig.BG_GLASS)
                ),
                themeMode = o.optString("themeMode", com.nexus.launcher.ui.widgets.NexusWidgetConfig.THEME_FOLLOW)
            )
        } catch (_: Exception) {
            FolderConfig()
        }
    }
}
