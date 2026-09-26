package com.nexus.launcher.ui.island

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

object IslandController {
    private const val TAG = "NexusIsland"
    private const val IDLE_HIDE_MS = 30_000L
    private const val IDLE_ALPHA = 0.45f

    private var view: IslandView? = null
    private var scrim: IslandScrimView? = null
    private var animator: IslandAnimator? = null
    private var hookedCanvas: LauncherCanvasView? = null
    private var drawerAlpha = 1f
    private var enabled = false
    private var settings: NexusSettingsData? = null
    private val session = IslandSession()
    private val handler = Handler(Looper.getMainLooper())
    private var sources: IslandSources? = null
    private var observing = false

    /**
     * Nothing has happened for a while, so the resting capsule steps back — but it stays on
     * screen and still takes touches. It used to be hidden outright, which left the dock and
     * the widgets unreachable until some other event happened to wake it.
     */
    private val idleDim = Runnable {
        if (!session.pinned && session.primary == null && session.shape == IslandShape.DORMANT) {
            session.idleDimmed = true
            view?.let { it.alpha = IDLE_ALPHA * drawerAlpha }
        }
    }

    private val chromeTick = object : Runnable {
        override fun run() {
            notifyChrome()
            if (enabled) handler.postDelayed(this, 1000L)
        }
    }

    fun apply(activity: MainActivity, data: NexusSettingsData) {
        settings = data
        enabled = data.islandEnabled
        if (!enabled) {
            stopSources()
            handler.removeCallbacks(chromeTick)
            view?.visibility = View.GONE
            return
        }
        attach(activity)
        session.idleDimmed = false
        val island = view ?: return
        island.position = data.islandPosition
        island.compactSize = data.islandCompactSize
        island.animSpeed = data.islandAnimSpeed
        island.calibratedWidthDp = data.islandWidthDp
        island.calibratedHeightDp = data.islandHeightDp
        island.xOffsetDp = data.islandXOffsetDp
        island.yOffsetDp = data.islandYOffsetDp
        island.settingsData = data
        IslandGlanceDraw.countShape =
            IslandGlanceDraw.CountShape(data.statusShowSilent, data.statusGroupByApp)
        IslandPlacementHelper.syncPlacement(island) { morph(bounce = false) }
        handler.removeCallbacks(chromeTick)
        handler.post(chromeTick)
        refreshVisibility(activity)
        startSources(activity)
    }

    fun notifyChrome() {
        val island = view ?: return
        val activity = island.context as? MainActivity ?: return
        refreshVisibility(activity)
    }

    fun onBatteryLowPulse() {
        animator?.pulseDanger()
    }

