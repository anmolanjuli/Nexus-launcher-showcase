package com.nexus.launcher.integration

import com.nexus.launcher.data.prefs.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface IconPackStudioEntryPoint {
    fun settingsRepository(): SettingsRepository
}
