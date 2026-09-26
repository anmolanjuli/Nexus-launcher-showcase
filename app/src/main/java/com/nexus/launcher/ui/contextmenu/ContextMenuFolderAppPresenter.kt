package com.nexus.launcher.ui.contextmenu

import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.model.DisplayItem

/**
 * Folder-window app long-press menu (overlay on decorView).
 * Writes into the manager's shared [assignOverlay]/[assignMenu] so [dismiss] clears either path.
 */
class ContextMenuFolderAppPresenter(
    private val activity: MainActivity,
    private val getAccentColor: () -> Int,
    private val getGlobalCoordinates: (View) -> android.graphics.Rect,
    private val setupHeader: (ContextMenuView, DisplayItem) -> Unit,
    private val dismiss: () -> Unit,
    private val assignOverlay: (View) -> Unit,
    private val assignMenu: (ContextMenuView) -> Unit
) {

    fun show(
        item: DisplayItem,
        itemLocalRect: android.graphics.Rect,
        coordinateView: View,
        onPopulateMenu: (ContextMenuView, () -> Unit) -> Unit
    ) {
        dismiss()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // setWorkspaceBlur blurs the home canvas — invisible here since the open folder
            // card sits on top of it. What's actually behind this menu is the folder's own
            // card content, so that needs its own blur too, or the menu's translucent fill
            // just reveals the folder's sharp icon grid instead of reading as frosted.
            com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, true)
            com.nexus.launcher.ui.folder.FolderWindowManager.activeCard?.setRenderEffect(
                com.nexus.launcher.ui.glass.ChromeBackdrop.effect(
                    com.nexus.launcher.ui.glass.ChromeBackdrop.OVER_FOLDER_RADIUS_PX
                )
            )
            // FolderWindowScrimView punches a literal hole in its dim layer at the folder's
            // original icon spot ("canvas icon stays sharp underneath" — the open-animation
            // hugging effect) — that hole reveals raw, unblurred content by design. If this
            // menu's translucent fill extends over it, the mismatch reads as broken
            // transparency rather than frost, so suppress the hole while the menu is up
            // (restored on dismiss — see ContextMenuManager.dismiss()).
            com.nexus.launcher.ui.folder.FolderWindowManager.activeScrim?.setHighlightSuppressed(true)
        }
        activity.canvasView.invalidate()

        val canvasGlobal = getGlobalCoordinates(coordinateView)
        val itemGlobal = android.graphics.Rect(
            canvasGlobal.left + itemLocalRect.left,
            canvasGlobal.top + itemLocalRect.top,
            canvasGlobal.left + itemLocalRect.right,
            canvasGlobal.top + itemLocalRect.bottom
        )
        val decorView = activity.window.decorView as android.view.ViewGroup
        val containerGlobal = getGlobalCoordinates(decorView)
        val x = itemGlobal.exactCenterX() - containerGlobal.left
        val y = itemGlobal.exactCenterY() - containerGlobal.top

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
        assignOverlay(overlay)
        decorView.addView(overlay)
        overlay.animate().alpha(1f).setDuration(200).start()

        val menu = ContextMenuView(activity).apply {
            minimumWidth = (240 * resources.displayMetrics.density).toInt()
            elevation = 16f * resources.displayMetrics.density
        }
        assignMenu(menu)
        menu.setAccentColor(getAccentColor())
        setupHeader(menu, item)

        onPopulateMenu(menu) { dismiss() }

        menu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val menuWidth = menu.measuredWidth
        val menuHeight = menu.measuredHeight
        val containerHeight = decorView.height
        val density = activity.resources.displayMetrics.density
        val isAbove = y + menuHeight > containerHeight - (32f * density)
        var finalX = x - (menuWidth / 2f)
        val containerWidth = decorView.width
        if (finalX < 0f) finalX = 0f
        else if (finalX + menuWidth > containerWidth - (20f * density)) {
            finalX = containerWidth - menuWidth - (20f * density)
        }
        val cornerRadiusPx = 12 * density
        if (menuWidth > 0) {
            menu.setNotchPosition(x - finalX)
        }
        menu.isAbove = isAbove
        menu.globalIconCenterX = x
        menu.globalIconBounds = itemGlobal
        menu.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        menu.elevation = 32f * density
        decorView.addView(menu)
        menu.x = finalX
        val finalY = if (isAbove) y - menuHeight else y
        val minBoundY = 0f
        val maxBoundY = (containerHeight - menuHeight).toFloat()
        menu.y = safeCoerceIn(finalY, minBoundY, maxBoundY)

        menu.post {
            applyMenuLayout(menu, x, finalX, y, isAbove, containerHeight, cornerRadiusPx, retry = false)
        }
        menu.alpha = 0f
        menu.scaleX = 0.9f
        menu.scaleY = 0.9f
        menu.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
    }
}
