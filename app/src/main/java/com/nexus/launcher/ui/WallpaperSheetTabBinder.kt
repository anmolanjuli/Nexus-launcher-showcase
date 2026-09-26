package com.nexus.launcher.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.theme.NexusColorTokens

/** Coordinates tab switching, section visibility, and wallpaper previews for Wallpaper Sheet. */
internal class WallpaperSheetTabBinder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: MainViewModel,
    private val density: Float,
    private val wallpaperTypeRow: LinearLayout,
    private val gradientSection: LinearLayout,
    private val gallerySection: LinearLayout,
    private val othersSection: LinearLayout,
    private val systemSection: LinearLayout,
    private val previewView: WallpaperPreviewView,
    private val getTreatmentController: () -> WallpaperTreatmentControls.TreatmentController?,
    private val isIgnoreCallbacks: () -> Boolean,
    private val getPendingSelection: () -> PendingWallpaper?,
    private val setPendingSelection: (PendingWallpaper?) -> Unit,
    private val updateApplyButtonState: () -> Unit,
    private val getUiTab: () -> String,
    private val setUiTab: (String) -> Unit
) {

    fun refreshChips(selected: String, tokens: NexusColorTokens) {
        WallpaperTypeChips.build(
            context = context,
            container = wallpaperTypeRow,
            density = density,
            selected = selected,
            tokens = tokens
        ) { type ->
            if (!isIgnoreCallbacks()) {
                if (type == "gallery") {
                    handleGalleryChip(tokens = tokens)
                } else {
                    setUiTab(type)
                    if (type == "system") {
                        setPendingSelection(PendingWallpaper.System)
                    } else if (type == "gradient") {
                        val s = viewModel.nexusSettings.value
                        setPendingSelection(
                            PendingWallpaper.Gradient(
                                s.wallpaperGradientStart,
                                s.wallpaperGradientEnd,
                                s.wallpaperGradientDirection
                            )
                        )
                    }
                    if (type != "others") {
                        getTreatmentController()?.setTreatment(0f, 0f, 0f)
                        updateApplyButtonState()
                        updatePreview(type, viewModel.nexusSettings.value)
                    }
                    refreshChips(type, tokens)
                    updateWallpaperSections(type)
                }
            }
        }
    }

    /** Deep-link entry point (from Nexus Settings > Appearance's Frosted Glass capture row) —
     *  opens the sheet directly on the System tab instead of the usual default-to-Gradient. */
    fun openOnSystemTab(tokens: NexusColorTokens) {
        setUiTab("system")
        setPendingSelection(PendingWallpaper.System)
        refreshChips("system", tokens)
        updateWallpaperSections("system")
        previewView.showSystem()
        getTreatmentController()?.setEnabled(false)
        updateApplyButtonState()
    }

    fun openOnGradientTab(settings: NexusSettingsData, tokens: NexusColorTokens) {
        setUiTab("gradient")
        val start = settings.wallpaperGradientStart
        val end = settings.wallpaperGradientEnd
        val dir = settings.wallpaperGradientDirection
        setPendingSelection(PendingWallpaper.Gradient(start, end, dir))
        refreshChips("gradient", tokens)
        updateWallpaperSections("gradient")
        previewView.showGradient(start, end, dir)
        getTreatmentController()?.setEnabled(true)
        updateApplyButtonState()
    }

    fun updatePreview(type: String, s: NexusSettingsData) {
        when (type) {
            "gradient" -> {
                previewView.showGradient(s.wallpaperGradientStart, s.wallpaperGradientEnd, s.wallpaperGradientDirection)
                getTreatmentController()?.setEnabled(true)
            }
            "gallery" -> {
                previewView.showGallery(s.wallpaperGalleryPath)
                getTreatmentController()?.setEnabled(true)
            }
            else -> {
                previewView.showSystem()
                getTreatmentController()?.setEnabled(false)
            }
        }
    }

    fun handleGalleryChip(forcePicker: Boolean = false, tokens: NexusColorTokens) {
        val existing = viewModel.nexusSettings.value.wallpaperGalleryPath
        if (!forcePicker && existing.isNotEmpty() && java.io.File(existing).exists()) {
            setUiTab("gallery")
            setPendingSelection(PendingWallpaper.Gallery(existing))
            getTreatmentController()?.setTreatment(0f, 0f, 0f)
            updateApplyButtonState()
            previewView.showGallery(existing)
            refreshChips("gallery", tokens)
            updateWallpaperSections("gallery")
        } else {
            val activity = lifecycleOwner as? ComponentActivity ?: return
            WallpaperGallerySource.launchPicker(activity) { path ->
                if (path != null) {
                    setUiTab("gallery")
                    setPendingSelection(PendingWallpaper.Gallery(path))
                    getTreatmentController()?.setTreatment(0f, 0f, 0f)
                    updateApplyButtonState()
                    previewView.showGallery(path)
                    refreshChips("gallery", tokens)
                    updateWallpaperSections("gallery")
                }
            }
        }
    }

    fun updateWallpaperSections(type: String) {
        gradientSection.visibility = if (type == "gradient") View.VISIBLE else View.GONE
        gallerySection.visibility = if (type == "gallery") View.VISIBLE else View.GONE
        othersSection.visibility = if (type == "others") View.VISIBLE else View.GONE
        systemSection.visibility = if (type == "system") View.VISIBLE else View.GONE
    }
}
