package com.nexus.launcher.theme

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shared helper for observing theme token, calm palette, colorblind mode, and background layer changes across views without duplicate EntryPoint lookups. */
object ThemeObserver {

    fun currentTokens(context: Context): NexusColorTokens {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController().currentTokens.value
    }

    fun currentBackgroundLayerMode(context: Context): BackgroundLayerMode {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController().backgroundLayerMode.value
    }

    suspend fun setBackgroundLayerMode(context: Context, mode: BackgroundLayerMode) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController().setBackgroundLayerMode(mode)
    }

    fun currentCalmPalette(context: Context): CalmPalette {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController().calmPalette.value
    }

    fun currentColorBlindMode(context: Context): ColorBlindMode {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController().colorBlindMode.value
    }

    fun currentBadgeColor(context: Context): String? {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController().notificationBadgeColor.value
    }

    fun observe(
        context: Context,
        scope: CoroutineScope,
        onTokensChanged: (NexusColorTokens) -> Unit
    ): Job {
        val controller = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController()
        return scope.launch(Dispatchers.Main) {
            controller.currentTokens.collect { tokens ->
                onTokensChanged(tokens)
            }
        }
    }

    fun observeBackgroundLayer(
        context: Context,
        scope: CoroutineScope,
        onModeChanged: (BackgroundLayerMode) -> Unit
    ): Job {
        val controller = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController()
        return scope.launch(Dispatchers.Main) {
            controller.backgroundLayerMode.collect { mode ->
                onModeChanged(mode)
            }
        }
    }

    fun observeCalmPalette(
        context: Context,
        scope: CoroutineScope,
        onPaletteChanged: (CalmPalette) -> Unit
    ): Job {
        val controller = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController()
        return scope.launch(Dispatchers.Main) {
            controller.calmPalette.collect { palette ->
                onPaletteChanged(palette)
            }
        }
    }

    fun observeColorBlindMode(
        context: Context,
        scope: CoroutineScope,
        onModeChanged: (ColorBlindMode) -> Unit
    ): Job {
        val controller = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController()
        return scope.launch(Dispatchers.Main) {
            controller.colorBlindMode.collect { mode ->
                onModeChanged(mode)
            }
        }
    }

    fun observeBadgeColor(
        context: Context,
        scope: CoroutineScope,
        onBadgeColorChanged: (String?) -> Unit
    ): Job {
        val controller = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController()
        return scope.launch(Dispatchers.Main) {
            controller.notificationBadgeColor.collect { hex ->
                onBadgeColorChanged(hex)
            }
        }
    }
}
