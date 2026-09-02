package com.kurixutian.oreotunes.ui.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kurixutian.oreotunes.data.audio.LoudnessNormalizationEngine
import com.kurixutian.oreotunes.data.model.Playlist
import com.kurixutian.oreotunes.data.preferences.*
import com.kurixutian.oreotunes.data.repository.*
import com.kurixutian.oreotunes.domain.model.FolderInfo
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PlayerUiState(
    val songs: List<Song> = emptyList(),
    val currentQueue: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isShuffleOn: Boolean = false,
    val repeatMode: Int = 0,
    val palette: ArtworkPalette = ArtworkPalette(),
    val isNowPlayingExpanded: Boolean = false,
    val searchQuery: String = "",
    val searchHistory: List<String> = emptyList(),
    val detectedFolders: List<FolderInfo> = emptyList(),
    val selectedFolders: Set<String> = emptySet(),
    val selectedStatsTimeFrame: StatsTimeFrame = StatsTimeFrame.ALL_TIME,
    val totalListeningTimeMs: Long = 0L,
    val mostPlayedStats: List<SongPlayStat> = emptyList(),
    val leastPlayedStats: List<SongPlayStat> = emptyList(),
    val topArtistStats: List<ArtistPlayStat> = emptyList(),
    val crossfadeEnabled: Boolean = true,
    val crossfadeDurationSec: Int = 8,
    val volumeNormalizationEnabled: Boolean = true,
    val hiFiBypassEnabled: Boolean = false,
    val isExternalDacConnected: Boolean = false,
    val isBluetoothAudioConnected: Boolean = false,
    val connectedAudioDeviceName: String = "",
    val playlists: List<Playlist> = emptyList(),
    val quickPickMode: String = "Recently Played",
    val quickPickSongs: List<Song> = emptyList(),
    val recentlyPlayedSongs: List<Song> = emptyList(),
    val recentlyAddedSongs: List<Song> = emptyList(),
    val suggestedAlbums: List<AlbumGroup> = emptyList(),
    val featuredHeroAlbums: List<AlbumGroup> = emptyList(),
    val allArtists: List<ArtistGroup> = emptyList(),
    val suggestedArtists: List<ArtistGroup> = emptyList(),
    val heroRefreshHours: Int = 3,
    val activeSongAction: Song? = null,
    val appThemeMode: AppThemeMode = AppThemeMode.DEFAULT,
    val darkThemeStyle: DarkThemeStyle = DarkThemeStyle.AMOLED_DYNAMIC,
    val lightThemeStyle: LightThemeStyle = LightThemeStyle.PURE_WHITE_DYNAMIC,
    val customAccentColor: Color = Color(0xFF64D2FF),
    val artworkScalePercent: Int = 100,
    val artworkCornerRadiusDp: Int = 28
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val musicRepo = MusicRepository(application)
    private val playlistPrefs = PlaylistPreferences(application)
    private val statsTracker = PlaybackStatsTracker(application)
    private val recEngine = MusicRecommendationEngine(statsTracker)
    private val artworkRepo = ArtworkRepository(application)
    private val loudnessEngine = LoudnessNormalizationEngine(application)
    private val onlineMatcher = OnlineMetadataMatcher(application)
    private val tagWriter = PhysicalTagWriter(application)

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val usbManager = application.getSystemService(Context.USB_SERVICE) as? UsbManager

    private var allRawScannedSongs: List<Song> = emptyList()

    private var primaryPlayer: ExoPlayer? = null
    private var secondaryPlayer: ExoPlayer? = null
    private var crossfadeJob: Job? = null
    private var seekCrossfadeJob: Job? = null
    private var isCrossfading: Boolean = false
    private var hasAutoCrossfadedThisTrack: Boolean = false

    private val ACTION_USB_PERMISSION = "com.kurixutian.oreotunes.USB_PERMISSION"

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            crossfadeEnabled = playlistPrefs.isCrossfadeEnabled(),
            crossfadeDurationSec = playlistPrefs.getCrossfadeDurationSec(),
            heroRefreshHours = playlistPrefs.getHeroRefreshHours(),
            volumeNormalizationEnabled = playlistPrefs.isVolumeNormalizationEnabled(),
            hiFiBypassEnabled = playlistPrefs.isHiFiBypassEnabled(),
            quickPickMode = playlistPrefs.getQuickPickMode(),
            searchHistory = playlistPrefs.getSearchHistory(),
            appThemeMode = playlistPrefs.getAppThemeMode(),
            darkThemeStyle = playlistPrefs.getDarkThemeStyle(),
            lightThemeStyle = playlistPrefs.getLightThemeStyle(),
            customAccentColor = Color(playlistPrefs.getCustomAccentColor()),
            artworkScalePercent = playlistPrefs.getArtworkScalePercent(),
            artworkCornerRadiusDp = playlistPrefs.getArtworkCornerRadiusDp()
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val mediaControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    "com.kurixutian.oreotunes.MEDIA_CONTROL" -> {
                        when (intent.getStringExtra("control_action")) {
                            "NEXT" -> skipNext()
                            "PREV" -> skipPrevious()
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        checkAndPromptUsbDacPermission()
                        updateAudioRoutingDevices()
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        updateAudioRoutingDevices()
                    }
                    ACTION_USB_PERMISSION -> {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted) {
                            updateAudioRoutingDevices()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateAudioRoutingDevices()
            checkAndPromptUsbDacPermission()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateAudioRoutingDevices()
        }
    }

    init {
        try {
            val filter = IntentFilter().apply {
                addAction("com.kurixutian.oreotunes.MEDIA_CONTROL")
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_USB_PERMISSION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(mediaControlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                application.registerReceiver(mediaControlReceiver, filter)
            }
        } catch (_: Exception) {}

        try {
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (_: Exception) {}

        ensureServiceStarted()
        startPositionAndCrossfadeLoop()
        startHeroRefreshLoop()
        loadMusicLibrary()
        updateAudioRoutingDevices()
    }

    private fun isBypassActive(): Boolean {
        return _uiState.value.hiFiBypassEnabled &&
                (_uiState.value.isExternalDacConnected || _uiState.value.isBluetoothAudioConnected)
    }

    private fun updateAudioRoutingDevices() {
        val manager = audioManager ?: return
        val devices = try {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Exception) {
            emptyArray<AudioDeviceInfo>()
        }

        var isDac = false
        var isBt = false
        var deviceName = ""

        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    isDac = true
                    deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        device.productName?.toString() ?: "External USB DAC"
                    } else "External USB DAC"
                    break
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                    isBt = true
                    deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        device.productName?.toString() ?: "Bluetooth Hi-Fi Audio"
                    } else "Bluetooth Hi-Fi Audio"
                }
            }
        }

        _uiState.update {
            it.copy(
                isExternalDacConnected = isDac,
                isBluetoothAudioConnected = isBt,
                connectedAudioDeviceName = deviceName
            )
        }
    }

    fun checkAndPromptUsbDacPermission() {
        val manager = usbManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deviceList = manager.deviceList ?: return@launch
                for (device in deviceList.values) {
                    var isAudio = device.deviceClass == UsbConstants.USB_CLASS_AUDIO
                    if (!isAudio) {
                        for (i in 0 until device.interfaceCount) {
                            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                                isAudio = true
                                break
                            }
                        }
                    }

                    if (isAudio && !manager.hasPermission(device)) {
                        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        val permissionIntent = PendingIntent.getBroadcast(
                            getApplication(),
                            0,
                            Intent(ACTION_USB_PERMISSION).setPackage(getApplication<Application>().packageName),
                            flags
                        )
                        manager.requestPermission(device, permissionIntent)
                        break
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun ensureServiceStarted() {
        val intent = Intent(getApplication(), PlaybackService::class.java)
        try {
            ContextCompat.startForegroundService(getApplication(), intent)
        } catch (_: Exception) {}

        viewModelScope.launch(Dispatchers.Main) {
            while (primaryPlayer == null) {
                val pA = PlaybackService.playerA
                val pB = PlaybackService.playerB
                if (pA != null && pB != null) {
                    val activeFromDelegating = PlaybackService.delegatingPlayer?.activePlayer
                    if (activeFromDelegating == pB) {
                        primaryPlayer = pB
                        secondaryPlayer = pA
                    } else {
                        primaryPlayer = pA
                        secondaryPlayer = pB
                    }
                    setupPlayerListeners(pA)
                    setupPlayerListeners(pB)
                    syncStateFromActivePlayback()
                }
                delay(200)
            }
        }
    }

    private fun syncStateFromActivePlayback() {
        val activeP = primaryPlayer ?: PlaybackService.delegatingPlayer?.activePlayer ?: return
        val currentItem = activeP.currentMediaItem ?: return
        val metadata = currentItem.mediaMetadata

        val title = metadata.title?.toString() ?: ""
        val artist = metadata.artist?.toString() ?: ""
        val uri = currentItem.requestMetadata.mediaUri

        val matchedSong = allRawScannedSongs.find { song ->
            (uri != null && song.contentUri == uri) ||
            (song.title.equals(title, ignoreCase = true) && song.artist.equals(artist, ignoreCase = true))
        } ?: Song(
            id = (title.hashCode().toLong()),
            title = if (title.isNotBlank()) title else "Playing Audio",
            artist = if (artist.isNotBlank()) artist else "Unknown Artist",
            album = metadata.albumTitle?.toString() ?: "",
            duration = activeP.duration.coerceAtLeast(0L),
            contentUri = uri ?: Uri.EMPTY,
            albumArtUri = metadata.artworkUri,
            folderPath = "",
            isFavorite = playlistPrefs.isSongFavorite(title.hashCode().toLong())
        )

        val isPlaying = activeP.isPlaying
        val pos = activeP.currentPosition.coerceAtLeast(0L)
        val dur = if (activeP.duration > 0) activeP.duration else matchedSong.duration

        viewModelScope.launch(Dispatchers.IO) {
            val palette = artworkRepo.extractPalette(matchedSong.albumArtUri)
            val profile = loudnessEngine.getOrComputeLoudnessProfile(matchedSong)

            withContext(Dispatchers.Main) {
                if (!isBypassActive() && _uiState.value.volumeNormalizationEnabled) {
                    activeP.volume = profile.linearGainMultiplier
                } else {
                    activeP.volume = 1.0f
                }
                _uiState.update {
                    it.copy(
                        currentSong = matchedSong,
                        isPlaying = isPlaying,
                        currentPosition = pos,
                        duration = dur,
                        palette = palette
                    )
                }
            }
        }
    }

    private fun setupPlayerListeners(p: ExoPlayer) {
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (p == primaryPlayer) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (p == primaryPlayer) {
                    if (playbackState == Player.STATE_READY) {
                        _uiState.update { it.copy(duration = p.duration.coerceAtLeast(0L)) }
                    } else if (playbackState == Player.STATE_ENDED && !isCrossfading) {
                        if (_uiState.value.repeatMode == 1) {
                            p.seekTo(0L)
                            p.play()
                        } else {
                            skipNext()
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (p == primaryPlayer && mediaItem != null) {
                    syncStateFromActivePlayback()
                }
            }
        })
    }

    private fun startPositionAndCrossfadeLoop() {
        viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                try {
                    val p = primaryPlayer ?: PlaybackService.playerA
                    if (p != null && (p.isPlaying || p.playbackState == Player.STATE_READY)) {
                        val pos = p.currentPosition.coerceAtLeast(0L)
                        val dur = p.duration.coerceAtLeast(0L)

                        if (_uiState.value.currentSong == null && p.currentMediaItem != null) {
                            syncStateFromActivePlayback()
                        }

                        val progress = if (dur > 0) pos.toFloat() / dur.toFloat() else 0f
                        _playbackProgress.value = progress

                        _uiState.update {
                            it.copy(
                                currentPosition = pos,
                                duration = if (dur > 0) dur else it.duration,
                                isPlaying = p.isPlaying
                            )
                        }

                        val crossfadeDurationMs = _uiState.value.crossfadeDurationSec * 1000L
                        if (!isBypassActive() &&
                            _uiState.value.crossfadeEnabled &&
                            _uiState.value.repeatMode != 1 &&
                            p.isPlaying &&
                            dur > (crossfadeDurationMs + 3000L) &&
                            pos >= (dur - crossfadeDurationMs) &&
                            !isCrossfading &&
                            !hasAutoCrossfadedThisTrack
                        ) {
                            hasAutoCrossfadedThisTrack = true
                            triggerAutoNextCrossfade()
                        }
                    }
                } catch (_: Exception) {}
                delay(200)
            }
        }
    }

    private fun startHeroRefreshLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val refreshHours = _uiState.value.heroRefreshHours.coerceIn(1, 6)
                delay(60_000L) // Poll every minute to evaluate elapsed time against stored timestamp
                val lastRefresh = playlistPrefs.getHeroLastRefreshTimestamp()
                val now = System.currentTimeMillis()
                val intervalMs = refreshHours * 60L * 60L * 1000L

                if (now - lastRefresh >= intervalMs && _uiState.value.suggestedAlbums.isNotEmpty()) {
                    val newHero = _uiState.value.suggestedAlbums.shuffled().take(6)
                    if (newHero.isNotEmpty()) {
                        playlistPrefs.saveHeroLastRefreshTimestamp(now)
                        playlistPrefs.saveHeroAlbumTitles(newHero.map { it.title })
                        _uiState.update { it.copy(featuredHeroAlbums = newHero) }
                    }
                }
            }
        }
    }

    private fun triggerAutoNextCrossfade() {
        val activeQueue = if (_uiState.value.currentQueue.isNotEmpty()) _uiState.value.currentQueue else _uiState.value.songs
        if (activeQueue.isEmpty()) return
        val currentIndex = activeQueue.indexOfFirst { it.id == _uiState.value.currentSong?.id }
        val nextIndex = if (_uiState.value.isShuffleOn) {
            if (activeQueue.size <= 1) 0
            else (currentIndex + 1) % activeQueue.size
        } else {
            if (currentIndex == -1) 0 else (currentIndex + 1) % activeQueue.size
        }
        playSongInternal(activeQueue[nextIndex], isManual = false)
    }

    fun playSong(song: Song, queue: List<Song>? = null, autoExpand: Boolean = false) {
        val targetQueue = when {
            queue != null -> queue
            _uiState.value.currentQueue.any { it.id == song.id } -> _uiState.value.currentQueue
            else -> _uiState.value.songs
        }
        _uiState.update { it.copy(currentQueue = targetQueue) }
        playSongInternal(song, isManual = true, autoExpand = autoExpand)
    }

    fun playSongFromSearch(song: Song, candidateMatches: List<Song>, autoExpand: Boolean = false) {
        val entirePool = _uiState.value.songs.ifEmpty { allRawScannedSongs }
        val matchIds = candidateMatches.map { it.id }.toSet()

        val otherMatchesShuffled = candidateMatches.filter { it.id != song.id }.shuffled()
        val restOfLibraryShuffled = entirePool.filter { it.id != song.id && it.id !in matchIds }.shuffled()

        val randomizedQueue = mutableListOf<Song>().apply {
            add(song)
            addAll(otherMatchesShuffled)
            addAll(restOfLibraryShuffled)
        }

        primaryPlayer?.shuffleModeEnabled = true
        secondaryPlayer?.shuffleModeEnabled = true

        _uiState.update {
            it.copy(
                currentQueue = randomizedQueue,
                isShuffleOn = true
            )
        }
        playSongInternal(song, isManual = true, autoExpand = autoExpand)
    }

    private fun playSongInternal(song: Song, isManual: Boolean, autoExpand: Boolean = false) {
        viewModelScope.launch(Dispatchers.Main) {
            val p1 = primaryPlayer ?: PlaybackService.playerA ?: return@launch
            val p2 = secondaryPlayer ?: PlaybackService.playerB ?: return@launch

            _uiState.value.currentSong?.let { prevSong ->
                val listenedMs = p1.currentPosition
                if (listenedMs >= 60000L) {
                    statsTracker.recordPlay(prevSong, listenedMs)
                } else if (listenedMs < 30000L && listenedMs > 1000L) {
                    statsTracker.recordSkip(prevSong, listenedMs)
                }
            }

            hasAutoCrossfadedThisTrack = false

            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(song.albumArtUri)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(song.contentUri)
                .setMediaMetadata(metadata)
                .build()

            val isBypassed = isBypassActive()
            val isNormEnabled = _uiState.value.volumeNormalizationEnabled && !isBypassed
            val targetProfile = loudnessEngine.getOrComputeLoudnessProfile(song)
            val baseLinearMultiplier = if (isNormEnabled) targetProfile.linearGainMultiplier else 1.0f

            val crossfadeEnabled = _uiState.value.crossfadeEnabled && !isBypassed
            val crossfadeDurationMs = if (isManual) 1200L else (_uiState.value.crossfadeDurationSec * 1000L).coerceAtLeast(1000L)

            if (crossfadeEnabled && p1.isPlaying) {
                isCrossfading = true
                crossfadeJob?.cancel()

                val oldPlayer = p1
                val newPlayer = p2

                newPlayer.setMediaItem(mediaItem)
                newPlayer.prepare()
                newPlayer.volume = 0f
                newPlayer.play()

                primaryPlayer = newPlayer
                secondaryPlayer = oldPlayer
                PlaybackService.delegatingPlayer?.activePlayer = newPlayer

                val palette = withContext(Dispatchers.IO) {
                    artworkRepo.extractPalette(song.albumArtUri)
                }

                _uiState.update {
                    it.copy(
                        currentSong = song,
                        isPlaying = true,
                        palette = palette,
                        duration = song.duration,
                        currentPosition = 0L,
                        isNowPlayingExpanded = if (autoExpand) true else it.isNowPlayingExpanded
                    )
                }

                PlaybackService.instance?.updateNotification()

                crossfadeJob = launch {
                    val steps = 20
                    val stepDelay = (crossfadeDurationMs / steps).coerceAtLeast(10L)

                    for (i in 1..steps) {
                        val progress = i.toFloat() / steps
                        newPlayer.volume = (progress * baseLinearMultiplier).coerceAtLeast(0f)
                        oldPlayer.volume = ((1f - progress) * (oldPlayer.volume.coerceAtLeast(0.01f))).coerceAtLeast(0f)
                        delay(stepDelay)
                    }

                    newPlayer.volume = baseLinearMultiplier
                    oldPlayer.pause()
                    oldPlayer.stop()
                    oldPlayer.clearMediaItems()
                    oldPlayer.volume = 1f
                    isCrossfading = false
                }
            } else {
                crossfadeJob?.cancel()
                isCrossfading = false
                p2.stop()
                p2.clearMediaItems()
                p1.volume = baseLinearMultiplier
                p1.setMediaItem(mediaItem)
                p1.prepare()
                p1.play()

                primaryPlayer = p1
                secondaryPlayer = p2
                PlaybackService.delegatingPlayer?.activePlayer = p1

                val palette = withContext(Dispatchers.IO) {
                    artworkRepo.extractPalette(song.albumArtUri)
                }

                _uiState.update {
                    it.copy(
                        currentSong = song,
                        isPlaying = true,
                        palette = palette,
                        duration = song.duration,
                        currentPosition = 0L,
                        isNowPlayingExpanded = if (autoExpand) true else it.isNowPlayingExpanded
                    )
                }

                PlaybackService.instance?.updateNotification()
            }

            withContext(Dispatchers.IO) {
                val updatedRecent = statsTracker.getRecentlyPlayed(_uiState.value.songs, limit = 20)
                val quickPicks = if (_uiState.value.quickPickMode.equals("Random", ignoreCase = true) && _uiState.value.quickPickSongs.isNotEmpty()) {
                    _uiState.value.quickPickSongs
                } else {
                    recEngine.generateQuickPicks(
                        _uiState.value.songs,
                        _uiState.value.quickPickMode,
                        updatedRecent,
                        playlists = _uiState.value.playlists
                    )
                }

                _uiState.update {
                    it.copy(
                        recentlyPlayedSongs = updatedRecent,
                        quickPickSongs = quickPicks
                    )
                }
            }

            loadStats(_uiState.value.selectedStatsTimeFrame)
        }
    }

    fun playAll(songList: List<Song> = _uiState.value.songs, autoExpand: Boolean = false) {
        if (songList.isEmpty()) return
        _uiState.update { it.copy(currentQueue = songList) }
        playSongInternal(songList.first(), isManual = true, autoExpand = autoExpand)
    }

    fun shuffleAll(songList: List<Song> = _uiState.value.songs, autoExpand: Boolean = false) {
        if (songList.isEmpty()) return
        val shuffled = songList.shuffled()
        primaryPlayer?.shuffleModeEnabled = true
        secondaryPlayer?.shuffleModeEnabled = true
        _uiState.update { it.copy(currentQueue = shuffled, isShuffleOn = true) }
        playSongInternal(shuffled.first(), isManual = true, autoExpand = autoExpand)
    }

    fun skipNext() {
        val activeQueue = if (_uiState.value.currentQueue.isNotEmpty()) _uiState.value.currentQueue else _uiState.value.songs
        if (activeQueue.isEmpty()) return
        val currentIndex = activeQueue.indexOfFirst { it.id == _uiState.value.currentSong?.id }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % activeQueue.size
        playSongInternal(activeQueue[nextIndex], isManual = true)
    }

    fun skipPrevious() {
        val p = primaryPlayer ?: PlaybackService.playerA
        if (p != null && p.currentPosition > 3000L) {
            seekTo(0L)
            return
        }
        val activeQueue = if (_uiState.value.currentQueue.isNotEmpty()) _uiState.value.currentQueue else _uiState.value.songs
        if (activeQueue.isEmpty()) return
        val currentIndex = activeQueue.indexOfFirst { it.id == _uiState.value.currentSong?.id }
        val prevIndex = if (currentIndex <= 0) activeQueue.size - 1 else currentIndex - 1
        playSongInternal(activeQueue[prevIndex], isManual = true)
    }

    fun togglePlayPause() {
        val p = primaryPlayer ?: PlaybackService.playerA ?: return
        if (p.isPlaying) {
            p.pause()
            secondaryPlayer?.pause()
        } else {
            if (p.playbackState == Player.STATE_IDLE && _uiState.value.currentSong != null) {
                _uiState.value.currentSong?.let { playSong(it) }
            } else {
                p.play()
            }
        }
        _uiState.update { it.copy(isPlaying = p.isPlaying) }
    }

    fun seekTo(positionMs: Long) {
        val p = primaryPlayer ?: PlaybackService.playerA ?: return
        val clamped = positionMs.coerceIn(0L, _uiState.value.duration.coerceAtLeast(p.duration))
        hasAutoCrossfadedThisTrack = false
        _uiState.update { it.copy(currentPosition = clamped) }
        val dur = _uiState.value.duration.coerceAtLeast(1L)
        _playbackProgress.value = (clamped.toFloat() / dur.toFloat()).coerceIn(0f, 1f)

        val currentSong = _uiState.value.currentSong
        val targetProfile = currentSong?.let { loudnessEngine.calculateSafeMultiplier(1.0f, _uiState.value.volumeNormalizationEnabled && !isBypassActive(), it) } ?: 1.0f

        if (p.isPlaying && !isBypassActive()) {
            seekCrossfadeJob?.cancel()
            seekCrossfadeJob = viewModelScope.launch(Dispatchers.Main) {
                val duckSteps = 6
                for (i in duckSteps downTo 1) {
                    p.volume = (targetProfile * (i.toFloat() / duckSteps)).coerceAtLeast(0f)
                    delay(25)
                }
                p.volume = 0f
                p.seekTo(clamped)

                val riseSteps = 10
                for (i in 1..riseSteps) {
                    p.volume = (targetProfile * (i.toFloat() / riseSteps)).coerceAtLeast(0f)
                    delay(35)
                }
                p.volume = targetProfile
            }
        } else {
            p.seekTo(clamped)
        }
    }

    fun toggleShuffle() {
        val newState = !_uiState.value.isShuffleOn
        val currentQueue = _uiState.value.currentQueue.ifEmpty { _uiState.value.songs }
        val currentSong = _uiState.value.currentSong

        val newQueue = if (newState) {
            if (currentSong != null) {
                listOf(currentSong) + currentQueue.filter { it.id != currentSong.id }.shuffled()
            } else {
                currentQueue.shuffled()
            }
        } else {
            if (currentSong != null) {
                listOf(currentSong) + _uiState.value.songs.filter { it.id != currentSong.id }
            } else {
                _uiState.value.songs
            }
        }

        primaryPlayer?.shuffleModeEnabled = newState
        secondaryPlayer?.shuffleModeEnabled = newState
        _uiState.update { it.copy(isShuffleOn = newState, currentQueue = newQueue) }
    }

    fun toggleRepeat() {
        val newRepeat = (_uiState.value.repeatMode + 1) % 3
        val playerRepeatMode = when (newRepeat) {
            1 -> Player.REPEAT_MODE_ONE
            2 -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        primaryPlayer?.repeatMode = playerRepeatMode
        secondaryPlayer?.repeatMode = playerRepeatMode
        _uiState.update { it.copy(repeatMode = newRepeat) }
    }

    fun addSongsToQueue(songs: List<Song>) {
        val queue = (_uiState.value.currentQueue.ifEmpty { _uiState.value.songs }).toMutableList()
        val currentId = _uiState.value.currentSong?.id
        songs.forEach { s ->
            if (s.id != currentId) {
                queue.remove(s)
                queue.add(s)
            }
        }
        _uiState.update { it.copy(currentQueue = queue) }
    }

    fun playNext(song: Song) {
        val queue = (_uiState.value.currentQueue.ifEmpty { _uiState.value.songs }).toMutableList()
        val currentIndex = queue.indexOfFirst { it.id == _uiState.value.currentSong?.id }
        queue.remove(song)
        if (currentIndex != -1 && currentIndex + 1 <= queue.size) {
            queue.add(currentIndex + 1, song)
        } else {
            queue.add(song)
        }
        _uiState.update { it.copy(currentQueue = queue) }
    }

    fun addToQueue(song: Song) {
        val queue = (_uiState.value.currentQueue.ifEmpty { _uiState.value.songs }).toMutableList()
        queue.remove(song)
        queue.add(song)
        _uiState.update { it.copy(currentQueue = queue) }
    }

    fun removeFromQueue(song: Song) {
        val queue = _uiState.value.currentQueue.toMutableList()
        queue.remove(song)
        _uiState.update { it.copy(currentQueue = queue) }
    }

    fun clearQueue() {
        val current = _uiState.value.currentSong
        _uiState.update { it.copy(currentQueue = if (current != null) listOf(current) else emptyList()) }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val queue = _uiState.value.currentQueue.toMutableList()
        if (fromIndex in queue.indices && toIndex in queue.indices) {
            val item = queue.removeAt(fromIndex)
            queue.add(toIndex, item)
            _uiState.update { it.copy(currentQueue = queue) }
        }
    }

    fun setStatsTimeFrame(timeFrame: StatsTimeFrame) {
        _uiState.update { it.copy(selectedStatsTimeFrame = timeFrame) }
        loadStats(timeFrame)
    }

    fun loadStats(timeFrame: StatsTimeFrame = _uiState.value.selectedStatsTimeFrame) {
        viewModelScope.launch(Dispatchers.IO) {
            val allSongs = _uiState.value.songs
            val most = statsTracker.getMostPlayed(allSongs, timeFrame, limit = 15)
            val least = statsTracker.getLeastPlayed(allSongs, timeFrame, limit = 15)
            val artists = statsTracker.getTopArtists(timeFrame, limit = 15)
            val totalListeningTime = statsTracker.getTotalListeningTimeMs(timeFrame)

            _uiState.update {
                it.copy(
                    selectedStatsTimeFrame = timeFrame,
                    mostPlayedStats = most,
                    leastPlayedStats = least,
                    topArtistStats = artists,
                    totalListeningTimeMs = totalListeningTime
                )
            }
        }
    }

    fun toggleCrossfade(enabled: Boolean) {
        playlistPrefs.saveCrossfadeEnabled(enabled)
        _uiState.update { it.copy(crossfadeEnabled = enabled) }
    }

    fun setCrossfadeDuration(sec: Int) {
        playlistPrefs.saveCrossfadeDurationSec(sec)
        _uiState.update { it.copy(crossfadeDurationSec = sec) }
    }

    fun setHeroRefreshHours(hours: Int) {
        val clamped = hours.coerceIn(1, 6)
        playlistPrefs.saveHeroRefreshHours(clamped)
        _uiState.update { it.copy(heroRefreshHours = clamped) }
    }

    fun toggleHiFiBypass(enabled: Boolean) {
        playlistPrefs.saveHiFiBypassEnabled(enabled)
        _uiState.update { it.copy(hiFiBypassEnabled = enabled) }
        viewModelScope.launch(Dispatchers.Main) {
            val p = primaryPlayer ?: PlaybackService.playerA
            val current = _uiState.value.currentSong
            if (p != null) {
                if (enabled && isBypassActive()) {
                    p.volume = 1.0f
                } else if (_uiState.value.volumeNormalizationEnabled && current != null) {
                    val profile = loudnessEngine.getOrComputeLoudnessProfile(current)
                    p.volume = profile.linearGainMultiplier
                }
            }
        }
    }

    fun toggleVolumeNormalization(enabled: Boolean) {
        playlistPrefs.saveVolumeNormalizationEnabled(enabled)
        _uiState.update { it.copy(volumeNormalizationEnabled = enabled) }
        viewModelScope.launch(Dispatchers.Main) {
            val current = _uiState.value.currentSong
            val p = primaryPlayer ?: PlaybackService.playerA
            if (p != null && current != null) {
                if (enabled && !isBypassActive()) {
                    val profile = loudnessEngine.getOrComputeLoudnessProfile(current)
                    p.volume = profile.linearGainMultiplier
                } else {
                    p.volume = 1.0f
                }
            }
        }
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        playlistPrefs.saveAppThemeMode(mode)
        _uiState.update { it.copy(appThemeMode = mode) }
    }

    fun setDarkThemeStyle(style: DarkThemeStyle) {
        playlistPrefs.saveDarkThemeStyle(style)
        _uiState.update { it.copy(darkThemeStyle = style) }
    }

    fun setLightThemeStyle(style: LightThemeStyle) {
        playlistPrefs.saveLightThemeStyle(style)
        _uiState.update { it.copy(lightThemeStyle = style) }
    }

    fun setCustomAccentColor(color: Color) {
        playlistPrefs.saveCustomAccentColor(color.value.toLong())
        _uiState.update { it.copy(customAccentColor = color) }
    }

    fun setArtworkScalePercent(percent: Int) {
        val clamped = percent.coerceIn(65, 100)
        playlistPrefs.saveArtworkScalePercent(clamped)
        _uiState.update { it.copy(artworkScalePercent = clamped) }
    }

    fun setArtworkCornerRadiusDp(radiusDp: Int) {
        val clamped = radiusDp.coerceIn(0, 36)
        playlistPrefs.saveArtworkCornerRadiusDp(clamped)
        _uiState.update { it.copy(artworkCornerRadiusDp = clamped) }
    }

    fun recordSearchQuery(query: String) {
        playlistPrefs.addSearchQuery(query)
        _uiState.update { it.copy(searchHistory = playlistPrefs.getSearchHistory()) }
    }

    fun deleteSearchQuery(query: String) {
        playlistPrefs.deleteSearchQuery(query)
        _uiState.update { it.copy(searchHistory = playlistPrefs.getSearchHistory()) }
    }

    fun clearSearchHistory() {
        playlistPrefs.clearSearchHistory()
        _uiState.update { it.copy(searchHistory = emptyList()) }
    }

    fun searchMetadataCandidates(title: String, artist: String, onResult: (List<OnlineMetadataResult>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = onlineMatcher.searchMetadataCandidates(title, artist)
            val list = result.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { onResult(list) }
        }
    }

    fun applyChosenCandidate(song: Song, candidate: OnlineMetadataResult, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val artBytes = candidate.highResArtUrl?.let { onlineMatcher.downloadArtworkBytes(it) }

            val success = tagWriter.applyPhysicalMetadata(
                song = song,
                newTitle = candidate.title,
                newArtist = candidate.artist,
                newAlbum = candidate.album,
                artworkBytes = artBytes
            )

            val updatedArtUri = artBytes?.let {
                val albumArtDir = File(getApplication<Application>().filesDir, "album_covers")
                val file = File(albumArtDir, "cover_${song.id}.jpg")
                Uri.fromFile(file)
            } ?: song.albumArtUri

            updateSongMetadataInternal(song.id, candidate.title, candidate.artist, candidate.album, updatedArtUri)
            withContext(Dispatchers.Main) { onComplete?.invoke(success) }
        }
    }

    fun updateSongMetadata(songId: Long, newTitle: String, newArtist: String, newAlbum: String) {
        val targetSong = allRawScannedSongs.find { it.id == songId }
        if (targetSong != null) {
            viewModelScope.launch(Dispatchers.IO) {
                tagWriter.applyPhysicalMetadata(
                    song = targetSong,
                    newTitle = newTitle,
                    newArtist = newArtist,
                    newAlbum = newAlbum,
                    artworkBytes = null
                )
            }
        }
        updateSongMetadataInternal(songId, newTitle, newArtist, newAlbum, null)
    }

    private fun updateSongMetadataInternal(songId: Long, newTitle: String, newArtist: String, newAlbum: String, updatedArtUri: Uri?) {
        playlistPrefs.saveMetadataOverride(songId, newTitle, newArtist, newAlbum)

        allRawScannedSongs = allRawScannedSongs.map { s ->
            if (s.id == songId) s.copy(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                albumArtUri = updatedArtUri ?: s.albumArtUri
            ) else s
        }

        val updatedSongs = _uiState.value.songs.map { s ->
            if (s.id == songId) s.copy(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                albumArtUri = updatedArtUri ?: s.albumArtUri
            ) else s
        }

        val updatedQueue = _uiState.value.currentQueue.map { s ->
            if (s.id == songId) s.copy(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                albumArtUri = updatedArtUri ?: s.albumArtUri
            ) else s
        }

        val updatedCurrentSong = _uiState.value.currentSong?.let { s ->
            if (s.id == songId) s.copy(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                albumArtUri = updatedArtUri ?: s.albumArtUri
            ) else s
        }

        val updatedAction = _uiState.value.activeSongAction?.let { s ->
            if (s.id == songId) s.copy(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                albumArtUri = updatedArtUri ?: s.albumArtUri
            ) else s
        }

        _uiState.update {
            it.copy(
                songs = updatedSongs,
                currentQueue = updatedQueue,
                currentSong = updatedCurrentSong,
                activeSongAction = updatedAction
            )
        }

        PlaybackService.instance?.updateNotification()
    }

    fun loadMusicLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val rawScanned = try {
                musicRepo.loadSongs()
            } catch (_: Exception) {
                emptyList()
            }

            val favIds = playlistPrefs.getFavoriteSongIds()
            val overrides = playlistPrefs.getMetadataOverrides()

            allRawScannedSongs = rawScanned.map { song ->
                val override = overrides[song.id]
                val base = if (override != null) {
                    song.copy(title = override.first, artist = override.second, album = override.third)
                } else song

                val cachedCover = File(getApplication<Application>().filesDir, "album_covers/cover_${song.id}.jpg")
                val artUri = if (cachedCover.exists()) Uri.fromFile(cachedCover) else base.albumArtUri

                base.copy(albumArtUri = artUri, isFavorite = favIds.contains(song.id))
            }

            val savedSelectedFolders = playlistPrefs.getSelectedFolders()
            applyFolderFilter(savedSelectedFolders)

            withContext(Dispatchers.Main) {
                syncStateFromActivePlayback()
            }
        }
    }

    fun getChildFolders(parentPath: String?): List<FolderInfo> {
        val rawPaths = allRawScannedSongs.map { it.folderPath }.filter { it.isNotBlank() }.distinct()
        val baseStorage = "/storage/emulated/0"

        if (parentPath == null) {
            val topLevelHubs = mutableSetOf<String>()
            rawPaths.forEach { path ->
                if (path.startsWith(baseStorage)) {
                    val relativeParts = path.removePrefix(baseStorage).split('/').filter { it.isNotBlank() }
                    if (relativeParts.isNotEmpty()) {
                        val hubDepth = if (relativeParts.size >= 2) 2 else 1
                        val hub = baseStorage + "/" + relativeParts.take(hubDepth).joinToString("/")
                        topLevelHubs.add(hub)
                    }
                } else {
                    topLevelHubs.add(path)
                }
            }

            return topLevelHubs.map { hubPath ->
                val count = allRawScannedSongs.count { it.folderPath.startsWith(hubPath, ignoreCase = true) }
                FolderInfo(
                    name = hubPath.substringAfterLast('/', hubPath),
                    path = hubPath,
                    songCount = count
                )
            }.filter { it.songCount > 0 }
        } else {
            val childHubs = mutableSetOf<String>()
            val normalizedParent = parentPath.trimEnd('/')

            rawPaths.forEach { path ->
                if (path.startsWith("$normalizedParent/", ignoreCase = true)) {
                    val remainder = path.removePrefix("$normalizedParent/").split('/').filter { it.isNotBlank() }
                    if (remainder.isNotEmpty()) {
                        val immediateChild = "$normalizedParent/${remainder.first()}"
                        childHubs.add(immediateChild)
                    }
                }
            }

            return childHubs.map { childPath ->
                val count = allRawScannedSongs.count { it.folderPath.startsWith(childPath, ignoreCase = true) }
                FolderInfo(
                    name = childPath.substringAfterLast('/', childPath),
                    path = childPath,
                    songCount = count
                )
            }.filter { it.songCount > 0 }
        }
    }

    fun toggleFolderSelection(path: String) {
        val currentSet = _uiState.value.selectedFolders.toMutableSet()
        if (path in currentSet) {
            currentSet.remove(path)
        } else {
            currentSet.add(path)
        }
        playlistPrefs.saveSelectedFolders(currentSet)
        applyFolderFilter(currentSet)
    }

    fun clearFolderFilters() {
        val emptySet = emptySet<String>()
        playlistPrefs.saveSelectedFolders(emptySet)
        applyFolderFilter(emptySet)
    }

    private fun applyFolderFilter(selectedFolders: Set<String>) {
        val favIds = playlistPrefs.getFavoriteSongIds()
        val filteredSongs = if (selectedFolders.isEmpty()) {
            allRawScannedSongs
        } else {
            allRawScannedSongs.filter { song ->
                selectedFolders.any { folder ->
                    song.folderPath.equals(folder, ignoreCase = true) ||
                    song.folderPath.startsWith("$folder/", ignoreCase = true)
                }
            }
        }.map { it.copy(isFavorite = favIds.contains(it.id)) }

        val libraryFolders = if (selectedFolders.isNotEmpty()) {
            selectedFolders.map { path ->
                val count = allRawScannedSongs.count {
                    it.folderPath.equals(path, ignoreCase = true) || it.folderPath.startsWith("$path/", ignoreCase = true)
                }
                FolderInfo(
                    name = path.substringAfterLast('/', path),
                    path = path,
                    songCount = count
                )
            }.sortedWith(compareBy({ it.name.lowercase() }, { it.path }))
        } else {
            getChildFolders(null).sortedWith(compareBy({ it.name.lowercase() }, { it.path }))
        }

        val savedPlaylists = playlistPrefs.loadPlaylists().toMutableList()
        val favSongIdsList = allRawScannedSongs.filter { favIds.contains(it.id) }.map { it.id }
        var favPlaylistIndex = savedPlaylists.indexOfFirst { it.name.equals("Favorites", ignoreCase = true) }

        if (favPlaylistIndex == -1) {
            val systemFav = Playlist(
                id = "system_favorites_playlist",
                name = "Favorites",
                description = "Your favorited tracks",
                songIds = favSongIdsList
            )
            savedPlaylists.add(0, systemFav)
        } else {
            savedPlaylists[favPlaylistIndex] = savedPlaylists[favPlaylistIndex].copy(songIds = favSongIdsList)
        }
        playlistPrefs.savePlaylists(savedPlaylists)

        val recMode = playlistPrefs.getQuickPickMode()
        val recentlyPlayed = statsTracker.getRecentlyPlayed(filteredSongs, limit = 20)
        val recentlyAdded = recEngine.getRecentlyAdded(filteredSongs)
        val suggestedAlbums = recEngine.getSuggestedAlbums(filteredSongs)
        val allArtists = recEngine.getSuggestedArtists(filteredSongs)
        val randomizedArtists = allArtists.shuffled().take(15)

        // Persistent Featured Hero Check
        val heroRefreshHours = playlistPrefs.getHeroRefreshHours().coerceIn(1, 6)
        val lastRefreshTimestamp = playlistPrefs.getHeroLastRefreshTimestamp()
        val now = System.currentTimeMillis()
        val refreshIntervalMs = heroRefreshHours * 60L * 60L * 1000L
        val savedHeroTitles = playlistPrefs.getSavedHeroAlbumTitles()

        val featuredHeroAlbums = if (savedHeroTitles.isNotEmpty() && (now - lastRefreshTimestamp < refreshIntervalMs)) {
            val albumMap = suggestedAlbums.associateBy { it.title }
            val existing = savedHeroTitles.mapNotNull { albumMap[it] }
            if (existing.isNotEmpty()) existing else suggestedAlbums.shuffled().take(6)
        } else {
            val newHero = suggestedAlbums.shuffled().take(6)
            if (newHero.isNotEmpty()) {
                playlistPrefs.saveHeroLastRefreshTimestamp(now)
                playlistPrefs.saveHeroAlbumTitles(newHero.map { it.title })
            }
            newHero
        }

        val quickPicks = recEngine.generateQuickPicks(filteredSongs, recMode, recentlyPlayed, playlists = savedPlaylists)

        _uiState.update {
            it.copy(
                songs = filteredSongs,
                currentQueue = if (it.currentQueue.isEmpty()) filteredSongs else it.currentQueue,
                playlists = savedPlaylists,
                detectedFolders = libraryFolders,
                selectedFolders = selectedFolders,
                quickPickMode = recMode,
                recentlyPlayedSongs = recentlyPlayed,
                recentlyAddedSongs = recentlyAdded,
                suggestedAlbums = suggestedAlbums,
                featuredHeroAlbums = featuredHeroAlbums,
                allArtists = allArtists,
                suggestedArtists = randomizedArtists,
                quickPickSongs = quickPicks
            )
        }

        loadStats(_uiState.value.selectedStatsTimeFrame)
    }

    fun getSongsInFolderRecursively(folderPath: String): List<Song> {
        val favIds = playlistPrefs.getFavoriteSongIds()
        return allRawScannedSongs.filter { song ->
            song.folderPath.equals(folderPath, ignoreCase = true) ||
            song.folderPath.startsWith("$folderPath/", ignoreCase = true)
        }.map { it.copy(isFavorite = favIds.contains(it.id)) }
        .sortedBy { it.title.lowercase() }
    }

    fun setQuickPickMode(mode: String) {
        playlistPrefs.saveQuickPickMode(mode)
        viewModelScope.launch(Dispatchers.IO) {
            val quickPicks = recEngine.generateQuickPicks(
                _uiState.value.songs,
                mode,
                _uiState.value.recentlyPlayedSongs,
                playlists = _uiState.value.playlists
            )
            _uiState.update {
                it.copy(
                    quickPickMode = mode,
                    quickPickSongs = quickPicks
                )
            }
        }
    }

    fun createPlaylist(name: String, description: String, coverUri: String?) {
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            coverUri = coverUri,
            songIds = emptyList()
        )
        val updated = _uiState.value.playlists + newPlaylist
        _uiState.update { it.copy(playlists = updated) }
        playlistPrefs.savePlaylists(updated)
    }

    fun createPlaylistWithSongs(name: String, description: String, songIds: List<Long>) {
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            coverUri = null,
            songIds = songIds
        )
        val updated = _uiState.value.playlists + newPlaylist
        _uiState.update { it.copy(playlists = updated) }
        playlistPrefs.savePlaylists(updated)
    }

    fun editPlaylist(playlistId: String, name: String, description: String, coverUri: String?) {
        val updated = _uiState.value.playlists.map {
            if (it.id == playlistId) it.copy(name = name, description = description, coverUri = coverUri, updatedAt = System.currentTimeMillis())
            else it
        }
        _uiState.update { it.copy(playlists = updated) }
        playlistPrefs.savePlaylists(updated)
    }

    fun deletePlaylist(playlist: Playlist) {
        if (playlist.name.equals("Favorites", ignoreCase = true)) return
        val updated = _uiState.value.playlists.filterNot { it.id == playlist.id }
        _uiState.update { it.copy(playlists = updated) }
        playlistPrefs.savePlaylists(updated)
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        val updated = _uiState.value.playlists.map {
            if (it.id == playlistId && songId !in it.songIds) {
                it.copy(songIds = it.songIds + songId, updatedAt = System.currentTimeMillis())
            } else it
        }
        _uiState.update { it.copy(playlists = updated) }
        playlistPrefs.savePlaylists(updated)
    }

    fun addMultipleSongsToPlaylist(playlistId: String, songIds: List<Long>) {
        val updated = _uiState.value.playlists.map {
            if (it.id == playlistId) {
                val newSet = (it.songIds + songIds).distinct()
                it.copy(songIds = newSet, updatedAt = System.currentTimeMillis())
            } else it
        }
        _uiState.update { it.copy(playlists = updated) }
        playlistPrefs.savePlaylists(updated)
    }

    fun setSongAction(song: Song?) {
        val latestSong = song?.let { s ->
            val favIds = playlistPrefs.getFavoriteSongIds()
            s.copy(isFavorite = favIds.contains(s.id))
        }
        _uiState.update { it.copy(activeSongAction = latestSong) }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val isNowFav = playlistPrefs.toggleFavoriteSongId(song.id)
            val favIds = playlistPrefs.getFavoriteSongIds()

            allRawScannedSongs = allRawScannedSongs.map {
                if (it.id == song.id) it.copy(isFavorite = isNowFav) else it
            }

            val updatedSongs = _uiState.value.songs.map {
                if (it.id == song.id) it.copy(isFavorite = isNowFav) else it
            }

            val updatedQueue = _uiState.value.currentQueue.map {
                if (it.id == song.id) it.copy(isFavorite = isNowFav) else it
            }

            val updatedCurrentSong = _uiState.value.currentSong?.let {
                if (it.id == song.id) it.copy(isFavorite = isNowFav) else it
            }

            val updatedActionSong = _uiState.value.activeSongAction?.let {
                if (it.id == song.id) it.copy(isFavorite = isNowFav) else it
            }

            val currentPlaylists = _uiState.value.playlists.toMutableList()
            var favIndex = currentPlaylists.indexOfFirst { it.name.equals("Favorites", ignoreCase = true) }
            val favSongIdsList = allRawScannedSongs.filter { favIds.contains(it.id) }.map { it.id }

            if (favIndex == -1) {
                val systemFav = Playlist(
                    id = "system_favorites_playlist",
                    name = "Favorites",
                    description = "Your favorited tracks",
                    songIds = favSongIdsList
                )
                currentPlaylists.add(0, systemFav)
            } else {
                currentPlaylists[favIndex] = currentPlaylists[favIndex].copy(
                    songIds = favSongIdsList,
                    updatedAt = System.currentTimeMillis()
                )
            }
            playlistPrefs.savePlaylists(currentPlaylists)

            val quickPicks = if (_uiState.value.quickPickMode.equals("Random", ignoreCase = true)) {
                _uiState.value.quickPickSongs
            } else {
                recEngine.generateQuickPicks(
                    updatedSongs,
                    _uiState.value.quickPickMode,
                    _uiState.value.recentlyPlayedSongs,
                    playlists = currentPlaylists
                )
            }

            _uiState.update {
                it.copy(
                    songs = updatedSongs,
                    currentQueue = updatedQueue,
                    currentSong = updatedCurrentSong,
                    activeSongAction = updatedActionSong,
                    playlists = currentPlaylists,
                    quickPickSongs = quickPicks
                )
            }
        }
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isNowPlayingExpanded = expanded) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            getApplication<Application>().unregisterReceiver(mediaControlReceiver)
        } catch (_: Exception) {}
    }
}