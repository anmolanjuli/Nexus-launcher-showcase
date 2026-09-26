package com.nexus.launcher.ui.island

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.provider.MediaStore
import android.text.TextPaint
import com.nexus.launcher.theme.NexusColorTokens

/**
 * The parts of the open card that belong to no single page: the "nothing is playing" panel and
 * the page dots.
 *
 * What was here before — an app dock and two widget slots — has gone. A dock belongs on the home
 * screen, and a capsule under the camera is the wrong size for a real widget; the island is for
 * what is happening now.
 */
object IslandCardDraw {

    private val mediaEmptyRect = RectF()
    private val mediaBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mediaStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val mediaTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val mediaSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pageDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Opens the music app when the empty media panel is tapped. */
    fun hitTestMedia(x: Float, y: Float, context: Context): Boolean {
        if (mediaEmptyRect.isEmpty || !mediaEmptyRect.contains(x, y)) return false
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        return true
    }

    fun drawMediaEmpty(
        canvas: Canvas,
        pill: RectF,
        tokens: NexusColorTokens,
        density: Float,
        pad: Float,
        context: Context,
    ) {
        mediaEmptyRect.set(pill.left + pad, pill.top + pad, pill.right - pad, pill.bottom - pad - 16f * density)
        mediaBgPaint.color = tokens.surfaceRaised
        mediaStrokePaint.color = tokens.divider
        mediaStrokePaint.strokeWidth = 1f * density
        val corner = 16f * density
        canvas.drawRoundRect(mediaEmptyRect, corner, corner, mediaBgPaint)
        canvas.drawRoundRect(mediaEmptyRect, corner, corner, mediaStrokePaint)

        val cy = mediaEmptyRect.centerY()
        val cx = mediaEmptyRect.centerX()
        mediaTextPaint.textSize = 15f * density
        mediaTextPaint.color = tokens.textPrimary
        val title = context.getString(com.nexus.launcher.R.string.island_media_controller)
        val tw = mediaTextPaint.measureText(title)
        canvas.drawText(title, cx - tw / 2f, cy - 4f * density, mediaTextPaint)

        mediaSubPaint.textSize = 11f * density
        mediaSubPaint.color = tokens.textSecondary
        val subtitle = context.getString(com.nexus.launcher.R.string.island_media_empty_subtitle)
        val sw = mediaSubPaint.measureText(subtitle)
        canvas.drawText(subtitle, cx - sw / 2f, cy + 12f * density, mediaSubPaint)
    }

    fun drawPageIndicators(
        canvas: Canvas,
        pill: RectF,
        activePage: Int,
        totalPages: Int,
        tokens: NexusColorTokens,
        density: Float,
    ) {
        val dotRadius = 2.5f * density
        val spacing = 10f * density
        val totalW = (totalPages - 1) * spacing
        var dotX = pill.centerX() - totalW / 2f
        val dotY = pill.bottom - 7f * density

        for (i in 0 until totalPages) {
            val isActive = i == activePage
            pageDotPaint.color = if (isActive) tokens.accent else tokens.divider
            pageDotPaint.alpha = if (isActive) 255 else 110
            val r = if (isActive) dotRadius * 1.25f else dotRadius
            canvas.drawCircle(dotX, dotY, r, pageDotPaint)
            dotX += spacing
        }
    }
}
