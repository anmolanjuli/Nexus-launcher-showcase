package com.nexus.launcher.feed

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.provider.Settings
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.data.prefs.SettingsRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.view.View
import com.nexus.launcher.data.prefs.NexusSettingsKeys
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumGate
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Coordinates E-Ink Paper Mode for Nexus Feed.
 *
 * Provides paper palette tokens (Light Paper / Dark Paper), root grayscale hardware layer
 * management, persistence synchronization with both DataStore preferences and fast-access
 * SharedPreferences, motion reduction detection, and paywall access checks.
 */
object NexusFeedEInkCoordinator {

    private val mirrorScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    const val KEY_EINK_MODE = "feed_eink_mode"
    const val KEY_EINK_DARK = "feed_eink_dark"

    // Light Paper Palette
    const val COLOR_LIGHT_BG = 0xFFFAF9F6.toInt()
    const val COLOR_LIGHT_TEXT_PRIMARY = 0xFF2A2A2A.toInt()
    const val COLOR_LIGHT_TEXT_SECONDARY = 0xFF7A7A7A.toInt()
    const val COLOR_LIGHT_DIVIDER = 0xFFE0DDD8.toInt()
    const val COLOR_LIGHT_SURFACE_RAISED = 0xFFF2F0EB.toInt()
    const val COLOR_LIGHT_IMAGE_BORDER = 0x1A2A2A2A // 10% opacity ink border

    // Dark Paper Palette
    const val COLOR_DARK_BG = 0xFF1C1C1C.toInt()
    const val COLOR_DARK_TEXT_PRIMARY = 0xFFE8E5DF.toInt()
    const val COLOR_DARK_TEXT_SECONDARY = 0xFF8A8782.toInt()
    const val COLOR_DARK_DIVIDER = 0xFF2A2A2A.toInt()
    const val COLOR_DARK_SURFACE_RAISED = 0xFF262626.toInt()
    const val COLOR_DARK_IMAGE_BORDER = 0x1AE8E5DF // 10% opacity cream border

    val LightPaperTokens = NexusColorTokens(
        bg = COLOR_LIGHT_BG,
        surface = COLOR_LIGHT_BG,
        surfaceRaised = COLOR_LIGHT_SURFACE_RAISED,
        divider = COLOR_LIGHT_DIVIDER,
        textPrimary = COLOR_LIGHT_TEXT_PRIMARY,
        textSecondary = COLOR_LIGHT_TEXT_SECONDARY,
        accent = COLOR_LIGHT_TEXT_PRIMARY,
        accentMuted = 0x202A2A2A,
        danger = COLOR_LIGHT_TEXT_PRIMARY,
        bgTop = null,
        bgBottom = null
    )

    val DarkPaperTokens = NexusColorTokens(
        bg = COLOR_DARK_BG,
        surface = COLOR_DARK_BG,
        surfaceRaised = COLOR_DARK_SURFACE_RAISED,
        divider = COLOR_DARK_DIVIDER,
        textPrimary = COLOR_DARK_TEXT_PRIMARY,
        textSecondary = COLOR_DARK_TEXT_SECONDARY,
        accent = COLOR_DARK_TEXT_PRIMARY,
        accentMuted = 0x20E8E5DF,
        danger = COLOR_DARK_TEXT_PRIMARY,
        bgTop = null,
        bgBottom = null
    )

