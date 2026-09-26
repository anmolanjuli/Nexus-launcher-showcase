package com.nexus.launcher.ui.settings

import android.animation.LayoutTransition
import android.graphics.Color
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
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.R
import com.nexus.launcher.theme.ThemeEntryPoint
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
class DrawerSettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by activityViewModels()

    private lateinit var previewView: DrawerPreviewView
    private lateinit var segmentedLayout: NexusSegmentedRow
    private lateinit var segmentedCategoryLayout: NexusSegmentedRow
    private lateinit var gridHeader: NexusNavRow
    private lateinit var sliderDrawerColumns: NexusSliderRow
    private lateinit var sliderDrawerIconSize: NexusSliderRow
    private lateinit var toggleDrawerLabels: NexusToggleRow
    private lateinit var toggleDrawerTwoLineLabels: NexusToggleRow
    private lateinit var segmentedSortOrder: NexusSegmentedRow
    private lateinit var transitionsHeader: NexusNavRow
    private lateinit var segmentedDrawerTransition: NexusSegmentedRow
    private lateinit var categoriesHeader: NexusNavRow
    private lateinit var toggleSearchPill: NexusToggleRow
    private lateinit var segmentedSearchPosition: NexusSegmentedRow
    private lateinit var segmentedCategoryStyle: NexusSegmentedRow
    private lateinit var segmentedCategoryPosition: NexusSegmentedRow
    private lateinit var toggleDrawerCategoryBar: NexusToggleRow
    private lateinit var toggleDrawerRail: NexusToggleRow
    private lateinit var hiddenAppsRow: NexusNavRow

    private var isGridExpanded = true
    private var isTransitionsExpanded = true
    private var isCategoriesExpanded = true
    private var ignoreCallbacks = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()
        val childIndent = (8 * dp).toInt()

        val scrollView = NestedScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.TRANSPARENT)
        }

        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(padH, 0, padH, (80 * dp).toInt())
            layoutTransition = LayoutTransition()
        }

        val mainSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
        }

        previewView = DrawerPreviewView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * dp).toInt()
            }
        }
        mainSection.addRow(previewView)

        // 1. Layout (Grid/List Mode, Grid & Icon Sizing, Labels, Sort Order)
        segmentedLayout = NexusSegmentedRow(requireContext())
        segmentedCategoryLayout = NexusSegmentedRow(requireContext())
        sliderDrawerColumns = NexusSliderRow(requireContext())
        sliderDrawerIconSize = NexusSliderRow(requireContext())
        toggleDrawerLabels = NexusToggleRow(requireContext())
        toggleDrawerTwoLineLabels = NexusToggleRow(requireContext())
        segmentedSortOrder = NexusSegmentedRow(requireContext())

        val layoutGroupContainer = SettingsSectionGroupView(requireContext()).apply {
            visibility = View.VISIBLE
            addChildRow(segmentedLayout)
            addChildRow(segmentedCategoryLayout)
            addChildRow(sliderDrawerColumns)
            addChildRow(sliderDrawerIconSize)
            addChildRow(toggleDrawerLabels)
            addChildRow(toggleDrawerTwoLineLabels)
            addChildRow(segmentedSortOrder)
        }

        gridHeader = NexusNavRow(
            requireContext(),
            title = getString(R.string.drawer_settings_layout_title),
            subtitle = getString(R.string.drawer_settings_layout_subtitle),
            iconRes = R.drawable.ic_drawermode,
            showChevron = true
        ) {
            isGridExpanded = !isGridExpanded
            layoutGroupContainer.visibility = if (isGridExpanded) View.VISIBLE else View.GONE
            gridHeader.setChevronRotation(if (isGridExpanded) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
        }
        val layoutSectionContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(gridHeader)
            addView(layoutGroupContainer)
        }
        mainSection.addRow(layoutSectionContainer)

        // 2. Transitions (Accordion Section)
        segmentedDrawerTransition = NexusSegmentedRow(requireContext())

        val transitionsContainer = SettingsSectionGroupView(requireContext()).apply {
            visibility = View.VISIBLE
            addChildRow(segmentedDrawerTransition)
        }

        transitionsHeader = NexusNavRow(
            requireContext(),
            title = getString(R.string.drawer_settings_transitions_title),
            subtitle = DrawerSettingsFormatters.getTransitionName(requireContext(), viewModel.draft.value?.drawerTransition ?: com.nexus.launcher.data.prefs.NexusDefaults.DRAWER_TRANSITION),
            iconRes = R.drawable.ic_transition,
            showChevron = true
        ) {
            isTransitionsExpanded = !isTransitionsExpanded
            transitionsContainer.visibility = if (isTransitionsExpanded) View.VISIBLE else View.GONE
            transitionsHeader.setChevronRotation(if (isTransitionsExpanded) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
        }
        val transitionsSectionContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(transitionsHeader)
            addView(transitionsContainer)
        }
        mainSection.addRow(transitionsSectionContainer)

        // 4. Navigation & Categories (Accordion Section)
        toggleSearchPill = NexusToggleRow(requireContext())
        segmentedSearchPosition = NexusSegmentedRow(requireContext())
        toggleDrawerCategoryBar = NexusToggleRow(requireContext())
        segmentedCategoryStyle = NexusSegmentedRow(requireContext())
        segmentedCategoryPosition = NexusSegmentedRow(requireContext())
        toggleDrawerRail = NexusToggleRow(requireContext())

        val categoriesContainer = SettingsSectionGroupView(requireContext()).apply {
            visibility = View.VISIBLE
            addChildRow(toggleSearchPill)
            addChildRow(segmentedSearchPosition)
            addChildRow(toggleDrawerCategoryBar)
            addChildRow(segmentedCategoryStyle)
            addChildRow(segmentedCategoryPosition)
            addChildRow(toggleDrawerRail)
        }

        categoriesHeader = NexusNavRow(
            requireContext(),
            title = getString(R.string.drawer_settings_search_navigation_title),
            subtitle = DrawerSettingsFormatters.getNavigationSubtitle(requireContext(), viewModel.draft.value ?: NexusSettingsData()),
            iconRes = R.drawable.ic_category,
            showChevron = true
        ) {
            isCategoriesExpanded = !isCategoriesExpanded
            categoriesContainer.visibility = if (isCategoriesExpanded) View.VISIBLE else View.GONE
            categoriesHeader.setChevronRotation(if (isCategoriesExpanded) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
        }
        val categoriesSectionContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(categoriesHeader)
            addView(categoriesContainer)
        }
        mainSection.addRow(categoriesSectionContainer)

        // 5. Hidden Apps (Segmented Card Pillow)
        hiddenAppsRow = NexusNavRow(
            requireContext(),
            title = getString(R.string.drawer_settings_hidden_apps_title),
            subtitle = getString(R.string.drawer_settings_hidden_apps_subtitle),
            iconRes = R.drawable.ic_hidden,
            showChevron = true
        ) {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, HiddenAppsFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }.apply {
            setContentPadding((14 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }
        val hiddenAppsContainer = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(hiddenAppsRow)
        }
        mainSection.addRow(hiddenAppsContainer)

        contentLayout.addView(mainSection)
        scrollView.addView(contentLayout)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val controller = EntryPointAccessors.fromApplication(
                        requireContext().applicationContext,
                        ThemeEntryPoint::class.java
                    ).themeController()
                    controller.currentTokens.collect { tokens ->
                        previewView.applyTokens(tokens)
                    }
                } catch (_: Exception) {}
            }
        }

        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        DrawerSettingsListenerWire.wireListeners(
            context = requireContext(),
            segmentedLayout = segmentedLayout,
            segmentedCategoryLayout = segmentedCategoryLayout,
            sliderDrawerColumns = sliderDrawerColumns,
            sliderDrawerIconSize = sliderDrawerIconSize,
            toggleDrawerLabels = toggleDrawerLabels,
            toggleDrawerTwoLineLabels = toggleDrawerTwoLineLabels,
            segmentedSortOrder = segmentedSortOrder,
            transitionsHeader = transitionsHeader,
            segmentedDrawerTransition = segmentedDrawerTransition,
            toggleSearchPill = toggleSearchPill,
            segmentedSearchPosition = segmentedSearchPosition,
            segmentedCategoryStyle = segmentedCategoryStyle,
            segmentedCategoryPosition = segmentedCategoryPosition,
            toggleDrawerCategoryBar = toggleDrawerCategoryBar,
            toggleDrawerRail = toggleDrawerRail,
            onPatch = { patch(it) },
            onUpdateDrawerTransition = { viewModel.updateDrawerTransition(it) },
            onRebindRequired = { bindDraft() },
            isIgnoreCallbacks = { ignoreCallbacks },
            isLabelsShowing = { viewModel.draft.value?.drawerShowLabels == true },
            isSearchPillShowing = { viewModel.draft.value?.drawerShowSearchPill != false },
            setIgnoreCallbacks = { ignoreCallbacks = it }
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

    private fun patch(transform: (com.nexus.launcher.data.prefs.NexusSettingsData) -> com.nexus.launcher.data.prefs.NexusSettingsData) {
        viewModel.patchDraft(transform = transform)
        bindDraft()
    }

    private fun bindDraft() {
        val s = viewModel.draft.value ?: return
        ignoreCallbacks = true
        previewView.pendingSettings = PendingDrawerSettings.from(s)

        segmentedLayout.visibility = View.VISIBLE
        sliderDrawerColumns.visibility = View.VISIBLE
        toggleDrawerLabels.visibility = View.VISIBLE
        toggleDrawerTwoLineLabels.visibility = View.VISIBLE

        DrawerSettingsBinder.bindControls(
            context = requireContext(),
            s = s,
            sliderDrawerIconSize = sliderDrawerIconSize,
            segmentedLayout = segmentedLayout,
            segmentedCategoryLayout = segmentedCategoryLayout,
            sliderDrawerColumns = sliderDrawerColumns,
            toggleDrawerLabels = toggleDrawerLabels,
            toggleDrawerTwoLineLabels = toggleDrawerTwoLineLabels,
            gridHeader = gridHeader,
            segmentedSortOrder = segmentedSortOrder,
            transitionsHeader = transitionsHeader,
            segmentedDrawerTransition = segmentedDrawerTransition,
            categoriesHeader = categoriesHeader,
            toggleSearchPill = toggleSearchPill,
            segmentedSearchPosition = segmentedSearchPosition,
            toggleDrawerCategoryBar = toggleDrawerCategoryBar,
            segmentedCategoryStyle = segmentedCategoryStyle,
            segmentedCategoryPosition = segmentedCategoryPosition,
            toggleDrawerRail = toggleDrawerRail
        )

        ignoreCallbacks = false
    }
}
