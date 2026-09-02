package com.kurixutian.oreotunes.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.kurixutian.oreotunes.MainActivity
import com.kurixutian.oreotunes.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class MediaNotificationHelper(
    private val context: Context,
    private val player: ExoPlayer
) {
    private val channelId = "oreo_playback_channel"
    private val notificationId = 1001
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var currentSong: Song? = null
    private var cachedArtwork: Bitmap? = null

    init {
        createChannel()
        setupPlayerListener()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    // Keep or clear notification
                } else {
                    updateNotification()
                }
            }
        })
    }

    fun updateMetadata(song: Song?) {
        currentSong = song
        if (song?.albumArtUri != null) {
            scope.launch {
                cachedArtwork = loadBitmap(song.albumArtUri)
                updateNotification()
            }
        } else {
            cachedArtwork = null
            updateNotification()
        }
    }

    private suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.setTargetSize(300, 300)
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun updateNotification() {
        val song = currentSong ?: return
        val isPlaying = player.isPlaying

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title)
            .setContentText("${song.artist} • ${song.album}")
            .setContentIntent(contentIntent)
            .setLargeIcon(cachedArtwork)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            notificationManager.cancel(notificationId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
