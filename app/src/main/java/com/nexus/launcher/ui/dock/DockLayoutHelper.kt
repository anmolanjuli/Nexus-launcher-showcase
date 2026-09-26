package com.nexus.launcher.ui.dock

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.ui.dock.settings.DockResetEntryPoint
import com.nexus.launcher.ui.dock.settings.DockSettingsEntryPoint
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import com.nexus.launcher.ui.glass.UiStyleCoordinator
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal object DockLayoutSettingsBinder {

    fun observeDockHeight(
        view: View,
        owner: LifecycleOwner,
        density: Float,
        onHeightDp: (Int) -> Unit,
        onJob: (Job) -> Unit,
        recompute: () -> Unit
    ) {
        val repo = EntryPointAccessors.fromApplication(
            view.context.applicationContext,
            DockSettingsEntryPoint::class.java
        ).dockSettingsRepository()
        onJob(owner.lifecycleScope.launch {
            repo.dockHeightDp.collect { dp -> applyDockHeightDp(view, density, dp, onHeightDp, recompute) }
        })
        applyDockHeightDp(view, density, repo.dockHeightDp.value, onHeightDp, recompute)
    }

    fun observeAppearance(
        view: View,
        owner: LifecycleOwner,
        onJob: (Job) -> Unit
    ) {
        val repo = EntryPointAccessors.fromApplication(
            view.context.applicationContext,
            DockSettingsEntryPoint::class.java
        ).dockSettingsRepository()
        val settingsRepo = EntryPointAccessors.fromApplication(
            view.context.applicationContext,
            DockResetEntryPoint::class.java
        ).settingsRepository()
        val themeController = EntryPointAccessors.fromApplication(
            view.context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeController()
        // The style listener outlives any single emission, so it must not capture `view`
        // strongly — that would pin a detached dock for as long as this object lives.
        val dockRef = java.lang.ref.WeakReference(view)
        onJob(owner.lifecycleScope.launch {
            supervisorScope {

                launch {
                    themeController.backgroundLayerMode.collect {
                        com.nexus.launcher.ui.folder.HomeScreenFrameCache.invalidate()
                        DockBackgroundRenderer.invalidateFrostedBackdrop()
                        DockBackgroundRenderer.syncBackgroundLayer(view)
                        view.invalidate()
                    }
                }
                launch {
                    themeController.currentTokens.collect { tokens ->
                        (view as? DockLayout)?.let { dock ->
                            dock.currentThemeTokens = tokens
                            dock.backgroundPaint.color = tokens.surface
                        }
                        com.nexus.launcher.ui.folder.HomeScreenFrameCache.invalidate()
                        DockBackgroundRenderer.invalidateFrostedBackdrop()
                        DockBackgroundRenderer.syncBackgroundLayer(view)
                        view.invalidate()
                    }
                }

                launch {
                    repo.iconSizeDp.collect { dp ->
                        DockSlotLayout.applyUserIconSizeDp(dp)
                        view.invalidate()
                    }
                }
                launch {
                    repo.maxIcons.collect { max ->
                        (view as? DockLayout)?.let { dock ->
                            dock.maxDockIcons = max
                            dock.requestLayout()
                            dock.invalidate()
                        }
                    }
                }
                launch {
                    repo.backgroundMode.collect { mode ->
                        DockBackgroundRenderer.backgroundMode = mode
                        DockBackgroundRenderer.syncBackgroundLayer(view)
                        view.invalidate()
                    }
                }
                launch {
                    // Reports what this flow saw to UiStyleCoordinator and lets it decide. The
                    // two collectors that used to live here — one on uiStyleMode, one a "safety
                    // net" on the legacy frostedGlassEnabled — both wrote the shared style flags
                    // directly, as did MainActivityStyleSync on a different flow. Each then
                    // inferred "did it change?" from those same flags, so the first writer to
                    // notice silenced the others and their refreshes were skipped. The coordinator
                    // owns the comparison and the write, and calls the dock's refresh below as a
                    // listener, so this runs on every real change regardless of who saw it first.
                    //
                    // Collapsing the pair into one collector also removes the window where
                    // frostedGlassEnabled and isDefaultFlatStyleEnabled disagreed: the old safety
                    // net wrote the first without the second, and a reader catching that state
                    // (both false) got shouldDrawNeumorphicOnCanvas() == false while the frosted
                    // view was hidden too — a dock that drew no background at all.
                    val dockStyleListener = {
                        dockRef.get()?.let { dock ->
                            (dock as? android.view.ViewGroup)?.let {
                                DockBackgroundRenderer.applyDockClipOutline(it)
                            }
                            DockBackgroundRenderer.syncBackgroundLayer(dock)
                            dock.invalidate()
                            // This setting used to reach only the Dock — widgets/folders had no
                            // code path reading it, so the toggle looked dead outside a dock in
                            // TRANSPARENT mode. Every isGlass check across widgets/boxes/folders
                            // now ANDs against the shared flag, so they all need refreshing here.
                            refreshAllGlassSurfaces(dock)
                        }
                        Unit
                    }
                    UiStyleCoordinator.setListener(UiStyleCoordinator.LISTENER_DOCK, dockStyleListener)
                    try {
                        settingsRepo.settingsFlow
                            .map { it.uiStyleMode to it.frostedGlassEnabled }
                            .distinctUntilChanged()
                            .collect { (mode, legacyEnabled) ->
                                UiStyleCoordinator.apply(mode, legacyEnabled)
                            }
                    } finally {
                        UiStyleCoordinator.removeListener(
                            UiStyleCoordinator.LISTENER_DOCK, dockStyleListener
                        )
                    }
                }
                launch {
                    repo.frostedGradientIndex.collect { index ->
                        DockBackgroundRenderer.frostedGradientIndex = index
                        DockBackgroundRenderer.syncBackgroundLayer(view)
                        view.invalidate()
                    }
                }
                launch {
                    repo.solidColorArgb.collect { argb ->
                        DockBackgroundRenderer.solidColorArgb = argb
                        DockBackgroundRenderer.syncBackgroundLayer(view)
                        view.invalidate()
                    }
                }
                launch {
                    repo.cornerRadiusDp.collect { dp ->
                        DockCornerRadius.cornerRadiusDp = dp
                        (view as? DockLayout)?.applyCornerRadiusOutline()
                        view.invalidate()
                    }
                }
                launch {
                    repo.showLabels.collect { enabled ->
                        DockLabelRenderer.showLabels = enabled
                        (view as? DockLayout)?.let { dock ->
                            val d = dock.resources.displayMetrics.density
                            updateOrientationBounds(dock, dock.dockHeightDp, d)
                        }
                        view.requestLayout()
                        view.invalidate()
                    }
                }
                launch {
                    repo.labelFontSizeSp.collect { sp ->
                        DockLabelRenderer.labelFontSizeSp = sp
                        (view as? DockLayout)?.let { dock ->
                            val d = dock.resources.displayMetrics.density
                            updateOrientationBounds(dock, dock.dockHeightDp, d)
                        }
                        view.requestLayout()
                        view.invalidate()
                    }
                }
                launch {
                    repo.searchInDock.collect { enabled ->
                        DockSearchSlot.enabled = enabled
                        (view as? DockLayout)?.requestLayout()
                        view.invalidate()
                    }
                }
                launch {
                    repo.searchSlotIndex.collect { index ->
                        DockSearchSlot.slotIndex = if (index < 0) 0 else index
                        view.invalidate()
                    }
                }
            }
        })
        DockSlotLayout.applyUserIconSizeDp(repo.iconSizeDp.value)
        (view as? DockLayout)?.maxDockIcons = repo.maxIcons.value
        DockBackgroundRenderer.backgroundMode = repo.backgroundMode.value
        DockBackgroundRenderer.solidColorArgb = repo.solidColorArgb.value
        DockBackgroundRenderer.frostedGradientIndex = repo.frostedGradientIndex.value
        DockCornerRadius.cornerRadiusDp = repo.cornerRadiusDp.value
        DockLabelRenderer.showLabels = repo.showLabels.value
        DockLabelRenderer.labelFontSizeSp = repo.labelFontSizeSp.value
        DockSearchSlot.enabled = repo.searchInDock.value
        DockSearchSlot.slotIndex = repo.searchSlotIndex.value.coerceAtLeast(0)
    }

    /** Shared refresh cascade for anything that changes what "glass" means app-wide (the master
     *  toggle, or the Default-flat-style flag) — pushes the change out to every surface that
     *  can't pick it up from a bare invalidate() alone. See the frostedGlassEnabled collector's
     *  original comments for why each of these calls is actually necessary.
     *
     *  Every step is independently wrapped: this whole function runs inside a settings-flow
     *  `collect{}` block, itself inside a shared `coroutineScope { launch{...}; launch{...} }` —
     *  an uncaught exception ANYWHERE in one step cancels that coroutineScope entirely, silently
     *  killing every sibling collector in it (icon size, dock height, background mode, ...), not
     *  just this one. That happened for real: a bug in one step here previously took down the
     *  whole cascade for widgets/folders while the Dock's own already-completed steps (earlier in
     *  this same function) looked fine — read as "Dock responds, nothing else does." Catching each
     *  step individually means one surface's refresh failing can never take any other down with
     *  it, or kill this collector for future toggle changes.
     */
    private fun refreshAllGlassSurfaces(view: View) {
        runCatching { com.nexus.launcher.ui.folder.FolderBlurCoordinator.findCanvas(view.context)?.invalidate() }
        val overlay = runCatching { com.nexus.launcher.ui.folder.FolderBlurCoordinator.findWidgetOverlay(view.context) }.getOrNull()
        runCatching { overlay?.invalidate() }
        runCatching { overlay?.rebindCachedWidgets() }
        runCatching { com.nexus.launcher.ui.widgets.NexusWidgetGlobalRefresh.refreshAllGlassCapableWidgets(view.context) }
        runCatching { com.nexus.launcher.ui.folder.FolderWindowRefresh.refreshActiveBackgroundIfShowing(view.context) }
    }

    private fun parseAccent(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.parseColor(NexusDefaults.ACCENT_COLOR)
    }

    private fun applyDockHeightDp(
        view: View,
        density: Float,
        dp: Int,
        onHeightDp: (Int) -> Unit,
        recompute: () -> Unit
    ) {
        val heightDp = dp.coerceIn(
            DockSettingsRepository.MIN_DOCK_HEIGHT_DP,
            DockSettingsRepository.MAX_DOCK_HEIGHT_DP
        )
        onHeightDp(heightDp)
        // Same bounds as every other path (margins, labels, hidden dock) — one source.
        updateOrientationBounds(view, heightDp, density)
        recompute()
        view.requestLayout()
        view.invalidate()
    }

    fun updateOrientationBounds(view: View, dockHeightDp: Int, density: Float) {
        val clampedHeightDp = dockHeightDp.coerceIn(
            DockSettingsRepository.MIN_DOCK_HEIGHT_DP,
            DockSettingsRepository.MAX_DOCK_HEIGHT_DP
        )
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        val isPhoneLandscape = com.nexus.launcher.ui.canvas.LayoutProfile.isPhoneLandscape(
            view.resources.configuration
        )
        view.minimumWidth = 0
        view.minimumHeight = 0
        if (!DockPresence.enabled) {
            params.width = 0
            params.height = 0
            params.setMargins(0, 0, 0, 0)
            view.layoutParams = params
            return
        }
        if (isPhoneLandscape) {
            val thickness = (clampedHeightDp * density).toInt()
            val rotation = com.nexus.launcher.ui.canvas.CanvasDisplayHelper.getDisplayRotation(view)
            val dockOnRight = rotation == android.view.Surface.ROTATION_90 || rotation != android.view.Surface.ROTATION_270

            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val insetTop = insets?.top ?: 0
            val insetBottom = insets?.bottom ?: 0
            val insetLeft = insets?.left ?: 0
            val insetRight = insets?.right ?: 0

            val marginBase = (16 * density).toInt()
            val marginTop = insetTop + marginBase
            val marginBottom = insetBottom + marginBase

            params.width = thickness
            params.height = FrameLayout.LayoutParams.MATCH_PARENT
            if (dockOnRight) {
                params.gravity = android.view.Gravity.TOP or android.view.Gravity.RIGHT
                params.setMargins(0, marginTop, insetRight + marginBase, marginBottom)
            } else {
                params.gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
                params.setMargins(insetLeft + marginBase, marginTop, 0, marginBottom)
            }
        } else {
            val marginSide = (16 * density).toInt()
            // Immersive mode has no navigation bar to clear, so the dock sits nearer the edge —
            // but a status row along that edge is something to clear.
            val immersive = com.nexus.launcher.ui.ImmersiveModeController.isActive
            val marginBottom = ((if (immersive) 10 else 24) * density).toInt() +
                com.nexus.launcher.ui.immersive.ImmersiveStatus.reservedBottomPx
            params.width = FrameLayout.LayoutParams.MATCH_PARENT
            val labelExtraDp = if (DockLabelRenderer.showLabels) {
                DockLabelRenderer.labelFontSizeSp + 4
            } else {
                0
            }
            params.height = ((clampedHeightDp + labelExtraDp) * density).toInt()
            params.gravity = android.view.Gravity.BOTTOM
            params.setMargins(marginSide, 0, marginSide, marginBottom)
        }
        view.layoutParams = params
    }
}

internal object DockLayoutHelper {
    internal fun findCanvasView(dock: View): LauncherCanvasView? {
        var parent = dock.parent
        while (parent is android.view.ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is LauncherCanvasView) return child
            }
            parent = parent.parent
        }
        return null
    }
}
