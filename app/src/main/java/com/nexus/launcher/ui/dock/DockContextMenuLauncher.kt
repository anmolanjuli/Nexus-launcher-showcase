package com.nexus.launcher.ui.dock

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.contextmenu.ContextMenuManager
import com.nexus.launcher.ui.folder.FolderContextMenuLauncher
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

object DockContextMenuLauncher {

    /** Workspace actions that must never appear on dock icons. */
    private val EXCLUDED_DOCK_ACTIONS = setOf("Select")

    private var currentContextMenuManager: ContextMenuManager? = null
    private var activeDockOverlay: WeakReference<ViewGroup>? = null
    private var activeBackCallback: OnBackPressedCallback? = null

    fun dismissIfShowing() {
        FolderContextMenuLauncher.dismiss()
        currentContextMenuManager?.takeIf { it.isMenuVisible() }?.dismiss()
        activeDockOverlay?.get()?.let { overlay ->
            val host = overlay.parent as? ViewGroup
            host?.removeView(overlay)
        }
        activeDockOverlay = null
        activeBackCallback?.remove()
        activeBackCallback = null
    }

    fun dockItemScreenCoords(
        dockLayout: DockLayout,
        item: HomeScreenItem
    ): Triple<Float, Float, Float>? {
        dockLayout.syncRendererLayoutInternal()
        val visible = dockLayout.pageItemsInternal()
            .filter { it.column < dockLayout.maxDockIcons }
            .sortedBy { it.column }
        val slotIndex = visible.indexOfFirst { it.id == item.id }
        if (slotIndex < 0) return null
        val bounds = DockLayoutRenderer.getIconBoundsAt(slotIndex) ?: return null
        val dockLoc = IntArray(2)
        dockLayout.getLocationOnScreen(dockLoc)
        val iconX = bounds.centerX() + dockLoc[0]
        val iconY = bounds.centerY() + dockLoc[1]
        val iconSize = bounds.width().coerceAtLeast(bounds.height())
        return Triple(iconX, iconY, iconSize)
    }

