package com.nexus.launcher.ui.widgets.music

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import com.nexus.launcher.service.NexusNotificationService

object NexusMusicManager {
    private var isListening = false
    private var activeController: MediaController? = null
    private var sessionManager: MediaSessionManager? = null
    private var contextRef: Context? = null

    private var optimisticPlaying: Boolean? = null
    private val clearOptimisticRunnable = Runnable {
        optimisticPlaying = null
        notifyWidgets()
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            progressHandler.removeCallbacks(clearOptimisticRunnable)
            optimisticPlaying = null
            val nowPlaying = state?.state == PlaybackState.STATE_PLAYING
            isPlaying = nowPlaying
            progressHandler.removeCallbacks(progressRunnable)
            if (isPlaying) {
                progressHandler.post(progressRunnable)
                contextRef?.let { RetroMusicAnimationCoordinator.start(it) }
            } else {
                RetroMusicAnimationCoordinator.stop()
            }
            notifyWidgets()
            fireSessionHooks()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            notifyWidgets()
            fireSessionHooks()
        }

        override fun onSessionDestroyed() {
            activeController = null
            optimisticPlaying = null
            isPlaying = false
            RetroMusicAnimationCoordinator.stop()
            progressHandler.removeCallbacks(progressRunnable)
            progressHandler.removeCallbacks(clearOptimisticRunnable)
            resyncActiveController()
            notifyWidgets()
            fireSessionHooks()
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(findBestController(controllers))
    }

    fun startListening(context: Context) {
        contextRef = context.applicationContext
        val app = context.applicationContext
        // Art arrives after the frame that asked for it, so that frame is drawn again.
        RetroMusicArtDecoder.onArtDecoded = { notifyWidgets() }
        sessionManager = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val componentName = ComponentName(app, NexusNotificationService::class.java)

        try {
            if (hasNotificationAccess(app)) {
                if (!isListening) {
                    sessionManager?.addOnActiveSessionsChangedListener(sessionListener, componentName)
                    isListening = true
                }
                resyncActiveController()
            }
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    fun stopListening() {
        if (!isListening) return
        sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        isListening = false
        RetroMusicAnimationCoordinator.stop()
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.removeCallbacks(clearOptimisticRunnable)
        optimisticPlaying = null
        isPlaying = false
        contextRef = null
    }

    private var isPlaying = false
    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                notifyWidgets()
                progressHandler.postDelayed(this, 1000)
            }
        }
    }

    private fun findBestController(controllers: List<MediaController>?): MediaController? {
        if (controllers.isNullOrEmpty()) return null
        val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (playing != null) return playing
        val withMeta = controllers.firstOrNull {
            val meta = it.metadata
            meta != null && !meta.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrEmpty()
        }
        if (withMeta != null) return withMeta
        return controllers.firstOrNull()
    }

    private var isResyncing = false

    private fun resyncActiveController() {
        if (isResyncing) return
        isResyncing = true
        try {
            val app = contextRef ?: return
            val componentName = ComponentName(app, NexusNotificationService::class.java)
            val active = sessionManager?.getActiveSessions(componentName)
            updateActiveController(findBestController(active))
        } catch (_: SecurityException) {
        } finally {
            isResyncing = false
        }
    }

    private fun updateActiveController(controller: MediaController?) {
        val old = activeController
        val changed = old != controller
        if (changed) {
            old?.unregisterCallback(controllerCallback)
            activeController = controller
            activeController?.registerCallback(controllerCallback)
        }

        val state = activeController?.playbackState?.state
        val newPlaying = state == PlaybackState.STATE_PLAYING
        val playStateChanged = isPlaying != newPlaying
        isPlaying = newPlaying
        progressHandler.removeCallbacks(progressRunnable)
        if (isPlaying) {
            progressHandler.post(progressRunnable)
            contextRef?.let { RetroMusicAnimationCoordinator.start(it) }
        } else {
            RetroMusicAnimationCoordinator.stop()
        }

        if (changed || playStateChanged) {
            notifyWidgets()
            fireSessionHooks()
        }
    }

    fun setOptimisticPlayback(playing: Boolean) {
        optimisticPlaying = playing
        progressHandler.removeCallbacks(clearOptimisticRunnable)
        progressHandler.postDelayed(clearOptimisticRunnable, 2500L)
        if (playing) {
            contextRef?.let { RetroMusicAnimationCoordinator.start(it) }
        } else {
            RetroMusicAnimationCoordinator.stop()
        }
        notifyWidgets()
    }

    fun isPlaybackPlaying(): Boolean {
        optimisticPlaying?.let { return it }
        val controller = getActiveController()
        return controller?.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    fun notifyWidgets() {
        val ctx = contextRef ?: return
        NexusMusicWidgetProvider.updateAllWidgets(ctx)
    }

    fun getActiveController(): MediaController? {
        if (activeController == null && !isResyncing) {
            resyncActiveController()
        }
        return activeController
    }

    fun addSessionHook(hook: () -> Unit) {
        if (hook !in sessionHooks) sessionHooks.add(hook)
    }

    fun removeSessionHook(hook: () -> Unit) {
        sessionHooks.remove(hook)
    }

    private fun fireSessionHooks() {
        sessionHooks.toList().forEach { it.invoke() }
    }

    private val sessionHooks = mutableListOf<() -> Unit>()

    fun hasNotificationAccess(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
