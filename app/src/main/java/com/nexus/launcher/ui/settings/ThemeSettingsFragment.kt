package com.nexus.launcher.ui.settings

import android.animation.LayoutTransition
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.theme.ThemeMode
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.AppFontFamily
import com.nexus.launcher.typography.CustomFontImporter
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ThemeSettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by activityViewModels()

    /**
     * Which slice of the old Appearance page this instance renders.
     *
     * Appearance had grown to four unrelated blocks on one scroll - theme mode with its four
     * nested pickers, the UI Style picker, icons, and typography with language - which is why it
     * was split into three hub pages. Every section is still *constructed* here regardless of the
     * block; only the rows added to the page are filtered. That keeps the collectors in
     * [onViewCreated] untouched and unconditional, which is the part that would otherwise need
     * a guard per section and be easy to get subtly wrong.
     */
    private val block: AppearanceBlock
        get() = runCatching {
            AppearanceBlock.valueOf(arguments?.getString(ARG_BLOCK) ?: AppearanceBlock.THEME.name)
        }.getOrDefault(AppearanceBlock.THEME)

    private lateinit var modeSection: ThemeSettingsModeSection
    private lateinit var frostedSection: ThemeSettingsFrostedSection
    private lateinit var typographySection: ThemeSettingsTypographySection

    private var ignoreCallbacks = false

    private val openFontLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = CustomFontImporter.importFont(requireContext(), uri)
                result.onSuccess { entry ->
                    viewModel.addCustomFont(entry)
                    typographySection.candidateFontKey = "custom:${entry.id}"
                    val updated = viewModel.customFonts.value + entry
        refreshTypographyPreview()
                    typographySection.customFontSection.render(updated, typographySection.candidateFontKey)
                    typographySection.updateSubtitle(updated)
                    Toast.makeText(requireContext(), getString(R.string.toast_font_added, entry.name), Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(requireContext(), getString(R.string.toast_font_invalid), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()

        val scrollView = NestedScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(padH, 0, padH, (80 * dp).toInt())
            layoutTransition = LayoutTransition()
        }

        val mainSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
        }

        // 1. Theme Mode (Parent) & Nested Children (Calm Palette, Background Layer, Color Accessibility, Accent Color)
        modeSection = ThemeSettingsModeSection(
            context = requireContext(),
            onThemeModeChanged = { mode -> viewModel.setThemeMode(mode) },
            onCalmPaletteSelected = { palette -> viewModel.setCalmPalette(palette) },
            onBackgroundLayerChanged = { mode -> viewModel.setBackgroundLayerMode(mode) },
            onColorBlindModeChanged = { mode -> viewModel.setColorBlindMode(mode) },
            onAccentColorSelected = { hex ->
                if (!ignoreCallbacks) {
                    viewModel.patchDraft { s -> s.copy(accentColor = hex) }
                }
            }
        )
        if (block == AppearanceBlock.THEME) mainSection.addRow(modeSection.groupContainer)

        val initialTokens = try { ThemeObserver.currentTokens(requireContext()) } catch (_: Exception) { NexusColorTokens.Dark }

        // 2. UI Options — Neumorphism / Default / Frosted Glass style picker (moved above Icons
        // per explicit request, since it's a more fundamental visual choice than icon styling).
        frostedSection = ThemeSettingsFrostedSection(
            context = requireContext(),
            onUiStyleModeChanged = { mode -> viewModel.updateUiStyleMode(mode) },
            onCaptureWallpaperRequested = {
                // The capture itself only runs inside MainActivity's own window — hand off to
                // it instead of trying to do it from this (separate) Settings activity.
                // Routed, not an explicit MainActivity intent: started from Settings that was
                // typed as an ordinary launch and got a second MainActivity (see HomeRoute).
                // Settings shares the home task, so reaching the existing instance closes
                // Settings — the same thing the Home button does from here.
                startActivity(
                    com.nexus.launcher.ui.HomeRoute.to(
                        requireContext(),
                        com.nexus.launcher.ui.MainActivity.ACTION_OPEN_WALLPAPER_CAPTURE,
                    ),
                )
            }
        ).apply {
            applyTokens(initialTokens)
        }
        if (block == AppearanceBlock.THEME) mainSection.addRow(frostedSection.container)

        // 3. Typography & Language (Accordion)
        typographySection = ThemeSettingsTypographySection(
            context = requireContext(),
            fragmentManager = { parentFragmentManager },
            onOpenFontPicker = { openFontLauncher.launch(ThemeSettingsFormatters.FONT_MIME_TYPES) },
            onFontSelected = { key ->
        refreshTypographyPreview()
                typographySection.customFontSection.render(viewModel.customFonts.value, key)
                typographySection.updateSubtitle(viewModel.customFonts.value)
            },
            onFontApplied = { key ->
                viewModel.setFontSelection(key)
        refreshTypographyPreview()
                typographySection.customFontSection.render(viewModel.customFonts.value, key)
                typographySection.updateSubtitle(viewModel.customFonts.value)
            },
            onLanguageChanged = { loc ->
        refreshTypographyPreview()
                typographySection.updateSubtitle(viewModel.customFonts.value)
            },
            onLanguageApplied = { loc ->
                // Capture the screen first, then apply — setAppLocale restarts this activity, and
                // the snapshot is what the new one shows until it has drawn. Re-picking the
                // current language restarts nothing, so there is nothing to cover.
                val act = activity
                if (loc != viewModel.currentAppLocale.value && act != null) {
                    SettingsLocaleTransition.captureThen(act) { viewModel.setAppLocale(loc) }
                } else {
                    viewModel.setAppLocale(loc)
                }
                refreshTypographyPreview()
                typographySection.updateSubtitle(viewModel.customFonts.value)
            },
            onCustomFontDeleted = { id ->
                val wasCandidate = typographySection.candidateFontKey == "custom:$id"
                viewModel.deleteCustomFont(id)
                if (wasCandidate) {
                    typographySection.candidateFontKey = AppFontFamily.NEXUS_DEFAULT.key
                    typographySection.updateFontSubtitle()
        refreshTypographyPreview()
                }
                typographySection.updateSubtitle(viewModel.customFonts.value.filterNot { it.id == id })
                Toast.makeText(requireContext(), getString(R.string.toast_font_deleted), Toast.LENGTH_SHORT).show()
            }
        )
        if (block == AppearanceBlock.TYPOGRAPHY) {
            mainSection.addRow(typographySection.headerRow)
            mainSection.addRow(typographySection.containerLayout)
        }

        content.addView(mainSection)
        scrollView.addView(content)

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(padH, (4 * dp).toInt(), padH, systemBars.bottom + (80 * dp).toInt())
            insets
        }
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        typographySection.candidateFontKey = viewModel.currentFontSelectionKey.value
        typographySection.candidateLocale = viewModel.currentAppLocale.value
        typographySection.updateFontSubtitle()
        typographySection.updateLanguageSubtitle()
        refreshTypographyPreview()
        typographySection.customFontSection.render(viewModel.customFonts.value, typographySection.candidateFontKey)
        typographySection.updateSubtitle(viewModel.customFonts.value)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ensureDraftReady()
            bindDraft()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.bindEpoch.collect { bindDraft() } }
                launch {
                    val controller = EntryPointAccessors.fromApplication(
                        requireContext().applicationContext,
                        ThemeEntryPoint::class.java
                    ).themeController()
                    controller.currentTokens.collect { tokens ->
                        frostedSection.applyTokens(tokens)
                    }
                }
                launch {
                    viewModel.settings.collect { s ->
                        frostedSection.setSelectedMode(s.uiStyleMode)
                    }
                }
                launch {
                    viewModel.currentThemeMode.collect { mode ->
                        modeSection.setThemeModeValue(mode.name)
                        modeSection.setCalmModeVisible(mode == ThemeMode.CALM)
                    }
                }
                launch {
                    viewModel.currentCalmPalette.collect { palette ->
                        modeSection.calmPalettePicker.setSelectedPalette(palette)
                        modeSection.calmPaletteSubtitle.text = ThemeSettingsFormatters.getCalmPaletteLabel(requireContext(), palette)
                    }
                }
                launch {
                    viewModel.currentBackgroundLayerMode.collect { mode ->
                        modeSection.rowBackgroundLayer.setSelectedValue(mode.name)
                        modeSection.rowBackgroundLayer.setSubtitle(ThemeSettingsFormatters.getBackgroundLayerLabel(requireContext(), mode))
                    }
                }
                launch {
                    viewModel.currentColorBlindMode.collect { mode ->
                        modeSection.setColorBlindValue(mode.name)
                    }
                }
                launch {
                    viewModel.currentFontSelectionKey.collect { key ->
                        typographySection.candidateFontKey = key
                        typographySection.updateFontSubtitle()
        refreshTypographyPreview()
                        typographySection.customFontSection.render(viewModel.customFonts.value, key)
                        typographySection.updateSubtitle(viewModel.customFonts.value)
                    }
                }
                launch {
                    viewModel.customFonts.collect { fonts ->
                        typographySection.customFontSection.render(fonts, typographySection.candidateFontKey)
        refreshTypographyPreview()
                        typographySection.updateSubtitle(fonts)
                    }
                }
                launch {
                    viewModel.currentAppLocale.collect { loc ->
                        typographySection.candidateLocale = loc
                        typographySection.updateLanguageSubtitle()
        refreshTypographyPreview()
                        typographySection.updateSubtitle(viewModel.customFonts.value)
                    }
                }
            }
        }
    }

    private fun bindDraft() {
        val s = viewModel.draft.value ?: return
        ignoreCallbacks = true
        modeSection.accentColorPicker.setSelectedColor(s.accentColor)
        modeSection.accentHeader.setSubtitle(ThemeSettingsFormatters.getAccentLabel(requireContext(), s.accentColor))
        ignoreCallbacks = false
    }

    enum class AppearanceBlock { THEME, ICONS, TYPOGRAPHY }

    companion object {
        private const val ARG_BLOCK = "appearance_block"

        fun newInstance(block: AppearanceBlock) = ThemeSettingsFragment().apply {
            arguments = android.os.Bundle().apply { putString(ARG_BLOCK, block.name) }
        }
    }

    /**
     * Repaints the single font+language preview from whatever is currently staged.
     *
     * Replaces eleven separate `updateSelection(candidate, active, ...)` calls across two holders.
     * They passed the active value only so each card could decide whether to show its own Apply
     * button; with selections committing immediately there is no such decision, and no second
     * value to keep in step.
     */
    private fun refreshTypographyPreview() {
        if (!::typographySection.isInitialized) return
        typographySection.previewHolder.update(
            fontKey = typographySection.candidateFontKey,
            locale = typographySection.candidateLocale,
            customFonts = viewModel.customFonts.value,
            context = requireContext(),
        )
    }
}
