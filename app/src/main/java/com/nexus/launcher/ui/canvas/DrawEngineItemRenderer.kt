package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.RectF
import com.nexus.launcher.ui.model.GridItem

/**
 * Renders a single drawer/dock/search grid item (icon + optional label + search subtitle).
 * Uses [DrawerIconDrawCache] and O(1) [LauncherCanvasView.folderById] — no per-frame
 * homeScreenItems scans, Paint allocation, or TextUtils.ellipsize.
 */
class DrawEngineItemRenderer(private val view: LauncherCanvasView) {

    private val tempBoundsRectF = RectF()

    fun drawItem(
        canvas: Canvas,
        item: GridItem,
        baseAlpha: Int,
        listLayout: Boolean = false,
        cache: DrawerIconCache = view.drawerIconCache,
        isDrawerContext: Boolean = false
    ) {
        val rect = item.drawRect
        val icon = item.icon

        var currentAlpha = baseAlpha
        if (view.activeLetter != null) {
            val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                view.context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                view.context.resources.configuration.locale
            }
            val appLetter = DrawerAlphabetHelper.getRailLetter(item.label, locale)
            if (appLetter != view.activeLetter) {
                currentAlpha = (baseAlpha * 0.3f).toInt()
            }
        }

        if (item.intent?.action == "nexus.folder.OPEN") {
            drawDrawerFolder(canvas, item, rect, currentAlpha, cache)
        } else if (icon != null) {
            val pkg = item.intent?.component?.packageName ?: item.label
            cache.drawIcon(canvas, icon, rect, pkg, currentAlpha)
        }

        val pkg = item.intent?.component?.packageName
        if (pkg != null && item.intent?.action != "nexus.folder.OPEN") {
            val count = view.badgeCounts[pkg] ?: 0
            if (count > 0 && view.badgeStyleApp != 0) {
                view.badgeRenderer.drawBadge(
                    canvas,
                    rect,
                    count,
                    view.badgeStyleApp,
                    currentAlpha
                )
            }
        }

