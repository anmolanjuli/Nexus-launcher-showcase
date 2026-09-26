package com.nexus.launcher.typography

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TypographyEntryPoint {
    fun typographyResolver(): NexusTypographyResolver
    fun fontFamilyController(): FontFamilyController
}
