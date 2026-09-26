package com.nexus.launcher.search

import android.content.Context
import android.content.SharedPreferences

class NexusSearchSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nexus_search_prefs", Context.MODE_PRIVATE)

    var searchContactsEnabled: Boolean
        get() = prefs.getBoolean("search_contacts", true)
        set(value) = prefs.edit().putBoolean("search_contacts", value).apply()

    var searchWebEnabled: Boolean
        get() = prefs.getBoolean("search_web", true)
        set(value) = prefs.edit().putBoolean("search_web", value).apply()

    var searchCalculatorEnabled: Boolean
        get() = prefs.getBoolean("search_calculator", true)
        set(value) = prefs.edit().putBoolean("search_calculator", value).apply()

    var searchConversionEnabled: Boolean
        get() = prefs.getBoolean("search_conversion", true)
        set(value) = prefs.edit().putBoolean("search_conversion", value).apply()

    var searchMapsEnabled: Boolean
        get() = prefs.getBoolean("search_maps", true)
        set(value) = prefs.edit().putBoolean("search_maps", value).apply()
}
