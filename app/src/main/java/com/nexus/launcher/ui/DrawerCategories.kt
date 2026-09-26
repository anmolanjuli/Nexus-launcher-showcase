package com.nexus.launcher.ui

import android.content.Context
import androidx.annotation.DrawableRes
import com.nexus.launcher.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nexus.launcher.domain.search.AppCategoryCatalog
import org.json.JSONObject

/**
 * The drawer's categories: the eight it starts with, the suggested ones the user can add, and
 * any the user names themselves.
 *
 * A category is known by a string key, which is what the drawer's selection and each
 * [com.nexus.launcher.ui.model.DisplayItem] carry, and by an int id, which is what app and
 * folder assignments store.
 * - Built-ins keep the ids `CategoryEngine` assigns (0 = All, 1..7) and their English names as
 *   keys, so every existing assignment and backup still means the same thing.
 * - Added categories get ids from [FIRST_CUSTOM_ID] up and the key `custom:<id>`. Ids are never
 *   reused, so a stale assignment can only point at nothing, never at the wrong category.
 *
 * Deleting a built-in hides it (its apps then only show under All); deleting either kind also
 * clears every app override and folder tag pointing at it. All stays: it is the unfiltered view.
 *
 * Every category, built-in or added, can wear any icon from [CategoryIconCatalog] and be renamed.
 * A built-in's name is its translation until renamed; the rename is stored as an override, and
 * clearing it (or setting the translation itself) goes back to following the app language.
 */
