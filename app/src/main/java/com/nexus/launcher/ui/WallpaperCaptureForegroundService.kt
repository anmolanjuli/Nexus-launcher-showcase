package com.nexus.launcher.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexus.launcher.R

/**
 * Minimal foreground service that exists ONLY to satisfy Android 14+ (API 34)'s requirement that
 * a foreground service declared with `foregroundServiceType="mediaProjection"` be actively running
 * before `MediaProjectionManager.getMediaProjection()` will succeed. It lives for the few hundred
 * milliseconds [WallpaperFrostCapture] needs to grab one clean wallpaper frame, then is stopped
 * immediately — it does not keep capturing or run in the background otherwise.
 */
class WallpaperCaptureForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // Only now is the service guaranteed to actually be in the foreground state that
        // getMediaProjection() checks for — signal the caller waiting on this via onReady.
        pendingReadyCallback?.let { callback ->
            pendingReadyCallback = null
            Handler(Looper.getMainLooper()).post { callback() }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, this.getString(com.nexus.launcher.R.string.frost_capture_channel), NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(this.getString(com.nexus.launcher.R.string.frost_capture_notification))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "wallpaper_capture"
        private const val NOTIFICATION_ID = 4821

        @Volatile private var pendingReadyCallback: (() -> Unit)? = null

        /** Starts the service and invokes [onReady] once it has actually entered the foreground state. */
        fun start(context: Context, onReady: () -> Unit) {
            pendingReadyCallback = onReady
            ContextCompat.startForegroundService(
                context, Intent(context, WallpaperCaptureForegroundService::class.java)
            )
        }

        fun stop(context: Context) {
            pendingReadyCallback = null
            context.stopService(Intent(context, WallpaperCaptureForegroundService::class.java))
        }
    }
}
