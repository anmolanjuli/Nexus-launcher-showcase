package com.nexus.launcher.ui.widgets.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.RemoteViews
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

class NexusMusicWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.nexus.launcher.music.PLAY_PAUSE"
        const val ACTION_NEXT = "com.nexus.launcher.music.NEXT"
        const val ACTION_PREV = "com.nexus.launcher.music.PREV"

        fun triggerHaptic(context: Context) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(25)
                }
            } catch (_: Exception) {}
        }

        @Volatile
        private var isUpdatingWidgets = false

        fun updateAllWidgets(context: Context) {
            if (isUpdatingWidgets) return
            isUpdatingWidgets = true
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val component = android.content.ComponentName(context, NexusMusicWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(component)
                if (appWidgetIds.isEmpty()) return
                val provider = NexusMusicWidgetProvider()
                for (id in appWidgetIds) {
                    provider.updateWidget(context, appWidgetManager, id, null)
                }
            } finally {
                isUpdatingWidgets = false
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        NexusMusicManager.startListening(context)

        val controller = NexusMusicManager.getActiveController()
        when (intent.action) {
            "com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED" -> {
                val widgetId = intent.getIntExtra("appWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val info = appWidgetManager.getAppWidgetInfo(widgetId)
                    if (info?.provider?.className == this::class.java.name) {
                        MusicWidgetChrome.invalidate(widgetId)
                        RetroMusicAnimationCoordinator.invalidateTarget()
                        updateWidget(context, appWidgetManager, widgetId, null)
                    }
                }
            }
            ACTION_PLAY_PAUSE -> {
                triggerHaptic(context)
                val ctrl = controller ?: NexusMusicManager.getActiveController()
                val currentlyPlaying = NexusMusicManager.isPlaybackPlaying()
                val targetPlaying = !currentlyPlaying

                NexusMusicManager.setOptimisticPlayback(targetPlaying)

                if (currentlyPlaying) {
                    ctrl?.transportControls?.pause()
                } else {
                    ctrl?.transportControls?.play()
                }

                updateAllWidgets(context)
            }
            ACTION_NEXT -> {
                triggerHaptic(context)
                controller?.transportControls?.skipToNext()
            }
            ACTION_PREV -> {
                triggerHaptic(context)
                controller?.transportControls?.skipToPrevious()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        NexusMusicManager.startListening(context)
        RetroMusicAnimationCoordinator.invalidateTarget()
        for (id in appWidgetIds) {
            MusicWidgetChrome.invalidate(id)
            updateWidget(context, appWidgetManager, id, null)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        MusicWidgetChrome.invalidate(appWidgetId)
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            NexusWidgetConfig.delete(context, id)
            MusicWidgetChrome.invalidate(id)
            RetroMusicAnimationCoordinator.invalidateTarget()
            RetroMusicBitmapPool.release(id)
            RetroMusicStaticCache.release(id)
        }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = android.content.ComponentName(context, NexusMusicWidgetProvider::class.java)
        if (appWidgetManager.getAppWidgetIds(component).isEmpty()) {
            NexusMusicManager.stopListening()
            RetroMusicAnimationCoordinator.stop()
        }
    }

    /**
     * @param fromFrame true when the animation coordinator is driving this: the widget's size,
     *   settings, permission state and click targets are taken from [MusicWidgetChrome] instead
     *   of being worked out again fifteen times a second.
     */
    internal fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        options: Bundle?,
        fromFrame: Boolean = false,
    ) {
        val chrome = MusicWidgetChrome.of(context, appWidgetManager, appWidgetId, options, reuse = fromFrame)

        val controller = NexusMusicManager.getActiveController()
        val state = controller?.playbackState
        val metadata = controller?.metadata
        val isPlaying = NexusMusicManager.isPlaybackPlaying()

        var progress = -1f
        if (state != null && metadata != null) {
            val duration = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
            if (duration > 0) {
                val elapsed = if (isPlaying && state.lastPositionUpdateTime > 0L) {
                    (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0L)
                } else {
                    0L
                }
                val livePosition = state.position + (elapsed * state.playbackSpeed).toLong()
                progress = (livePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            }
        }

        val targetBmp = RetroMusicBitmapPool.acquire(appWidgetId, chrome.widthPx, chrome.heightPx)
        val bmp = NexusMusicRenderer().render(
            context, chrome.widthPx, chrome.heightPx, chrome.config,
            chrome.minWidthDp, chrome.minHeightDp, progress, targetBmp,
        )

        // A fresh RemoteViews each time: its actions are appended, never replaced, so one kept
        // instance would grow by a bitmap every frame.
        val views = RemoteViews(context.packageName, chrome.layoutId)
        views.setImageViewBitmap(com.nexus.launcher.R.id.widget_canvas, bmp)
        chrome.applyTo(views)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
