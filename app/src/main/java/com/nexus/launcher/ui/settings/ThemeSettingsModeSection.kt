package com.nexus.launcher.ui.settings

import android.animation.LayoutTransition
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.theme.BackgroundLayerMode
import com.nexus.launcher.theme.CalmPalette
import com.nexus.launcher.theme.ColorBlindMode
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.theme.ThemeMode
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.AccentColorPicker
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumGate
import com.nexus.launcher.ui.settings.views.CalmPalettePicker
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Manages the Appearance section using a segmented card structure (cut-out pillows with 3dp recessed gaps).
 */
class ThemeSettingsModeSection(
    private val context: Context,
    val onThemeModeChanged: (ThemeMode) -> Unit,
    val onCalmPaletteSelected: (CalmPalette) -> Unit,
    val onBackgroundLayerChanged: (BackgroundLayerMode) -> Unit,
    val onColorBlindModeChanged: (ColorBlindMode) -> Unit,
    val onAccentColorSelected: (String) -> Unit
) {
    var isExpanded = true

    /** Last accepted value of each gated row, so a refused selection can be painted back. */
    private var currentThemeModeValue = ThemeMode.AUTOMATIC.name
    private var currentColorBlindValue = ColorBlindMode.NONE.name
    val groupContainer: LinearLayout
    val appearanceHeader: NexusNavRow
    val appearanceGroup: LinearLayout
    val rowThemeMode: NexusSegmentedRow
    val calmPaletteContainer: LinearLayout
    val calmPaletteSubtitle: TextView
    val calmPaletteTitle: TextView
    val calmPaletteIcon: ImageView
    val calmPalettePicker: CalmPalettePicker
    val rowBackgroundLayer: NexusSegmentedRow
    val rowColorAccessibility: NexusSegmentedRow
    val accentHeader: NexusNavRow
    val accentColorPicker: AccentColorPicker
    private val accentPill: ThemeSettingsAccentPill

    private val themePillBackground = GradientDrawable()
    private val bgLayerPillBackground = GradientDrawable()
    private val colorAccessPillBackground = GradientDrawable()

    init {
        val dp = context.resources.displayMetrics.density
        val childPadH = (14 * dp).toInt()
        val gap = (3 * dp).toInt()
        val radiusOuter = 16f * dp
        val radiusInner = 4f * dp
        val tokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark }

        groupContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutTransition = LayoutTransition()
        }

        // --- Pillow 1: Theme (Theme Mode Selector + Calm Palette Options) ---
        themePillBackground.cornerRadii = floatArrayOf(
            radiusOuter, radiusOuter,
            radiusOuter, radiusOuter,
            radiusInner, radiusInner,
            radiusInner, radiusInner
        )
        themePillBackground.setColor(tokens.surface)

        rowThemeMode = NexusSegmentedRow(context).apply {
            configure(
                label = context.getString(R.string.theme_mode_label),
                options = ThemeSettingsFormatters.getThemeModeOptions(context),
                initialValue = ThemeMode.AUTOMATIC.name,
                inline = true,
                iconRes = R.drawable.contrast_24,
                subtitle = null
            )
            setContentPadding(childPadH, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setPremiumOptions(PremiumFeature.EXTRA_THEMES) { it == ThemeMode.CALM.name || it == ThemeMode.AMOLED.name }
            onValueChanged = { valueKey ->
                val mode = try { ThemeMode.valueOf(valueKey) } catch (_: Exception) { ThemeMode.AUTOMATIC }
                // The row paints its own selection before reporting it, so a refused choice has to
                // be painted back — PremiumGate cannot know a control already moved.
                if ((mode == ThemeMode.CALM || mode == ThemeMode.AMOLED) &&
                    !PremiumGate.allow(context, PremiumFeature.EXTRA_THEMES)
                ) {
                    rowThemeMode.setSelectedValue(currentThemeModeValue)
                } else {
                    currentThemeModeValue = valueKey
                    onThemeModeChanged(mode)
                    setCalmModeVisible(mode == ThemeMode.CALM)
                }
            }
        }

        calmPaletteIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_palette_theme)
            layoutParams = LinearLayout.LayoutParams((18 * dp).toInt(), (18 * dp).toInt()).apply {
                marginEnd = (14 * dp).toInt()
            }
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        }
        calmPaletteTitle = TextView(context).apply {
            text = context.getString(R.string.settings_section_calm_palette)
            textSize = 14f
            letterSpacing = 0.02f
        }
        calmPaletteSubtitle = TextView(context).apply {
            text = ThemeSettingsFormatters.getCalmPaletteLabel(context, CalmPalette.OCEAN_BLUE)
            textSize = 12.5f
        }
        NexusTypeScale.bodyStrong.bindTo(calmPaletteTitle, tokens.textPrimary)
        NexusTypeScale.iconLabel.bindTo(calmPaletteSubtitle, tokens.textSecondary)

        val calmPaletteTextContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * dp).toInt()
            }
            addView(calmPaletteTitle)
            addView(calmPaletteSubtitle)
        }

        calmPalettePicker = CalmPalettePicker(context).apply {
            val pad = (2 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            onPaletteSelected = { palette ->
                onCalmPaletteSelected(palette)
                calmPaletteSubtitle.text = ThemeSettingsFormatters.getCalmPaletteLabel(context, palette)
            }
        }
        // No scroll container. The picker divides whatever width it is given between the
        // palettes, so it always fits on one row and there is nothing to scroll.

        // Header row and picker are stacked, not side by side. Beside a picker this wide the
        // weight-1 text column was squeezed to zero width, where its title wrapped one character
        // per line and stretched the section to several hundred dp.
        val calmPaletteHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(calmPaletteIcon)
            addView(calmPaletteTextContainer)
        }

        calmPaletteContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(childPadH, 0, (8 * dp).toInt(), (12 * dp).toInt())
            addView(calmPaletteHeaderRow)
            addView(
                calmPalettePicker,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (10 * dp).toInt() }
            )
        }

        val themePill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = gap
            }
            background = themePillBackground
            clipToOutline = true
            addView(rowThemeMode)
            addView(calmPaletteContainer)
        }

        // --- Pillow 2: Background Layer ---
        bgLayerPillBackground.cornerRadius = radiusInner
        bgLayerPillBackground.setColor(tokens.surface)

        rowBackgroundLayer = NexusSegmentedRow(context).apply {
            configure(
                label = context.getString(R.string.settings_section_background_layer),
                options = ThemeSettingsFormatters.getBackgroundLayerOptions(context),
                initialValue = BackgroundLayerMode.WALLPAPER.name,
                inline = true,
                iconRes = R.drawable.outline_photo_24,
                subtitle = ThemeSettingsFormatters.getBackgroundLayerLabel(context, BackgroundLayerMode.WALLPAPER)
            )
            setContentPadding(childPadH, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            onValueChanged = { valueKey ->
                val mode = try { BackgroundLayerMode.valueOf(valueKey) } catch (_: Exception) { BackgroundLayerMode.WALLPAPER }
                onBackgroundLayerChanged(mode)
                setSubtitle(ThemeSettingsFormatters.getBackgroundLayerLabel(context, mode))
            }
        }

        val bgLayerPill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = gap
            }
            background = bgLayerPillBackground
            clipToOutline = true
            addView(rowBackgroundLayer)
        }

        // --- Pillow 3: Color Accessibility ---
        colorAccessPillBackground.cornerRadius = radiusInner
        colorAccessPillBackground.setColor(tokens.surface)

        rowColorAccessibility = NexusSegmentedRow(context).apply {
            configure(
                label = context.getString(R.string.settings_section_color_accessibility),
                options = ThemeSettingsFormatters.getColorBlindModeOptions(context),
                initialValue = ColorBlindMode.NONE.name,
                inline = true,
                iconRes = R.drawable.outline_eyeglasses_3_24,
                subtitle = context.getString(R.string.color_accessibility_subtitle),
                badge = context.getString(R.string.settings_badge_beta).uppercase()
            )
            setContentPadding(childPadH, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            // Every correction is Premium; turning them off (NONE) never is.
            setPremiumOptions(PremiumFeature.COLOR_ACCESSIBILITY) { it != ColorBlindMode.NONE.name }
            onValueChanged = { valueKey ->
                val mode = try { ColorBlindMode.valueOf(valueKey) } catch (_: Exception) { ColorBlindMode.NONE }
                // NONE is the off state, so it stays free — only turning a correction *on* is gated.
                if (mode != ColorBlindMode.NONE &&
                    !PremiumGate.allow(context, PremiumFeature.COLOR_ACCESSIBILITY)
                ) {
                    rowColorAccessibility.setSelectedValue(currentColorBlindValue)
                } else {
                    currentColorBlindValue = valueKey
                    onColorBlindModeChanged(mode)
                }
            }
        }

        val colorAccessPill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = gap
            }
            background = colorAccessPillBackground
            clipToOutline = true
            addView(rowColorAccessibility)
        }

        // --- Pillow 4: Accent Color --- (ThemeSettingsAccentPill)
        val accent = ThemeSettingsAccentPill(
            context, tokens, dp, childPadH, radiusInner, radiusOuter, onAccentColorSelected,
        )
        accentPill = accent
        accentHeader = accent.header
        accentColorPicker = accent.picker

        // --- Outer Appearance Container ---
        appearanceGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (4 * dp).toInt()
                bottomMargin = (12 * dp).toInt()
            }
            visibility = View.VISIBLE
            addView(themePill)
            addView(bgLayerPill)
            addView(colorAccessPill)
            addView(accent.pill)
        }

        appearanceHeader = NexusNavRow(
            context,
            title = context.getString(R.string.settings_section_theme),
            subtitle = context.getString(R.string.settings_section_appearance_subtitle),
            iconRes = R.drawable.outline_format_paint_24,
            showChevron = true
        ) {
            isExpanded = !isExpanded
            appearanceGroup.visibility = if (isExpanded) View.VISIBLE else View.GONE
            appearanceHeader.setChevronRotation(if (isExpanded) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
        }

        groupContainer.addView(appearanceHeader)
        groupContainer.addView(appearanceGroup)

        setupThemeBinding()
    }

    /**
     * Sync helpers for the gated rows.
     *
     * The fragment used to call `setSelectedValue` on these rows directly. They now also track the
     * last accepted value so a refused Premium selection can be painted back, and that tracking
     * has to move with the stored value too — otherwise a refusal reverts to whatever was last
     * picked in this session instead of what is saved.
     */
    fun setThemeModeValue(value: String) {
        currentThemeModeValue = value
        rowThemeMode.setSelectedValue(value)
    }

    fun setColorBlindValue(value: String) {
        currentColorBlindValue = value
        rowColorAccessibility.setSelectedValue(value)
    }

    fun setCalmModeVisible(visible: Boolean) {
        calmPaletteContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun applyTokens(tokens: NexusColorTokens) {
        themePillBackground.setColor(tokens.surface)
        bgLayerPillBackground.setColor(tokens.surface)
        colorAccessPillBackground.setColor(tokens.surface)

        calmPaletteTitle.setTextColor(tokens.textPrimary)
        calmPaletteSubtitle.setTextColor(tokens.textSecondary)
        calmPaletteIcon.imageTintList = ColorStateList.valueOf(tokens.textPrimary)

        appearanceHeader.applyTokens(tokens)
        rowThemeMode.applyTokens(tokens)
        rowBackgroundLayer.applyTokens(tokens)
        rowColorAccessibility.applyTokens(tokens)
        accentPill.applyTokens(tokens)
        calmPalettePicker.invalidate()
    }

    private fun setupThemeBinding() {
        val listener = object : View.OnAttachStateChangeListener {
            private var job: Job? = null
            override fun onViewAttachedToWindow(v: View) {
                val owner = v.findViewTreeLifecycleOwner() ?: (v.context as? LifecycleOwner)
                if (owner != null) {
                    try {
                        val controller = EntryPointAccessors.fromApplication(
                            v.context.applicationContext,
                            ThemeEntryPoint::class.java
                        ).themeController()
                        job = owner.lifecycleScope.launch {
                            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                controller.currentTokens.collect { tokens ->
                                    applyTokens(tokens)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            override fun onViewDetachedFromWindow(v: View) {
                job?.cancel()
                job = null
            }
        }
        groupContainer.addOnAttachStateChangeListener(listener)
        if (ViewCompat.isAttachedToWindow(groupContainer)) {
            listener.onViewAttachedToWindow(groupContainer)
        }
    }
}
