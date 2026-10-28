package com.ming.videowallpaper

import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.roundToInt

class VideoWallpaperService : WallpaperService() {

    override fun onCreate() {
        super.onCreate()
        // The system restarts the wallpaper after a reboot or app update, which clears
        // notifications; put the carrier barcode back.
        CarrierNotification.update(this)
    }

    override fun onCreateEngine(): Engine = VideoEngine()

    /**
     * One engine per wallpaper instance (the system preview gets its own). Engine callbacks, the
     * player and the settings listener all run on the main thread.
     */
    private inner class VideoEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener, Player.Listener {

        private val settings = WallpaperSettings(this@VideoWallpaperService)
        private val mainHandler = Handler(Looper.getMainLooper())
        private lateinit var renderer: VideoRenderer
        private lateinit var player: ExoPlayer

        private var playlist: List<Uri> = emptyList()
        private var index = -1
        private var lastSwitchAt = 0L
        private var hasBeenVisible = false
        private var consecutiveErrors = 0

        private val intervalTick = Runnable {
            next()
            scheduleInterval()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            surfaceHolder.setFormat(PixelFormat.RGBX_8888)
            renderer = VideoRenderer()
            player = ExoPlayer.Builder(this@VideoWallpaperService).build()
            player.setVideoSurface(renderer.inputSurface)
            player.addListener(this)
            applyOutputSettings()
            // SharedPreferences keeps listeners weakly; the engine itself holds the reference.
            settings.prefs.registerOnSharedPreferenceChangeListener(this)
            loadPlaylist()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderer.setOutput(holder.surface)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.requestRender()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            renderer.requestRender()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            renderer.clearOutput()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                if (hasBeenVisible && !isPreview && settings.switchMode == SwitchMode.ON_RETURN) next()
                hasBeenVisible = true
                player.play()
                // Also switches right away if the interval ran out while the wallpaper was hidden.
                scheduleInterval()
            } else {
                player.pause()
                mainHandler.removeCallbacks(intervalTick)
            }
        }

        override fun onDestroy() {
            settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
            mainHandler.removeCallbacksAndMessages(null)
            player.release()
            renderer.release()
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
            when (key) {
                WallpaperSettings.KEY_CURRENT_URI,
                WallpaperSettings.KEY_CARRIER_CODE,
                WallpaperSettings.KEY_CARRIER_NOTIFY -> return
                WallpaperSettings.KEY_PLAYLIST -> loadPlaylist()
                WallpaperSettings.KEY_SOUND_ON, WallpaperSettings.KEY_SCALE_MODE -> applyOutputSettings()
                // Start the countdown from now rather than switching at once.
                WallpaperSettings.KEY_SWITCH_MODE, WallpaperSettings.KEY_INTERVAL_MINUTES ->
                    lastSwitchAt = SystemClock.elapsedRealtime()
                null -> { // Preferences were cleared.
                    applyOutputSettings()
                    loadPlaylist()
                }
            }
            updateRepeatMode()
            scheduleInterval()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> consecutiveErrors = 0
                // Only reached with REPEAT_MODE_OFF, i.e. "switch after each video".
                Player.STATE_ENDED -> next()
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Cannot play ${playlist.getOrNull(index)}", error)
            consecutiveErrors++
            // Skip the broken video, but stop once every video has failed in a row.
            if (consecutiveErrors < playlist.size) mainHandler.post { next() }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            renderer.setVideoSize(
                (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt(),
                videoSize.height,
            )
        }

        private fun loadPlaylist() {
            val current = playlist.getOrNull(index) ?: settings.currentUri
            playlist = settings.playlist
            if (playlist.isEmpty()) {
                index = -1
                player.stop()
                player.clearMediaItems()
                renderer.clearFrame()
                return
            }
            val kept = playlist.indexOf(current)
            when {
                kept < 0 -> play(if (settings.shuffle) playlist.indices.random() else 0)
                player.mediaItemCount == 0 -> play(kept)
                else -> index = kept // Still playing the same video; only its position changed.
            }
            updateRepeatMode()
        }

        private fun next() {
            if (playlist.size < 2) {
                lastSwitchAt = SystemClock.elapsedRealtime()
                return
            }
            val nextIndex =
                if (settings.shuffle) (playlist.indices - index).random()
                else (index + 1) % playlist.size
            play(nextIndex)
        }

        private fun play(newIndex: Int) {
            index = newIndex
            val uri = playlist[newIndex]
            settings.currentUri = uri
            lastSwitchAt = SystemClock.elapsedRealtime()
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = isVisible
        }

        private fun updateRepeatMode() {
            val switchOnEnd = settings.switchMode == SwitchMode.AFTER_EACH && playlist.size > 1
            player.repeatMode = if (switchOnEnd) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        }

        private fun applyOutputSettings() {
            renderer.setScaleMode(settings.scaleMode)
            // Disabling the audio track (rather than muting) also skips audio decoding.
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !settings.soundOn)
                .build()
        }

        private fun scheduleInterval() {
            mainHandler.removeCallbacks(intervalTick)
            if (!isVisible || settings.switchMode != SwitchMode.INTERVAL || playlist.size < 2) return
            val intervalMs = settings.intervalMinutes * 60_000L
            val remaining = intervalMs - (SystemClock.elapsedRealtime() - lastSwitchAt)
            mainHandler.postDelayed(intervalTick, remaining.coerceAtLeast(0))
        }
    }

    private companion object {
        const val TAG = "VideoWallpaper"
    }
}
