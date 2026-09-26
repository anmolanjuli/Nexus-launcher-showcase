package com.nexus.launcher.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun getPreferences(): LauncherPreferences {
        val rowCount = prefs.getInt("row_count", 12)
        val columnCount = prefs.getInt("column_count", 6)
        val labelVisibilityStr = prefs.getString("label_visibility", LabelVisibility.ELLIPSIZE.name) ?: LabelVisibility.ELLIPSIZE.name
        
        val labelVisibility = try {
            LabelVisibility.valueOf(labelVisibilityStr)
        } catch (e: Exception) {
            LabelVisibility.ELLIPSIZE
        }

        return LauncherPreferences(
            rowCount = rowCount,
            columnCount = columnCount,
            labelVisibility = labelVisibility
        )
    }

    fun savePreferences(preferences: LauncherPreferences) {
        prefs.edit().apply {
            putInt("row_count", preferences.rowCount)
            putInt("column_count", preferences.columnCount)
            putString("label_visibility", preferences.labelVisibility.name)
            apply()
        }
    }

    fun getHiddenApps(): Set<String> {
        return prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()
    }

    fun saveHiddenApps(packages: Set<String>) {
        prefs.edit()
            .putStringSet("hidden_apps", packages)
            .apply()
    }

    fun clearHiddenApps() {
        prefs.edit().remove("hidden_apps").apply()
    }

    fun addHiddenApp(packageName: String) {
        val current = getHiddenApps().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet("hidden_apps", current).apply()
    }

    fun getRenamedApps(): Map<String, String> {
        val renames = mutableMapOf<String, String>()
        val keys = prefs.getStringSet("renamed_apps_keys", emptySet()) ?: emptySet()
        for (key in keys) {
            val name = prefs.getString("renamed_app_$key", null)
            if (name != null) {
                renames[key] = name
            }
        }
        return renames
    }

    fun setAppRename(packageName: String, newName: String) {
        val keys = (prefs.getStringSet("renamed_apps_keys", emptySet()) ?: emptySet()).toMutableSet()
        keys.add(packageName)
        prefs.edit()
            .putStringSet("renamed_apps_keys", keys)
            .putString("renamed_app_$packageName", newName)
            .apply()
    }

    fun clearRenamedApps() {
        val editor = prefs.edit()
        val keys = prefs.getStringSet("renamed_apps_keys", emptySet()) ?: emptySet()
        keys.forEach { editor.remove("renamed_app_$it") }
        editor.remove("renamed_apps_keys").apply()
    }

    fun getCustomIcons(): Map<String, String> {
        val customIcons = mutableMapOf<String, String>()
        val keys = prefs.getStringSet("custom_icons_keys", emptySet()) ?: emptySet()
        for (key in keys) {
            val iconData = prefs.getString("custom_icon_$key", null)
            if (iconData != null) {
                customIcons[key] = iconData
            }
        }
        return customIcons
    }

    fun setCustomIcon(packageName: String, iconData: String?) {
        val keys = (prefs.getStringSet("custom_icons_keys", emptySet()) ?: emptySet()).toMutableSet()
        val editor = prefs.edit()
        if (iconData == null) {
            keys.remove(packageName)
            editor.remove("custom_icon_$packageName")
        } else {
            keys.add(packageName)
            editor.putString("custom_icon_$packageName", iconData)
        }
        editor.putStringSet("custom_icons_keys", keys).apply()
    }

    fun getSwipeUpActions(): Map<String, String> {
        val actions = mutableMapOf<String, String>()
        val keys = prefs.getStringSet("swipe_up_action_keys", emptySet()) ?: emptySet()
        for (key in keys) {
            val actionStr = prefs.getString("swipe_up_action_$key", null)
            if (actionStr != null) {
                actions[key] = actionStr
            }
        }
        return actions
    }

    fun setSwipeUpAction(packageName: String, actionStr: String?) {
        val keys = (prefs.getStringSet("swipe_up_action_keys", emptySet()) ?: emptySet()).toMutableSet()
        val editor = prefs.edit()
        if (actionStr == null || actionStr == "NONE") {
            keys.remove(packageName)
            editor.remove("swipe_up_action_$packageName")
        } else {
            keys.add(packageName)
            editor.putString("swipe_up_action_$packageName", actionStr)
        }
        editor.putStringSet("swipe_up_action_keys", keys).apply()
    }

    fun clearSwipeUpActions() {
        val editor = prefs.edit()
        val keys = prefs.getStringSet("swipe_up_action_keys", emptySet()) ?: emptySet()
        keys.forEach { editor.remove("swipe_up_action_$it") }
        editor.remove("swipe_up_action_keys").apply()
    }

    fun getSwipeDownActions(): Map<String, String> {
        val actions = mutableMapOf<String, String>()
        val keys = prefs.getStringSet("swipe_down_action_keys", emptySet()) ?: emptySet()
        for (key in keys) {
            val actionStr = prefs.getString("swipe_down_action_$key", null)
            if (actionStr != null) {
                actions[key] = actionStr
            }
        }
        return actions
    }

    fun setSwipeDownAction(packageName: String, actionStr: String?) {
        val keys = (prefs.getStringSet("swipe_down_action_keys", emptySet()) ?: emptySet()).toMutableSet()
        val editor = prefs.edit()
        if (actionStr == null || actionStr == "NONE") {
            keys.remove(packageName)
            editor.remove("swipe_down_action_$packageName")
        } else {
            keys.add(packageName)
            editor.putString("swipe_down_action_$packageName", actionStr)
        }
        editor.putStringSet("swipe_down_action_keys", keys).apply()
    }

    fun clearSwipeDownActions() {
        val editor = prefs.edit()
        val keys = prefs.getStringSet("swipe_down_action_keys", emptySet()) ?: emptySet()
        keys.forEach { editor.remove("swipe_down_action_$it") }
        editor.remove("swipe_down_action_keys").apply()
    }

    fun getDoubleTapActions(): Map<String, String> {
        val actions = mutableMapOf<String, String>()
        val keys = prefs.getStringSet("double_tap_action_keys", emptySet()) ?: emptySet()
        for (key in keys) {
            val actionStr = prefs.getString("double_tap_action_$key", null)
            if (actionStr != null) {
                actions[key] = actionStr
            }
        }
        return actions
    }

    fun setDoubleTapAction(packageName: String, actionStr: String?) {
        val keys = (prefs.getStringSet("double_tap_action_keys", emptySet()) ?: emptySet()).toMutableSet()
        val editor = prefs.edit()
        if (actionStr == null || actionStr == "NONE") {
            keys.remove(packageName)
            editor.remove("double_tap_action_$packageName")
        } else {
            keys.add(packageName)
            editor.putString("double_tap_action_$packageName", actionStr)
        }
        editor.putStringSet("double_tap_action_keys", keys).apply()
    }

    fun clearDoubleTapActions() {
        val editor = prefs.edit()
        val keys = prefs.getStringSet("double_tap_action_keys", emptySet()) ?: emptySet()
        keys.forEach { editor.remove("double_tap_action_$it") }
        editor.remove("double_tap_action_keys").apply()
    }

    /**
     * Per-app icon shape overrides.
     * Missing key → follow global Theme shape.
     * Stored int → IconShapePaths / IconShapeMasker ID (incl. -1 = System / no mask).
     */
    fun getAppIconShapes(): Map<String, Int> {
        val shapes = mutableMapOf<String, Int>()
        val keys = prefs.getStringSet("app_icon_shape_keys", emptySet()) ?: emptySet()
        for (key in keys) {
            if (prefs.contains("app_icon_shape_$key")) {
                shapes[key] = prefs.getInt("app_icon_shape_$key", -1)
            }
        }
        return shapes
    }

    /** Null = follow global Theme shape (no per-app override). */
    fun getAppIconShape(packageName: String): Int? {
        if (!prefs.contains("app_icon_shape_$packageName")) return null
        return prefs.getInt("app_icon_shape_$packageName", -1)
    }

    fun setAppIconShape(packageName: String, shapeId: Int?) {
        val keys = (prefs.getStringSet("app_icon_shape_keys", emptySet()) ?: emptySet()).toMutableSet()
        val editor = prefs.edit()
        if (shapeId == null) {
            keys.remove(packageName)
            editor.remove("app_icon_shape_$packageName")
        } else {
            keys.add(packageName)
            editor.putInt("app_icon_shape_$packageName", shapeId)
        }
        editor.putStringSet("app_icon_shape_keys", keys).apply()
    }

    fun clearAppIconShapes() {
        val editor = prefs.edit()
        val keys = prefs.getStringSet("app_icon_shape_keys", emptySet()) ?: emptySet()
        keys.forEach { editor.remove("app_icon_shape_$it") }
        editor.remove("app_icon_shape_keys").apply()
    }

    fun getPendingWidgetRebind(): Boolean {
        return prefs.getBoolean("pending_widget_rebind", false)
    }

    fun setPendingWidgetRebind(pending: Boolean) {
        prefs.edit().putBoolean("pending_widget_rebind", pending).apply()
    }

    /** Native backup restored per-app prefs / custom icons — refresh flows + app list on resume. */
    fun getPendingPrefsRefresh(): Boolean {
        return prefs.getBoolean("pending_prefs_refresh", false)
    }

    fun setPendingPrefsRefresh(pending: Boolean) {
        prefs.edit().putBoolean("pending_prefs_refresh", pending).apply()
    }

    fun getAnimSpeedMultiplier(): Float {
        return prefs.getFloat("anim_speed_multiplier", 1.0f)
    }

    fun setAnimSpeedMultiplier(value: Float) {
        prefs.edit().putFloat("anim_speed_multiplier", value).apply()
        com.nexus.launcher.ui.canvas.MotionSpeed.invalidate()
    }

    fun getReduceMotion(): Boolean {
        return prefs.getBoolean("reduce_motion", false)
    }

    fun setReduceMotion(enabled: Boolean) {
        prefs.edit().putBoolean("reduce_motion", enabled).apply()
    }

    /**
     * Per-app category override — takes precedence over [com.nexus.launcher.domain.search.CategoryEngine]'s
     * auto-classification. Missing key → no override, use the auto-classified category.
     */
    fun getAppCategoryOverride(packageName: String): Int? {
        if (!prefs.contains("app_category_override_$packageName")) return null
        return prefs.getInt("app_category_override_$packageName", -1)
    }

    /** Every user-set category override, for backup export. Uses the same key index the
     *  per-package setter maintains. */
    fun getAppCategoryOverrides(): Map<String, Int> {
        val keys = prefs.getStringSet("app_category_override_keys", emptySet()) ?: emptySet()
        return keys.mapNotNull { pkg ->
            getAppCategoryOverride(pkg)?.let { pkg to it }
        }.toMap()
    }

    fun setAppCategoryOverride(packageName: String, categoryId: Int?) {
        val keys = (prefs.getStringSet("app_category_override_keys", emptySet()) ?: emptySet()).toMutableSet()
        val editor = prefs.edit()
        if (categoryId == null) {
            keys.remove(packageName)
            editor.remove("app_category_override_$packageName")
        } else {
            keys.add(packageName)
            editor.putInt("app_category_override_$packageName", categoryId)
        }
        editor.putStringSet("app_category_override_keys", keys).apply()
    }

    /** Drawer categories the user added (JSON id -> name) and built-ins they deleted, for backup;
     *  see com.nexus.launcher.ui.DrawerCategories, which owns these keys. */
    fun getDrawerCategoryJson(): String? = prefs.getString("drawer_custom_categories", null)

    fun getHiddenDrawerCategories(): Set<String> =
        prefs.getStringSet("drawer_hidden_builtin_categories", emptySet()) ?: emptySet()

    /** Suggested categories the user added (Shopping, Travel, ...). */
    fun getAddedDrawerCategories(): Set<String> =
        prefs.getStringSet("drawer_added_suggested_categories", emptySet()) ?: emptySet()

    /** Category icon picks (JSON category id -> CategoryIconCatalog name). */
    fun getDrawerCategoryIconsJson(): String? = prefs.getString("drawer_category_icons", null)

    /** Renamed built-in categories (JSON category id -> name). */
    fun getDrawerCategoryNamesJson(): String? = prefs.getString("drawer_category_names", null)

    fun setDrawerCategories(
        customJson: String?, hiddenBuiltIns: Set<String>, iconsJson: String? = null,
        namesJson: String? = null, addedSuggested: Set<String>? = null,
    ) {
        prefs.edit().apply {
            if (customJson == null) remove("drawer_custom_categories") else putString("drawer_custom_categories", customJson)
            if (iconsJson == null) remove("drawer_category_icons") else putString("drawer_category_icons", iconsJson)
            if (namesJson == null) remove("drawer_category_names") else putString("drawer_category_names", namesJson)
            putStringSet("drawer_added_suggested_categories", addedSuggested.orEmpty())
        }.putStringSet("drawer_hidden_builtin_categories", hiddenBuiltIns).apply()
    }

    /** Drawer folders have no category of their own by default (folder id -> category index). */
    fun getFolderCategories(): Map<Long, Int> {
        val result = mutableMapOf<Long, Int>()
        val keys = prefs.getStringSet("folder_category_keys", emptySet()) ?: emptySet()
        for (key in keys) {
            val folderId = key.toLongOrNull() ?: continue
            if (prefs.contains("folder_category_$key")) {
                result[folderId] = prefs.getInt("folder_category_$key", -1)
            }
        }
        return result
    }

    fun setFolderCategory(folderId: Long, categoryId: Int?) {
        val key = folderId.toString()
        val keys = (prefs.getStringSet("folder_category_keys", emptySet()) ?: emptySet()).toMutableSet()
        val editor = prefs.edit()
        if (categoryId == null) {
            keys.remove(key)
            editor.remove("folder_category_$key")
        } else {
            keys.add(key)
            editor.putInt("folder_category_$key", categoryId)
        }
        editor.putStringSet("folder_category_keys", keys).apply()
    }
}
