package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import androidx.annotation.DrawableRes
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.DrawerLayoutModes
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.DrawerCategories

/**
 * App Drawer settings live preview for Categories. Spatial, Strip, and List are
 * distinct thumbnails of the real overlay, using the same sample icons as Grid/List.
 */
object DrawerPreviewCategoriesDraw {

    fun draw(
        context: Context,
        canvas: Canvas,
        tokens: NexusColorTokens,
        density: Float,
        settings: PendingDrawerSettings,
        left: Float,
        top: Float,
        contentW: Float,
        bottomY: Float,
        showRail: Boolean,
        pillPaint: Paint,
        borderPaint: Paint,
        textPaint: TextPaint,
        railPaint: TextPaint,
        iconPaint: Paint,
        destRect: Rect,
        plate: RectF,
        isLight: Boolean,
    ) {
        val right = left + contentW
        when (settings.drawerCategoryLayout) {
            DrawerLayoutModes.STRIP -> strip(
                context, canvas, tokens, density, settings, left, top, right, bottomY,
                pillPaint, borderPaint, textPaint, iconPaint, destRect, plate, isLight,
            )
            DrawerLayoutModes.CAT_LIST -> list(
                context, canvas, tokens, density, settings, left, top, right, bottomY,
                showRail, textPaint, railPaint, iconPaint, destRect, isLight,
            )
            else -> spatial(
                context, canvas, tokens, density, settings, left, top, contentW, bottomY,
                pillPaint, borderPaint, textPaint, iconPaint, destRect, plate, isLight,
            )
        }
    }

    /**
     * Everything here is a fraction of the space the preview actually has. It used to use fixed
     * minimum sizes — a 96dp card, a 40dp tab strip — which in a short preview drew past the
     * bottom and over the row underneath.
     */
    private fun spatial(
        context: Context,
        canvas: Canvas,
        tokens: NexusColorTokens,
        density: Float,
        settings: PendingDrawerSettings,
        left: Float,
        top: Float,
        contentW: Float,
        bottomY: Float,
        pillPaint: Paint,
        borderPaint: Paint,
        textPaint: TextPaint,
        iconPaint: Paint,
        destRect: Rect,
        plate: RectF,
        isLight: Boolean,
    ) {
        val chrome = DrawerPreviewCategoriesChrome
        val areaH = (bottomY - top).coerceAtLeast(48f * density)
        val tabH = (areaH * 0.18f).coerceAtMost(30f * density)
        val cardH = areaH - tabH - 6f * density
        val cardW = contentW * 0.82f
        val centerX = left + contentW / 2f
        val cardLeft = centerX - cardW / 2f
        // The cards on either side, showing there are more categories to swipe to.
        val sideScale = 0.74f
        val sideY = top + cardH * (1f - sideScale) / 2f
        chrome.plate(
            canvas, plate, centerX - cardW * 0.52f, sideY,
            cardW * sideScale, cardH * sideScale, tokens, pillPaint, borderPaint, density, 16f,
        )
        chrome.plate(
            canvas, plate, centerX + cardW * 0.52f, sideY,
            cardW * sideScale, cardH * sideScale, tokens, pillPaint, borderPaint, density, 16f,
        )
        chrome.plate(canvas, plate, centerX, top, cardW, cardH, tokens, pillPaint, borderPaint, density, 16f)

        val pad = (cardH * 0.08f).coerceIn(5f * density, 10f * density)
        val badge = (cardH * 0.16f).coerceIn(12f * density, 20f * density)
        chrome.glyph(
            context, canvas, destRect, DrawerCategories.iconFor(context, "Social"),
            tokens.textPrimary, cardLeft + pad, top + pad, badge,
        )
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.isFakeBoldText = true
        textPaint.textSize = badge * 0.6f
        textPaint.color = tokens.textPrimary
        canvas.drawText(
            context.getString(R.string.category_social),
            cardLeft + pad + badge + 6f * density,
            top + pad + badge * 0.72f,
            textPaint,
        )
        textPaint.isFakeBoldText = false

        val iconTop = top + pad + badge + pad
        val innerW = cardW - pad * 2f
        val gridH = (top + cardH - pad) - iconTop
        val rows = if (gridH > 74f * density) 2 else 1
        val iconSize = minOf(innerW / 4f * 0.74f, gridH / rows * 0.62f)
        chrome.iconGrid(
            context, canvas, settings, tokens, isLight, 0, 4, rows,
            cardLeft + pad, iconTop, innerW, iconSize, 5f * density, iconPaint, destRect,
        )
        drawSpatialTabs(
            context, canvas, tokens, density, destRect,
            left, top + cardH + 5f * density, contentW, textPaint,
        )
    }

    private fun strip(
        context: Context,
        canvas: Canvas,
        tokens: NexusColorTokens,
        density: Float,
        settings: PendingDrawerSettings,
        left: Float,
        top: Float,
        right: Float,
        bottomY: Float,
        pillPaint: Paint,
        borderPaint: Paint,
        textPaint: TextPaint,
        iconPaint: Paint,
        destRect: Rect,
        plate: RectF,
        isLight: Boolean,
    ) {
        val width = right - left
        val cardH = 88f * density
        val gap = 10f * density
        drawStripCard(
            context, canvas, tokens, density, settings, left, top, width, cardH,
            context.getString(R.string.drawer_category_recent), R.drawable.ic_recent,
            showSeeAll = false, iconStart = 0,
            pillPaint, borderPaint, textPaint, iconPaint, destRect, plate, isLight,
        )
        val secondTop = top + cardH + gap
        if (secondTop + cardH <= bottomY) {
            drawStripCard(
                context, canvas, tokens, density, settings, left, secondTop, width, cardH,
                context.getString(R.string.category_social), DrawerCategories.iconFor(context, "Social"),
                showSeeAll = true, iconStart = 5,
                pillPaint, borderPaint, textPaint, iconPaint, destRect, plate, isLight,
            )
        }
    }