        if (view.showDrawerLabels && listLayout) {
            drawListLabel(canvas, item, rect, currentAlpha, cache)
        } else if (view.showDrawerLabels) {
            drawGridLabel(canvas, item, rect, currentAlpha, cache)
        }
    }

    private fun drawDrawerFolder(
        canvas: Canvas,
        item: GridItem,
        rect: android.graphics.Rect,
        currentAlpha: Int,
        cache: DrawerIconCache
    ) {
        val folderId = item.intent?.getLongExtra("folderId", -1L) ?: -1L
        // O(1) — never scan homeScreenItems / FolderItemLookup on the draw path.
        val folderItem = if (folderId > 0L) view.folderById[folderId] else null
        val density = view.resources.displayMetrics.density
        if (folderItem != null) {
            val contents = view.homeScreenRenderer.folderContentsForItem(folderItem)
            val drewFromCache = view.drawerFolderTileCache.draw(
                canvas, folderItem, contents, rect, currentAlpha
            )
            if (!drewFromCache) {
                tempBoundsRectF.set(
                    rect.left.toFloat(), rect.top.toFloat(),
                    rect.right.toFloat(), rect.bottom.toFloat()
                )
                val radius = minOf(rect.width(), rect.height()) / 2f
                view.homeScreenRenderer.folderIconRenderer.drawFolder(
                    canvas = canvas,
                    cx = tempBoundsRectF.centerX(),
                    cy = tempBoundsRectF.centerY(),
                    folderItem = folderItem,
                    contents = contents,
                    iconCache = view.homeScreenRenderer.iconCache,
                    density = density,
                    folderRadiusOverride = radius,
                    folderBoundsOverride = tempBoundsRectF,
                    showLabels = false,
                    matchAppIconSize = true
                )
                view.drawerFolderTileCache.prewarm(
                    view.context, folderItem, contents,
                    view.homeScreenRenderer.iconCache, density,
                    rect.width(), rect.height()
                )
            }
            drawFolderBadge(canvas, rect, contents, currentAlpha)
        } else {
            drawFolderFallback(canvas, rect, density, currentAlpha, cache)
        }
    }

    private fun drawFolderBadge(
        canvas: Canvas,
        rect: android.graphics.Rect,
        contents: List<com.nexus.launcher.data.HomeScreenItem>,
        currentAlpha: Int
    ) {
        if (view.badgeStyleFolder == 0) return
        val count = contents.sumOf { view.badgeCounts[it.packageName] ?: 0 }
        if (count > 0) {
            view.badgeRenderer.drawBadge(
                canvas, rect, count, view.badgeStyleFolder, currentAlpha
            )
        }
    }

    private fun drawFolderFallback(
        canvas: Canvas,
        rect: android.graphics.Rect,
        density: Float,
        currentAlpha: Int,
        cache: DrawerIconCache
    ) {
        cache.folderFallbackPaint.alpha = currentAlpha
        canvas.drawCircle(
            rect.centerX().toFloat(), rect.centerY().toFloat(), 28 * density,
            cache.folderFallbackPaint
        )
    }

    private fun drawListLabel(
        canvas: Canvas,
        item: GridItem,
        rect: android.graphics.Rect,
        currentAlpha: Int,
        cache: DrawerIconCache
    ) {
        val density = view.resources.displayMetrics.density
        val isTwoCol = view.gridRenderer.layoutMode == "list_2"
        val textGap = if (isTwoCol) 10f * density else 16f * density
        val textX = rect.right + textGap
        val listTextSize = if (isTwoCol) 14.5f * view.resources.displayMetrics.scaledDensity else 16f * view.resources.displayMetrics.scaledDensity
        val oldTextSize = view.textPaint.textSize
        val oldAlign = view.textPaint.textAlign
        val oldColor = view.textPaint.color
        val oldBold = view.textPaint.isFakeBoldText

        view.textPaint.textSize = listTextSize
        view.textPaint.alpha = currentAlpha
        view.textPaint.textAlign = android.graphics.Paint.Align.LEFT
        // Drawer scrim is now theme-driven (surface in light, bg in dark) — labels must follow.
        view.textPaint.color = view.currentThemeTokens.textPrimary
        view.textPaint.isFakeBoldText = true

        val itemRight = item.hitRect.right - (if (isTwoCol) 8f * density else 24f * density)
        val maxWidth = (itemRight - textX).coerceAtLeast(0f)
        val fm = view.textPaint.fontMetrics
        val twoLine = view.drawerTwoLineLabels
        val wrapped = cache.wrappedLabel(
            item.label, view.textPaint, maxWidth, listTextSize, twoLine
        )
        val step = IconLabelText.lineStepPx(listTextSize)
        val firstBaseline = if (wrapped.line2 != null) {
            rect.exactCenterY() - step / 2f - fm.ascent
        } else {
            rect.exactCenterY() - (fm.ascent + fm.descent) / 2f
        }
        IconLabelText.drawStart(
            canvas, view.textPaint, textX, firstBaseline,
            wrapped.line1, wrapped.line2, step
        )

        view.textPaint.textSize = oldTextSize
        view.textPaint.textAlign = oldAlign
        view.textPaint.color = oldColor
        view.textPaint.isFakeBoldText = oldBold
    }

    private fun drawGridLabel(
        canvas: Canvas,
        item: GridItem,
        rect: android.graphics.Rect,
        currentAlpha: Int,
        cache: DrawerIconCache
    ) {
        val textX = rect.centerX().toFloat()
        val textY = rect.bottom.toFloat() + 30f
        view.textPaint.alpha = currentAlpha
        val cellWidth = rect.width() * 1.5f
        val twoLine = view.drawerTwoLineLabels
        val wrapped = cache.wrappedLabel(
            item.label, view.textPaint, cellWidth, view.textPaint.textSize, twoLine
        )
        val step = IconLabelText.lineStepPx(view.textPaint.textSize)

        val oldColor2 = view.textPaint.color
        view.textPaint.color = view.currentThemeTokens.textPrimary
        val oldFakeBold = view.textPaint.isFakeBoldText
        view.textPaint.isFakeBoldText = true
        IconLabelText.drawCentered(
            canvas, view.textPaint, textX, textY,
            wrapped.line1, wrapped.line2, step
        )
        view.textPaint.isFakeBoldText = oldFakeBold
        view.textPaint.color = oldColor2

        if (view.isSearchMode && item.categoryName != null) {
            val oldSize = view.textPaint.textSize
            val oldColor = view.textPaint.color
            view.textPaint.textSize = 9f * view.resources.displayMetrics.density
            view.textPaint.color = cache.subtitleColor()

            val lastLabelY = if (wrapped.line2 != null) textY + step else textY
            val subY = lastLabelY + (12f * view.resources.displayMetrics.density)
            val subLabel = cache.ellipsizedLabel(
                com.nexus.launcher.ui.DrawerSearchPillBuilder.categoryLabelText(view.context, item.categoryName),
                view.textPaint, cellWidth, view.textPaint.textSize
            )
            canvas.drawText(subLabel, textX, subY, view.textPaint)

            view.textPaint.textSize = oldSize
            view.textPaint.color = oldColor
        }
    }
}
