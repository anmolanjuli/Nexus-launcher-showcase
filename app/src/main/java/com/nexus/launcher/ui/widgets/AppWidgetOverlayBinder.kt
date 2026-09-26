package com.nexus.launcher.ui.widgets

import kotlin.math.roundToInt

object AppWidgetOverlayBinder {
    fun bindFromOverlay(
        overlay: WidgetOverlayLayout,
        items: List<com.nexus.launcher.data.HomeScreenItem>,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetHost: android.appwidget.AppWidgetHost,
        columns: Int,
        rows: Int
    ) {
        android.util.Log.d("WidgetNudge", "bindWidgets called, repositioning views")

        if (overlay.viewWidth == 0f || overlay.height == 0) return

        val widgets = items.filter { it.itemType == 3 && it.appWidgetId != -1 }.sortedBy { it.zIndex }

        val hostViews = ArrayList<android.appwidget.AppWidgetHostView>()
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            if (child is android.appwidget.AppWidgetHostView) hostViews.add(child)
        }
        var matches = hostViews.size == widgets.size

        for (item in widgets) {
            overlay.liveItems[item.appWidgetId] = item
        }

        if (matches) {
            for (i in widgets.indices) {
                if (hostViews[i].appWidgetId != widgets[i].appWidgetId) {
                    matches = false
                    break
                }
            }
        }
        if (matches) {
            for (i in widgets.indices) {
                val item = widgets[i]
                val info = appWidgetManager.getAppWidgetInfo(item.appWidgetId)
                if (info?.provider?.packageName == overlay.context.packageName) {
                    NexusWidgetHostChrome.apply(hostViews[i], item.appWidgetId)
                }
                val override = overlay.positionOverrides.remove(item.appWidgetId)
                val overrideXF = override?.first ?: item.xFraction
                val overrideYF = override?.second ?: item.yFraction
                overlay.updateWidgetLayoutParams(item.appWidgetId, overrideXF, overrideYF, item.spanX, item.spanY)
                // updateWidgetLayoutParams records its input as an override; a rebind must not
                // leave one behind, or it outranks the item's real (e.g. per-shape) position on
                // every later bind and the widget never moves again.
                overlay.positionOverrides.remove(item.appWidgetId)
                val hostParams = hostViews[i].layoutParams as? android.widget.FrameLayout.LayoutParams
                if (hostParams != null) {
                    syncGlassBackdrop(overlay, appWidgetManager, item, hostViews[i], hostParams)
                }
            }
            return
        }

        overlay.removeAllViews()

