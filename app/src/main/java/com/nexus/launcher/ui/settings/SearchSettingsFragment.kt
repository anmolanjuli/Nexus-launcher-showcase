package com.nexus.launcher.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.nexus.launcher.R
import com.nexus.launcher.search.NexusSearchSettings
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

class SearchSettingsFragment : Fragment() {

    private lateinit var toggleContacts: NexusToggleRow
    private lateinit var toggleWeb: NexusToggleRow
    private lateinit var toggleCalculator: NexusToggleRow
    private lateinit var toggleConversion: NexusToggleRow
    private lateinit var toggleMaps: NexusToggleRow

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()

        val scrollView = NestedScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(padH, 0, padH, (80 * dp).toInt())
        }

        val mainSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
        }

        // 1. Micro Actions Section
        val headerMicroActions = NexusNavRow(
            requireContext(),
            title = getString(R.string.search_micro_actions_title),
            subtitle = getString(R.string.search_micro_actions_desc),
            iconRes = R.drawable.outline_function_24,
            showChevron = false
        ).apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
        }

        toggleCalculator = NexusToggleRow(requireContext())
        toggleConversion = NexusToggleRow(requireContext())

        val microActionsGroup = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(toggleCalculator)
            addChildRow(toggleConversion)
        }

        val microActionsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerMicroActions)
            addView(microActionsGroup)
        }
        mainSection.addRow(microActionsContainer)

        // 2. Search Sources Section
        val headerSources = NexusNavRow(
            requireContext(),
            title = getString(R.string.search_sources_title),
            subtitle = getString(R.string.search_sources_desc),
            iconRes = R.drawable.outline_person_search_24,
            showChevron = false
        ).apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
        }

        toggleContacts = NexusToggleRow(requireContext())
        toggleWeb = NexusToggleRow(requireContext())
        toggleMaps = NexusToggleRow(requireContext())

        val sourcesGroup = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(toggleContacts)
            addChildRow(toggleWeb)
            addChildRow(toggleMaps)
        }

        val sourcesContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerSources)
            addView(sourcesGroup)
        }
        mainSection.addRow(sourcesContainer)

        content.addView(mainSection)
        scrollView.addView(content)

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, top, 0, (16 * dp).toInt())
            insets
        }

        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val settings = NexusSearchSettings(requireContext())

        toggleCalculator.configure(
            getString(R.string.search_calc_title),
            settings.searchCalculatorEnabled,
            subtitle = getString(R.string.search_calc_desc)
        )
        toggleConversion.configure(
            getString(R.string.search_conv_title),
            settings.searchConversionEnabled,
            subtitle = getString(R.string.search_conv_desc)
        )
        toggleContacts.configure(
            getString(R.string.search_contacts_title),
            settings.searchContactsEnabled,
            subtitle = getString(R.string.search_contacts_desc)
        )
        toggleWeb.configure(
            getString(R.string.search_web_title),
            settings.searchWebEnabled,
            subtitle = getString(R.string.search_web_desc)
        )
        toggleMaps.configure(
            getString(R.string.search_maps_title),
            settings.searchMapsEnabled,
            subtitle = getString(R.string.search_maps_desc)
        )

        toggleCalculator.onCheckedChanged = { settings.searchCalculatorEnabled = it }
        toggleConversion.onCheckedChanged = { settings.searchConversionEnabled = it }
        com.nexus.launcher.ui.premium.PremiumBadges.bindRowBadge(toggleConversion, com.nexus.launcher.premium.PremiumFeature.SMART_SEARCH, toggleConversion::setBadge)
        toggleContacts.onCheckedChanged = { settings.searchContactsEnabled = it }
        toggleWeb.onCheckedChanged = { settings.searchWebEnabled = it }
        toggleMaps.onCheckedChanged = { settings.searchMapsEnabled = it }
    }
}
