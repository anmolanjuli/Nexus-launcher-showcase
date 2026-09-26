package com.nexus.launcher.ui.dock.settings

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DockSettingsEntryPoint {
    fun dockSettingsRepository(): DockSettingsRepository
}