    fun show(
        activity: MainActivity,
        contextMenuManager: ContextMenuManager,
        dockLayout: DockLayout,
        item: HomeScreenItem
    ) {
        if (item.itemType == 1) {
            val coords = dockItemScreenCoords(dockLayout, item) ?: return
            val scope = dockLayout.findViewTreeLifecycleOwner()?.lifecycleScope
                ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
            val dao = EntryPointAccessors.fromApplication(
                activity.applicationContext,
                DaoEntryPoint::class.java
            ).homeScreenDao()
            FolderContextMenuLauncher.show(
                context = activity,
                folderItem = item,
                dao = dao,
                coroutineScope = scope,
                iconX = coords.first,
                iconY = coords.second,
                iconSize = coords.third
            )
            return
        }

        val pm = activity.packageManager
        val intent = pm.getLaunchIntentForPackage(item.packageName) ?: return
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(item.packageName, 0)).toString()
        } catch (_: Exception) {
            item.packageName
        }
        val icon = dockLayout.cachedIconFor(item.packageName)
            ?: try {
                pm.getApplicationIcon(item.packageName)
            } catch (_: Exception) {
                null
            }

        val USE_RADIAL_MENU = false
        if (USE_RADIAL_MENU) {
            val coords = dockItemScreenCoords(dockLayout, item) ?: return
            val (iconX, iconY, iconSize) = coords
            
            val host = activity.window.decorView as ViewGroup
            
            val overlay = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            
            val scrim = com.nexus.launcher.ui.folder.FolderContextMenuScrimView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                alpha = 0f
            }
            overlay.addView(scrim)
            
            val decorView = activity.window.decorView
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decorView)
            val statusBarH = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top?.toFloat() ?: 0f
            val navBarH = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom?.toFloat() ?: 0f
            
            val initialAnchor = android.graphics.RectF(
                iconX - iconSize / 2f,
                iconY - iconSize / 2f,
                iconX + iconSize / 2f,
                iconY + iconSize / 2f
            )
            
            val radialGeometry = com.nexus.launcher.ui.canvas.RadialMenuGeometry.computeFinalGeometry(
                anchorRect = initialAnchor,
                itemsSize = 3,
                density = activity.resources.displayMetrics.density,
                w = activity.resources.displayMetrics.widthPixels.toFloat(),
                h = activity.resources.displayMetrics.heightPixels.toFloat(),
                statusBarH = statusBarH,
                navBarH = navBarH,
                canonical1x1Size = iconSize,
                isFixedUpwardHalfRing = true
            )
            
            scrim.setIconSpot(
                screenX = radialGeometry.cx,
                screenY = radialGeometry.cy,
                iconSize = iconSize,
                showAccentRing = false
            )
            scrim.animate().alpha(1f).setDuration(180).start()
            
            val iconCopy = android.widget.ImageView(activity).apply {
                setImageDrawable(icon)
                layoutParams = FrameLayout.LayoutParams(
                    iconSize.toInt(),
                    iconSize.toInt(),
                    android.view.Gravity.TOP or android.view.Gravity.LEFT
                ).apply {
                    leftMargin = (radialGeometry.cx - iconSize / 2f).toInt()
                    topMargin = (radialGeometry.cy - iconSize / 2f).toInt()
                }
                alpha = 0f
            }
            overlay.addView(iconCopy)
            iconCopy.animate().alpha(1f).setDuration(180).start()
            
            val dismissAction = {
                scrim.animate().alpha(0f).setDuration(150).start()
                iconCopy.animate().alpha(0f).setDuration(150).start()
                if (overlay.childCount > 2) {
                    (overlay.getChildAt(2) as? com.nexus.launcher.ui.contextmenu.UniversalRadialMenu)?.closeMenu()
                }
                activeBackCallback?.remove()
                activeBackCallback = null
                overlay.postDelayed({
                    val h = overlay.parent as? ViewGroup
                    h?.removeView(overlay)
                    if (activeDockOverlay?.get() == overlay) {
                        activeDockOverlay = null
                    }
                    FolderContextMenuLauncher.dismiss()
                    currentContextMenuManager?.takeIf { it.isMenuVisible() }?.dismiss()
                }, 150)
                Unit
            }
            
            val items = listOf(
                com.nexus.launcher.ui.contextmenu.UniversalRadialMenuItem(activity.getString(com.nexus.launcher.R.string.action_edit), R.drawable.ic_edit) {
                    dismissAction()
                    val mainViewModel = androidx.lifecycle.ViewModelProvider(activity)[com.nexus.launcher.ui.MainViewModel::class.java]
                    val sheet = com.nexus.launcher.ui.settings.IconEditSheet(item.packageName, label, mainViewModel)
                    sheet.show(activity.supportFragmentManager, "icon_edit")
                },
                com.nexus.launcher.ui.contextmenu.UniversalRadialMenuItem(activity.getString(com.nexus.launcher.R.string.menu_dock_settings), R.drawable.ic_settings) {
                    dismissAction()
                    dockLayout.onDockSettingsRequested?.invoke()
                },
                com.nexus.launcher.ui.contextmenu.UniversalRadialMenuItem(activity.getString(com.nexus.launcher.R.string.action_remove), R.drawable.ic_remove) {
                    dismissAction()
                    val dao = EntryPointAccessors.fromApplication(
                        activity.applicationContext,
                        DaoEntryPoint::class.java
                    ).homeScreenDao()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        dao.removeItemById(item.id)
                    }
                }
            )
            
            val radialMenu = com.nexus.launcher.ui.contextmenu.UniversalRadialMenu(
                context = activity,
                geometry = radialGeometry,
                items = items,
                folderTitle = "",
                onDismissRequest = dismissAction
            )
            
            overlay.addView(radialMenu)
            overlay.setOnClickListener { dismissAction() }
            
            activeDockOverlay = WeakReference(overlay)
            host.addView(overlay)
            
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    dismissAction()
                }
            }
            activeBackCallback = callback
            activity.onBackPressedDispatcher.addCallback(activity, callback)
            return
        }

        val displayItem = com.nexus.launcher.ui.model.DisplayItem(label, icon, intent, null)
        val localRect = dockLayout.iconHitRectFor(item)
        currentContextMenuManager = contextMenuManager
        contextMenuManager.currentItemId = item.id
        contextMenuManager.show(
            displayItem,
            localRect,
            dockLayout,
            isHomeScreen = true,
            isDockMenu = true,
            excludedActionTitles = EXCLUDED_DOCK_ACTIONS,
            onDockSettingsClick = { dockLayout.onDockSettingsRequested?.invoke() }
        )
    }
}
