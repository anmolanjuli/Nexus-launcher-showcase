package com.nexus.launcher.ui.widgets

import android.content.Context

object NexusWidgetConfig {
    const val PREFS_NAME = "nexus_widget_prefs"
    
    const val BG_GLASS = "GLASS"
    const val BG_FROSTED = "FROSTED"
    const val BG_SOLID = "SOLID"
    const val BG_NEUMORPHIC = "NEUMORPHIC"

    const val THEME_FOLLOW = "FOLLOW"
    const val THEME_LIGHT = "LIGHT"
    const val THEME_DARK = "DARK"

    data class InstanceConfig(
        val appWidgetId: Int,
        val backgroundMode: String,
        val backgroundOpacity: Float,
        val accentColor: String,
        val frostedGradientIndex: Int = 0,
        val shapeStyle: Int = 1, // 1 = Squircle default
        val calendarViewMode: String = "MONTHLY",
        val showCountWhenSmall: Boolean = false,
        val cornerRadius: Int = 20,
        val isExpressive: Boolean = false,
        val clockStyle: Int = 0, // 0 = Greeting Analog, 1 = Retro LCD, 2 = Minimal Bauhaus, 3 = Bold Typo
        val customText: String? = null,
        val isBorderless: Boolean = false,
        val fontFamily: String = "default",
        val themeMode: String = THEME_FOLLOW,
        val agendaRange: String = com.nexus.launcher.ui.widgets.agenda.AgendaRange.DEFAULT,
        val showAgendaCountdown: Boolean = true,
        val showNoteTitle: Boolean = true,
        val showNoteUrgencyLabel: Boolean = true,
        val glassRefraction: Float = 0.70f,
        /** True while this widget instance is hosted as a child of a Living Mosaic tile — set/
         *  cleared by [com.nexus.launcher.ui.widgets.mosaic.LivingMosaicChildHost] as children
         *  are added to/removed from a mosaic. Suppresses this widget's own independent outer
         *  plate (see [NexusWidgetRenderer]'s `showBackground`) so every child visually inherits
         *  the mosaic tile's own single background instead of each carrying its own separate
         *  Glass/Neumorphic choice. Never set directly from a widget's own settings sheet. */
        val isMosaicEmbedded: Boolean = false
    )