    private fun attach(activity: MainActivity) {
        view?.let {
            if (it.isAttachedToWindow) return
        }
        val root = activity.findViewById<FrameLayout>(R.id.main_container) ?: return
        val created = IslandView(activity)
        created.tag = TAG
        created.onWidthChanged = { IslandPlacementHelper.syncPlacement(created) { morph(bounce = false) } }
        val index = IslandPlacementHelper.indexAboveStatus(root, activity)
        scrim = IslandScrimView.install(root, index) {
            session.collapse()
            view?.let { island ->
                applySession(island)
                morph(bounce = false)
            }
        }
        root.addView(
            created, index + 1,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.LEFT,
            ),
        )
        view = created
        animator = IslandAnimator(created)
        bindGestures(activity, created)
        hookDrawer(activity)
        observe(activity)
        ViewCompat.setOnApplyWindowInsetsListener(created) { v, insets ->
            IslandPlacementHelper.syncPlacement(v as IslandView) { morph(bounce = false) }
            insets
        }
        ViewCompat.requestApplyInsets(created)
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) detach(activity)
        })
    }

    private fun bindGestures(activity: MainActivity, island: IslandView) {
        island.onTap = {
            when (session.tap()) {
                IslandTapResult.EXPAND -> {
                    island.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    // Opens on whatever the capsule was showing, not always the first page.
                    island.expandedPage = session.selectedIndex.coerceIn(0, island.pages.lastIndex.coerceAtLeast(0))
                    applySession(island)
                    morph(bounce = false)
                }
                IslandTapResult.RETRACT -> {
                    island.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    applySession(island)
                    morph(bounce = false)
                }
                IslandTapResult.LAUNCH -> session.primary?.let { IslandActions.launch(activity, it) }
                IslandTapResult.NONE -> Unit
            }
            bumpIdle()
        }
        island.onLaunch = {
            val prim = session.primary
            val content = prim?.contentIntent
            if (content != null) {
                runCatching { content.send() }
            } else if (prim != null) {
                IslandActions.launch(activity, prim)
            }
        }
        island.onDoubleTap = {
            session.swap()
            applySession(island)
            morph(bounce = true)
        }
        island.onLongPress = {
            // Pinned, the island stays put and bright until it is pinned again.
            island.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            session.togglePin()
            bumpIdle()
        }
        island.onSwipeUp = {
            session.collapse()
            applySession(island)
            morph(bounce = false)
            bumpIdle()
        }
        island.onSwipeLeft = {
            if (session.shape == IslandShape.EXPANDED && island.pages.size > 1) {
                island.expandedPage = (island.expandedPage + 1) % island.pages.size
                session.select(island.expandedPage)
                island.bindPage()
                island.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                morph(bounce = false)
                island.invalidate()
                bumpIdle()
            } else if (session.cycleNext()) {
                island.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                applySession(island)
                morph(bounce = true)
                bumpIdle()
            }
        }
        island.onSwipeRight = {
            if (session.shape == IslandShape.EXPANDED && island.pages.size > 1) {
                island.expandedPage = (island.expandedPage - 1 + island.pages.size) % island.pages.size
                session.select(island.expandedPage)
                island.bindPage()
                island.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                morph(bounce = false)
                island.invalidate()
                bumpIdle()
            } else if (session.cyclePrev()) {
                island.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                applySession(island)
                morph(bounce = true)
                bumpIdle()
            }
        }
        island.onPlayToggle = {
            session.primary?.let { IslandActions.secondaryAction(activity, it) }
        }
        island.onNext = {
            val prim = session.primary
            if (prim?.kind == IslandKind.CALL) {
                IslandActions.answerCall(prim)
                session.collapse()
                applySession(island)
            } else {
                IslandActions.skipTrack(next = true)
            }
        }
        island.onPrev = {
            val prim = session.primary
            if (prim?.kind == IslandKind.CALL) {
                IslandActions.declineCall(prim)
                session.collapse()
                applySession(island)
            } else {
                IslandActions.skipTrack(next = false)
            }
        }
        island.onAction = {
            session.primary?.let { IslandActions.secondaryAction(activity, it) }
        }
        island.onActivityAction = { index ->
            // The app's own button, sent back to the app that offered it.
            val action = (island.pagePayload() ?: session.primary)?.actions?.getOrNull(index)
            if (action != null) {
                runCatching { action.intent.send() }
                bumpIdle()
            }
        }
    }

    private fun observe(activity: MainActivity) {
        if (observing) return
        observing = true
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    IslandTriggerBus.active.collect { list ->
                        val filtered = list.filter { payload ->
                            val data = settings ?: return@filter false
                            IslandPrefs.triggerEnabled(data, payload.kind)
                        }
                        val before = session.primary?.identity
                        session.applyActive(filtered)
                        applySession(view ?: return@collect)
                        morph(bounce = session.primary?.identity != null && session.primary?.identity != before)
                        bumpIdle()
                        refreshVisibility(activity)
                    }
                }
                launch {
                    try {
                        val controller = EntryPointAccessors.fromApplication(
                            activity.applicationContext,
                            ThemeEntryPoint::class.java,
                        ).themeController()
                        controller.currentTokens.collect { tokens ->
                            view?.applyTokens(tokens)
                        }
                    } catch (_: Exception) { }
                }
            }
        }
    }

    private fun hookDrawer(activity: MainActivity) {
        val canvas = activity.canvasView
        if (hookedCanvas === canvas) return
        hookedCanvas = canvas
        val previous = canvas.onDrawerAnimationProgress
        canvas.onDrawerAnimationProgress = { progress ->
            previous?.invoke(progress)
            drawerAlpha = (1f - progress * 2f).coerceIn(0f, 1f)
            refreshVisibility(activity)
        }
    }

    private fun startSources(activity: MainActivity) {
        if (sources != null) return
        sources = IslandSources(activity).apply { start() }
    }

    private fun stopSources() {
        sources?.stop()
        sources = null
    }

    private fun detach(activity: MainActivity) {
        stopSources()
        handler.removeCallbacks(idleDim)
        handler.removeCallbacks(chromeTick)
        animator?.cancel()
        val root = activity.findViewById<FrameLayout>(R.id.main_container)
        view?.let { root?.removeView(it) }
        scrim?.let { root?.removeView(it) }
        view = null
        scrim = null
        animator = null
        observing = false
        hookedCanvas = null
    }

    private fun applySession(island: IslandView) {
        island.live = session.active
        island.pages = IslandPages.of(session.active)
        // Opened on a live page, stay on it while its app updates; only fall back when the page
        // it was on has gone.
        if (island.expandedPage >= island.pages.size) {
            island.expandedPage = (island.pages.size - 1).coerceAtLeast(0)
        }
        if (session.shape == IslandShape.EXPANDED) session.select(island.expandedPage)
        island.bind(session.shape, session.primary, session.secondary)
    }

    private fun morph(bounce: Boolean) {
        val island = view ?: return
        val data = settings ?: return
        val density = island.resources.displayMetrics.density
        // The open card is as tall as the page it is about to draw, not a fixed card height.
        val openHeightDp = IslandContentMeasure.heightDp(
            island.currentPage(), island.pagePayload(), island.pages.size,
        )
        val (w, h) = IslandGeometry.targetPx(
            session.shape,
            data.islandCompactSize,
            island.width.toFloat(),
            density,
            data.islandWidthDp,
            data.islandHeightDp,
            openHeightDp,
        )
        animator?.morphTo(w, h, data.islandAnimSpeed, bounce)
        if (bounce) animator?.crossfade()
        IslandScrimView.show(scrim, session.shape == IslandShape.EXPANDED, data.islandAnimSpeed)
    }

    private fun bumpIdle() {
        handler.removeCallbacks(idleDim)
        session.idleDimmed = false
        view?.alpha = drawerAlpha
        if (!session.pinned) handler.postDelayed(idleDim, IDLE_HIDE_MS)
    }

    private fun refreshVisibility(activity: MainActivity) {
        val island = view ?: return
        val show = IslandVisibility.shouldShow(activity, enabled, drawerAlpha)
        val idle = session.idleDimmed && session.primary == null
        island.alpha = if (show) drawerAlpha * (if (idle) IDLE_ALPHA else 1f) else 0f
        island.visibility = if (show && drawerAlpha > 0.05f) View.VISIBLE else View.GONE
        island.isEnabled = show
    }
}
