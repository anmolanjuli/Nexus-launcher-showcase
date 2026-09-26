package com.nexus.launcher.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HiddenAppItem(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?
)

@HiltViewModel
class HiddenAppsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val appRepository: AppRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _hiddenApps =
        MutableStateFlow<List<HiddenAppItem>>(
            emptyList())
    val hiddenApps: StateFlow<List<HiddenAppItem>>
        = _hiddenApps.asStateFlow()

    init {
        loadHiddenApps()
    }

    private fun loadHiddenApps() {
        viewModelScope.launch {
            val hiddenPackages =
                preferenceManager.getHiddenApps()
            val allApps =
                appRepository.getInstalledApps()
            val collator = java.text.Collator.getInstance(java.util.Locale.getDefault()).apply { strength = java.text.Collator.SECONDARY }
            _hiddenApps.value = allApps
                .filter { app ->
                    hiddenPackages.contains(
                        app.packageName)
                }
                .map { app ->
                    HiddenAppItem(
                        packageName = app.packageName,
                        label = app.label,
                        icon = app.icon
                    )
                }
                .sortedWith(compareBy(collator) { it.label })
        }
    }

    fun unhideApp(packageName: String) {
        val current = preferenceManager
            .getHiddenApps().toMutableSet()
        current.remove(packageName)
        preferenceManager.saveHiddenApps(current)
        loadHiddenApps()
    }
}
