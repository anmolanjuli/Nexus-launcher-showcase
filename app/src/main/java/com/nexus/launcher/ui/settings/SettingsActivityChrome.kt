package com.nexus.launcher.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.glass.UiStyleCoordinator
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Theme token collection, UI-style recreate, and chrome backgrounds for [SettingsActivity].
 *
 * Kept out of the activity so the activity stays under the 400-line cap.
 */
internal object SettingsActivityChrome {

    /**
     * Status-bar clearance for the hub and the page header, and — in phone landscape — side
     * padding on both content containers so every settings page clears the camera cutout and
     * sits at a readable width (the frost backdrop behind them stays full-screen).
     */
    fun installInsets(hubInset: View, fragmentHeader: View, hub: View, pages: View) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(hubInset) { v, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            v.layoutParams = v.layoutParams.apply { height = top }
            v.requestLayout()
            insets
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(fragmentHeader) { v, insets ->
            v.setPadding(0, insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top, 0, 0)
            insets
        }
        com.nexus.launcher.ui.LandscapeSheets.installReadableWidth(hub)
        com.nexus.launcher.ui.LandscapeSheets.installReadableWidth(pages)
    }

    fun observeUiStyle(activity: SettingsActivity, settingsRepository: SettingsRepository) {
        UiStyleCoordinator.setListener(UiStyleCoordinator.LISTENER_SETTINGS) {
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                !activity.isFinishing
            ) {
                activity.recreate()
            }
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Reported from here as well as from the launcher, because MainActivity's
                // collectors are not guaranteed to be running while Settings is in front — and a
                // style picked here must take effect here.
                settingsRepository.settingsFlow
                    .map { it.uiStyleMode to it.frostedGlassEnabled }
                    .distinctUntilChanged()
                    .collect { (mode, legacyEnabled) ->
                        UiStyleCoordinator.apply(mode, legacyEnabled)
                    }
            }
        }
    }

    fun collectThemeAndDraft(
        activity: SettingsActivity,
        viewModel: SettingsViewModel,
        settingsRepository: SettingsRepository,
        applyBar: SettingsApplyBarBinder,
        jumpButton: ImageView,
        pageIconView: ImageView,
        pageTitleView: TextView,
        frostBackdropView: SettingsFrostBackdropView?,
        fragmentContainer: LinearLayout,
        categoryContainer: LinearLayout,
        onTokens: (NexusColorTokens) -> Unit,
    ) {
        observeUiStyle(activity, settingsRepository)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.hasUnsaved.collect { applyBar.applyButton.isEnabled = it } }
                launch {
                    try {
                        val controller = EntryPointAccessors.fromApplication(
                            activity.applicationContext,
                            ThemeEntryPoint::class.java,
                        ).themeController()
                        controller.currentTokens.collect { tokens ->
                            onTokens(tokens)
                            val insetsController =
                                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                            val isLight = tokens.bg == NexusColorTokens.Light.bg ||
                                androidx.core.graphics.ColorUtils.calculateLuminance(tokens.bg) > 0.5
                            insetsController.isAppearanceLightStatusBars = isLight
                            insetsController.isAppearanceLightNavigationBars = isLight
                            applyChromeBackground(
                                activity, tokens, frostBackdropView,
                                fragmentContainer, categoryContainer,
                            )
                            val tint = ColorStateList.valueOf(tokens.textPrimary)
                            activity.findViewById<ImageView>(R.id.btn_back_arrow).imageTintList = tint
                            jumpButton.imageTintList = tint
                            pageIconView.imageTintList = tint
                            pageTitleView.setTextColor(tokens.textPrimary)
                            applyBar.updateColors(tokens)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun applyChromeBackground(
        activity: SettingsActivity,
        tokens: NexusColorTokens,
        frostBackdropView: SettingsFrostBackdropView?,
        fragmentContainer: LinearLayout,
        categoryContainer: LinearLayout,
    ) {
        // Do not replace the locale-switch snapshot backdrop while the cover is still up —
        // that solid token colour is exactly the luminance flash the snapshot exists to hide.
        if (!SettingsLocaleTransition.hasPending()) {
            activity.window.setBackgroundDrawable(ColorDrawable(tokens.bg))
        }
        val header = activity.findViewById<View>(R.id.fragment_header)
        val bar = activity.findViewById<View>(R.id.settings_apply_bar)
        val backdrop = frostBackdropView
        if (!FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            backdrop?.visibility = View.GONE
            fragmentContainer.setBackgroundColor(tokens.bg)
            header.setBackgroundColor(tokens.bg)
            bar.setBackgroundColor(tokens.bg)
            categoryContainer.setBackgroundColor(tokens.bg)
            return
        }

        backdrop?.visibility = View.VISIBLE
        backdrop?.invalidateBackdrop()
        header.setBackgroundColor(Color.TRANSPARENT)
        bar.setBackgroundColor(Color.TRANSPARENT)
        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
        val fillAlpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.65f) * 255).toInt()
        val frostColor = (fillAlpha shl 24) or (frostedTokens.surface and 0x00FFFFFF)

        fragmentContainer.setBackgroundColor(frostColor)
        categoryContainer.setBackgroundColor(frostColor)
    }
}
