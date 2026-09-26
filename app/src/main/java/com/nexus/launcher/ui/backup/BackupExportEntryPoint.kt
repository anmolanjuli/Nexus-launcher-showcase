package com.nexus.launcher.ui.backup

import com.nexus.launcher.data.FeedDao
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupExportEntryPoint {
    fun homeScreenDao(): HomeScreenDao
    fun settingsRepository(): SettingsRepository
    fun dockSettingsRepository(): DockSettingsRepository
    fun preferenceManager(): PreferenceManager
    fun themeController(): com.nexus.launcher.theme.ThemeController
    fun fontFamilyController(): com.nexus.launcher.typography.FontFamilyController
    fun feedDao(): FeedDao
    fun localeController(): com.nexus.launcher.locale.LocaleController
}
