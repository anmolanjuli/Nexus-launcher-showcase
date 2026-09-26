package com.nexus.launcher.ui.settings

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.icons.IconPackInfo
import com.nexus.launcher.ui.icons.IconPackParser
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dedicated Icons settings page under Appearance.
 * Retains complete visual parity with the Nexus segmented card standard:
 * live preview card with icon pack reflection, Global Appearance group (Shape + Pack),
 * and independent Adaptive Styling (Themed Icons).
 */
@AndroidEntryPoint
class IconsSettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by activityViewModels()

    @Inject
    lateinit var iconPackParser: IconPackParser

    private lateinit var previewView: IconsPreviewView
    // Every other settings page makes its groups collapsible accordions; these two were fixed
    // open with no chevron, which is the most visible way Icons read as a different kind of page.
    private var isAppearanceExpanded = true
    private var isThemingExpanded = true

    private lateinit var headerGlobalAppearance: NexusNavRow
    private lateinit var headerTheming: NexusNavRow

    private lateinit var globalAppearanceGroup: SettingsSectionGroupView
    private lateinit var themingGroup: SettingsSectionGroupView

    private lateinit var shapeCaption: TextView
    private lateinit var packCaption: TextView
    private lateinit var iconShapeRow: View
    private lateinit var iconPackContainer: LinearLayout
    private lateinit var themedIconsToggle: NexusToggleRow

    private var currentShape: Int = -1
    private var currentPack: String = NexusDefaults.ICON_PACK
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark
    private var cachedPacks: List<IconPackInfo> = emptyList()
    private var previewLoadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()
        currentTokens = try { ThemeObserver.currentTokens(requireContext()) } catch (_: Exception) { NexusColorTokens.Dark }

        val scrollView = NestedScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(padH, 0, padH, (80 * dp).toInt())
            layoutTransition = LayoutTransition()
        }

        // Top Live Preview Card
        previewView = IconsPreviewView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * dp).toInt()
            }
        }
        val mainSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
        }
        // Inside the section, not loose above it — Home Screen and App Drawer both do
        // `mainSection.addRow(previewView)`, which is what gives the preview the same horizontal
        // insets as the rows beneath it.
        mainSection.addRow(previewView)

        // 1. Global Appearance Section (Shape and Icon Pack grouped together)
        headerGlobalAppearance = NexusNavRow(
            requireContext(),
            title = getString(R.string.settings_global_appearance),
            subtitle = getGlobalAppearanceSubtitle(currentShape, currentPack, cachedPacks),
            iconRes = R.drawable.outline_square_circle_24,
            showChevron = true
        ) {
            isAppearanceExpanded = !isAppearanceExpanded
            globalAppearanceGroup.visibility = if (isAppearanceExpanded) View.VISIBLE else View.GONE
            headerGlobalAppearance.setChevronRotation(if (isAppearanceExpanded) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
        }

        shapeCaption = TextView(requireContext()).apply {
            text = getString(R.string.icon_shape_row_title)
            NexusTypeScale.body.bindTo(this, currentTokens.textPrimary)
            setPadding(padH, (14 * dp).toInt(), padH, (4 * dp).toInt())
        }

        iconShapeRow = IconShapeTileRow.build(
            context = requireContext(),
            density = dp,
            selectedId = currentShape,
            tokens = currentTokens
        ) { shapeId ->
            currentShape = shapeId
            previewView.setShape(shapeId)
            headerGlobalAppearance.setSubtitle(getGlobalAppearanceSubtitle(shapeId, currentPack, cachedPacks))
            viewModel.updateIconShape(shapeId)
            viewModel.patchDraft { s -> s.copy(iconShape = shapeId) }
            // Themed bitmaps carry the shape mask baked in, so a shape change has to re-bake them
            // rather than just re-masking the placeholder renderer's output.
            refreshPreview(currentPack)
        }.apply {
            findViewWithTag<View>("icon_shape_row_title")?.visibility = View.GONE
            setPadding(0, (2 * dp).toInt(), 0, (4 * dp).toInt())
        }

        val shapePill = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(shapeCaption)
            addView(iconShapeRow)
        }

        packCaption = TextView(requireContext()).apply {
            text = getString(R.string.settings_icons_pack_label)
            NexusTypeScale.body.bindTo(this, currentTokens.textPrimary)
            setPadding(padH, (14 * dp).toInt(), padH, (4 * dp).toInt())
        }

        iconPackContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        rebuildIconPackRow(currentTokens)

        val packPill = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(packCaption)
            addView(iconPackContainer)
        }

        globalAppearanceGroup = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(shapePill)
            addChildRow(packPill)
        }

        val appearanceContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerGlobalAppearance)
            addView(globalAppearanceGroup)
        }
        mainSection.addRow(appearanceContainer)

        // 2. Adaptive Styling Section (Independent Themed Icons Toggle)
        headerTheming = NexusNavRow(
            requireContext(),
            title = getString(R.string.settings_adaptive_styling),
            subtitle = getString(R.string.settings_adaptive_styling_subtitle),
            iconRes = R.drawable.ic_palette_theme,
            showChevron = true
        ) {
            isThemingExpanded = !isThemingExpanded
            themingGroup.visibility = if (isThemingExpanded) View.VISIBLE else View.GONE
            headerTheming.setChevronRotation(if (isThemingExpanded) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
        }

        themedIconsToggle = NexusToggleRow(requireContext()).apply {
            configure(
                label = getString(R.string.settings_themed_icons),
                checked = false,
                subtitle = getString(R.string.settings_themed_icons_subtitle)
            )
            setIcon(R.drawable.ic_palette_theme)
            setBadge(getString(R.string.settings_badge_beta))
            onCheckedChanged = { checked ->
                viewModel.updateIconTheming(checked)
                viewModel.patchDraft { s -> s.copy(iconTheming = checked) }
                // An icon pack outranks theming in IconResolver, so with a pack selected this
                // toggle silently did nothing - in the launcher and in the preview alike. The two
                // are mutually exclusive, so turning theming on clears the pack.
                if (checked && currentPack != NexusDefaults.ICON_PACK) {
                    currentPack = NexusDefaults.ICON_PACK
                    viewModel.updateIconPack(NexusDefaults.ICON_PACK)
                    viewModel.patchDraft { s -> s.copy(iconPack = NexusDefaults.ICON_PACK) }
                    rebuildIconPackRow(currentTokens)
                    headerGlobalAppearance.setSubtitle(
                        getGlobalAppearanceSubtitle(currentShape, currentPack, cachedPacks)
                    )
                }
                refreshPreview(currentPack, checked)
            }
        }

        themingGroup = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(themedIconsToggle)
        }

        val themingContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerTheming)
            addView(themingGroup)
        }
        mainSection.addRow(themingContainer)

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

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    val controller = EntryPointAccessors.fromApplication(
                        requireContext().applicationContext,
                        ThemeEntryPoint::class.java
                    ).themeController()
                    controller.currentTokens.collect { tokens ->
                        currentTokens = tokens
                        previewView.applyTokens(tokens)
                        headerGlobalAppearance.applyTokens(tokens)
                        headerTheming.applyTokens(tokens)
                        themedIconsToggle.applyTokens(tokens)
                        globalAppearanceGroup.applyTokens(tokens)
                        themingGroup.applyTokens(tokens)
                        shapeCaption.setTextColor(tokens.textPrimary)
                        packCaption.setTextColor(tokens.textPrimary)
                        rebuildIconPackRow(tokens)
                    }
                }
                launch {
                    viewModel.availableIconPacks.collect { packs ->
                        cachedPacks = packs
                        headerGlobalAppearance.setSubtitle(getGlobalAppearanceSubtitle(currentShape, currentPack, packs))
                        rebuildIconPackRow(currentTokens)
                    }
                }
                launch {
                    viewModel.settings.collect { s ->
                        currentShape = s.iconShape
                        currentPack = s.iconPack
                        previewView.setShape(s.iconShape)
                        headerGlobalAppearance.setSubtitle(getGlobalAppearanceSubtitle(s.iconShape, s.iconPack, cachedPacks))
                        IconShapeTileRow.refreshSelection(iconShapeRow, s.iconShape, currentTokens)
                        themedIconsToggle.setChecked(s.iconTheming)
                        refreshPreview(s.iconPack, s.iconTheming)
                    }
                }
            }
        }
    }

    /**
     * An icon pack wins; failing that, themed icons; failing both, the shape-only placeholders.
     *
     * That order mirrors `IconResolver`: a pack and theming are mutually exclusive, and theming
     * only renders when no pack is active. The themed branch was missing entirely, so enabling
     * Themed Icons changed the home screen while the preview sat unchanged.
     */
    private fun refreshPreview(
        pack: String,
        themed: Boolean = viewModel.settings.value.iconTheming,
    ) {
        previewLoadJob?.cancel()
        val hasPack = pack.isNotBlank() && pack != "none"

        if (!hasPack) {
            if (!themed) {
                previewView.setPackIcons(null)
                return
            }
            previewLoadJob = viewLifecycleOwner.lifecycleScope.launch {
                previewView.setPackIcons(
                    IconPreviewThemedLoader.loadPreviewIcons(requireContext(), currentShape)
                )
            }
            return
        }

        previewLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val bitmaps = IconPreviewPackLoader.loadPreviewIcons(
                context = requireContext(),
                packPackage = pack,
                iconPackParser = iconPackParser
            )
            previewView.setPackIcons(bitmaps)
        }
    }

    private fun rebuildIconPackRow(tokens: NexusColorTokens) {
        if (!::iconPackContainer.isInitialized) return
        val dp = resources.displayMetrics.density
        iconPackContainer.removeAllViews()
        iconPackContainer.addView(
            IconPackTileRow.build(
                requireContext(), dp, cachedPacks, currentPack, tokens
            ) { pack ->
                currentPack = pack
                headerGlobalAppearance.setSubtitle(getGlobalAppearanceSubtitle(currentShape, pack, cachedPacks))
                viewModel.updateIconPack(pack)
                viewModel.patchDraft { s -> s.copy(iconPack = pack) }
                // The other half of the same exclusion: choosing a pack - including System
                // Default - means "show me those icons", so theming steps aside rather than
                // leaving a toggle switched on that has no effect.
                val wasThemed = viewModel.settings.value.iconTheming
                if (wasThemed) {
                    viewModel.updateIconTheming(false)
                    viewModel.patchDraft { s -> s.copy(iconTheming = false) }
                    themedIconsToggle.setChecked(false)
                }
                refreshPreview(pack, themed = false)
            }
        )
    }

    private fun getGlobalAppearanceSubtitle(shape: Int, pack: String, packs: List<IconPackInfo>): String {
        val shapeLabel = IconShapeTileRow.OPTIONS.find { it.id == shape }?.let { getString(it.labelRes) }
            ?: getString(R.string.icon_shape_system)
        val packLabel = if (pack.isBlank() || pack == "none") {
            getString(R.string.settings_icon_pack_system_default)
        } else {
            packs.find { it.packageName == pack }?.label ?: pack
        }
        return "$shapeLabel • $packLabel"
    }
}
