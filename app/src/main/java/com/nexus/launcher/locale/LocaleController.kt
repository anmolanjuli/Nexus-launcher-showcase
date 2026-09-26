package com.nexus.launcher.locale

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.launcher.ui.widgets.calendar.NexusCalendarWidgetProvider
import com.nexus.launcher.ui.widgets.weather.NexusWeatherWidgetProvider
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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocaleController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keyAppLocale = stringPreferencesKey("app_locale")

    val appLocale: StateFlow<AppLocale> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val stored = prefs[keyAppLocale]
            AppLocale.fromTag(stored)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, AppLocale.SYSTEM)

    suspend fun setAppLocale(locale: AppLocale) {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                if (locale == AppLocale.SYSTEM) {
                    prefs.remove(keyAppLocale)
                } else {
                    prefs[keyAppLocale] = locale.tag
                }
            }
        }
        withContext(Dispatchers.Main) {
            val localeList = if (locale == AppLocale.SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(locale.tag)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
            notifyWidgetsLocaleChanged()
        }
    }

    companion object {
        const val ACTION_APP_LOCALE_CHANGED = "com.nexus.launcher.ACTION_APP_LOCALE_CHANGED"
    }

    /** Broadcasts custom intent to in-launcher and standalone AppWidgets to trigger immediate re-render. */
    private fun notifyWidgetsLocaleChanged() {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val calendarIds = appWidgetManager.getAppWidgetIds(ComponentName(context, NexusCalendarWidgetProvider::class.java))
        if (calendarIds.isNotEmpty()) {
            val calIntent = Intent(context, NexusCalendarWidgetProvider::class.java).apply {
                action = ACTION_APP_LOCALE_CHANGED
            }
            context.sendBroadcast(calIntent)
        }

        val weatherIds = appWidgetManager.getAppWidgetIds(ComponentName(context, NexusWeatherWidgetProvider::class.java))
        if (weatherIds.isNotEmpty()) {
            val weatherIntent = Intent(context, NexusWeatherWidgetProvider::class.java).apply {
                action = ACTION_APP_LOCALE_CHANGED
            }
            context.sendBroadcast(weatherIntent)
        }
    }

    /** Returns the effective java.util.Locale currently active (in-app override if set, system default otherwise). */
    fun getEffectiveLocale(context: Context): Locale {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            val loc = appLocales[0]
            if (loc != null) return loc
        }
        val storedLocale = appLocale.value
        if (storedLocale != AppLocale.SYSTEM) {
            return Locale.forLanguageTag(storedLocale.tag)
        }
        return LocaleListCompat.getAdjustedDefault()[0] ?: Locale.getDefault()
    }
}
