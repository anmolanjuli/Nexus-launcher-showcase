package com.nexus.launcher.ui.widgets.music

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.view.Choreographer
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout
import java.lang.ref.WeakReference

/**
 * Global 15 FPS animation coordinator for Retro Music widgets.
 *
 * Implements:
 * 1. Global 15 FPS frame throttling: the next frame is asked for 66ms ahead
 *    ([Choreographer.postFrameCallbackDelayed]) rather than every frame at 60Hz with three out
 *    of four thrown away — those woke the main thread 45 times a second for nothing.
 * 2. Active playing widget filtering: only the single active playing retro widget animates.
 * 3. Off-screen suppression: widgets not on the current visible home screen page never animate.
 * 4. Immediate loop termination when music pauses or screen turns off.
 *
 * Which widget is the target is worked out from the widget list and each widget's settings,
 * which is far too much to do fifteen times a second: it is resolved when the loop starts,
 * when the home screen page changes, and otherwise once every few seconds.
 */
object RetroMusicAnimationCoordinator {

    private const val FRAME_INTERVAL_MS = 66L // 15 FPS

    private var appContext: Context? = null
    private var overlayRef: WeakReference<WidgetOverlayLayout>? = null
    private var isRunning = false
    private var isPaused = false

    /** The widget being animated, and when it was last looked up. */
    private var targetWidgetId = -1
    private var targetPage = -1
    private var targetResolvedAtMs = 0L
    private const val TARGET_TTL_MS = 3_000L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning || isPaused) return
            tickActiveWidget()
            if (isRunning && !isPaused) {
                Choreographer.getInstance().postFrameCallbackDelayed(this, FRAME_INTERVAL_MS)
            }
        }
    }

    fun bindOverlay(overlay: WidgetOverlayLayout) {
        overlayRef = WeakReference(overlay)
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        isPaused = false
        RetroMusicVisualizerData.onPlaybackStateChanged(true)

        if (isRunning) return
        isRunning = true

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            android.os.Handler(Looper.getMainLooper()).post {
                if (isRunning && !isPaused) {
                    Choreographer.getInstance().removeFrameCallback(frameCallback)
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }
            }
        }
    }

    /** Something changed that could move the animation to a different widget. */
    fun invalidateTarget() {
        targetWidgetId = -1
        targetResolvedAtMs = 0L
    }

    fun stop() {
        isRunning = false
        invalidateTarget()
        RetroMusicVisualizerData.onPlaybackStateChanged(false)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } else {
            android.os.Handler(Looper.getMainLooper()).post {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
        }
    }

    fun pause() {
        isPaused = true
    }

    fun resume(context: Context) {
        if (isPaused) {
            isPaused = false
            if (isRunning && NexusMusicManager.isPlaybackPlaying()) {
                start(context)
            }
        }
    }

    private fun tickActiveWidget() {
        val ctx = appContext ?: return
        if (!NexusMusicManager.isPlaybackPlaying()) {
            stop()
            return
        }
        val appWidgetManager = AppWidgetManager.getInstance(ctx) ?: return
        val currentPage = overlayRef?.get()?.canvasView?.currentPage ?: 0
        val now = System.currentTimeMillis()
        if (targetResolvedAtMs == 0L || currentPage != targetPage || now - targetResolvedAtMs > TARGET_TTL_MS) {
            val resolved = resolveTarget(ctx, appWidgetManager, currentPage)
            if (resolved == null) {
                // No music widget left anywhere: nothing to animate for.
                stop()
                return
            }
            targetWidgetId = resolved
            targetPage = currentPage
            targetResolvedAtMs = now
        }
        // Nothing of ours on the page in front of the user: the loop idles until it comes back.
        if (targetWidgetId == -1) return
        NexusMusicWidgetProvider().updateWidget(ctx, appWidgetManager, targetWidgetId, null, fromFrame = true)
    }

    /**
     * The one retro widget on the page in front of the user, -1 when there is none on this page,
     * and null when there is no music widget left at all — which ends the loop.
     */
    private fun resolveTarget(ctx: Context, manager: AppWidgetManager, currentPage: Int): Int? {
        val component = ComponentName(ctx, NexusMusicWidgetProvider::class.java)
        val appWidgetIds = try {
            manager.getAppWidgetIds(component)
        } catch (_: Exception) {
            IntArray(0)
        }
        if (appWidgetIds.isEmpty()) return null
        val overlay = overlayRef?.get()
        for (id in appWidgetIds) {
            val item = overlay?.liveItems?.get(id)
            val isOnCurrentPage = if (item != null) item.page == currentPage else true
            if (!isOnCurrentPage) continue
            // Only retro widgets (styles 1..4) have active 15 FPS animations
            if (NexusWidgetConfig.read(ctx, id).clockStyle > 0) return id
        }
        // Nothing to animate on this page: keep the loop, the page may come back.
        return -1
    }
}