        for (item in widgets) {
            val info = appWidgetManager.getAppWidgetInfo(item.appWidgetId) ?: continue

            val widgetView = appWidgetHost.createView(WidgetHostContext.themed(overlay.context), item.appWidgetId, info)
            widgetView.setAppWidget(item.appWidgetId, info)

            if (info.provider.packageName == overlay.context.packageName) {
                NexusWidgetHostChrome.apply(widgetView, item.appWidgetId)
            } else {
                NexusWidgetHostChrome.applyDefaultLauncherClip(widgetView)
            }

            if (widgetView is NexusWidgetView) {
                widgetView.onLongPressDetected = {
                    overlay.onWidgetLongPress?.invoke(item, widgetView)
                }
            } else {
                widgetView.setOnLongClickListener {
                    val currentItem = overlay.liveItems[item.appWidgetId] ?: return@setOnLongClickListener true
                    overlay.onWidgetLongPress?.invoke(currentItem, widgetView)
                    true
                }
            }

            val cv = overlay.canvasView
            val isLandscape = cv?.isLandscape ?: false
            val cols = cv?.currentGridCols ?: columns
            val rows = cv?.currentGridRows ?: rows
            val gridW = cv?.gridAreaWidth?.toFloat() ?: overlay.viewWidth
            val gridH = cv?.let { (it.viewHeight - it.topInset - it.dockBottomReserve).toFloat().coerceAtLeast(0f) } ?: overlay.gridAreaHeight
            val metrics = com.nexus.launcher.ui.canvas.GridMetrics.compute(
                availableWidthPx = gridW,
                availableHeightPx = gridH,
                columns = cols,
                rows = rows,
                paddingLeftRightDp = cv?.homePaddingLeftRightDp ?: 0f,
                paddingTopBottomDp = cv?.homePaddingTopBottomDp ?: 0f,
                gapHorizontalDp = cv?.homeGapHorizontalDp ?: 0f,
                gapVerticalDp = cv?.homeGapVerticalDp ?: 0f,
                density = overlay.context.resources.displayMetrics.density
            )
            val cellWidth = metrics.cellWidthPx
            val cellHeight = metrics.cellHeightPx

            val (width, heightPx) = WidgetFreeSize.pixelSize(
                item.folderConfigJson, item.spanX, item.spanY,
                overlay.viewWidth, overlay.height.toFloat(), cellWidth, cellHeight, isLandscape,
                page = item.page,
            )
            val override = overlay.positionOverrides.remove(item.appWidgetId)
            val overrideXF = override?.first ?: item.xFraction
            val overrideYF = override?.second ?: item.yFraction

            val pageW = overlay.singlePageWidth
            val maxWidgetWidth = pageW.toInt()
            val dockReserve = (cv?.dockBottomReserve ?: (140f * overlay.resources.displayMetrics.density).toInt())
            val maxWidgetHeight = (overlay.height - dockReserve).coerceAtLeast(1)
            val clampedWidth = width.coerceIn(1, maxWidgetWidth)
            val clampedHeight = heightPx.coerceIn(1, maxWidgetHeight)

            val cx = WidgetCoordinateSpace.centerXFromFraction(overrideXF, pageW)
            val cy = overrideYF * overlay.height.toFloat()
            if (override != null) overlay.positionOverrides.remove(item.appWidgetId)

            val density = overlay.context.resources.displayMetrics.density
            val dpWidth = (clampedWidth / density).toInt()
            val dpHeight = (clampedHeight / density).toInt()

            val options = android.os.Bundle().apply {
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, dpWidth)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, dpHeight)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, dpWidth)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, dpHeight)
            }
            appWidgetManager.updateAppWidgetOptions(item.appWidgetId, options)

            val absoluteCenterX = (item.page * pageW) + cx
            val absoluteCenterY = cy

            val params = android.widget.FrameLayout.LayoutParams(
                clampedWidth, clampedHeight, android.view.Gravity.TOP or android.view.Gravity.LEFT)
            params.leftMargin = (absoluteCenterX - (clampedWidth / 2f))
                .toInt()
            params.topMargin = (absoluteCenterY - (clampedHeight / 2f))
                .toInt()

            val pixelWidth = clampedWidth
            val pixelHeight = clampedHeight

            // Clamp so widget stays within its own page bounds
            val pageLeft = (item.page * pageW).toInt()
            val pageRight = (pageLeft + pageW).toInt()
            val minLeft = pageLeft
            val maxLeft = (pageRight - pixelWidth)
                .coerceAtLeast(pageLeft)
            params.leftMargin = params.leftMargin
                .coerceIn(minLeft, maxLeft)

            // Clamp vertical: stay above dock (same bottom as icon home grid), below the top
            // reserve. The shared rule, so a widget and a box cannot disagree about where the
            // workspace starts.
            params.topMargin = com.nexus.launcher.ui.widgets.WidgetCoordinateSpace.clampTop(
                params.topMargin, pixelHeight, overlay.canvasView, overlay.height, dockReserve,
            )

            overlay.addView(widgetView, params)
            syncGlassBackdrop(overlay, appWidgetManager, item, widgetView, params)

            val pad = if (!item.paddingEnabled) {
                0
            } else if (android.os.Build.VERSION.SDK_INT >= 31) {
                (8f * overlay.resources.displayMetrics.density).toInt()
            } else {
                0
            }
            widgetView.setPadding(pad, pad, pad, pad)
            // The blur backdrop sibling must be inset the SAME amount, or its clip stays at the
            // full (unpadded) bounds while the host's own content shrinks inward — leaving an
            // untinted ring of raw, unclipped blur in the padding gap (reported as a stray "glass
            // shadow" specifically when Padding is on). See WidgetGlassLiveBackdropView.applyShape.
            for (i in 0 until overlay.childCount) {
                val child = overlay.getChildAt(i)
                if (child is WidgetGlassLiveBackdropView && child.appWidgetId == item.appWidgetId) {
                    child.setPadding(pad, pad, pad, pad)
                    child.invalidateOutline()
                    child.invalidateBackdrop()
                    break
                }
            }
        }
        overlay.resyncScroll()
    }

    /**
     * Keeps a live [WidgetGlassLiveBackdropView] behind [hostView] for a first-party Glass-mode
     * widget, sized/positioned to match it exactly (creating or removing the sibling as the
     * widget's mode changes). See [WidgetGlassLiveBackdropView] for why the wallpaper blur can't
     * be baked into the widget's own static RemoteViews bitmap.
     */
    private fun syncGlassBackdrop(
        overlay: WidgetOverlayLayout,
        appWidgetManager: android.appwidget.AppWidgetManager,
        item: com.nexus.launcher.data.HomeScreenItem,
        hostView: android.view.View,
        hostParams: android.widget.FrameLayout.LayoutParams
    ) {
        val appWidgetId = item.appWidgetId
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        val isFirstParty = info?.provider?.packageName == overlay.context.packageName
        val config = NexusWidgetConfig.read(overlay.context, appWidgetId)
        // Opacity at zero means no glass at all — the blur goes too, as it does on a Mosaic tile
        // and a folder. The widget used to keep its blur backdrop at any opacity, so it could
        // never get below "frosted", while a Mosaic beside it went fully clear.
        val isRetro = NexusWidgetConfig.isRetroStyle(info?.provider?.className, config.clockStyle)
        val retroSurface = if (isRetro && info?.provider?.className?.endsWith("NexusMusicWidgetProvider") == true && config.clockStyle > 0) {
            com.nexus.launcher.ui.widgets.music.RetroMusicConfig.read(overlay.context, appWidgetId).resolveEffectiveSurface()
        } else {
            com.nexus.launcher.ui.widgets.music.RetroMusicConfig.SurfaceMode.DEFAULT
        }
        val wantsGlass = isFirstParty && (
            (!isRetro && NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)) ||
            (isRetro && retroSurface == com.nexus.launcher.ui.widgets.music.RetroMusicConfig.SurfaceMode.FROSTED)
        ) && config.backgroundOpacity > 0.01f

        var backdrop: WidgetGlassLiveBackdropView? = null
        for (i in 0 until overlay.childCount) {
            val c = overlay.getChildAt(i)
            if (c is WidgetGlassLiveBackdropView && c.appWidgetId == appWidgetId) {
                backdrop = c
                break
            }
        }

        if (!wantsGlass) {
            backdrop?.let { overlay.removeView(it) }
            return
        }

        val dp = overlay.resources.displayMetrics.density

        if (backdrop == null) {
            val fresh = WidgetGlassLiveBackdropView(overlay.context).apply { this.appWidgetId = appWidgetId }
            val hostIndex = overlay.indexOfChild(hostView).coerceAtLeast(0)
            val lp = android.widget.FrameLayout.LayoutParams(hostParams)
            overlay.addView(fresh, hostIndex, lp)
            fresh.applyShape(config.shapeStyle, config.cornerRadius * dp)
            fresh.setRefraction(config.glassRefraction, config.backgroundOpacity)
        } else {
            val lp = backdrop.layoutParams as? android.widget.FrameLayout.LayoutParams
                ?: android.widget.FrameLayout.LayoutParams(hostParams)
            lp.width = hostParams.width
            lp.height = hostParams.height
            lp.leftMargin = hostParams.leftMargin
            lp.topMargin = hostParams.topMargin
            backdrop.layoutParams = lp
            backdrop.applyShape(config.shapeStyle, config.cornerRadius * dp)
            backdrop.setRefraction(config.glassRefraction, config.backgroundOpacity)
        }
    }
}
