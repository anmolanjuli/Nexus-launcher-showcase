package com.nexus.launcher.locale

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** Shared helper for observing in-app locale changes across views without duplicate EntryPoint lookups. */
object LocaleObserver {

    fun currentLocale(context: Context): AppLocale {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            LocaleEntryPoint::class.java
        ).localeController().appLocale.value
    }

    fun getEffectiveLocale(context: Context): Locale {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            LocaleEntryPoint::class.java
        ).localeController().getEffectiveLocale(context)
    }

    fun wrapContext(context: Context): Context {
        val raw = getEffectiveLocale(context)
        val effectiveLocale = if (raw.language.equals("ne", ignoreCase = true) && raw.country.isEmpty()) {
            Locale("ne", "NP")
        } else raw
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocales(android.os.LocaleList(effectiveLocale))
        config.setLayoutDirection(effectiveLocale)
        return context.createConfigurationContext(config)
    }

    fun observe(
        context: Context,
        scope: CoroutineScope,
        onLocaleChanged: (AppLocale) -> Unit
    ): Job {
        val controller = EntryPointAccessors.fromApplication(
            context.applicationContext,
            LocaleEntryPoint::class.java
        ).localeController()
        return scope.launch(Dispatchers.Main) {
            controller.appLocale.collect { locale ->
                onLocaleChanged(locale)
            }
        }
    }
}
