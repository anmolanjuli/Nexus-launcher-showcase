package com.nexus.launcher.ui.contextmenu

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.nexus.launcher.R
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.MainViewModel
import com.nexus.launcher.ui.model.DisplayItem

class ContextMenuManager(
    private val activity: MainActivity,
    private val mainContainer: FrameLayout,
    private val viewModel: MainViewModel,
    private val homeScreenViewModel: com.nexus.launcher.ui.HomeScreenViewModel
) {
    private var currentOverlay: View? = null
    private var currentMenu: ContextMenuView? = null
    private var accentColor: Int = com.nexus.launcher.ui.NexusContextMenuDesignHelper.resolveTokens(activity).accent

    private val folderAppPresenter = ContextMenuFolderAppPresenter(
        activity = activity,
        getAccentColor = { accentColor },
        getGlobalCoordinates = ::getGlobalCoordinates,
        setupHeader = { menu, item -> setupHeader(menu, item, activity) },
        dismiss = ::dismiss,
        assignOverlay = { currentOverlay = it },
        assignMenu = { currentMenu = it }
    )

    fun isMenuVisible(): Boolean = currentMenu != null

    fun setAccentColor(color: Int) {
        accentColor = color
    }

    fun getGlobalCoordinates(view: View): android.graphics.Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return android.graphics.Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )
    }

    var currentPage: Int = 0
    var currentItemId: Int = -1
    var onEnterSelectionMode: ((String, com.nexus.launcher.ui.model.SelectionSource) -> Unit)? = null
    var onEnterHomeSelectionMode: ((Int, String) -> Unit)? = null
    var onMenuDismissed: (() -> Unit)? = null

    fun show(
        item: DisplayItem,
        itemLocalRect: android.graphics.Rect,
        canvasView: View,
        isUnhideMode: Boolean = false,
        isHomeScreen: Boolean = false,
        isDockMenu: Boolean = false,
        excludedActionTitles: Set<String> = emptySet(),
        onDockSettingsClick: (() -> Unit)? = null
    ) {
        dismiss()

        // Frost canvas icons + widgets under the menu (spotlight punch-out keeps the target sharp on top)
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, true)
        activity.canvasView.invalidate()

        val canvasGlobal = getGlobalCoordinates(canvasView)
        val itemGlobal = android.graphics.Rect(
            canvasGlobal.left + itemLocalRect.left,
            canvasGlobal.top + itemLocalRect.top,
            canvasGlobal.left + itemLocalRect.right,
            canvasGlobal.top + itemLocalRect.bottom
        )

        val windowX = itemGlobal.exactCenterX()
        val windowY = itemGlobal.exactCenterY()
        val containerGlobal = getGlobalCoordinates(mainContainer)

        // Coordinates relative to mainContainer for drawing and layout
        val x = windowX - containerGlobal.left
        val y = windowY - containerGlobal.top

        // 1. Create dimming interceptor layer with spotlight punch-out
        val overlay = object : View(activity) {
            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#80000000")
                style = android.graphics.Paint.Style.FILL
            }
            val clearPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                val canonicalW = itemGlobal.width().toFloat()
                val canonicalH = itemGlobal.height().toFloat()
                val rect = android.graphics.RectF(
                    x - canonicalW / 2f,
                    y - canonicalH / 2f,
                    x + canonicalW / 2f,
                    y + canonicalH / 2f
                )
                val rx = 16f * resources.displayMetrics.density
                canvas.drawRoundRect(rect, rx, rx, clearPaint)

                item.icon?.let { iconDrawable ->
                    iconDrawable.setBounds(
                        rect.left.toInt(),
                        rect.top.toInt(),
                        rect.right.toInt(),
                        rect.bottom.toInt()
                    )
                    iconDrawable.draw(canvas)
                }
            }
        }.apply {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            alpha = 0f
            setOnClickListener { dismiss() }
        }
        currentOverlay = overlay
        mainContainer.addView(overlay)
        overlay.animate().alpha(1f).setDuration(200).start()

        // 2. STEP 1 - Create the view
        val menu = buildPopulatedMenu(item, canvasView, isUnhideMode, isHomeScreen, isDockMenu, excludedActionTitles, onDockSettingsClick)

        // Measure to get dimensions before adding
        menu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val menuWidth = menu.measuredWidth
        val menuHeight = menu.measuredHeight

        val containerHeight =
            if (mainContainer.measuredHeight > 0)
                mainContainer.measuredHeight
            else
                activity.window.decorView.height

        var isAbove = false
        val density = activity.resources.displayMetrics.density
        if (y + menuHeight > containerHeight - (32f * density)) {
            isAbove = true
        }

        var finalX = x - (menuWidth / 2f)
        val containerWidth =
            if (mainContainer.measuredWidth > 0)
                mainContainer.measuredWidth
            else
                activity.window.decorView.width
        val canvasView = canvasView as? com.nexus.launcher.ui.canvas.LauncherCanvasView
        val dockReserve = if (canvasView?.isLandscape == true)
            canvasView.dockStripWidth else 0
        val effectiveContainerWidth = containerWidth - dockReserve
        if (finalX < 0f) {
            finalX = 0f
        } else if (finalX + menuWidth > effectiveContainerWidth.toFloat() - (20f * activity.resources.displayMetrics.density)) {
            finalX = effectiveContainerWidth - menuWidth.toFloat() - (20f * activity.resources.displayMetrics.density)
        }

        val cornerRadiusPx = 12 * activity.resources.displayMetrics.density
        if (menuWidth > 0) {
            menu.setNotchPosition(x - finalX)
        }

        // Neither above nor below fits (phone landscape: the screen is ~400dp tall) — open
        // beside the item instead, on whichever side has room.
        val sideX = sideAnchorX(canvasView, x, itemGlobal.width() / 2f, menuWidth, containerWidth, density)
        val sideAnchored = sideX != null && !fitsVertically(canvasView, y, menuHeight, containerHeight, density)
        if (sideAnchored) {
            menu.isSideAnchored = true
            finalX = sideX!!
        }

        menu.isAbove = isAbove && !sideAnchored
        menu.globalIconCenterX = x
        menu.globalIconBounds = itemGlobal

        // STEP 3 - ONLY THEN add to container
        menu.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP or android.view.Gravity.LEFT
        )
        mainContainer.addView(menu)

        // Set X and Y before post so the initial render isn't at 0,0
        menu.x = finalX
        if (sideAnchored) {
            centerBeside(menu, y, menuHeight, canvasView, containerHeight)
            menu.post { centerBeside(menu, y, menu.height, canvasView, containerHeight) }
        } else {
            val finalY = if (isAbove) y - menuHeight else y
            val minBoundY = 0f
            val maxBoundY = (containerHeight - menuHeight).toFloat()
            menu.y = safeCoerceIn(finalY, minBoundY, maxBoundY)

            menu.post {
                applyMenuLayout(menu, x, finalX, y, isAbove, containerHeight, cornerRadiusPx, retry = false)
            }
        }

        menu.alpha = 0f
        menu.scaleX = 0.9f
        menu.scaleY = 0.9f
        menu.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(150)
            .start()
    }

    fun showForFolderApp(
        item: DisplayItem,
        itemLocalRect: android.graphics.Rect,
        coordinateView: View,
        onPopulateMenu: (ContextMenuView, () -> Unit) -> Unit
    ) {
        folderAppPresenter.show(item, itemLocalRect, coordinateView, onPopulateMenu)
    }

    /** Shared by both presentations — builds and action-populates a [ContextMenuView] identically
     *  regardless of whether [show] or [showMinimal] will position/frame it. */
    private fun buildPopulatedMenu(
        item: DisplayItem,
        canvasView: View,
        isUnhideMode: Boolean,
        isHomeScreen: Boolean,
        isDockMenu: Boolean,
        excludedActionTitles: Set<String>,
        onDockSettingsClick: (() -> Unit)?
    ): ContextMenuView {
        val menu = ContextMenuView(activity).apply {
            minimumWidth = (240 * resources.displayMetrics.density).toInt()
            elevation = 16f * resources.displayMetrics.density
        }
        currentMenu = menu
        menu.setAccentColor(accentColor)
        setupHeader(menu, item, activity)

        when {
            isHomeScreen -> {
                ContextMenuHomeActions.populate(
                    menu = menu,
                    item = item,
                    activity = activity,
                    homeScreenViewModel = homeScreenViewModel,
                    currentItemId = currentItemId,
                    currentPage = currentPage,
                    isDockMenu = isDockMenu,
                    excludedTitles = excludedActionTitles,
                    onEnterHomeSelectionMode = onEnterHomeSelectionMode,
                    onDismiss = { dismiss() }
                )
                if (onDockSettingsClick != null) {
                    menu.addAction(activity.getString(R.string.menu_dock_settings), R.drawable.ic_settings) {
                        onDockSettingsClick.invoke()
                        dismiss()
                    }
                }
            }
            isUnhideMode -> {
                val packageName = item.intent?.component?.packageName ?: item.intent?.getStringExtra("packageName")
                if (packageName != null) {
                    menu.addAction(activity.getString(R.string.menu_unhide_app), R.drawable.ic_visibility_off) {
                        viewModel.unhideApp(packageName)
                        dismiss()
                    }
                }
            }
            else -> {
                ContextMenuDrawerActions.populateShortcuts(activity, menu, item) { dismiss() }
                ContextMenuDrawerActions.populateActions(
                    activity, menu, item, viewModel, { dismiss() }, onEnterSelectionMode
                )
            }
        }
        return menu
    }

    fun dismiss() {
        activity.canvasView.setBlurState(false) // clear the blur when the menu closes
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            com.nexus.launcher.ui.folder.FolderWindowManager.activeCard?.setRenderEffect(null)
        }
        com.nexus.launcher.ui.folder.FolderWindowManager.activeScrim?.setHighlightSuppressed(false)
        activity.canvasView.showDarkOverlay = false
        activity.canvasView.invalidate()
        currentOverlay?.let { overlay ->
            overlay.animate().alpha(0f).setDuration(200).withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }.start()
        }
        currentOverlay = null

        currentMenu?.let { menu ->
            menu.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction { (menu.parent as? ViewGroup)?.removeView(menu) }
                .start()
        }
        currentMenu = null
        onMenuDismissed?.invoke()
    }

    private fun setupHeader(menu: ContextMenuView, item: DisplayItem, activity: MainActivity) {
        menu.setAppIcon(item.icon)
        menu.setAppName(item.label)
        menu.setInfoClickListener {
            val packageName = item.intent?.component?.packageName ?: item.intent?.getStringExtra("packageName")
            if (packageName != null) {
                dismiss()
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                activity.startActivity(intent)
            }
        }
    }
}