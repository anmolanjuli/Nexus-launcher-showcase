package com.nexus.launcher.locale

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocaleEntryPoint {
    fun localeController(): LocaleController
}
