package com.nexus.launcher.ui.widgets.music

import android.content.Context
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.SystemClock
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.WidgetSize
import java.util.Locale

/**
 * Master orchestrator for Retro Music Player Widget styles (1..4).
 * Handles MediaSession extraction, time formatting, size classification,
 * and delegates to the appropriate retro style drawer.
 */
object RetroMusicRenderer {

    fun draw(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val dp = context.resources.displayMetrics.density
        val wDp = w / dp
        val hDp = h / dp

        val controller = NexusMusicManager.getActiveController()
        val meta = controller?.metadata
        val state = controller?.playbackState

        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: context.getString(R.string.music_not_playing)
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val art = RetroMusicArtDecoder.getScaledAlbumArt(context, meta, (120f * dp).toInt().coerceAtLeast(96))
        val isPlaying = NexusMusicManager.isPlaybackPlaying()

        val durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val elapsed = if (isPlaying && (state?.lastPositionUpdateTime ?: 0L) > 0L) {
            (SystemClock.elapsedRealtime() - state!!.lastPositionUpdateTime).coerceAtLeast(0L)
        } else 0L
        val currentMs = if (state != null) {
            (state.position + (elapsed * state.playbackSpeed).toLong()).coerceIn(0L, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        } else 0L

        val progress = if (durationMs > 0) (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0.35f
        val posStr = formatTime(currentMs)
        val durStr = if (durationMs > 0) formatTime(durationMs) else "--:--"

        val cfg = RetroMusicConfig.read(context, config.appWidgetId)

        val tier = RetroMusicTier.resolve(wDp, hDp)
        val isTiny = tier == RetroMusicTier.TIER_1_TINY
        val isCompact = tier == RetroMusicTier.TIER_2_COMPACT
        val isMedium = tier == RetroMusicTier.TIER_3_COMFORTABLE
        val isLarge = tier == RetroMusicTier.TIER_4_EXPANDED

        val effectiveSurface = cfg.resolveEffectiveSurface()
        val neuPalette = if (effectiveSurface != RetroMusicConfig.SurfaceMode.DEFAULT) {
            com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.resolvePalette(context, config.themeMode)
        } else null

        when (config.clockStyle) {
            RetroMusicConfig.STYLE_WINAMP -> {
                val colors = RetroMusicPalette.resolveWinamp()
                RetroMusicDrawWinamp.draw(
                    canvas, w, h, dp, title, artist, art, isPlaying,
                    progress, posStr, durStr, cfg, colors,
                    isTiny, isCompact, isMedium, isLarge,
                    effectiveSurface, neuPalette, config.appWidgetId
                )
            }
            RetroMusicConfig.STYLE_AMPLIFIER -> {
                val colors = RetroMusicPalette.resolveAmplifier()
                RetroMusicDrawAmplifier.draw(
                    canvas, w, h, dp, title, artist, art, isPlaying,
                    progress, posStr, durStr, cfg, colors,
                    isTiny, isCompact, isMedium, isLarge,
                    effectiveSurface, neuPalette, config.appWidgetId
                )
            }
            RetroMusicConfig.STYLE_TERMINAL -> {
                val colors = RetroMusicPalette.resolveTerminal()
                RetroMusicDrawTerminal.draw(
                    canvas, w, h, dp, title, artist, art, isPlaying,
                    progress, posStr, durStr, cfg, colors,
                    isTiny, isCompact, isMedium, isLarge,
                    effectiveSurface, neuPalette, config.appWidgetId
                )
            }
            RetroMusicConfig.STYLE_CASSETTE -> {
                val colors = RetroMusicPalette.resolveCassette()
                RetroMusicDrawCassette.draw(
                    canvas, w, h, dp, title, artist, art, isPlaying,
                    progress, posStr, durStr, cfg, colors,
                    isTiny, isCompact, isMedium, isLarge,
                    effectiveSurface, neuPalette, config.appWidgetId
                )
            }
        }
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0L) return "0:00"
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%d:%02d", min, sec)
    }
}
