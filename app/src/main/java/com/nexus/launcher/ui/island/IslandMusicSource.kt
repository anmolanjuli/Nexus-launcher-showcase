package com.nexus.launcher.ui.island

import android.content.Context
import android.media.MediaMetadata
import android.os.Handler
import android.os.Looper
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.music.NexusMusicManager
import com.nexus.launcher.ui.widgets.music.RetroMusicArtDecoder

class IslandMusicSource(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val onSession: () -> Unit = { emit() }
    private val tick = object : Runnable {
        override fun run() {
            emit()
            if (ticking) handler.postDelayed(this, 1000L)
        }
    }
    private var emitting = false
    private var ticking = false

    fun start() {
        NexusMusicManager.startListening(context)
        NexusMusicManager.addSessionHook(onSession)
        handler.removeCallbacks(tick)
        emit()
    }

    fun stop() {
        ticking = false
        handler.removeCallbacks(tick)
        NexusMusicManager.removeSessionHook(onSession)
        IslandTriggerBus.publish(IslandKind.MUSIC, null)
    }

    /**
     * The session hook reports every change the player makes; the once-a-second tick is only
     * for the position moving while something is actually playing.
     */
    private fun setTicking(now: Boolean) {
        if (ticking == now) return
        ticking = now
        handler.removeCallbacks(tick)
        if (now) handler.postDelayed(tick, 1000L)
    }

    private fun emit() {
        if (emitting) return
        emitting = true
        try {
            val controller = NexusMusicManager.getActiveController()
            val playing = NexusMusicManager.isPlaybackPlaying()
            val meta = controller?.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            setTicking(playing)
            if (controller == null || (!playing && title.isNullOrBlank())) {
                IslandTriggerBus.publish(IslandKind.MUSIC, null)
                return
            }
            val name = title?.takeIf { it.isNotBlank() } ?: appLabel(controller.packageName)
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            val density = context.resources.displayMetrics.density
            val art = RetroMusicArtDecoder.getScaledAlbumArt(context, meta, (48f * density).toInt())
            val line = if (playing) {
                context.getString(R.string.island_playing, name)
            } else {
                context.getString(R.string.island_paused, name)
            }
            IslandTriggerBus.publish(
                IslandKind.MUSIC,
                IslandPayload(
                    kind = IslandKind.MUSIC,
                    title = line,
                    subtitle = artist,
                    packageName = controller.packageName,
                    art = art,
                    playing = playing,
                    identity = "music:$name",
                ),
            )
        } finally {
            emitting = false
        }
    }

    private fun appLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    }.getOrElse { context.getString(R.string.island_trigger_music) }
}
