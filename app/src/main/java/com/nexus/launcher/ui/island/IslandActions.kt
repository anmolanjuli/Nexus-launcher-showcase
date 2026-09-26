package com.nexus.launcher.ui.island

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.provider.CalendarContract
import android.provider.Settings
import com.nexus.launcher.ui.widgets.music.NexusMusicManager

object IslandActions {
    fun launch(context: Context, payload: IslandPayload) {
        when (payload.kind) {
            IslandKind.MUSIC -> launchPackage(context, payload.packageName)
            IslandKind.TIMER, IslandKind.STOPWATCH -> launchPackage(context, payload.packageName)
            IslandKind.BATTERY_LOW, IslandKind.CHARGING -> {
                context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            IslandKind.DND -> {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            IslandKind.BLUETOOTH -> {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            IslandKind.CALL -> {
                payload.answerIntent?.let {
                    try { it.send() } catch (_: Exception) {}
                } ?: launchPackage(context, payload.packageName)
            }
            IslandKind.CALENDAR -> openEvent(context, payload.eventId)
            // The app said where its live work lives; failing that, just open the app.
            IslandKind.ACTIVITY -> {
                payload.contentIntent?.let {
                    try { it.send() } catch (_: Exception) {}
                } ?: launchPackage(context, payload.packageName)
            }
        }
    }

    fun answerCall(payload: IslandPayload) {
        try {
            payload.answerIntent?.send()
        } catch (_: Exception) {}
    }

    fun declineCall(payload: IslandPayload) {
        try {
            payload.declineIntent?.send()
        } catch (_: Exception) {}
    }

    fun secondaryAction(context: Context, payload: IslandPayload) {
        when (payload.kind) {
            IslandKind.CALL -> answerCall(payload)
            IslandKind.MUSIC -> {
                val controls = NexusMusicManager.getActiveController()?.transportControls ?: return
                if (NexusMusicManager.isPlaybackPlaying()) controls.pause() else controls.play()
            }
            IslandKind.CALENDAR -> openEvent(context, payload.eventId)
            else -> launch(context, payload)
        }
    }

    fun skipTrack(next: Boolean) {
        val controller = NexusMusicManager.getActiveController() ?: return
        val state = controller.playbackState
        val action = if (next) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (state != null && state.actions and action != 0L) {
            if (next) controller.transportControls.skipToNext() else controller.transportControls.skipToPrevious()
        }
    }

    private fun openEvent(context: Context, eventId: Long) {
        val intent = if (eventId > 0L) {
            Intent(Intent.ACTION_VIEW).setData(CalendarContract.Events.CONTENT_URI.buildUpon().appendPath(eventId.toString()).build())
        } else {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
        }
        launchIntent(context, intent)
    }

    private fun launchPackage(context: Context, packageName: String?) {
        if (packageName.isNullOrBlank()) return
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent(context, launch)
    }

    private fun launchIntent(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
