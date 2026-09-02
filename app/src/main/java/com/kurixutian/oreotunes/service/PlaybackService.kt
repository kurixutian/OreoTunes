package com.kurixutian.oreotunes.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.kurixutian.oreotunes.MainActivity

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var isForegroundActive = false
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val CHANNEL_ID = "oreotunes_playback_channel"
        const val NOTIFICATION_ID = 1001

        var instance: PlaybackService? = null
            private set
        var playerA: ExoPlayer? = null
            private set
        var playerB: ExoPlayer? = null
            private set
        var delegatingPlayer: DelegatingForwardingPlayer? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        fun createSafeExoPlayer(): ExoPlayer {
            val renderersFactory = object : DefaultRenderersFactory(this@PlaybackService) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): AudioSink? {
                    return try {
                        DefaultAudioSink.Builder(context)
                            .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                            .setEnableFloatOutput(true)
                            .build()
                    } catch (_: Exception) {
                        super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
                    }
                }
            }.apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            }

            return ExoPlayer.Builder(this, renderersFactory)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
        }

        val pA = createSafeExoPlayer()
        val pB = createSafeExoPlayer()

        playerA = pA
        playerB = pB

        val delegating = DelegatingForwardingPlayer(this, pA)
        delegatingPlayer = delegating

        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(availablePlayerCommands)
                    .build()
            }
        }

        mediaSession = MediaSession.Builder(this, delegating)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .build()

        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION
                    )
                ) {
                    updateNotification()
                }
            }
        }
        pA.addListener(listener)
        pB.addListener(listener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    fun updateNotification() {
        try {
            val session = mediaSession ?: return
            val activeP = delegatingPlayer?.activePlayer ?: playerA ?: return
            val currentMediaItem = activeP.currentMediaItem ?: return
            val metadata = currentMediaItem.mediaMetadata

            val title = metadata.title?.toString() ?: "OreoTunes"
            val artist = metadata.artist?.toString() ?: "Unknown Artist"
            val album = metadata.albumTitle?.toString() ?: ""
            val artUri = metadata.artworkUri

            var artworkBitmap: Bitmap? = null
            if (artUri != null) {
                try {
                    artworkBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, artUri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(contentResolver, artUri)
                    }
                } catch (_: Exception) {}
            }

            val contentIntent = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val prevIntent = PendingIntent.getService(
                this, 1, Intent(this, PlaybackService::class.java).apply { action = "ACTION_PREV" },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val playPauseIntent = PendingIntent.getService(
                this, 2, Intent(this, PlaybackService::class.java).apply { action = "ACTION_PLAY_PAUSE" },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val nextIntent = PendingIntent.getService(
                this, 3, Intent(this, PlaybackService::class.java).apply { action = "ACTION_NEXT" },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val playPauseIcon = if (activeP.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(if (album.isNotBlank()) "$artist • $album" else artist)
                .setLargeIcon(artworkBitmap)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(activeP.isPlaying)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
                .addAction(playPauseIcon, "Play/Pause", playPauseIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
                .setStyle(
                    MediaStyleNotificationHelper.MediaStyle(session)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .build()

            if (!isForegroundActive && activeP.isPlaying) {
                startForeground(NOTIFICATION_ID, notification)
                isForegroundActive = true
            } else {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PLAY_PAUSE" -> {
                val activeP = delegatingPlayer?.activePlayer
                if (activeP != null) {
                    if (activeP.isPlaying) activeP.pause() else activeP.play()
                }
            }
            "ACTION_NEXT" -> {
                sendBroadcast(Intent("com.kurixutian.oreotunes.MEDIA_CONTROL").apply {
                    putExtra("control_action", "NEXT")
                    setPackage(packageName)
                })
            }
            "ACTION_PREV" -> {
                sendBroadcast(Intent("com.kurixutian.oreotunes.MEDIA_CONTROL").apply {
                    putExtra("control_action", "PREV")
                    setPackage(packageName)
                })
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback notifications with transport controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isForegroundActive = false
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        playerA?.release()
        playerB?.release()
        playerA = null
        playerB = null
        instance = null
        super.onDestroy()
    }
}

@OptIn(UnstableApi::class)
class DelegatingForwardingPlayer(
    private val serviceContext: Context,
    initialPlayer: Player
) : ForwardingPlayer(initialPlayer) {
    var activePlayer: Player = initialPlayer

    override fun getAvailableCommands(): Player.Commands {
        return activePlayer.availableCommands.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> activePlayer.isCommandAvailable(command)
        }
    }

    override fun isPlaying(): Boolean = activePlayer.isPlaying
    override fun getPlaybackState(): Int = activePlayer.playbackState
    override fun getPlayWhenReady(): Boolean = activePlayer.playWhenReady
    override fun getDuration(): Long = activePlayer.duration
    override fun getCurrentPosition(): Long = activePlayer.currentPosition
    override fun getCurrentMediaItem(): MediaItem? = activePlayer.currentMediaItem
    override fun getMediaMetadata(): MediaMetadata = activePlayer.mediaMetadata

    override fun play() { activePlayer.play() }
    override fun pause() { activePlayer.pause() }
    override fun seekTo(positionMs: Long) { activePlayer.seekTo(positionMs) }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) { activePlayer.seekTo(mediaItemIndex, positionMs) }

    override fun seekToNext() {
        serviceContext.sendBroadcast(Intent("com.kurixutian.oreotunes.MEDIA_CONTROL").apply {
            putExtra("control_action", "NEXT")
            setPackage(serviceContext.packageName)
        })
    }

    override fun seekToNextMediaItem() { seekToNext() }

    override fun seekToPrevious() {
        serviceContext.sendBroadcast(Intent("com.kurixutian.oreotunes.MEDIA_CONTROL").apply {
            putExtra("control_action", "PREV")
            setPackage(serviceContext.packageName)
        })
    }

    override fun seekToPreviousMediaItem() { seekToPrevious() }
}