    fun read(context: Context, appWidgetId: Int, defaultShapeStyle: Int = 1): InstanceConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return InstanceConfig(
            appWidgetId = appWidgetId,
            // Default to Glass (not Neumorphic) for a NEW/never-configured widget, matching
            // AppBox/ShortcutBox's existing default — this is what makes the global Frosted
            // Glass toggle (Nexus Settings > Appearance) actually apply out of the box: isGlass
            // gates on `backgroundMode == BG_GLASS && isGlobalFrostedGlassEnabled`, so with the
            // toggle on a new widget renders Glass, and with it off the same BG_GLASS value
            // simply falls through to the normal Neumorphic rendering path anyway — the toggle
            // stays a true master switch either way. Only affects widgets with nothing persisted
            // yet; anything with an explicit saved "bg_mode_$appWidgetId" (Neumorphic or
            // otherwise) keeps that explicit choice regardless of this default.
            backgroundMode = prefs.getString("bg_mode_$appWidgetId", BG_GLASS) ?: BG_GLASS,
            backgroundOpacity = prefs.getFloat("bg_opacity_$appWidgetId", 0.65f), // default to 65% opacity
            accentColor = prefs.getString("accent_$appWidgetId", "#7EB8D4") ?: "#7EB8D4",
            frostedGradientIndex = prefs.getInt("bg_frosted_index_$appWidgetId", 0),
            shapeStyle = prefs.getInt("shape_style_$appWidgetId", defaultShapeStyle),
            calendarViewMode = prefs.getString("cal_view_mode_$appWidgetId", "MONTHLY") ?: "MONTHLY",
            showCountWhenSmall = prefs.getBoolean("cal_show_count_$appWidgetId", false),
            cornerRadius = prefs.getInt("corner_radius_$appWidgetId", 20),
            isExpressive = prefs.getBoolean("is_expressive_$appWidgetId", false),
            clockStyle = prefs.getInt("clock_style_$appWidgetId", 0),
            customText = prefs.getString("clock_custom_text_$appWidgetId", null),
            isBorderless = prefs.getBoolean("is_borderless_$appWidgetId", false),
            fontFamily = prefs.getString("font_family_$appWidgetId", "default") ?: "default",
            themeMode = prefs.getString("theme_mode_$appWidgetId", THEME_FOLLOW) ?: THEME_FOLLOW,
            // Unset (never changed) reads as the default, now 30 days; a chosen range is kept.
            agendaRange = prefs.getString("agenda_range_$appWidgetId", null)
                ?: com.nexus.launcher.ui.widgets.agenda.AgendaRange.DEFAULT,
            showAgendaCountdown = prefs.getBoolean("agenda_countdown_$appWidgetId", true),
            showNoteTitle = prefs.getBoolean("note_show_title_$appWidgetId", true),
            showNoteUrgencyLabel = prefs.getBoolean("note_show_urgency_$appWidgetId", true),
            glassRefraction = prefs.getFloat("glass_refraction_$appWidgetId", 0.70f),
            isMosaicEmbedded = prefs.getBoolean("mosaic_embedded_$appWidgetId", false)
        )
    }

    fun write(context: Context, config: InstanceConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString("bg_mode_${config.appWidgetId}", config.backgroundMode)
            .putFloat("bg_opacity_${config.appWidgetId}", config.backgroundOpacity)
            .putString("accent_${config.appWidgetId}", config.accentColor)
            .putInt("bg_frosted_index_${config.appWidgetId}", config.frostedGradientIndex)
            .putInt("shape_style_${config.appWidgetId}", config.shapeStyle)
            .putString("cal_view_mode_${config.appWidgetId}", config.calendarViewMode)
            .putBoolean("cal_show_count_${config.appWidgetId}", config.showCountWhenSmall)
            .putInt("corner_radius_${config.appWidgetId}", config.cornerRadius)
            .putBoolean("is_expressive_${config.appWidgetId}", config.isExpressive)
            .putFloat("glass_refraction_${config.appWidgetId}", config.glassRefraction)
            .putInt("clock_style_${config.appWidgetId}", config.clockStyle)
            .putBoolean("is_borderless_${config.appWidgetId}", config.isBorderless)
            .putString("font_family_${config.appWidgetId}", config.fontFamily)
            .putString("theme_mode_${config.appWidgetId}", config.themeMode)
            .putString("agenda_range_${config.appWidgetId}", config.agendaRange)
            .putBoolean("agenda_countdown_${config.appWidgetId}", config.showAgendaCountdown)
            .putBoolean("note_show_title_${config.appWidgetId}", config.showNoteTitle)
            .putBoolean("note_show_urgency_${config.appWidgetId}", config.showNoteUrgencyLabel)
            .putBoolean("mosaic_embedded_${config.appWidgetId}", config.isMosaicEmbedded)

        if (config.customText != null) {
            editor.putString("clock_custom_text_${config.appWidgetId}", config.customText)
        } else {
            editor.remove("clock_custom_text_${config.appWidgetId}")
        }
        editor.apply()
    }
    
    fun delete(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("bg_mode_$appWidgetId")
            .remove("bg_opacity_$appWidgetId")
            .remove("accent_$appWidgetId")
            .remove("bg_frosted_index_$appWidgetId")
            .remove("shape_style_$appWidgetId")
            .remove("cal_view_mode_$appWidgetId")
            .remove("cal_show_count_$appWidgetId")
            .remove("corner_radius_$appWidgetId")
            .remove("is_expressive_$appWidgetId")
            .remove("glass_refraction_$appWidgetId")
            .remove("clock_style_$appWidgetId")
            .remove("clock_custom_text_$appWidgetId")
            .remove("is_borderless_$appWidgetId")
            .remove("font_family_$appWidgetId")
            .remove("theme_mode_$appWidgetId")
            .remove("agenda_range_$appWidgetId")
            .remove("agenda_countdown_$appWidgetId")
            .remove("note_show_title_$appWidgetId")
            .remove("note_show_urgency_$appWidgetId")
            .apply()
    }

    /**
     * Canonical 3-way UI Style check for Glass mode across widgets, boxes, and folders.
     * Solid color backgrounds and expressive gradient presets retain their distinct styling;
     * standard surfaces dynamically follow the global Frosted Glass master toggle.
     */
    /**
     * Expressive deliberately does **not** disqualify glass.
     *
     * It used to: `isExpressive` short-circuited this to false, so choosing a custom gradient on
     * a widget or a folder icon dropped the blurred backdrop and swapped the frost-density alpha
     * for the raw opacity slider. The result was a flat plate that went straight to transparent
     * as opacity came down, instead of a frosted one - reported as "custom gradient makes the
     * widget transparent" and the same on folder icons.
     *
     * Expressive describes the *tint* - a gradient in place of a flat surface colour - not
     * whether the surface is glass. Only an explicitly solid background, or Frosted Glass being
     * off globally, should drop the blur.
     *
     * [isNeumorphicSurface] keeps its own expressive check: with glass off, an expressive surface
     * draws its gradient rather than a raised plate.
     */
    fun isGlassSurface(backgroundMode: String, isExpressive: Boolean = false): Boolean {
        if (backgroundMode == BG_SOLID) return false
        return com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
    }

    /**
     * Canonical 3-way UI Style check for Neumorphic / Default mode across widgets, boxes, and folders.
     * Evaluates to true whenever global Frosted Glass is disabled (rendering either raised Soft UI
     * plates or flat plates depending on [com.nexus.launcher.ui.glass.FrostedGlassEngine.isDefaultFlatStyleEnabled]).
     */
    fun isNeumorphicSurface(backgroundMode: String, isExpressive: Boolean = false): Boolean {
        if (backgroundMode == BG_SOLID || isExpressive) return false
        return !com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
    }

    /**
     * Identifies whether a widget instance is operating in an era-specific retro style
     * (Music: Winamp, Amplifier, Terminal, Cassette; Clock: La Crosse LCD).
     * Retro styles ignore the global UI Mode and suppress the shared Nexus background plate.
     */
    fun isRetroStyle(providerClassName: String?, clockStyle: Int): Boolean {
        if (providerClassName == null) return false
        return when {
            providerClassName.endsWith("NexusMusicWidgetProvider") -> clockStyle > 0
            providerClassName.endsWith("NexusClockWidgetProvider") -> clockStyle == 4 // STYLE_LACROSSE_LCD
            else -> false
        }
    }
}