    private fun list(
        context: Context,
        canvas: Canvas,
        tokens: NexusColorTokens,
        density: Float,
        settings: PendingDrawerSettings,
        left: Float,
        top: Float,
        right: Float,
        bottomY: Float,
        showRail: Boolean,
        textPaint: TextPaint,
        railPaint: TextPaint,
        iconPaint: Paint,
        destRect: Rect,
        isLight: Boolean,
    ) {
        val chrome = DrawerPreviewCategoriesChrome
        val railW = if (showRail) 20f * density else 0f
        val width = right - left - railW
        val badge = 22f * density
        chrome.glyph(
            context, canvas, destRect, DrawerCategories.iconFor(context, "Social"),
            tokens.textPrimary, left, top, badge,
        )
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.isFakeBoldText = true
        textPaint.textSize = 13f * density
        textPaint.color = tokens.textPrimary
        canvas.drawText(
            context.getString(R.string.category_social),
            left + badge + 8f * density,
            top + badge * 0.55f,
            textPaint,
        )
        textPaint.isFakeBoldText = false
        textPaint.textSize = 10f * density
        textPaint.color = tokens.textSecondary
        canvas.drawText(
            context.resources.getQuantityString(R.plurals.drawer_category_app_count, 8, 8),
            left + badge + 8f * density,
            top + badge,
            textPaint,
        )
        val iconTop = top + badge + 12f * density
        val iconSize = (width / 4f * 0.70f).coerceAtMost(48f * density)
        val rows = if (iconTop + iconSize * 2f + 16f * density < bottomY) 2 else 1
        chrome.iconGrid(
            context, canvas, settings, tokens, isLight, 0, 4, rows,
            left, iconTop, width, iconSize, 10f * density, iconPaint, destRect,
        )
        if (showRail) {
            val railTop = top + (bottomY - top) / 2f
            chrome.rail(canvas, railPaint, tokens, density, right - 8f * density, railTop, bottomY)
        }
    }

    private fun drawStripCard(
        context: Context,
        canvas: Canvas,
        tokens: NexusColorTokens,
        density: Float,
        settings: PendingDrawerSettings,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        title: String,
        @DrawableRes badgeRes: Int,
        showSeeAll: Boolean,
        iconStart: Int,
        pillPaint: Paint,
        borderPaint: Paint,
        textPaint: TextPaint,
        iconPaint: Paint,
        destRect: Rect,
        plate: RectF,
        isLight: Boolean,
    ) {
        val chrome = DrawerPreviewCategoriesChrome
        chrome.plate(
            canvas, plate, left + width / 2f, top, width, height,
            tokens, pillPaint, borderPaint, density, 18f,
        )
        val pad = 12f * density
        val badge = 22f * density
        chrome.glyph(
            context, canvas, destRect, badgeRes, tokens.textPrimary,
            left + pad, top + pad, badge,
        )
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.isFakeBoldText = true
        textPaint.textSize = 12f * density
        textPaint.color = tokens.textPrimary
        canvas.drawText(title, left + pad + badge + 8f * density, top + pad + badge * 0.72f, textPaint)
        textPaint.isFakeBoldText = false
        if (showSeeAll) {
            val see = context.getString(R.string.drawer_category_see_all)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 10f * density
            textPaint.color = tokens.textSecondary
            val chevronW = 10f * density
            val chevronLeft = left + width - pad - chevronW
            canvas.drawText(see, chevronLeft - 4f * density, top + pad + badge * 0.72f, textPaint)
            chrome.chevron(
                canvas, borderPaint, tokens, density,
                chevronLeft, top + pad + badge * 0.55f,
            )
            textPaint.textAlign = Paint.Align.LEFT
        }
        val iconSize = 36f * density
        chrome.iconRow(
            context, canvas, settings, tokens, isLight, iconStart, 5,
            left + pad, top + pad + badge + 10f * density, width - pad * 2f, iconSize,
            iconPaint, destRect, density,
        )
    }

    private fun drawSpatialTabs(
        context: Context,
        canvas: Canvas,
        tokens: NexusColorTokens,
        density: Float,
        destRect: Rect,
        left: Float,
        top: Float,
        width: Float,
        textPaint: TextPaint,
    ) {
        val labels = arrayOf(
            Triple("Social", context.getString(R.string.category_social), true),
            Triple("Media", context.getString(R.string.category_media), false),
            Triple("Games", context.getString(R.string.category_games), false),
        )
        val cell = width / labels.size
        labels.forEachIndexed { i, (key, label, selected) ->
            val cx = left + cell * i + cell / 2f
            val size = if (selected) 22f * density else 16f * density
            DrawerPreviewCategoriesChrome.glyph(
                context, canvas, destRect, DrawerCategories.iconFor(context, key),
                if (selected) tokens.textPrimary else tokens.textSecondary,
                cx - size / 2f, top, size,
            )
            // Only the category on screen is named, as the real strip does.
            if (selected) {
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.isFakeBoldText = true
                textPaint.textSize = 9f * density
                textPaint.color = tokens.textPrimary
                canvas.drawText(label, cx, top + size + 9f * density, textPaint)
            }
        }
        textPaint.isFakeBoldText = false
    }
}
