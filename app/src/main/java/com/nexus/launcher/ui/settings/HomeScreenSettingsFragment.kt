package com.nexus.launcher.ui.settings

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeScreenSettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by activityViewModels()

    private lateinit var previewView: HomeScreenPreviewView
    private lateinit var gridSection: NexusSection
    private lateinit var spacingSection: NexusSection
    // Per view, not per fragment: a ViewPager2 page rebuilds its view, and a row kept from the
    // last view still has that view as its parent ("child already has a parent" on add).
    private lateinit var dockRow: HomeShowDockRow
    private lateinit var sliderHomeColumns: NexusSliderRow
    private lateinit var sliderHomeRows: NexusSliderRow
    private lateinit var sliderHomeIconSize: NexusSliderRow
    private lateinit var toggleHomeLabels: NexusToggleRow
    private lateinit var toggleHomeTwoLineLabels: NexusToggleRow
    private lateinit var toggleHomeIndicator: NexusToggleRow
    private lateinit var sliderPaddingLR: NexusSliderRow
    private lateinit var sliderPaddingTB: NexusSliderRow
    private lateinit var sliderGapH: NexusSliderRow
    private lateinit var sliderGapV: NexusSliderRow
    private lateinit var toggleShowFeed: NexusToggleRow
    private lateinit var transitionsHeader: NexusNavRow
    private lateinit var segmentedPageTransition: NexusSegmentedRow
    private lateinit var toggleReduceMotion: NexusToggleRow
    private lateinit var sliderAnimSpeed: NexusSliderRow

    private var isGridExpanded = true
    private var isSpacingExpanded = true
    private var isTransitionsExpanded = true
    private var isNewsFeedExpanded = true
    private var ignoreCallbacks = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dockRow = HomeShowDockRow(requireContext())
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()

        val rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutTransition = heightChangeTransition()
        }

        val previewContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(padH, (4 * dp).toInt(), padH, (8 * dp).toInt())
            layoutTransition = heightChangeTransition()
        }

        previewView = HomeScreenPreviewView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        previewContainer.addView(previewView)
        rootLayout.addView(previewContainer)

        val scrollView = NestedScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }

        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(padH, 0, padH, (80 * dp).toInt())
            layoutTransition = LayoutTransition()
        }

        sliderHomeColumns = NexusSliderRow(requireContext())
        sliderHomeRows = NexusSliderRow(requireContext())
        sliderHomeIconSize = NexusSliderRow(requireContext())
        toggleHomeLabels = NexusToggleRow(requireContext())
        toggleHomeTwoLineLabels = NexusToggleRow(requireContext())
        toggleHomeIndicator = NexusToggleRow(requireContext())

        sliderPaddingLR = NexusSliderRow(requireContext())
        sliderPaddingTB = NexusSliderRow(requireContext())
        sliderGapH = NexusSliderRow(requireContext())
        sliderGapV = NexusSliderRow(requireContext())

        segmentedPageTransition = NexusSegmentedRow(requireContext())
        toggleReduceMotion = NexusToggleRow(requireContext())
        sliderAnimSpeed = NexusSliderRow(requireContext())
        toggleShowFeed = NexusToggleRow(requireContext())

        val gridContainer = SettingsSectionGroupView(requireContext()).apply {
            visibility = View.VISIBLE
            addChildRow(sliderHomeColumns)
            addChildRow(sliderHomeRows)
            addChildRow(sliderHomeIconSize)
            addChildRow(toggleHomeLabels)
            addChildRow(toggleHomeTwoLineLabels)
            addChildRow(toggleHomeIndicator)
            addChildRow(dockRow.row)
        }

        lateinit var gridHeader: NexusNavRow
        gridHeader = NexusNavRow(
            requireContext(),
            title = getString(R.string.home_settings_grid_layout_title),
            subtitle = getString(R.string.home_settings_grid_layout_subtitle),
            iconRes = R.drawable.ic_grid,
            showChevron = true
        ) {
            isGridExpanded = !isGridExpanded
            gridContainer.visibility = if (isGridExpanded) View.VISIBLE else View.GONE
            gridHeader.setChevronRotation(if (isGridExpanded) 180f else 0f)
        }
        gridHeader.setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        gridHeader.setChevronRotation(180f, animate = false)

        gridSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
            addRow(gridHeader)
            addRow(gridContainer)
        }

        val spacingContainer = SettingsSectionGroupView(requireContext()).apply {
            visibility = View.VISIBLE
            addChildRow(sliderPaddingLR)
            addChildRow(sliderPaddingTB)
            addChildRow(sliderGapH)
            addChildRow(sliderGapV)
        }

        lateinit var spacingHeader: NexusNavRow
        spacingHeader = NexusNavRow(
            requireContext(),
            title = getString(R.string.home_settings_screen_spacing_title),
            subtitle = getString(R.string.home_settings_screen_spacing_subtitle),
            iconRes = R.drawable.ic_resize,
            showChevron = true
        ) {
            isSpacingExpanded = !isSpacingExpanded
            spacingContainer.visibility = if (isSpacingExpanded) View.VISIBLE else View.GONE
            spacingHeader.setChevronRotation(if (isSpacingExpanded) 180f else 0f)
        }
        spacingHeader.setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        spacingHeader.setChevronRotation(180f, animate = false)

        spacingSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
            addRow(spacingHeader)
            addRow(spacingContainer)
        }

        val transitionsContainer = SettingsSectionGroupView(requireContext()).apply {
            visibility = View.VISIBLE
            addChildRow(segmentedPageTransition)
            addChildRow(toggleReduceMotion)
            addChildRow(sliderAnimSpeed)
        }

        transitionsHeader = NexusNavRow(
            requireContext(),
            title = getString(R.string.home_settings_transitions_title),
            subtitle = getString(R.string.home_settings_transition_slide),
            iconRes = R.drawable.ic_transition,
            showChevron = true
        ) {
            isTransitionsExpanded = !isTransitionsExpanded
            transitionsContainer.visibility = if (isTransitionsExpanded) View.VISIBLE else View.GONE
            transitionsHeader.setChevronRotation(if (isTransitionsExpanded) 180f else 0f)
        }
        transitionsHeader.setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        transitionsHeader.setChevronRotation(180f, animate = false)

        val transitionsSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
            addRow(transitionsHeader)
            addRow(transitionsContainer)
        }

        val newsFeedContainer = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(toggleShowFeed)
        }

        lateinit var newsFeedHeader: NexusNavRow
        newsFeedHeader = NexusNavRow(
            requireContext(),
            title = getString(R.string.home_settings_news_feed_title),
            subtitle = getString(R.string.home_settings_news_feed_subtitle),
            iconRes = R.drawable.baseline_newspaper_24,
            showChevron = true
        ) {
            isNewsFeedExpanded = !isNewsFeedExpanded
            newsFeedContainer.visibility = if (isNewsFeedExpanded) View.VISIBLE else View.GONE
            newsFeedHeader.setChevronRotation(if (isNewsFeedExpanded) 180f else 0f)
        }
        newsFeedHeader.setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        newsFeedHeader.setChevronRotation(180f, animate = false)

        val newsFeedSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
            addRow(newsFeedHeader)
            addRow(newsFeedContainer)
        }

        contentLayout.addView(gridSection)
        contentLayout.addView(spacingSection)
        contentLayout.addView(transitionsSection)
        contentLayout.addView(newsFeedSection)

        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val controller = EntryPointAccessors.fromApplication(
                        requireContext().applicationContext,
                        ThemeEntryPoint::class.java
                    ).themeController()
                    controller.currentTokens.collect { tokens ->
                        applyPageBackground(tokens, rootLayout, previewContainer, scrollView)
                        previewView.applyTokens(tokens)
                    }
                } catch (_: Exception) {}
            }
        }

        return rootLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dockRow.setListeners({ transform -> patch(transform = transform) }, { ignoreCallbacks })
        HomeScreenSettingsBinder.setupListeners(
            sliderHomeColumns = sliderHomeColumns,
            sliderHomeRows = sliderHomeRows,
            sliderHomeIconSize = sliderHomeIconSize,
            toggleHomeLabels = toggleHomeLabels,
            toggleHomeTwoLineLabels = toggleHomeTwoLineLabels,
            toggleHomeIndicator = toggleHomeIndicator,
            sliderPaddingLR = sliderPaddingLR,
            sliderPaddingTB = sliderPaddingTB,
            sliderGapH = sliderGapH,
            sliderGapV = sliderGapV,
            transitionsHeader = transitionsHeader,
            segmentedPageTransition = segmentedPageTransition,
            toggleReduceMotion = toggleReduceMotion,
            sliderAnimSpeed = sliderAnimSpeed,
            toggleShowFeed = toggleShowFeed,
            onPatch = { transform -> patch(transform = transform) },
            onPatchStaged = { transform -> patch(staged = true, transform = transform) },
            onReduceMotionChanged = { checked -> viewModel.setReduceMotion(checked) },
            onAnimSpeedChanged = { speed ->
                viewModel.patchAnimSpeed(speed)
                refreshPreview()
            },
            onRebindRequired = { bindDraft() },
            isIgnoreCallbacks = { ignoreCallbacks },
            isLabelsShowing = { viewModel.draft.value?.homeShowLabels == true }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ensureDraftReady()
            bindDraft()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.bindEpoch.collect { bindDraft() }
            }
        }
    }

    /** [staged] holds the change behind the Apply bar instead of committing it — used for the
     *  grid geometry, where applying reflows placements and can move the user's icons. */
    private fun patch(
        staged: Boolean = false,
        transform: (com.nexus.launcher.data.prefs.NexusSettingsData) -> com.nexus.launcher.data.prefs.NexusSettingsData
    ) {
        viewModel.patchDraft(staged = staged, transform = transform)
        refreshPreview()
    }

    private fun refreshPreview() {
        val s = viewModel.draft.value ?: return
        previewView.pendingSettings = PendingHomeSettings.from(s, viewModel.draftAnimSpeed.value)
    }

    private fun bindDraft() {
        val pending = viewModel.draft.value ?: return
        ignoreCallbacks = true
        gridSection.visibility = View.VISIBLE
        spacingSection.visibility = View.VISIBLE
        dockRow.bind(pending)
        HomeScreenSettingsBinder.bindControls(
            context = requireContext(),
            pending = pending,
            reduceMotion = viewModel.getReduceMotion(),
            animSpeed = viewModel.draftAnimSpeed.value,
            sliderHomeColumns = sliderHomeColumns,
            sliderHomeRows = sliderHomeRows,
            sliderHomeIconSize = sliderHomeIconSize,
            toggleHomeLabels = toggleHomeLabels,
            toggleHomeTwoLineLabels = toggleHomeTwoLineLabels,
            toggleHomeIndicator = toggleHomeIndicator,
            sliderPaddingLR = sliderPaddingLR,
            sliderPaddingTB = sliderPaddingTB,
            sliderGapH = sliderGapH,
            sliderGapV = sliderGapV,
            transitionsHeader = transitionsHeader,
            segmentedPageTransition = segmentedPageTransition,
            toggleReduceMotion = toggleReduceMotion,
            sliderAnimSpeed = sliderAnimSpeed,
            toggleShowFeed = toggleShowFeed
        )
        refreshPreview()
        ignoreCallbacks = false
    }

    /**
     * Settings is a separate full-screen Activity — no home-screen wallpaper view sits behind it
     * the way dialogs/pickers/sheets have one behind their window. Default/Neumorphism: flat
     * theme-color background, same as always, on every layer (fragment root, preview card, scroll
     * container) — matching what [SettingsActivity] paints on the header/apply-bar chrome around
     * it. Frosted Glass: this fragment goes fully transparent on all three layers instead of
     * sampling its own separate wallpaper bitmap — [SettingsActivity] paints ONE blurred-wallpaper
     * bitmap edge-to-edge behind the whole `settings_fragment_container` (header + this fragment's
     * pager slot + apply bar together), so a per-fragment bitmap here would only reproduce a
     * second, differently-cropped copy and read as a visible seam between "this fragment's frost"
     * and "the chrome's frost" — exactly the bug reported after the first version of this pilot.
     */
    private fun applyPageBackground(
        tokens: com.nexus.launcher.theme.NexusColorTokens,
        rootLayout: LinearLayout,
        previewContainer: LinearLayout,
        scrollView: NestedScrollView
    ) {
        if (!FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            rootLayout.background = null
            rootLayout.setBackgroundColor(tokens.bg)
            previewContainer.setBackgroundColor(tokens.bg)
            scrollView.setBackgroundColor(tokens.bg)
            return
        }
        rootLayout.background = null
        rootLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        previewContainer.background = null
        previewContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        scrollView.background = null
        scrollView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    private fun heightChangeTransition(): LayoutTransition {
        return LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(LayoutTransition.CHANGING, 180L)
            setAnimateParentHierarchy(false)
        }
    }
}
