package com.nexus.launcher.ui.widgets.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.widgets.WidgetSize

class NexusMusicRenderer : NexusWidgetRenderer() {

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        if (config.clockStyle > 0) {
            RetroMusicRenderer.draw(context, canvas, width, height, size, config)
            return
        }

        val dp = context.resources.displayMetrics.density
        val controller = NexusMusicManager.getActiveController()
        val meta = controller?.metadata
        val state = controller?.playbackState
        val hasAccess = NexusMusicManager.hasNotificationAccess(context)

        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val isGlass = NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val isNeumorphic = NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)
        val palette = NexusNeumorphicDraw.resolvePalette(tokens)
        val isLight = palette.isLight
        val highContrastSecondary = if (isLight) Color.parseColor("#222A35") else if (isGlass) Color.argb(225, 245, 245, 245) else palette.textSecondary

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = (15f * dp).toInt().toFloat()
            typeface = getTypeface(context, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
            applyTextLegibility(this, dp, isGlass, isLight)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highContrastSecondary
            textSize = (12f * dp).toInt().toFloat()
            typeface = getTypeface(context, android.graphics.Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
            applyTextLegibility(this, dp, isGlass, isLight)
        }

        if (!hasAccess) {
            canvas.drawText(context.getString(com.nexus.launcher.R.string.music_enable_notification_access), (width / 2f).toInt().toFloat(), (height / 2f).toInt().toFloat(), textPaint)
            canvas.drawText(context.getString(com.nexus.launcher.R.string.music_for_widget_desc), (width / 2f).toInt().toFloat(), (height / 2f + 20f * dp).toInt().toFloat(), subPaint)
            return
        }

        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: context.getString(com.nexus.launcher.R.string.music_not_playing)
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val art = RetroMusicArtDecoder.getScaledAlbumArt(context, meta, (120f * dp).toInt().coerceAtLeast(96))
        val isPlaying = NexusMusicManager.isPlaybackPlaying()

        val durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val elapsed = if (isPlaying && state?.lastPositionUpdateTime ?: 0L > 0L) {
            (android.os.SystemClock.elapsedRealtime() - state!!.lastPositionUpdateTime).coerceAtLeast(0L)
        } else 0L
        val currentMs = if (state != null) (state.position + (elapsed * state.playbackSpeed).toLong()).coerceIn(0L, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE) else 0L
        val progress = if (durationMs > 0) (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0.35f
        val posStr = formatTime(currentMs)
        val durStr = if (durationMs > 0) formatTime(durationMs) else "--:--"

        val wDp = width / dp
        val hDp = height / dp
        val tier = RetroMusicTier.resolve(wDp, hDp)

        when {
            wDp < 115f && hDp < 115f -> drawAbsoluteSmall(canvas, width, height, dp, isPlaying, palette, isNeumorphic)
            wDp < 135f && hDp >= 135f -> drawVerticalTall(context, canvas, width, height, dp, title, isPlaying, palette, isNeumorphic, isGlass)
            isExpandedLayout(wDp, hDp) -> {
                NexusMusicDrawExpanded.drawExpanded(
                    context, canvas, width, height, dp, title, artist, art,
                    isPlaying, progress, posStr, durStr, palette, highContrastSecondary,
                    isNeumorphic, isGlass
                )
            }
            wDp >= 140f && hDp < 88f -> drawHorizontalCompact(context, canvas, width, height, dp, config, title, artist, art, isPlaying, palette, highContrastSecondary, isNeumorphic, isGlass)
            hDp >= 140f -> drawStandardStacked(context, canvas, width, height, dp, title, artist, art, isPlaying, palette, highContrastSecondary, isNeumorphic, isGlass)
            else -> drawCompactStacked(context, canvas, width, height, dp, title, isPlaying, palette, isNeumorphic, isGlass)
        }
    }

    private fun applyTextLegibility(paint: Paint, dp: Float, isGlass: Boolean, isLight: Boolean) {
        if (isGlass) {
            val shadowColor = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(120, 0, 0, 0)
            paint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor)
        } else {
            paint.clearShadowLayer()
        }
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0L) return "0:00"
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(java.util.Locale.US, "%d:%02d", min, sec)
    }

    private fun drawAbsoluteSmall(canvas: Canvas, w: Int, h: Int, dp: Float, isPlaying: Boolean, palette: NexusNeumorphicDraw.SoftPalette, isNeumorphic: Boolean) {
        val cx = (w / 2f).toInt().toFloat()
        val cy = (h / 2f).toInt().toFloat()
        val radius = (minOf(32f * dp, minOf(w, h) * 0.38f)).toInt().toFloat()
        NexusMusicDrawIcons.drawRoundControl(canvas, cx, cy, radius, dp, palette, isNeumorphic)
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.textPrimary }
        if (isPlaying) NexusMusicDrawIcons.drawPauseIcon(canvas, cx, cy, radius * 0.62f, iconPaint)
        else NexusMusicDrawIcons.drawPlayIcon(canvas, cx, cy, radius * 0.62f, iconPaint)
    }

    private fun drawVerticalTall(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        title: String, isPlaying: Boolean, palette: NexusNeumorphicDraw.SoftPalette,
        isNeumorphic: Boolean, isGlass: Boolean
    ) {
        val cx = (w / 2f).toInt().toFloat()
        val centerR = (minOf(26f * dp, w * 0.34f)).toInt().toFloat()
        val sideR = (minOf(20f * dp, w * 0.28f)).toInt().toFloat()

        if (h >= 190f * dp) {
            val artSize = (minOf(70f * dp, w - 24f * dp)).toInt().toFloat()
            val artTop = (14f * dp).toInt().toFloat()
            val artLeft = ((w - artSize) / 2f).toInt().toFloat()
            val meta = NexusMusicManager.getActiveController()?.metadata
            val art = RetroMusicArtDecoder.getScaledAlbumArt(context, meta, artSize.toInt().coerceAtLeast(64))
            NexusMusicDrawIcons.drawArtwork(canvas, art, artLeft, artTop, artSize, (12f * dp).toInt().toFloat(), palette)

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = (13f * dp).toInt().toFloat()
                typeface = getTypeface(context, android.graphics.Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isSubpixelText = true
                applyTextLegibility(this, dp, isGlass, palette.isLight)
            }
            val titleY = (artTop + artSize + 16f * dp).toInt().toFloat()
            val titleEllipsized = TextUtils.ellipsize(title, textPaint, w - 16f * dp, TextUtils.TruncateAt.END).toString()
            canvas.drawText(titleEllipsized, cx, titleY, textPaint)

            val cyPrev = (h * 0.32f).toInt().toFloat()
            val cyPlay = (h * 0.56f).toInt().toFloat()
            val cyNext = (h * 0.80f).toInt().toFloat()
            NexusMusicDrawIcons.drawVerticalTriad(canvas, cx, cyPrev, cyPlay, cyNext, sideR, centerR, isPlaying, dp, palette, isNeumorphic)
        } else {
            val cyPrev = (h * 0.22f).toInt().toFloat()
            val cyPlay = (h * 0.50f).toInt().toFloat()
            val cyNext = (h * 0.78f).toInt().toFloat()
            NexusMusicDrawIcons.drawVerticalTriad(canvas, cx, cyPrev, cyPlay, cyNext, sideR, centerR, isPlaying, dp, palette, isNeumorphic)
        }
    }

    private fun drawStandardStacked(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        title: String, artist: String, art: Bitmap?, isPlaying: Boolean,
        palette: NexusNeumorphicDraw.SoftPalette, highContrastSecondary: Int,
        isNeumorphic: Boolean, isGlass: Boolean
    ) {
        val ctrlY = (h - 36f * dp).toInt().toFloat()
        val centerR = (28f * dp).toInt().toFloat()
        val sideR = (22f * dp).toInt().toFloat()

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = (17f * dp).toInt().toFloat()
            typeface = getTypeface(context, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
            applyTextLegibility(this, dp, isGlass, palette.isLight)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highContrastSecondary
            textSize = (13f * dp).toInt().toFloat()
            typeface = getTypeface(context, android.graphics.Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
            applyTextLegibility(this, dp, isGlass, palette.isLight)
        }

        val showArtist = h >= 165f * dp
        val titleY = (if (showArtist) ctrlY - 50f * dp else ctrlY - 40f * dp).toInt().toFloat()
        val titleEllipsized = TextUtils.ellipsize(title, textPaint, w - 32f * dp, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, (w / 2f).toInt().toFloat(), titleY, textPaint)

        if (showArtist) {
            val artistEllipsized = TextUtils.ellipsize(artist, subPaint, w - 40f * dp, TextUtils.TruncateAt.END).toString()
            canvas.drawText(artistEllipsized, (w / 2f).toInt().toFloat(), (titleY + 18f * dp).toInt().toFloat(), subPaint)
        }

        val artBottom = titleY - 18f * dp
        val artSize = (minOf(artBottom - 12f * dp, w - 60f * dp).coerceAtLeast(10f)).toInt().toFloat()
        if (artSize >= 28f * dp) {
            val artLeft = ((w - artSize) / 2f).toInt().toFloat()
            val artTop = (10f * dp).toInt().toFloat()
            NexusMusicDrawIcons.drawArtwork(canvas, art, artLeft, artTop, artSize, (14f * dp).toInt().toFloat(), palette)
        }

        drawTransportButtons(canvas, w, ctrlY, sideR, centerR, isPlaying, dp, palette, isNeumorphic)
    }

    private fun drawHorizontalCompact(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        config: NexusWidgetConfig.InstanceConfig, title: String, artist: String,
        art: Bitmap?, isPlaying: Boolean, palette: NexusNeumorphicDraw.SoftPalette,
        highContrastSecondary: Int, isNeumorphic: Boolean, isGlass: Boolean
    ) {
        val ctrlY = (h - 28f * dp).toInt().toFloat()
        val centerR = (24f * dp).toInt().toFloat()
        val sideR = (19f * dp).toInt().toFloat()

        val insets = com.nexus.launcher.ui.widgets.NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val artSize = ((ctrlY - 32f * dp).coerceIn(24f * dp, 44f * dp)).toInt().toFloat()
        val artLeft = (insets.left).toInt().toFloat()
        val artTop = (10f * dp).toInt().toFloat()
        NexusMusicDrawIcons.drawArtwork(canvas, art, artLeft, artTop, artSize, (10f * dp).toInt().toFloat(), palette)

        val rightAreaLeft = (artLeft + artSize + 12f * dp).toInt().toFloat()
        val rightAreaRight = (w - insets.right).toInt().toFloat()
        val rightW = (rightAreaRight - rightAreaLeft).coerceAtLeast(10f)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = (15f * dp).toInt().toFloat()
            typeface = getTypeface(context, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            isSubpixelText = true
            applyTextLegibility(this, dp, isGlass, palette.isLight)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highContrastSecondary
            textSize = (12f * dp).toInt().toFloat()
            typeface = getTypeface(context, android.graphics.Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            isSubpixelText = true
            applyTextLegibility(this, dp, isGlass, palette.isLight)
        }

        val titleEllipsized = TextUtils.ellipsize(title, textPaint, rightW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, rightAreaLeft, (artTop + 16f * dp).toInt().toFloat(), textPaint)
        if (artist.isNotEmpty() && artSize >= 34f * dp) {
            val artistEllipsized = TextUtils.ellipsize(artist, subPaint, rightW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(artistEllipsized, rightAreaLeft, (artTop + 32f * dp).toInt().toFloat(), subPaint)
        }

        drawTransportButtons(canvas, w, ctrlY, sideR, centerR, isPlaying, dp, palette, isNeumorphic)
    }

    private fun drawCompactStacked(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        title: String, isPlaying: Boolean, palette: NexusNeumorphicDraw.SoftPalette,
        isNeumorphic: Boolean, isGlass: Boolean
    ) {
        val maxRadiusH = h * 0.28f
        val centerR = minOf(25f * dp, maxRadiusH).toInt().toFloat()
        val sideR = (centerR * 0.80f).toInt().toFloat()

        val titleY = if (h < 90f * dp) (h * 0.30f).toInt().toFloat() else (26f * dp).toInt().toFloat()
        val titleSize = if (h < 80f * dp) 13f * dp else 15f * dp
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = titleSize.toInt().toFloat()
            typeface = getTypeface(context, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
            applyTextLegibility(this, dp, isGlass, palette.isLight)
        }
        val maxTitleW = (w - 24f * dp).coerceAtLeast(20f * dp)
        val titleEllipsized = TextUtils.ellipsize(title, textPaint, maxTitleW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, (w / 2f).toInt().toFloat(), titleY, textPaint)

        val ctrlY = if (h < 90f * dp) {
            (h - centerR - 8f * dp).toInt().toFloat()
        } else {
            (h - 30f * dp).toInt().toFloat()
        }

        drawTransportButtons(canvas, w, ctrlY, sideR, centerR, isPlaying, dp, palette, isNeumorphic)
    }

    private fun drawTransportButtons(
        canvas: Canvas, w: Int, ctrlY: Float, sideR: Float, centerR: Float,
        isPlaying: Boolean, dp: Float, palette: NexusNeumorphicDraw.SoftPalette,
        isNeumorphic: Boolean
    ) {
        val cxCenter = (w / 2f).toInt().toFloat()

        // Margin from widget card edge to ensure rounded corners never clip buttons
        val margin = (14f * dp).coerceAtLeast(10f * dp)
        val maxAllowedSpan = (w / 2f - margin).coerceAtLeast(16f * dp)

        // Baseline standard offset is 68dp with standard sideR ~20dp -> 88dp span from center
        val standardSpan = 88f * dp
        val scale = if (maxAllowedSpan < standardSpan) {
            (maxAllowedSpan / standardSpan).coerceIn(0.55f, 1.0f)
        } else {
            1.0f
        }

        val maxR = ((maxAllowedSpan - 6f * dp) / 2.6f).coerceAtLeast(8f * dp)
        val actualCenterR = minOf(centerR * scale, maxR * 1.25f).toInt().toFloat()
        val actualSideR = minOf(sideR * scale, maxR).toInt().toFloat()

        val minGap = (4f * dp).coerceAtMost(8f * dp)
        val minOffset = actualCenterR + actualSideR + minGap
        val maxOffset = (maxAllowedSpan - actualSideR).coerceAtLeast(minOffset)

        val targetOffset = 68f * dp * scale
        val offset = targetOffset.coerceIn(minOffset, maxOffset).toInt().toFloat()

        val cxPrev = cxCenter - offset
        val cxNext = cxCenter + offset
        NexusMusicDrawIcons.drawHorizontalTriad(
            canvas, cxPrev, cxCenter, cxNext, ctrlY.toInt().toFloat(),
            actualSideR, actualCenterR, isPlaying, dp, palette, isNeumorphic
        )
    }

    private fun isExpandedLayout(wDp: Float, hDp: Float): Boolean {
        val isTiny = wDp < 115f && hDp < 115f
        val isVerticalTall = !isTiny && wDp < 135f && hDp >= 135f
        return !isTiny && !isVerticalTall && ((wDp >= 140f && hDp >= 88f) || RetroMusicTier.resolve(wDp, hDp) == RetroMusicTier.TIER_4_EXPANDED)
    }

    override fun isRetroStyle(config: NexusWidgetConfig.InstanceConfig): Boolean =
        config.clockStyle > 0

    override fun shouldDrawOuterProgressRing(config: NexusWidgetConfig.InstanceConfig, w: Int, h: Int, dp: Float): Boolean {
        if (config.clockStyle > 0) return false
        val wDp = w / dp
        val hDp = h / dp
        return !isExpandedLayout(wDp, hDp)
    }
}
