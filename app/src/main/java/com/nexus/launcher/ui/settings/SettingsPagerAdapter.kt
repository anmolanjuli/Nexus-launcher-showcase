package com.nexus.launcher.ui.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = SettingsHubCatalog.PAGE_COUNT

    // Icons, Typography, Immersive Mode and Nexus Island were appended (7, 8, 11, 12)
    // rather than inserted, so every existing index — and the deep links and search entries
    // that name them — keeps its meaning.
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> HomeScreenSettingsFragment()
        1 -> DrawerSettingsFragment()
        2 -> ThemeSettingsFragment.newInstance(ThemeSettingsFragment.AppearanceBlock.THEME)
        3 -> NotificationsSettingsFragment()
        4 -> GesturesSettingsFragment()
        5 -> BackupRestoreSettingsFragment()
        6 -> SearchSettingsFragment()
        7 -> IconsSettingsFragment()
        8 -> ThemeSettingsFragment.newInstance(ThemeSettingsFragment.AppearanceBlock.TYPOGRAPHY)
        9 -> LicensesFragment()
        10 -> PremiumFragment()
        11 -> ImmersiveSettingsFragment()
        12 -> IslandSettingsFragment()
        else -> ImmersiveSettingsFragment()
    }
}
