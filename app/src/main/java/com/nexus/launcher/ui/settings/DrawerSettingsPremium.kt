package com.nexus.launcher.ui.settings

import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.ui.premium.PremiumBadges
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow

/**
 * Which drawer controls are Premium ([PremiumFeature.DRAWER_CUSTOMIZATION]), kept out of
 * [DrawerSettingsFragment], which sits at the 400-line limit.
 *
 * The segmented rows refuse a locked option themselves. Free: the search pill on or off, both bars
 * at the top, categories In Pill, and the A–Z rail on. The strip the drawer falls back to when the
 * pill is off is not a choice, so it stays free there.
 */
internal object DrawerSettingsPremium {

    val FEATURE = PremiumFeature.DRAWER_CUSTOMIZATION

    fun bind(
        searchPosition: NexusSegmentedRow,
        categoryStyle: NexusSegmentedRow,
        categoryPosition: NexusSegmentedRow,
        rail: NexusToggleRow,
        pillOn: () -> Boolean,
    ) {
        searchPosition.setPremiumOptions(FEATURE) { it == "bottom" }
        categoryPosition.setPremiumOptions(FEATURE) { it == "bottom" }
        categoryStyle.setPremiumOptions(FEATURE) { it == "dropdown" || (it == "strip" && pillOn()) }
        PremiumBadges.bindRowBadge(rail, FEATURE, rail::setBadge)
    }
}
