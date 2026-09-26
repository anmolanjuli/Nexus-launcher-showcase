package com.nexus.launcher.search.ui

import android.content.Context
import android.widget.LinearLayout
import com.nexus.launcher.R
import com.nexus.launcher.search.NexusSearchSettings
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

/**
 * Inline settings panel shown inside [NexusSearchOverlay] when the gear icon is tapped.
 * Mirrors the section layout of [SearchSettingsFragment] — Micro Actions and Search Sources.
 */
class NexusSearchSettingsPanel(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        val dp = resources.displayMetrics.density
        val s = NexusSearchSettings(context)

        fun header(title: String, subtitle: String, iconRes: Int) =
            NexusNavRow(context, title, subtitle, iconRes, showChevron = false).apply {
                setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            }

        fun row(title: String, sub: String, get: () -> Boolean, set: (Boolean) -> Unit) =
            NexusToggleRow(context).also { row ->
                row.configure(title, get(), subtitle = sub)
                row.onCheckedChanged = set
            }

        // Micro Actions
        addView(header(context.getString(com.nexus.launcher.R.string.search_micro_actions_title), context.getString(com.nexus.launcher.R.string.search_micro_actions_desc), R.drawable.outline_function_24))
        addView(SettingsSectionGroupView(context).apply {
            addChildRow(row(context.getString(com.nexus.launcher.R.string.search_calc_title), context.getString(com.nexus.launcher.R.string.search_calc_desc),
                { s.searchCalculatorEnabled }, { s.searchCalculatorEnabled = it }))
            addChildRow(row(context.getString(com.nexus.launcher.R.string.search_conv_title), context.getString(com.nexus.launcher.R.string.search_conv_desc),
                { s.searchConversionEnabled }, { s.searchConversionEnabled = it }).also { r ->
                    com.nexus.launcher.ui.premium.PremiumBadges.bindRowBadge(r, com.nexus.launcher.premium.PremiumFeature.SMART_SEARCH, r::setBadge)
                })
        })

        // Search Sources
        addView(header(context.getString(com.nexus.launcher.R.string.search_sources_title), context.getString(com.nexus.launcher.R.string.search_sources_desc), R.drawable.outline_person_search_24))
        addView(SettingsSectionGroupView(context).apply {
            addChildRow(row(context.getString(com.nexus.launcher.R.string.search_contacts_title), context.getString(com.nexus.launcher.R.string.search_contacts_desc),
                { s.searchContactsEnabled }, { s.searchContactsEnabled = it }))
            addChildRow(row(context.getString(com.nexus.launcher.R.string.search_web_title), context.getString(com.nexus.launcher.R.string.search_web_desc),
                { s.searchWebEnabled }, { s.searchWebEnabled = it }))
            addChildRow(row(context.getString(com.nexus.launcher.R.string.search_maps_title), context.getString(com.nexus.launcher.R.string.search_maps_desc),
                { s.searchMapsEnabled }, { s.searchMapsEnabled = it }))
        })
    }
}