class DrawerCategories(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(load())

    /** Ids of [keys], kept beside them: [visibleIdFor] runs once per app on every drawer build. */
    @Volatile private var visibleIds: Set<Int> = _keys.value.map { idOf(it) }.toSet()
    val keys: StateFlow<List<String>> = _keys.asStateFlow()

    private val _folderCategories = MutableStateFlow(readFolderCategories())
    /** Drawer folder id -> category id. */
    val folderCategories: StateFlow<Map<Long, Int>> = _folderCategories.asStateFlow()

    val hasHiddenBuiltIns: Boolean get() = hiddenBuiltIns().isNotEmpty()

    private val _revision = MutableStateFlow(0)
    /** Ticks on any change, names and icons included, which [keys] alone would not show. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Held strongly: SharedPreferences keeps its listeners weakly. A backup restore writes these
     *  keys from elsewhere, and the drawer has to follow without a restart. */
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_CUSTOM || key == KEY_HIDDEN || key == KEY_ADDED || key == KEY_ICONS || key == KEY_NAMES || key?.startsWith("folder_category") == true) reload()
    }.also { prefs.registerOnSharedPreferenceChangeListener(it) }

    fun idOf(key: String): Int = Companion.idOf(key)

    /** The visible category with [id], or null when it was deleted or never existed. */
    fun keyFor(id: Int): String? = _keys.value.firstOrNull { idOf(it) == id }

    /** The category an app assigned to [rawId] shows under, after [visibleIdFor]. */
    fun keyForApp(rawId: Int): String? = keyFor(visibleIdFor(rawId))

    /** Adds a category named [name] and returns its key, or null for a blank name. */
    fun add(name: String): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        // Past both the counter and anything a restored backup brought in.
        val id = maxOf(prefs.getInt(KEY_NEXT_ID, FIRST_CUSTOM_ID), (customNames().keys.maxOrNull() ?: 0) + 1)
        val names = customNames().apply { put(id, clean) }
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).putString(KEY_CUSTOM, toJson(names)).apply()
        reload()
        return CUSTOM_PREFIX + id
    }

    /**
     * Adds one of the suggested categories ([AppCategoryCatalog.SUGGESTED]). Every app the
     * engine already sorted into it moves there at once, out of whichever broader category was
     * holding it — see [visibleIdFor].
     */
    fun addSuggested(id: Int): String? {
        val key = BUILT_IN.getOrNull(id) ?: return null
        if (id < AppCategoryCatalog.FIRST_SUGGESTED) return null
        prefs.edit().putStringSet(
            KEY_ADDED, addedSuggested().plus(id).map { it.toString() }.toSet(),
        ).apply()
        reload()
        return key
    }

    /** Suggested categories not on screen yet, in catalog order. */
    fun suggestedToAdd(): List<Int> {
        val added = addedSuggested()
        return AppCategoryCatalog.SUGGESTED.filter { it !in added }
    }

    /**
     * The visible category an app or folder assigned to [rawId] belongs in: itself when it is on
     * screen, else the nearest broader one that is ([AppCategoryCatalog.fallbackOf]) — so a
     * deleted category hands its apps to a related one rather than losing them, and Utilities
     * stops being the place everything unclaimed piles up once suggestions are added.
     */
    fun visibleIdFor(rawId: Int): Int {
        val visible = visibleIds
        var id = rawId
        var guard = 0
        while (guard++ < 8) {
            if (id in visible) return id
            id = AppCategoryCatalog.fallbackOf(id) ?: break
        }
        return if (AppCategoryCatalog.UTILITIES in visible) AppCategoryCatalog.UTILITIES else 0
    }

    fun delete(key: String, preferenceManager: com.nexus.launcher.data.prefs.PreferenceManager) {
        if (key == ALL) return
        val id = idOf(key)
        if (id < 0) return
        val edit = prefs.edit()
        if (isCustom(key)) {
            edit.putString(KEY_CUSTOM, toJson(customNames().apply { remove(id) }))
        } else if (id >= AppCategoryCatalog.FIRST_SUGGESTED) {
            edit.putStringSet(KEY_ADDED, addedSuggested().minus(id).map { it.toString() }.toSet())
        } else {
            edit.putStringSet(KEY_HIDDEN, hiddenBuiltIns().plus(id).map { it.toString() }.toSet())
        }
        edit.apply()
        preferenceManager.getAppCategoryOverrides().filterValues { it == id }.keys
            .forEach { preferenceManager.setAppCategoryOverride(it, null) }
        preferenceManager.getFolderCategories().filterValues { it == id }.keys
            .forEach { preferenceManager.setFolderCategory(it, null) }
        _folderCategories.value = readFolderCategories()
        reload()
    }

    fun rename(key: String, name: String, defaultName: String) {
        val clean = name.trim()
        if (isCustom(key)) {
            if (clean.isEmpty()) return
            prefs.edit().putString(KEY_CUSTOM, toJson(customNames().apply { put(idOf(key), clean) })).apply()
        } else {
            val names = readCustomNames(prefs.getString(KEY_NAMES, null)).apply {
                if (clean.isEmpty() || clean == defaultName) remove(idOf(key)) else put(idOf(key), clean)
            }
            prefs.edit().putString(KEY_NAMES, toJson(names)).apply()
        }
        reload()
    }

    /** [iconName] from [CategoryIconCatalog]; null puts the category back on its default icon. */
    fun setIcon(key: String, iconName: String?) {
        val icons = readIcons(prefs.getString(KEY_ICONS, null)).apply {
            val id = idOf(key)
            if (iconName == null || iconName == CategoryIconCatalog.defaultNameFor(key)) remove(id) else put(id, iconName)
        }
        prefs.edit().putString(KEY_ICONS, toJson(icons)).apply()
        reload()
    }

    fun restoreBuiltIns() {
        prefs.edit().remove(KEY_HIDDEN).apply()
        reload()
    }

    fun setFolderCategory(
        folderId: Long, categoryId: Int?, preferenceManager: com.nexus.launcher.data.prefs.PreferenceManager,
    ) {
        preferenceManager.setFolderCategory(folderId, categoryId)
        _folderCategories.value = readFolderCategories()
    }

    /** Reloads after something else (a backup restore) rewrote the stored categories. */
    fun reload() {
        _keys.value = load()
        visibleIds = _keys.value.map { idOf(it) }.toSet()
        _folderCategories.value = readFolderCategories()
        _revision.value++
    }

    private fun load(): List<String> {
        val hidden = hiddenBuiltIns()
        val added = addedSuggested()
        val builtIns = BUILT_IN.filterIndexed { id, _ ->
            when {
                id == 0 -> true
                id >= AppCategoryCatalog.FIRST_SUGGESTED -> id in added
                else -> id !in hidden
            }
        }
        return builtIns + customNames().keys.sorted().map { CUSTOM_PREFIX + it }
    }

    private fun addedSuggested(): Set<Int> =
        prefs.getStringSet(KEY_ADDED, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun hiddenBuiltIns(): Set<Int> =
        prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun customNames(): MutableMap<Int, String> = readCustomNames(prefs.getString(KEY_CUSTOM, null))

    private fun readFolderCategories(): Map<Long, Int> =
        prefs.getStringSet("folder_category_keys", emptySet()).orEmpty().mapNotNull { key ->
            val folderId = key.toLongOrNull() ?: return@mapNotNull null
            if (!prefs.contains("folder_category_$key")) null
            else folderId to prefs.getInt("folder_category_$key", -1)
        }.toMap()

    companion object {
        const val ALL = "All"
        /** Index = category id; see [AppCategoryCatalog]. Only ever append. */
        val BUILT_IN = listOf(
            "All", "Social", "Media", "Games", "Productivity", "Finance", "Utilities", "System",
            "Shopping", "Travel", "Health", "News", "Education", "Photography", "Music", "Video",
            "Food", "Weather", "Personalization",
        )

        /** Kept in nexus_prefs next to the per-app overrides and folder tags they give meaning to. */
        const val PREFS = "nexus_prefs"
        const val KEY_CUSTOM = "drawer_custom_categories"
        const val KEY_HIDDEN = "drawer_hidden_builtin_categories"
        const val KEY_NEXT_ID = "drawer_custom_category_next_id"
        const val KEY_ICONS = "drawer_category_icons"
        const val KEY_NAMES = "drawer_category_names"
        const val KEY_ADDED = "drawer_added_suggested_categories"
        private const val CUSTOM_PREFIX = "custom:"
        private const val FIRST_CUSTOM_ID = 100

        fun isCustom(key: String): Boolean = key.startsWith(CUSTOM_PREFIX)

        fun idOf(key: String): Int =
            if (isCustom(key)) key.removePrefix(CUSTOM_PREFIX).toIntOrNull() ?: -1 else BUILT_IN.indexOf(key)

        /** The name the user gave [key]: an added category's name, or a built-in's rename. */
        fun customName(context: Context, key: String): String? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(if (isCustom(key)) KEY_CUSTOM else KEY_NAMES, null)
            return readCustomNames(stored)[idOf(key)]
        }

        /** The catalog name of the icon [key] shows: the user's pick, else its default. */
        fun iconNameFor(context: Context, key: String): String {
            val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ICONS, null)
            return readIcons(stored)[idOf(key)]?.takeIf { CategoryIconCatalog.resOf(it) != null }
                ?: CategoryIconCatalog.defaultNameFor(key)
        }

        @DrawableRes
        fun iconFor(context: Context, key: String): Int =
            CategoryIconCatalog.resOf(iconNameFor(context, key)) ?: R.drawable.ic_category_custom

        private fun readIcons(json: String?): MutableMap<Int, String> = readCustomNames(json)

        private fun readCustomNames(json: String?): MutableMap<Int, String> {
            val out = sortedMapOf<Int, String>()
            if (json.isNullOrBlank()) return out
            runCatching {
                val obj = JSONObject(json)
                obj.keys().forEach { k -> k.toIntOrNull()?.let { out[it] = obj.getString(k) } }
            }
            return out
        }

        private fun toJson(names: Map<Int, String>): String =
            JSONObject().apply { names.forEach { (id, name) -> put(id.toString(), name) } }.toString()
    }
}
