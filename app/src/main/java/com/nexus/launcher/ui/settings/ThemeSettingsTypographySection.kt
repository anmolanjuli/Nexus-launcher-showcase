package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.locale.AppLocale
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.AppFontFamily
import com.nexus.launcher.typography.CustomFontEntry
import com.nexus.launcher.typography.DynamicFontProvider
import com.nexus.launcher.ui.settings.views.CustomFontSectionView
import com.nexus.launcher.ui.settings.views.NexusNavRow
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Manages the Typography & Language accordion section in [ThemeSettingsFragment] using a segmented card structure.
 */
class ThemeSettingsTypographySection(
    private val context: Context,
    private val fragmentManager: () -> FragmentManager,
    private val onOpenFontPicker: () -> Unit,
    private val onFontSelected: (String) -> Unit,
    private val onFontApplied: (String) -> Unit,
    private val onLanguageChanged: (AppLocale) -> Unit,
    private val onLanguageApplied: (AppLocale) -> Unit,
    private val onCustomFontDeleted: (String) -> Unit
) {
    var candidateFontKey: String = AppFontFamily.NEXUS_DEFAULT.key
    var candidateLocale: AppLocale = AppLocale.SYSTEM
    private var isExpanded = true
    private var cachedCustomFonts: List<CustomFontEntry> = emptyList()

    val headerRow: NexusNavRow
    val containerLayout: LinearLayout
    val previewHolder: ThemePreviewCards.CombinedPreviewHolder
    val rowFontFamily: NexusNavRow
    val customFontSection: CustomFontSectionView
    val rowLanguage: NexusNavRow

    private val fontPillBackground = GradientDrawable()
    private val languagePillBackground = GradientDrawable()

    init {
        val dp = context.resources.displayMetrics.density
        val gap = (3 * dp).toInt()
        val radiusOuter = 16f * dp
        val radiusInner = 4f * dp
        val childPadH = (14 * dp).toInt()
        val tokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark }

        previewHolder = ThemePreviewCards.buildCombinedPreviewCard(context)
        previewHolder.container.setPadding(
            previewHolder.container.paddingLeft, (8 * dp).toInt(),
            previewHolder.container.paddingRight, (8 * dp).toInt()
        )

        // --- Pillow 1: Font Family ---
        fontPillBackground.cornerRadii = floatArrayOf(
            radiusOuter, radiusOuter,
            radiusOuter, radiusOuter,
            radiusInner, radiusInner,
            radiusInner, radiusInner
        )
        fontPillBackground.setColor(tokens.surface)

        rowFontFamily = NexusNavRow(
            context,
            title = context.getString(R.string.settings_section_font),
            subtitle = getFontDisplayName(candidateFontKey),
            iconRes = R.drawable.ic_fonts,
            showChevron = true
        ) {
            openFontSelectionSheet()
        }.apply {
            setContentPadding(childPadH, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }

        customFontSection = CustomFontSectionView(context).apply {
            setPadding(0, 0, 0, (8 * dp).toInt())
            onAddFontClicked = onOpenFontPicker
            onFontSelected = { key ->
                candidateFontKey = key
                updateFontSubtitle()
                onFontSelected(key)
                onFontApplied(key)
            }
            onFontDeleted = { id ->
                onCustomFontDeleted(id)
            }
        }

        val fontPill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = gap
            }
            background = fontPillBackground
            clipToOutline = true
            addView(rowFontFamily)
            addView(customFontSection)
        }

        // --- Pillow 2: Language ---
        languagePillBackground.cornerRadii = floatArrayOf(
            radiusInner, radiusInner,
            radiusInner, radiusInner,
            radiusOuter, radiusOuter,
            radiusOuter, radiusOuter
        )
        languagePillBackground.setColor(tokens.surface)

        rowLanguage = NexusNavRow(
            context,
            title = context.getString(R.string.settings_section_language),
            subtitle = context.getString(candidateLocale.displayNameRes),
            iconRes = R.drawable.outline_translate_indic_24,
            showChevron = true
        ) {
            openLanguageSelectionSheet()
        }.apply {
            setContentPadding(childPadH, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }

        val languagePill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = languagePillBackground
            clipToOutline = true
            addView(rowLanguage)
        }

        val childGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (4 * dp).toInt()
                bottomMargin = (12 * dp).toInt()
            }
            addView(fontPill)
            addView(languagePill)
        }

        containerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
            addView(previewHolder.container)
            addView(childGroup)
        }

        headerRow = NexusNavRow(
            context,
            title = context.getString(R.string.settings_section_typography_language),
            subtitle = ThemeSettingsFormatters.getTypographySubtitle(context, candidateFontKey, emptyList(), candidateLocale),
            iconRes = R.drawable.outline_translate_indic_24,
            showChevron = true
        ) {
            isExpanded = !isExpanded
            containerLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
            headerRow.setChevronRotation(if (isExpanded) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
        }

        setupThemeBinding()
    }

    private fun openFontSelectionSheet() {
        val fontOptions = mutableListOf<SelectionOption<String>>()
        AppFontFamily.entries.forEach { fam ->
            val tf = DynamicFontProvider(context, fam).getTypeface(Typeface.NORMAL)
            val sub = when (fam) {
                AppFontFamily.NEXUS_DEFAULT -> context.getString(R.string.font_desc_nexus)
                AppFontFamily.SYSTEM -> context.getString(R.string.font_desc_system)
                AppFontFamily.INTER -> context.getString(R.string.font_desc_inter)
                AppFontFamily.ROBOTO -> context.getString(R.string.font_desc_roboto)
                AppFontFamily.MANROPE -> context.getString(R.string.font_desc_manrope)
                AppFontFamily.IBM_PLEX_SANS -> context.getString(R.string.font_desc_ibm_plex)
                AppFontFamily.LORA -> context.getString(R.string.font_desc_lora)
                AppFontFamily.PLAYPEN_SANS -> context.getString(R.string.font_desc_playpen)
                AppFontFamily.ELMS_SANS -> context.getString(R.string.font_desc_elms)
                AppFontFamily.BITCOUNT_SINGLE -> context.getString(R.string.font_desc_bitcount)
                else -> null
            }
            fontOptions.add(
                SelectionOption(
                    key = fam.key,
                    title = context.getString(fam.displayNameRes),
                    subtitle = sub,
                    typeface = tf
                )
            )
        }

        cachedCustomFonts.forEach { cf ->
            val tf = try {
                if (java.io.File(cf.filePath).exists()) Typeface.createFromFile(cf.filePath) else null
            } catch (_: Exception) { null }
            fontOptions.add(
                SelectionOption(
                    key = "custom:${cf.id}",
                    title = cf.name,
                    subtitle = context.getString(R.string.typography_custom_font),
                    typeface = tf
                )
            )
        }

        NexusSelectionSheet(
            sheetTitle = context.getString(R.string.settings_section_font),
            sheetSubtitle = context.getString(R.string.typography_sheet_subtitle),
            options = fontOptions,
            selectedKey = candidateFontKey
        ) { selectedKey ->
            candidateFontKey = selectedKey
            updateFontSubtitle()
            onFontSelected(selectedKey)
            onFontApplied(selectedKey)
        }.show(fragmentManager(), "font_selection_sheet")
    }

    private fun openLanguageSelectionSheet() {
        val languageOptions = listOf(
            SelectionOption(AppLocale.SYSTEM, context.getString(R.string.locale_system_default), context.getString(R.string.language_system_subtitle)),
            SelectionOption(AppLocale.ENGLISH, context.getString(R.string.locale_english), "English"),
            SelectionOption(AppLocale.SPANISH, context.getString(R.string.locale_spanish), "Español"),
            SelectionOption(AppLocale.PORTUGUESE_BR, context.getString(R.string.locale_portuguese_br), "Português (Brasil)"),
            SelectionOption(AppLocale.HINDI, context.getString(R.string.locale_hindi), "हिन्दी"),
            SelectionOption(AppLocale.INDONESIAN, context.getString(R.string.locale_indonesian), "Bahasa Indonesia"),
            SelectionOption(AppLocale.GERMAN, context.getString(R.string.locale_german), "Deutsch"),
            SelectionOption(AppLocale.FRENCH, context.getString(R.string.locale_french), "Français"),
            SelectionOption(AppLocale.NEPALI, context.getString(R.string.locale_nepali), "नेपाली"),
            SelectionOption(AppLocale.ARABIC, context.getString(R.string.locale_arabic), "العربية"),
            SelectionOption(AppLocale.HEBREW, context.getString(R.string.locale_hebrew), "עברית")
        )

        NexusSelectionSheet(
            sheetTitle = context.getString(R.string.settings_section_language),
            sheetSubtitle = context.getString(R.string.language_sheet_subtitle),
            options = languageOptions,
            selectedKey = candidateLocale
        ) { selectedLocale ->
            candidateLocale = selectedLocale
            updateLanguageSubtitle()
            onLanguageChanged(selectedLocale)
            onLanguageApplied(selectedLocale)
        }.show(fragmentManager(), "language_selection_sheet")
    }

    fun updateFontSubtitle() {
        rowFontFamily.setSubtitle(getFontDisplayName(candidateFontKey))
    }

    fun updateLanguageSubtitle() {
        rowLanguage.setSubtitle(context.getString(candidateLocale.displayNameRes))
    }

    private fun getFontDisplayName(key: String): String {
        if (key.startsWith("custom:")) {
            val id = key.removePrefix("custom:")
            val match = cachedCustomFonts.firstOrNull { it.id == id }
            return match?.name ?: context.getString(R.string.typography_custom_font)
        }
        val fam = AppFontFamily.fromKey(key)
        return context.getString(fam.displayNameRes)
    }

    fun updateSubtitle(customFonts: List<CustomFontEntry> = emptyList()) {
        cachedCustomFonts = customFonts
        updateFontSubtitle()
        updateLanguageSubtitle()
        headerRow.setSubtitle(ThemeSettingsFormatters.getTypographySubtitle(context, candidateFontKey, customFonts, candidateLocale))
    }

    fun applyTokens(tokens: NexusColorTokens) {
        fontPillBackground.setColor(tokens.surface)
        languagePillBackground.setColor(tokens.surface)
        headerRow.applyTokens(tokens)
        rowFontFamily.applyTokens(tokens)
        rowLanguage.applyTokens(tokens)
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
        containerLayout.addOnAttachStateChangeListener(listener)
        if (ViewCompat.isAttachedToWindow(containerLayout)) {
            listener.onViewAttachedToWindow(containerLayout)
        }
    }
}
