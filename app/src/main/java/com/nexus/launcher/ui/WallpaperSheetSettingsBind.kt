package com.nexus.launcher.ui

import android.graphics.Color
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.settings.views.GradientBuilder
import kotlinx.coroutines.launch

/** Settings collect for [WallpaperSheet] — keeps the sheet under the 400-line cap. */
internal object WallpaperSheetSettingsBind {

    fun start(
        lifecycleOwner: LifecycleOwner,
        viewModel: MainViewModel,
        gradientBuilder: GradientBuilder,
        previewView: WallpaperPreviewView,
        getUiTab: () -> String,
        isOpenedToDefault: () -> Boolean,
        markOpenedToDefault: () -> Unit,
        openOnGradientTab: (NexusSettingsData) -> Unit,
        setIgnoreCallbacks: (Boolean) -> Unit,
        getTreatmentController: () -> WallpaperTreatmentControls.TreatmentController?,
        onStagedTreatment: (blur: Float, hue: Float, strength: Float) -> Unit,
        refreshFrost: (View) -> Unit,
        frostBg: () -> View?
    ) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.nexusSettings.collect { settings ->
                    setIgnoreCallbacks(true)
                    gradientBuilder.configure(
                        settings.wallpaperGradientStart,
                        settings.wallpaperGradientEnd,
                        settings.wallpaperGradientDirection,
                        settings.accentColor
                    )
                    // Always land on Gradient — do not mirror system-level wallpaperType.
                    if (!isOpenedToDefault()) {
                        openOnGradientTab(settings)
                        markOpenedToDefault()
                    } else if (getUiTab() == "gradient") {
                        previewView.showGradient(
                            settings.wallpaperGradientStart,
                            settings.wallpaperGradientEnd,
                            settings.wallpaperGradientDirection
                        )
                    }
                    var stagedHue = 0f
                    val colorInt = try {
                        Color.parseColor(settings.wallpaperTintColor)
                    } catch (_: Exception) {
                        Color.TRANSPARENT
                    }
                    if (colorInt != Color.TRANSPARENT) {
                        val hsv = FloatArray(3)
                        Color.colorToHSV(colorInt, hsv)
                        stagedHue = hsv[0]
                    }
                    onStagedTreatment(
                        settings.wallpaperBlur,
                        stagedHue,
                        settings.wallpaperTintStrength
                    )
                    getTreatmentController()?.setTreatment(
                        settings.wallpaperBlur,
                        stagedHue,
                        settings.wallpaperTintStrength
                    )
                    previewView.applyLiveTreatment(
                        settings.wallpaperBlur,
                        stagedHue,
                        settings.wallpaperTintStrength
                    )
                    frostBg()?.let { refreshFrost(it) }
                    setIgnoreCallbacks(false)
                }
            }
        }
    }
}
