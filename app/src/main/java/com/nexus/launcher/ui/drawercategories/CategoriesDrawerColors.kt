package com.nexus.launcher.ui.drawercategories

import android.content.Context
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.ui.DrawerCategories
import org.json.JSONObject

/**
 * Persists Spatial category accent colours. Missing keys (layouts saved before this existed)
 * fall back to the palette presets — never to a silent grey.
 */
class CategoriesDrawerColors(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(DrawerCategories.PREFS, Context.MODE_PRIVATE)
    private val palette = CategoryPalette(appContext)

    fun colorFor(categoryId: Int): Int {
        val stored = read()[categoryId]
        if (stored != null) return stored
        val presets = palette.categoryPresets
        if (presets.isEmpty()) return palette.mistBlue
        val index = (categoryId - 1).coerceAtLeast(0) % presets.size
        return presets[index]
    }

    fun save(categoryId: Int, color: Int) {
        val next = read().apply { put(categoryId, color or 0xFF000000.toInt()) }
        val obj = JSONObject()
        next.forEach { (id, value) -> obj.put(id.toString(), value) }
        prefs.edit().putString(KEY, obj.toString()).apply()
    }

    private fun read(): MutableMap<Int, Int> {
        val out = sortedMapOf<Int, Int>()
        val json = prefs.getString(KEY, null) ?: return out
        runCatching {
            val obj = JSONObject(json)
            obj.keys().forEach { key ->
                val id = key.toIntOrNull() ?: return@forEach
                val value = obj.optInt(key, 0)
                if (value != 0) out[id] = ColorUtils.setAlphaComponent(value, 255)
            }
        }
        return out
    }

    companion object {
        private const val KEY = "drawer_category_accents"
    }
}
