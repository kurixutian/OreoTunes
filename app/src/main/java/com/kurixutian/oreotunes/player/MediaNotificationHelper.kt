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
import kotlinx.coroutines.cancel
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

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

    private val scope =
        CoroutineScope(Dispatchers.Main + Job())

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
        player.addListener(
            object : Player.Listener {

                override fun onEvents(
                    player: Player,
                    events: Player.Events
                ) {
                    if (
                        events.containsAny(
                            Player.EVENT_IS_PLAYING_CHANGED,
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_MEDIA_METADATA_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION
                        )
                    ) {
                        updateNotification()
                    }
                }

                override fun onIsPlayingChanged(
                    isPlaying: Boolean
                ) {
                    updateNotification()
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int
                ) {
                    updateNotification()
                }

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {
                    updateNotification()
                }

                override fun onMediaMetadataChanged(
                    mediaMetadata: androidx.media3.common.MediaMetadata
                ) {
                    updateNotification()
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int
                ) {
                    updateNotification()
                }
            }
        )
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

    private suspend fun loadBitmap(
        uri: Uri
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source =
                        ImageDecoder.createSource(
                            context.contentResolver,
                            uri
                        )

                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.setTargetSize(300, 300)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(
                        context.contentResolver,
                        uri
                    )
                }
            } catch (_: Exception) {
                null
            }
        }

    fun updateNotification() {
        val song = currentSong ?: return

        /*
         * IMPORTANT:
         *
         * isPlaying is the actual playback state.
         *
         * Do NOT derive the notification state from
         * playWhenReady alone.
         *
         * playWhenReady can remain true while the player is
         * buffering, paused by another condition, or transitioning.
         */
        val isPlaying = player.isPlaying

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_IMMUTABLE
                    } else {
                        0
                    }
                )
        )

        /*
         * Use Media3's standard media button action.
         *
         * The important part is that the notification itself is
         * rebuilt every time isPlaying changes.
         */
        val playPauseIntent = PendingIntent.getService(
            context,
            2,
            Intent(
                context,
                com.kurixutian.oreotunes.service.PlaybackService::class.java
            ).apply {
                action = "ACTION_PLAY_PAUSE"
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_IMMUTABLE
                    } else {
                        0
                    }
                )
        )

        val notification =
            NotificationCompat.Builder(
                context,
                channelId
            )
                .setSmallIcon(
                    if (isPlaying) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    }
                )
                .setContentTitle(song.title)
                .setContentText(
                    "${song.artist} • ${song.album}"
                )
                .setContentIntent(contentIntent)
                .setLargeIcon(cachedArtwork)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setVisibility(
                    NotificationCompat.VISIBILITY_PUBLIC
                )
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )
                .addAction(
                    if (isPlaying) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    },
                    if (isPlaying) {
                        "Pause"
                    } else {
                        "Play"
                    },
                    playPauseIntent
                )
                .build()

        try {
            notificationManager.notify(
                notificationId,
                notification
            )
        } catch (_: Exception) {
            // Notification failures must never interrupt playback.
        }
    }

    fun release() {
        scope.coroutineContext.cancel()

        try {
            notificationManager.cancel(notificationId)
        } catch (_: Exception) {
            // Ignore notification cleanup failures.
        }
    }
}