    /**
     * Grey, and firmer than grey usually is.
     *
     * Desaturating a photograph on paper leaves it muddy — the mid-tones collapse together and the
     * picture reads as a smudge. E-ink hardware has less contrast to give than a screen, not more,
     * so the image has to arrive with its own: saturation out, then the remaining range stretched
     * about a quarter around mid-grey, which is what every e-ink reader does to photographs.
     */
    private val grayscaleMatrix = ColorMatrix().apply {
        setSaturation(0f)
        val contrast = 1.24f
        val lift = (-(contrast - 1f) / 2f) * 255f
        postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, lift,
            0f, contrast, 0f, 0f, lift,
            0f, 0f, contrast, 0f, lift,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
    val grayscaleColorFilter = ColorMatrixColorFilter(grayscaleMatrix)

    fun isEInkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NexusFeedTimeRefreshHelper.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EINK_MODE, false)
    }

    fun isEInkDark(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NexusFeedTimeRefreshHelper.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EINK_DARK, false)
    }

    /**
     * Attempts to enable or disable E-Ink Paper Mode. Gated by [PremiumGate].
     * Returns true if the operation succeeded, or false if blocked by paywall.
     */
    fun setEInkMode(context: Context, enabled: Boolean): Boolean {
        if (enabled && !PremiumGate.allow(context, PremiumFeature.EINK_FEED)) {
            return false
        }
        val prefs = context.getSharedPreferences(NexusFeedTimeRefreshHelper.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EINK_MODE, enabled).apply()
        mirrorToSettings(context) { it.updateFeedEInkMode(enabled) }
        return true
    }

    fun setEInkDark(context: Context, isDark: Boolean): Boolean {
        if (!PremiumGate.allow(context, PremiumFeature.EINK_FEED)) {
            return false
        }
        val prefs = context.getSharedPreferences(NexusFeedTimeRefreshHelper.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EINK_DARK, isDark).apply()
        mirrorToSettings(context) { it.updateFeedEInkDark(isDark) }
        return true
    }

    /**
     * E-Ink is read on the draw path — from a view being themed, in the middle of a frame — so the
     * live value lives in SharedPreferences, which answers without a coroutine. The settings
     * DataStore holds the same value because that is what a backup captures and restores.
     *
     * Both are written here, together, so they cannot drift. The DataStore copy used to be written
     * by nobody at all: it existed in the settings model and in every backup, always at its
     * default, so restoring a backup silently turned E-Ink off.
     */
    private fun mirrorToSettings(context: Context, write: suspend (SettingsRepository) -> Unit) {
        val repository = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext, com.nexus.launcher.ui.backup.BackupExportEntryPoint::class.java
            ).settingsRepository()
        } catch (_: Exception) {
            return
        }
        mirrorScope.launch {
            try {
                write(repository)
            } catch (_: Exception) {
                // The live value is already saved; the backup copy catches up on the next change.
            }
        }
    }

    /**
     * Takes restored settings as the truth and updates the fast copy — the other direction of
     * [mirrorToSettings].
     *
     * Only a restore may call this. Running it on every settings emission was a way to turn E-Ink
     * off: the DataStore copy had never been written by anything, so it read as its default and
     * overwrote the live value the moment the launcher looked at its settings.
     */
    fun restoreFastCopy(context: Context, enabled: Boolean, isDark: Boolean) {
        context.getSharedPreferences(NexusFeedTimeRefreshHelper.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EINK_MODE, enabled)
            .putBoolean(KEY_EINK_DARK, isDark)
            .apply()
    }

    fun syncFromSettings(context: Context, settings: NexusSettingsData) {
        val prefs = context.getSharedPreferences(NexusFeedTimeRefreshHelper.PREFS_NAME, Context.MODE_PRIVATE)
        val liveMode = prefs.getBoolean(KEY_EINK_MODE, false)
        val liveDark = prefs.getBoolean(KEY_EINK_DARK, false)
        if (liveMode == settings.feedEInkMode && liveDark == settings.feedEInkDark) return
        prefs.edit()
            .putBoolean(KEY_EINK_MODE, settings.feedEInkMode)
            .putBoolean(KEY_EINK_DARK, settings.feedEInkDark)
            .apply()
    }

    fun getTokens(context: Context): NexusColorTokens {
        return if (isEInkDark(context)) DarkPaperTokens else LightPaperTokens
    }

    /**
     * Drops any layer previously set on [view]'s root.
     *
     * E-Ink used to desaturate the whole screen through a full-screen hardware layer: an offscreen
     * buffer the size of the display, re-rendered in full whenever anything inside it changed, which
     * is every frame of a scroll. Colour now comes out where it enters instead — paper-palette
     * tokens for everything drawn from theme colours, [NexusFeedEInkStyler.applyImageGrayscale] on
     * photographs, a CSS grayscale filter inside EPUB chapters — so no layer is needed at all.
     */
    fun clearRootHardwareLayer(view: View) {
        if (view.layerType != View.LAYER_TYPE_NONE) view.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    /**
     * Checks whether animations should be suppressed either due to E-Ink mode
     * or the system "Reduce Motion" accessibility settings.
     */
    fun shouldReduceMotion(context: Context): Boolean {
        if (isEInkMode(context)) return true
        return isSystemReduceMotionEnabled(context)
    }

    fun isSystemReduceMotionEnabled(context: Context): Boolean {
        return try {
            val resolver = context.contentResolver
            val duration = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            duration == 0f || transition == 0f || !ValueAnimator.areAnimatorsEnabled()
        } catch (_: Exception) {
            false
        }
    }
}
