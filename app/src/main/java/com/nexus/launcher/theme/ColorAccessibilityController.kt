package com.nexus.launcher.theme

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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorAccessibilityController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val keyColorBlindMode = stringPreferencesKey("color_blind_mode")
    private val keyBadgeColor = stringPreferencesKey("notification_badge_color")

    val colorBlindMode: StateFlow<ColorBlindMode> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val stored = prefs[keyColorBlindMode]
            if (stored != null) {
                try {
                    ColorBlindMode.valueOf(stored)
                } catch (_: IllegalArgumentException) {
                    ColorBlindMode.NONE
                }
            } else {
                ColorBlindMode.NONE
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, ColorBlindMode.NONE)

    val notificationBadgeColor: StateFlow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[keyBadgeColor] }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun setColorBlindMode(mode: ColorBlindMode) {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[keyColorBlindMode] = mode.name
            }
        }
    }

    suspend fun setNotificationBadgeColor(hex: String?) {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                if (hex.isNullOrBlank()) {
                    prefs.remove(keyBadgeColor)
                } else {
                    prefs[keyBadgeColor] = hex
                }
            }
        }
    }
}
