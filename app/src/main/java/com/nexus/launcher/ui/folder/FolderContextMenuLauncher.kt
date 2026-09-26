package com.nexus.launcher.ui.folder

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.dock.DockSearchSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

object FolderContextMenuLauncher {

    private const val TAG = "FolderContextMenu"
    private var activeOverlay: WeakReference<View>? = null
    private var activeBackCallback: OnBackPressedCallback? = null
    private var onDismissExtra: (() -> Unit)? = null

    fun show(
        context: Context,
        folderItem: HomeScreenItem,
        dao: HomeScreenDao,
        coroutineScope: CoroutineScope,
        iconX: Float,
        iconY: Float,
        iconSize: Float,
        highlightBounds: FolderScrimHighlight.Bounds? = null,
        onSelect: (() -> Unit)? = null,
        hugHomeCanonical: Boolean = true,
        onDismiss: (() -> Unit)? = null,
    ) {
        val activity = context as? Activity ?: return
        dismiss()
        onDismissExtra = onDismiss
        FolderBlurCoordinator.setWorkspaceBlur(context, true)
        val inflater = LayoutInflater.from(context)
        val host = activity.window.decorView as ViewGroup
        val overlay = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { dismiss() }
        }

        val scrim = FolderContextMenuScrimView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            alpha = 0f
        }
        overlay.addView(scrim)
        scrim.setFolderSpot(folderItem.id)
        val USE_RADIAL_MENU = false // folderItem.page >= 0 // ONLY for Home Folders

        var finalHighlightBounds = highlightBounds
        var radialGeometry: com.nexus.launcher.ui.canvas.RadialMenuGeometry.SweepResult? = null

        val canvasView = (context as? com.nexus.launcher.ui.MainActivity)?.canvasView
            ?: com.nexus.launcher.ui.folder.FolderWindowManager.activeCanvas?.get()
        
        var canonicalW = iconSize
        var canonicalH = iconSize

        if (canvasView != null && hugHomeCanonical) {
            val layoutResult = com.nexus.launcher.ui.canvas.IconLayoutMetrics.compute(
                cell = canvasView.homeGridCells.firstOrNull() ?: android.graphics.RectF(0f, 0f, 72f, 72f),
                gridRows = canvasView.currentGridRows,
                gapHorizontalPx = canvasView.homeScreenRenderer.gapHorizontalPx,
                gapVerticalPx = canvasView.homeScreenRenderer.gapVerticalPx,
                spanX = 1,
                spanY = 1,
                showLabels = false,
                userIconSizeMultiplier = canvasView.homeIconSizeMultiplier,
                density = context.resources.displayMetrics.density,
                displayWidth = context.resources.displayMetrics.widthPixels,
                displayHeight = context.resources.displayMetrics.heightPixels,
                labelText = null,
                labelPaint = null
            )
            canonicalW = layoutResult.iconRect.width().toFloat()
            canonicalH = layoutResult.iconRect.height().toFloat()
        }

        if (highlightBounds != null && hugHomeCanonical) {
            val cx = (highlightBounds.left + highlightBounds.right) / 2f
            val cy = (highlightBounds.top + highlightBounds.bottom) / 2f
            finalHighlightBounds = FolderScrimHighlight.Bounds(
                left = cx - canonicalW / 2f,
                top = cy - canonicalH / 2f,
                right = cx + canonicalW / 2f,
                bottom = cy + canonicalH / 2f,
                shapeStyle = highlightBounds.shapeStyle
            )
        }

        if (USE_RADIAL_MENU) {
            if (canvasView != null) {
                var cx = if (finalHighlightBounds != null) (finalHighlightBounds!!.left + finalHighlightBounds!!.right) / 2f else iconX
                var cy = if (finalHighlightBounds != null) (finalHighlightBounds!!.top + finalHighlightBounds!!.bottom) / 2f else iconY
                
                val initialAnchor = android.graphics.RectF(
                    cx - canonicalW / 2f,
                    cy - canonicalH / 2f,
                    cx + canonicalW / 2f,
                    cy + canonicalH / 2f
                )
                
                val decorView = activity.window.decorView
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decorView)
                val statusBarH = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top?.toFloat() ?: 0f
                val navBarH = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom?.toFloat() ?: 0f
                
                radialGeometry = com.nexus.launcher.ui.canvas.RadialMenuGeometry.computeFinalGeometry(
                    anchorRect = initialAnchor,
                    itemsSize = 6,
                    density = context.resources.displayMetrics.density,
                    w = context.resources.displayMetrics.widthPixels.toFloat(),
                    h = context.resources.displayMetrics.heightPixels.toFloat(),
                    statusBarH = statusBarH,
                    navBarH = navBarH,
                    canonical1x1Size = canonicalW
                )
                
                cx = radialGeometry!!.cx
                cy = radialGeometry!!.cy
                
                if (finalHighlightBounds != null) {
                    finalHighlightBounds = FolderScrimHighlight.Bounds(
                        left = cx - canonicalW / 2f,
                        top = cy - canonicalH / 2f,
                        right = cx + canonicalW / 2f,
                        bottom = cy + canonicalH / 2f,
                        shapeStyle = finalHighlightBounds!!.shapeStyle
                    )
                }
            }
        }
        
        scrim.setHighlight(finalHighlightBounds)
        val config = FolderConfigCodec.parse(folderItem.folderConfigJson)
        val accentColor = 0
        
        if (finalHighlightBounds == null) {
            scrim.setIconSpot(
                iconX,
                iconY,
                iconSize,
                FolderScrimHighlight.shapeStyle(config),
                true,
                accentColor
            )
        }
        scrim.animate().alpha(1f).setDuration(180).start()

        if (USE_RADIAL_MENU && radialGeometry != null) {
            val items = mutableListOf(
                com.nexus.launcher.ui.contextmenu.UniversalRadialMenuItem(context.getString(R.string.action_add_apps), R.drawable.ic_add) {
                    dismiss()
                    FolderContextMenuActions.openAppPicker(context, folderItem, coroutineScope)
                },
                com.nexus.launcher.ui.contextmenu.UniversalRadialMenuItem(context.getString(R.string.action_edit), R.drawable.ic_edit) {
                    dismiss()
                    FolderContextMenuActions.openEditSheet(context, folderItem, dao, coroutineScope)
                },
                com.nexus.launcher.ui.contextmenu.UniversalRadialMenuItem(context.getString(R.string.action_select), R.drawable.ic_select) {
                    dismiss()
                    onSelect?.invoke()
                },
                com.nexus.launcher.ui.contextmenu.UniversalRadialMenuItem(context.getString(R.string.action_resize), R.drawable.ic_resize) {
                    dismiss()
                    (context as? com.nexus.launcher.ui.MainActivity)?.homeEditController?.showFolderResizeMode(folderItem)
                },
                com.nexus.launcher.ui.contextmenu.UniversalRadialMenuItem(context.getString(R.string.folder_context_flip), R.drawable.ic_flip) {
                    dismiss()
                    (context as? com.nexus.launcher.ui.MainActivity)?.homeEditController?.showFolderFlipMode(folderItem)
                },
                com.nexus.launcher.ui.contextmenu.UniversalRadialMenuItem(context.getString(R.string.action_remove), R.drawable.ic_remove) {
                    dismiss()
                    FolderContextMenuActions.confirmRemove(context, folderItem, dao, coroutineScope)
                }
            )

            val radialMenu = com.nexus.launcher.ui.contextmenu.UniversalRadialMenu(
                context = context,
                geometry = radialGeometry!!,
                items = items,
                folderTitle = folderDisplayName(context, folderItem),
                onDismissRequest = { dismiss() }
            )
            overlay.addView(radialMenu)
            
            activeOverlay = WeakReference(overlay)
            host.addView(overlay)

            (context as? FragmentActivity)?.let { owner ->
                val callback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        dismiss()
                        isEnabled = false
                    }
                }
                activeBackCallback = callback
                owner.onBackPressedDispatcher.addCallback(owner, callback)
            }
            return
        }

        val menuWrapper = inflater.inflate(R.layout.view_folder_context_menu, overlay, false)
        menuWrapper.setOnClickListener { /* consume */ }
        
        val menuCard = menuWrapper.findViewById<LinearLayout>(R.id.folder_menu_card)
        menuCard.removeAllViews()
        
        com.nexus.launcher.ui.NexusDesignSystem.applyContextMenuCardBackground(menuCard, activity, accentColor)
        
        FolderFlatMenuRows.build(menuCard, context, folderItem, dao, coroutineScope, accentColor, onSelect, ::dismiss)

        val density = context.resources.displayMetrics.density
        val gapPx = 12f * density
        val edgePx = (16 * density).toInt()
        val menuWidthPx = (com.nexus.launcher.ui.ContextMenuMetrics.MENU_WIDTH_DP * density).toInt()
        val screenW = context.resources.displayMetrics.widthPixels
        val statusBarTop = statusBarHeight(activity)

        menuWrapper.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val menuHeight = menuWrapper.measuredHeight
        val iconTop = iconY - iconSize / 2f
        val iconBottom = iconY + iconSize / 2f

        val menuBottom = iconTop - gapPx
        var menuTop = menuBottom - menuHeight
        var flippedBelow = false
        if (menuTop < statusBarTop + 8f * density) {
            flippedBelow = true
            menuTop = iconBottom + gapPx
        }
        // Neither above nor below fits (phone landscape): open beside the folder instead.
        val besideX = FolderMenuSidePlacement.xBeside(
            context, iconX, iconSize, menuWidthPx, menuTop, menuHeight, flippedBelow, gapPx
        )
        if (besideX != null) {
            menuTop = FolderMenuSidePlacement.centeredTop(context, iconY, menuHeight, statusBarTop)
        }

        val notchTop = menuWrapper.findViewById<ImageView>(R.id.folder_menu_notch_top)
        val notchBottom = menuWrapper.findViewById<ImageView>(R.id.folder_menu_notch_bottom)
        if (besideX != null) {
            notchTop.visibility = View.GONE
            notchBottom.visibility = View.GONE
        } else if (flippedBelow) {
            notchTop.visibility = View.VISIBLE
            notchBottom.visibility = View.GONE
        } else {
            notchTop.visibility = View.GONE
            notchBottom.visibility = View.VISIBLE
        }

        val originalMenuXScreen = (iconX - menuWidthPx / 2f).toInt()
        var menuXScreen = besideX ?: originalMenuXScreen.coerceIn(edgePx, (screenW - menuWidthPx - edgePx).coerceAtLeast(edgePx))
        val shiftX = menuXScreen - originalMenuXScreen
        
        notchTop.translationX = -shiftX.toFloat()
        notchBottom.translationX = -shiftX.toFloat()
        
        val menuYScreen = menuTop.toInt()

        val hostLoc = IntArray(2)
        host.getLocationOnScreen(hostLoc)
        val menuXLocal = menuXScreen - hostLoc[0]
        val menuYLocal = menuYScreen - hostLoc[1]

        overlay.addView(
            menuWrapper,
            FrameLayout.LayoutParams(
                menuWidthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.LEFT
            ).apply {
                leftMargin = menuXLocal
                topMargin = menuYLocal
            }
        )

        host.addView(overlay)
        activeOverlay = WeakReference(overlay)

        (context as? FragmentActivity)?.let { owner ->
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    dismiss()
                    isEnabled = false
                }
            }
            activeBackCallback = callback
            owner.onBackPressedDispatcher.addCallback(owner, callback)
        }

        Log.d(
            TAG,
            "menu screen=($menuXScreen, $menuYScreen) iconTop=$iconTop gap=$gapPx " +
                "flippedBelow=$flippedBelow size=${menuWidthPx}x$menuHeight"
        )
    }

    fun dismiss() {
        val extra = onDismissExtra
        onDismissExtra = null
        extra?.invoke()
        activeBackCallback?.remove()
        activeBackCallback = null
        val overlay = activeOverlay?.get() ?: return
        val ctx = overlay.context
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        activeOverlay = null
        if (!FolderWindowManager.isShowing()) {
            FolderBlurCoordinator.setWorkspaceBlur(ctx, false)
        }
    }

    fun isShowing(): Boolean = activeOverlay?.get() != null

    /** The stored name. Untitled folders are saved as "Folder", so keep this English. */
    fun folderDisplayName(folderItem: HomeScreenItem): String {
        val title = folderItem.folderTitle.trim()
        if (title.isNotEmpty() && !title.startsWith("folder_")) return title
        return "Folder"
    }

    /** The name to show: an untitled folder, including one stored as "Folder", reads in the app language. */
    fun folderDisplayName(context: Context, folderItem: HomeScreenItem): String {
        val title = folderDisplayName(folderItem)
        return if (title == "Folder") context.getString(R.string.folder_default_name) else title
    }

    private fun statusBarHeight(activity: Activity): Float {
        val root = activity.window.decorView
        val insets = ViewCompat.getRootWindowInsets(root)
        val top = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top
        return (top ?: (24 * activity.resources.displayMetrics.density).toInt()).toFloat()
    }
}