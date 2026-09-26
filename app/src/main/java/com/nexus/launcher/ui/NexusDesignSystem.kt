package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

object NexusDesignSystem {
    // Canonical Token Standard
    const val COLOR_BASE = "#0D1117"
    const val COLOR_GLASS_SURFACE = "#12FFFFFF"
    const val COLOR_GLASS_BORDER = "#21FFFFFF"
    const val COLOR_ACCENT = "#4F8DA6" // Harbor — the launcher default accent
    const val COLOR_DANGER = "#FF6B6B"
    const val COLOR_TEXT_DARK = "#0E0C18"
    const val COLOR_TEXT_PRIMARY = "#E0FFFFFF"
    const val COLOR_TEXT_SECONDARY = "#8AFFFFFF"

    /**
     * Builds a canonical Top-Level Section Header.
     * Matches the universally consistent 20sp/bold/white style.
     */
    fun buildSectionHeader(context: Context, title: String): TextView {
        val accent = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context).accent
        } catch (_: Exception) {
            Color.parseColor(COLOR_ACCENT)
        }
        return TextView(context).apply {
            text = title
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            com.nexus.launcher.typography.NexusTypeScale.title.bindTo(this, accent)
        }
    }

    /**
     * Builds a canonical Sub-Section Header.
     * Centralizes the previously divergent 14sp styles into one standard.
     */
    fun buildSubSectionHeader(context: Context, title: String): TextView {
        val accent = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context).accent
        } catch (_: Exception) {
            Color.parseColor(COLOR_ACCENT)
        }
        return TextView(context).apply {
            text = title
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * context.resources.displayMetrics.density).toInt()
                bottomMargin = (8 * context.resources.displayMetrics.density).toInt()
            }
            com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(this, accent)
        }
    }

    /**
     * Builds a canonical Apply/Save MaterialButton.
     * Matches the section tab style: surfaceRaised glass fill + divider stroke + accent text.
     */
    fun buildApplyButton(context: Context, label: String = "Apply", iconResId: Int? = null, accentArgb: Int = Color.parseColor(COLOR_ACCENT)): com.google.android.material.button.MaterialButton {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        val accent = if (accentArgb != Color.parseColor(COLOR_ACCENT)) accentArgb else tokens.accent
        return com.google.android.material.button.MaterialButton(context).apply {
            text = label
            if (iconResId != null) {
                setIconResource(iconResId)
                iconTint = android.content.res.ColorStateList.valueOf(accent)
            }
            backgroundTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)),
                intArrayOf(tokens.surface, Color.TRANSPARENT)
            )
            strokeColor = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)),
                intArrayOf(tokens.divider, Color.TRANSPARENT)
            )
            strokeWidth = (1 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val disabledText = androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.textPrimary, 0x4D)
            val cslText = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)),
                intArrayOf(accent, disabledText)
            )
            setTextColor(cslText)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            rippleColor = android.content.res.ColorStateList.valueOf(tokens.surfaceRaised)
        }
    }

    /**
     * Builds a canonical Reset/Cancel MaterialButton.
     * Outlined style with accent ring stroke.
     */
    fun buildResetButton(context: Context, label: String = "Reset", iconResId: Int? = null, accentArgb: Int = Color.parseColor(COLOR_ACCENT)): com.google.android.material.button.MaterialButton {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        val accent = if (accentArgb != Color.parseColor(COLOR_ACCENT)) accentArgb else tokens.accent
        val disabledText = androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.textPrimary, 0x4D)
        return com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            if (iconResId != null) {
                setIconResource(iconResId)
            }
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            val strokeColorList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)),
                intArrayOf(accent, disabledText)
            )
            strokeColor = strokeColorList
            strokeWidth = (1.5f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val cslText = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)),
                intArrayOf(tokens.textPrimary, disabledText)
            )
            setTextColor(cslText)
            if (iconResId != null) {
                iconTint = cslText
            }
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val baseRipple = Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent))
            rippleColor = android.content.res.ColorStateList.valueOf(baseRipple)
        }
    }

    fun styleSettingsChromePill(button: com.google.android.material.button.MaterialButton, density: Float) {
        val radius = 22 * density
        button.cornerRadius = radius.toInt()
        button.minimumWidth = 0
        button.minWidth = (96 * density).toInt()
        val hPad = (28 * density).toInt()
        val vPad = (10 * density).toInt()
        button.setPadding(hPad, vPad, hPad, vPad)
        button.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = (6 * density).toInt()
            marginEnd = (6 * density).toInt()
        }
    }

    /**
     * Builds the canonical "Glass Row" background.
     * DEPRECATES the divergent pill backgrounds (#14FFFFFF, #33FFFFFF, #1AFFFFFF, #4D000000).
     */
    fun buildGlassRowBackground(context: Context, dp: Float): GradientDrawable {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        return GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 14 * dp
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }
    }

    /**
     * Charcoal plate with Mist Blue rim — hub / Folder Edit language.
     * Fill is COLOR_BASE mixed with 8% accent so cards read as cool metal
     * instead of milky COLOR_GLASS_SURFACE on an opaque page.
     */
    fun buildPremiumPlate(density: Float, radiusDp: Float = 20f): GradientDrawable {
        val accent = Color.parseColor(COLOR_ACCENT)
        val base = Color.parseColor(COLOR_BASE)
        val fill = Color.rgb(
            (Color.red(base) * 0.92 + Color.red(accent) * 0.08).toInt().coerceIn(0, 255),
            (Color.green(base) * 0.92 + Color.green(accent) * 0.08).toInt().coerceIn(0, 255),
            (Color.blue(base) * 0.92 + Color.blue(accent) * 0.08).toInt().coerceIn(0, 255)
        )
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusDp * density
            setStroke(
                (1.5f * density).toInt().coerceAtLeast(1),
                Color.argb(0x66, Color.red(accent), Color.green(accent), Color.blue(accent))
            )
        }
    }

    fun buildTrackedSectionLabel(context: Context, title: String): TextView {
        val density = context.resources.displayMetrics.density
        val accent = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context).accent
        } catch (_: Exception) {
            Color.parseColor(COLOR_ACCENT)
        }
        return TextView(context).apply {
            text = title
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (20 * density).toInt()
                bottomMargin = (8 * density).toInt()
            }
            com.nexus.launcher.typography.NexusTypeScale.labelSmall.bindTo(this, accent)
        }
    }

    /**
     * Applies the canonical Selected State to a row/chip.
     * Matches the Nexus section tab structure: glass pill with subtle border + accent text.
     */
    fun applySelectedState(
        view: View, 
        textView: TextView?, 
        isSelected: Boolean, 
        dp: Float, 
        accentColor: Int = Color.parseColor(COLOR_ACCENT)
    ) {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(view.context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        if (isSelected) {
            view.background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 16 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            textView?.setTextColor(tokens.accent)
            textView?.typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(view.context, android.graphics.Typeface.BOLD)
        } else {
            view.background = buildGlassRowBackground(view.context, dp)
            textView?.setTextColor(tokens.textPrimary)
            textView?.typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(view.context, android.graphics.Typeface.NORMAL)
        }
    }

    /**
     * Note for future migrations: 
     * Container/Chrome level elements (like the sheet card itself, frost background, 
     * close buttons, and apply buttons) should continue to be built via:
     * - WallpaperSheetComponents.buildCard()
     * - WallpaperSheetFrost.attach()
     * 
     * Interactive controls (Toggles, Sliders, Segments) should be migrated to use
     * the tokens provided in this file, but their class implementations (NexusToggleRow, etc.)
     * are still valid.
     */

    /**
     * Applies the monochrome theme-token background to a context menu card.
     */
    fun applyContextMenuCardBackground(card: View, context: Context, accentArgb: Int = 0) {
        NexusContextMenuDesignHelper.applyContextMenuCardBackground(card, context, accentArgb)
    }

    /**
     * Builds a standard context menu row with fixed 48dp height and monochrome token styling.
     */
    fun buildContextMenuRow(
        context: Context,
        label: String,
        iconResId: Int? = null,
        isDestructive: Boolean = false,
        accentArgb: Int = 0,
        iconDrawable: android.graphics.drawable.Drawable? = null,
        onClick: () -> Unit
    ): View {
        return NexusContextMenuDesignHelper.buildContextMenuRow(
            context, label, iconResId, isDestructive, accentArgb, iconDrawable, onClick
        )
    }

    /**
     * Builds a standard context menu divider with token divider styling.
     */
    fun buildContextMenuDivider(context: Context): View {
        return NexusContextMenuDesignHelper.buildContextMenuDivider(context)
    }
}
