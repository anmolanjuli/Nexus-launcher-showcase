package com.nexus.launcher.typography

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.text.TextPaint
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.EnumMap
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.combine

/**
 * Resolves and caches [TextPaint] instances for Canvas rendering based on current
 * theme tokens, font family, and accessibility fontScale. Zero allocation inside draw passes.
 */
@Singleton
class NexusTypographyResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeController: ThemeController,
    private val fontFamilyController: FontFamilyController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fontProvider: FontProvider = fontFamilyController.getFontProvider()
    private var lastFontScale: Float = context.resources.configuration.fontScale

    private val _paintCache = MutableStateFlow<Map<NexusTypeSlot, TextPaint>>(emptyMap())
    val paintCache: StateFlow<Map<NexusTypeSlot, TextPaint>> = _paintCache.asStateFlow()

    init {
        rebuild(themeController.currentTokens.value)

        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                if (newConfig.fontScale != lastFontScale) {
                    lastFontScale = newConfig.fontScale
                    rebuild(themeController.currentTokens.value)
                }
            }
            override fun onLowMemory() {}
        }
        context.registerComponentCallbacks(callbacks)

        scope.launch {
            combine(
                themeController.currentTokens,
                fontFamilyController.fontSelectionKey,
                fontFamilyController.customFonts
            ) { tokens, fontKey, customFonts ->
                Triple(tokens, fontKey, customFonts)
            }.collect { (tokens, fontKey, customFonts) ->
                fontProvider = DynamicFontProvider(context, fontKey, customFonts)
                rebuild(tokens)
            }
        }
    }

    fun getPaint(slot: NexusTypeSlot): TextPaint {
        return _paintCache.value[slot] ?: buildPaintForSlot(
            slot,
            themeController.currentTokens.value,
            context.resources.displayMetrics.scaledDensity
        )
    }

    private fun rebuild(tokens: NexusColorTokens) {
        val scaledDensity = context.resources.displayMetrics.scaledDensity
        val map = EnumMap<NexusTypeSlot, TextPaint>(NexusTypeSlot::class.java)

        for (slot in NexusTypeSlot.values()) {
            map[slot] = buildPaintForSlot(slot, tokens, scaledDensity)
        }
        _paintCache.value = map
    }

    private fun buildPaintForSlot(
        slot: NexusTypeSlot,
        tokens: NexusColorTokens,
        scaledDensity: Float
    ): TextPaint {
        val (style, color) = when (slot) {
            NexusTypeSlot.LABEL_SMALL -> NexusTypeScale.labelSmall to tokens.textSecondary
            NexusTypeSlot.BODY -> NexusTypeScale.body to tokens.textPrimary
            NexusTypeSlot.BODY_STRONG -> NexusTypeScale.bodyStrong to tokens.textPrimary
            NexusTypeSlot.CAPTION -> NexusTypeScale.caption to tokens.textSecondary
            NexusTypeSlot.ICON_LABEL -> NexusTypeScale.iconLabel to tokens.textPrimary
            NexusTypeSlot.TITLE -> NexusTypeScale.title to tokens.textPrimary
        }

        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = style.sizeSp * scaledDensity
            this.typeface = fontProvider.getTypeface(style.weight)
            this.letterSpacing = style.letterSpacingEm
            this.color = color
        }
    }

    fun getTypeface(weight: Int): android.graphics.Typeface {
        return fontProvider.getTypeface(weight)
    }

    companion object {
        fun resolve(context: Context): NexusTypographyResolver {
            return dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                TypographyEntryPoint::class.java
            ).typographyResolver()
        }
    }
}
