package com.nexus.launcher.ui.widgets.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.SparseArray
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * Everything about a music widget that does not change from frame to frame.
 *
 * A playing retro widget is redrawn fifteen times a second, and each of those frames used to
 * re-read the widget's options over binder, re-read its settings, ask the system which apps
 * have notification access, and build four fresh [PendingIntent]s — an IPC apiece — before any
 * pixel was drawn. None of that changes while a song plays, so it is worked out once and kept
 * until something that would change it happens: a resize, a settings change, or a different app
 * taking over playback.
 *
 * The [RemoteViews] itself is deliberately not cached: its actions are appended, not replaced,
 * so reusing one instance across frames would grow the parcel sent to the launcher each time.
 * Applying already-built intents to a fresh one costs nothing.
 */
internal object MusicWidgetChrome {

    class Chrome(
        val layoutId: Int,
        val minWidthDp: Int,
        val minHeightDp: Int,
        val widthPx: Int,
        val heightPx: Int,
        val isTiny: Boolean,
        val hasAccess: Boolean,
        val config: NexusWidgetConfig.InstanceConfig,
        val controllerPackage: String?,
        private val canvas: PendingIntent?,
        private val playPause: PendingIntent?,
        private val next: PendingIntent?,
        private val prev: PendingIntent?,
    ) {
        fun applyTo(views: RemoteViews) {
            canvas?.let { views.setOnClickPendingIntent(R.id.widget_canvas, it) }
            if (!hasAccess) return
            playPause?.let { views.setOnClickPendingIntent(R.id.btn_play_pause, it) }
            if (isTiny) return
            next?.let { views.setOnClickPendingIntent(R.id.btn_next, it) }
            prev?.let { views.setOnClickPendingIntent(R.id.btn_prev, it) }
        }
    }

    private val cache = SparseArray<Chrome>()

    /**
     * @param reuse true for an animation frame, which may take what is already worked out;
     *   false for a real update, which rebuilds it.
     */
    fun of(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        options: android.os.Bundle?,
        reuse: Boolean,
    ): Chrome {
        val controllerPackage = NexusMusicManager.getActiveController()?.packageName
        if (reuse && options == null) {
            val cached = cache.get(appWidgetId)
            // A different app is playing, so the tap-through target has changed with it.
            if (cached != null && cached.controllerPackage == controllerPackage) return cached
        }
        val built = build(context, manager, appWidgetId, options, controllerPackage)
        cache.put(appWidgetId, built)
        return built
    }

    fun invalidate(appWidgetId: Int) = cache.remove(appWidgetId)

    fun invalidateAll() = cache.clear()

    private fun build(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        options: android.os.Bundle?,
        controllerPackage: String?,
    ): Chrome {
        val opts = options ?: manager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 100)
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)
        val config = NexusWidgetConfig.read(context, appWidgetId)
        val hasAccess = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

        val isTiny = minWidthDp < 115 && minHeightDp < 115
        val isVerticalTall = minWidthDp < 135 && minHeightDp >= 135
        val isLarge = minWidthDp >= 175 && minHeightDp >= 200
        val layoutId = if (!hasAccess) {
            R.layout.widget_nexus_permission_canvas
        } else when {
            isTiny -> R.layout.widget_music_tiny
            isVerticalTall -> R.layout.widget_music_small
            isLarge -> R.layout.widget_music_large
            else -> R.layout.widget_music_medium
        }

        val density = context.resources.displayMetrics.density
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return Chrome(
            layoutId = layoutId,
            minWidthDp = minWidthDp,
            minHeightDp = minHeightDp,
            widthPx = (minWidthDp * density).toInt().coerceAtLeast(1),
            heightPx = (minHeightDp * density).toInt().coerceAtLeast(1),
            isTiny = isTiny,
            hasAccess = hasAccess,
            config = config,
            controllerPackage = controllerPackage,
            canvas = if (hasAccess) canvasIntent(context, flags) else settingsIntent(context, flags),
            playPause = if (hasAccess) broadcast(context, 0, NexusMusicWidgetProvider.ACTION_PLAY_PAUSE, flags) else null,
            next = if (hasAccess && !isTiny) broadcast(context, 1, NexusMusicWidgetProvider.ACTION_NEXT, flags) else null,
            prev = if (hasAccess && !isTiny) broadcast(context, 2, NexusMusicWidgetProvider.ACTION_PREV, flags) else null,
        )
    }

    /** Tapping the artwork opens whatever is playing. */
    private fun canvasIntent(context: Context, flags: Int): PendingIntent? {
        val controller = NexusMusicManager.getActiveController()
        controller?.sessionActivity?.let { return it }
        val launch = controller?.packageName?.let { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)
        } ?: return PendingIntent.getBroadcast(context, 0, Intent(), flags)
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return PendingIntent.getActivity(context, 0, launch, flags)
    }

    private fun settingsIntent(context: Context, flags: Int): PendingIntent {
        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    private fun broadcast(context: Context, request: Int, action: String, flags: Int): PendingIntent {
        val intent = Intent(context, NexusMusicWidgetProvider::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(context, request, intent, flags)
    }
}
