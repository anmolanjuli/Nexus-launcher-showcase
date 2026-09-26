package com.nexus.launcher.ui.dock.settings

import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.prefs.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DockResetEntryPoint {
    fun homeScreenDao(): HomeScreenDao
    fun settingsRepository(): SettingsRepository
}
