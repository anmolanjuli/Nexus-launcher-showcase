package com.nexus.launcher.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Settings update delegate functions for [MainViewModel].
 * Extracted from [MainViewModel] to keep that file well under the 400-line limit.
 */
fun MainViewModel.updateHomeColumns(value: Int): Job = viewModelScope.launch { settingsRepository.updateHomeColumns(value) }
fun MainViewModel.updateHomeRows(value: Int): Job = viewModelScope.launch { settingsRepository.updateHomeRows(value) }
fun MainViewModel.updateHomeIconSize(value: Float): Job = viewModelScope.launch { settingsRepository.updateHomeIconSize(value) }
fun MainViewModel.updateHomeShowLabels(value: Boolean): Job = viewModelScope.launch { settingsRepository.updateHomeShowLabels(value) }
fun MainViewModel.updateHomeShowIndicator(value: Boolean): Job = viewModelScope.launch { settingsRepository.updateHomeShowIndicator(value) }
fun MainViewModel.updateDrawerColumns(value: Int): Job = viewModelScope.launch { settingsRepository.updateDrawerColumns(value) }
fun MainViewModel.updateDrawerIconSize(value: Float): Job = viewModelScope.launch { settingsRepository.updateDrawerIconSize(value) }
fun MainViewModel.updateDrawerShowCategoryBar(value: Boolean): Job = viewModelScope.launch { settingsRepository.updateDrawerShowCategoryBar(value) }
fun MainViewModel.updateDrawerShowSearchPill(value: Boolean): Job = viewModelScope.launch { settingsRepository.updateDrawerShowSearchPill(value) }
fun MainViewModel.updateDrawerCategoryMode(value: String): Job = viewModelScope.launch { settingsRepository.updateDrawerCategoryMode(value) }
fun MainViewModel.updateDrawerCategoryPosition(value: String): Job = viewModelScope.launch { settingsRepository.updateDrawerCategoryPosition(value) }
fun MainViewModel.updateDrawerSearchBarPosition(value: String): Job = viewModelScope.launch { settingsRepository.updateDrawerSearchBarPosition(value) }
fun MainViewModel.updateDrawerShowRail(value: Boolean): Job = viewModelScope.launch { settingsRepository.updateDrawerShowRail(value) }
fun MainViewModel.updateAccentColor(color: String): Job = viewModelScope.launch { settingsRepository.updateAccentColor(color) }
fun MainViewModel.updateMatchWallpaperColor(value: Boolean): Job = viewModelScope.launch { settingsRepository.updateMatchWallpaperColor(value) }
fun MainViewModel.updateWallpaperType(type: String): Job = viewModelScope.launch { settingsRepository.updateWallpaperType(type) }
fun MainViewModel.updateWallpaperSolidColor(value: String): Job = viewModelScope.launch { settingsRepository.updateWallpaperSolidColor(value) }
fun MainViewModel.updateWallpaperGradient(start: String, end: String, direction: String): Job = viewModelScope.launch { settingsRepository.updateWallpaperGradient(start, end, direction) }
fun MainViewModel.updateWallpaperGallery(path: String): Job = viewModelScope.launch { settingsRepository.updateWallpaperGallery(path) }
fun MainViewModel.updateWallpaperTreatment(blur: Float, tintColor: String, tintStrength: Float): Job = viewModelScope.launch { settingsRepository.updateWallpaperTreatment(blur, tintColor, tintStrength) }
fun MainViewModel.updateHomePageCount(value: Int): Job = viewModelScope.launch { settingsRepository.updateHomePageCount(value) }
fun MainViewModel.updatePageTransition(value: String): Job = viewModelScope.launch { settingsRepository.updatePageTransition(value) }
fun MainViewModel.updateDrawerLayout(value: String): Job = viewModelScope.launch { settingsRepository.updateDrawerLayout(value) }
fun MainViewModel.updateDrawerGridOrList(value: String): Job = viewModelScope.launch { settingsRepository.updateDrawerGridOrList(value) }
fun MainViewModel.updateDrawerListColumns(value: Int): Job = viewModelScope.launch { settingsRepository.updateDrawerListColumns(value) }
fun MainViewModel.updateDrawerCategoryLayout(value: String): Job = viewModelScope.launch { settingsRepository.updateDrawerCategoryLayout(value) }
