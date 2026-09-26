package com.nexus.launcher.typography

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Manages persistent font family configuration and custom uploaded fonts for Nexus Launcher. */
@Singleton
class FontFamilyController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keyFontFamily = stringPreferencesKey("font_family")
    private val keyCustomFonts = stringPreferencesKey("custom_fonts_json")

    val fontSelectionKey: StateFlow<String> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[keyFontFamily] ?: AppFontFamily.NEXUS_DEFAULT.key }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, AppFontFamily.NEXUS_DEFAULT.key)

    val customFonts: StateFlow<List<CustomFontEntry>> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> parseCustomFonts(prefs[keyCustomFonts]) }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val fontFamily: StateFlow<AppFontFamily> = fontSelectionKey
        .map { key -> if (key.startsWith("custom:")) AppFontFamily.NEXUS_DEFAULT else AppFontFamily.fromKey(key) }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, AppFontFamily.NEXUS_DEFAULT)

    suspend fun setFontSelection(selectionKey: String) {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                if (selectionKey == AppFontFamily.NEXUS_DEFAULT.key) {
                    prefs.remove(keyFontFamily)
                } else {
                    prefs[keyFontFamily] = selectionKey
                }
            }
        }
    }

    suspend fun setFontFamily(family: AppFontFamily) {
        setFontSelection(family.key)
    }

    suspend fun addCustomFont(entry: CustomFontEntry) {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                val current = parseCustomFonts(prefs[keyCustomFonts]).toMutableList()
                current.removeAll { it.id == entry.id }
                current.add(entry)
                prefs[keyCustomFonts] = serializeCustomFonts(current)
            }
        }
    }

    suspend fun deleteCustomFont(id: String) {
        withContext(Dispatchers.IO) {
            var targetFilePath: String? = null
            dataStore.edit { prefs ->
                val current = parseCustomFonts(prefs[keyCustomFonts]).toMutableList()
                val item = current.firstOrNull { it.id == id }
                if (item != null) {
                    targetFilePath = item.filePath
                    current.remove(item)
                    prefs[keyCustomFonts] = serializeCustomFonts(current)
                }
                if (prefs[keyFontFamily] == "custom:$id") {
                    prefs.remove(keyFontFamily)
                }
            }
            targetFilePath?.let { path ->
                try {
                    File(path).delete()
                } catch (_: Exception) {}
            }
        }
    }

    val fontProviderFlow: StateFlow<FontProvider> = kotlinx.coroutines.flow.combine(
        fontSelectionKey,
        customFonts
    ) { key, custom ->
        DynamicFontProvider(context, key, custom)
    }.stateIn(scope, SharingStarted.Eagerly, DynamicFontProvider(context, fontSelectionKey.value, customFonts.value))

    fun getFontProvider(): FontProvider {
        return DynamicFontProvider(context, fontSelectionKey.value, customFonts.value)
    }

    private fun parseCustomFonts(json: String?): List<CustomFontEntry> {
        if (json.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<CustomFontEntry>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomFontEntry(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        filePath = obj.optString("filePath")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun serializeCustomFonts(list: List<CustomFontEntry>): String {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("filePath", item.filePath)
            array.put(obj)
        }
        return array.toString()
    }

    companion object {
        fun resolve(context: Context): FontFamilyController {
            return dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                TypographyEntryPoint::class.java
            ).fontFamilyController()
        }
    }
}
