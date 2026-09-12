package com.kurixutian.oreotunes

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kurixutian.oreotunes.data.model.Playlist
import com.kurixutian.oreotunes.data.preferences.AppThemeMode
import com.kurixutian.oreotunes.data.preferences.DarkThemeStyle
import com.kurixutian.oreotunes.data.preferences.LightThemeStyle
import com.kurixutian.oreotunes.data.preferences.StatsTimeFrame
import com.kurixutian.oreotunes.data.repository.ArtworkPalette
import com.kurixutian.oreotunes.data.repository.OnlineMetadataResult
import com.kurixutian.oreotunes.data.repository.matchesArtist
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.components.*
import com.kurixutian.oreotunes.data.update.GitHubUpdateChecker
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.kurixutian.oreotunes.ui.screens.*
import com.kurixutian.oreotunes.ui.theme.LiquidMusicTheme
import com.kurixutian.oreotunes.ui.theme.Manrope
import com.kurixutian.oreotunes.ui.viewmodel.PlayerViewModel

private val SmoothEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

class MainActivity : ComponentActivity() {

    private val insightsExportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                try {
                    val json = viewModel.createInsightsBackupJson()
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    }

                    Toast.makeText(
                        this,
                        "Insights exported successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Failed to export Insights",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val insightsImportLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    val json = contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }

                    if (json == null) {
                        Toast.makeText(
                            this,
                            "Unable to read the selected file",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val result = viewModel.restoreInsightsBackupJson(json)

                        Toast.makeText(
                            this,
                            if (result.isSuccess) {
                                "Insights imported successfully"
                            } else {
                                result.exceptionOrNull()?.message
                                    ?: "Failed to import Insights"
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Failed to import Insights",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    private val viewModel: PlayerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.loadMusicLibrary()
        }
    }

    private fun shareInsightsAsImage(
        timeFrameLabel: String,
        totalListeningTimeMs: Long,
        totalPlays: Int,
        fullPlays: Int,
        skips: Int,
        uniqueTracks: Int,
        mostPlayedTitle: String?,
        mostPlayedArtist: String?,
        mostPlayedCount: Int,
        topArtists: List<com.kurixutian.oreotunes.data.preferences.ArtistPlayStat>
    ) {
        try {
            val width = 1200
            val height = 1500

            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)

            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(18, 20, 32)
            }

            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                backgroundPaint
            )

            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(100, 210, 255)
            }

            canvas.drawRoundRect(
                70f,
                70f,
                1130f,
                1430f,
                42f,
                42f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(28, 31, 46)
                }
            )

            canvas.drawRoundRect(
                70f,
                70f,
                1130f,
                82f,
                20f,
                20f,
                accentPaint
            )

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 58f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }

            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(180, 255, 255, 255)
                textSize = 30f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }

            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(100, 210, 255)
                textSize = 30f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }

            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 44f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(165, 255, 255, 255)
                textSize = 24f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }

            val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(185, 255, 255, 255)
                textSize = 26f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }

            canvas.drawText(
                "Listening Insights",
                120f,
                175f,
                titlePaint
            )

            canvas.drawText(
                timeFrameLabel,
                120f,
                225f,
                subtitlePaint
            )

            var totalSeconds = totalListeningTimeMs / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L

            val listeningTime = when {
                hours > 0L -> "${hours}h ${minutes}m"
                minutes > 0L -> "${minutes}m"
                totalSeconds > 0L -> "${totalSeconds}s"
                else -> "0m"
            }

            canvas.drawText(
                "LISTENING OVERVIEW",
                120f,
                315f,
                sectionPaint
            )

            val cards = listOf(
                Triple("Listening", listeningTime, 120f),
                Triple("Total Plays", totalPlays.toString(), 370f),
                Triple("Full Plays", fullPlays.toString(), 620f),
                Triple("Skips", skips.toString(), 870f)
            )

            cards.forEach { (label, value, x) ->
                canvas.drawRoundRect(
                    x,
                    350f,
                    x + 210f,
                    555f,
                    26f,
                    26f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(38, 42, 60)
                    }
                )

                canvas.drawText(
                    value,
                    x + 20f,
                    430f,
                    valuePaint
                )

                canvas.drawText(
                    label,
                    x + 20f,
                    485f,
                    labelPaint
                )
            }

            canvas.drawRoundRect(
                120f,
                595f,
                1080f,
                720f,
                26f,
                26f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(38, 42, 60)
                }
            )

            canvas.drawText(
                "Unique Tracks",
                155f,
                650f,
                labelPaint
            )

            canvas.drawText(
                uniqueTracks.toString(),
                155f,
                695f,
                valuePaint
            )

            canvas.drawText(
                "MOST PLAYED",
                120f,
                805f,
                sectionPaint
            )

            canvas.drawRoundRect(
                120f,
                835f,
                1080f,
                1030f,
                28f,
                28f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(38, 42, 60)
                }
            )

            if (!mostPlayedTitle.isNullOrBlank()) {
                canvas.drawText(
                    mostPlayedTitle.take(42),
                    155f,
                    900f,
                    valuePaint
                )

                canvas.drawText(
                    mostPlayedArtist?.take(48) ?: "",
                    155f,
                    945f,
                    smallPaint
                )

                canvas.drawText(
                    "$mostPlayedCount plays",
                    155f,
                    990f,
                    smallPaint
                )
            } else {
                canvas.drawText(
                    "No qualifying plays yet",
                    155f,
                    930f,
                    valuePaint
                )
            }

            canvas.drawText(
                "TOP ARTISTS",
                120f,
                1110f,
                sectionPaint
            )

            var artistY = 1165f

            topArtists.take(5).forEachIndexed { index, artist ->
                canvas.drawText(
                    "#${index + 1}",
                    125f,
                    artistY,
                    sectionPaint
                )

                canvas.drawText(
                    artist.artistName.take(38),
                    205f,
                    artistY,
                    smallPaint
                )

                canvas.drawText(
                    "${artist.count} plays",
                    900f,
                    artistY,
                    smallPaint
                )

                artistY += 48f
            }

            canvas.drawText(
                "OreoTunes",
                120f,
                1370f,
                subtitlePaint
            )

            val outputFile = File(
                cacheDir,
                "OreoTunes_Insights.png"
            )

            FileOutputStream(outputFile).use { output ->
                bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    output
                )
            }

            bitmap.recycle()

            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                outputFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(
                Intent.createChooser(
                    shareIntent,
                    "Share Listening Insights"
                )
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Failed to share Insights image",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAndRequestStoragePermissions()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val playbackProgress by viewModel.playbackProgress.collectAsState()
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: "home"

            var shouldReopenNowPlayingOnBack by remember { mutableStateOf(false) }

            fun handleBackPress() {
                if (uiState.isNowPlayingExpanded) {
                    viewModel.setNowPlayingExpanded(false)
                } else {
                    val popped = navController.popBackStack()
                    if (popped && shouldReopenNowPlayingOnBack) {
                        shouldReopenNowPlayingOnBack = false
                        viewModel.setNowPlayingExpanded(true)
                    }
                }
            }

            BackHandler(enabled = uiState.isNowPlayingExpanded || navController.previousBackStackEntry != null) {
                handleBackPress()
            }

            var isNavBarVisible by remember { mutableStateOf(true) }
            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < -14f && isNavBarVisible) {
                            isNavBarVisible = false
                        } else if (available.y > 14f && !isNavBarVisible) {
                            isNavBarVisible = true
                        }
                        return Offset.Zero
                    }
                }
            }

            var showCreatePlaylistDialog by remember { mutableStateOf(false) }
            var playlistToEdit by remember { mutableStateOf<Playlist?>(null) }
            var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
            var playlistToAddSongsTo by remember { mutableStateOf<Playlist?>(null) }
            var songToEditMetadata by remember { mutableStateOf<Song?>(null) }
            var songForCandidatePicker by remember { mutableStateOf<Pair<Song, List<OnlineMetadataResult>>?>(null) }
            var showStatsDialog by remember { mutableStateOf(false) }
            var showSettingsDialog by remember { mutableStateOf(false) }
            var selectedFolderSongsView by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
            var updateInfo by remember { mutableStateOf<com.kurixutian.oreotunes.data.update.UpdateInfo?>(null) }
            var isCheckingForUpdate by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                isCheckingForUpdate = true
                updateInfo = GitHubUpdateChecker.checkForUpdate(this@MainActivity)
                isCheckingForUpdate = false
            }

            val isAnyDialogOrActionActive = showStatsDialog ||
                    updateInfo != null ||
                    showSettingsDialog ||
                    uiState.activeSongAction != null ||
                    showCreatePlaylistDialog ||
                    playlistToEdit != null ||
                    songToAddToPlaylist != null ||
                    playlistToAddSongsTo != null ||
                    songToEditMetadata != null ||
                    songForCandidatePicker != null

            val backgroundBlurRadius by animateDpAsState(
                targetValue = if (isAnyDialogOrActionActive) 28.dp else 0.dp,
                animationSpec = tween(durationMillis = 200, easing = SmoothEasing),
                label = "backgroundBlurRadius"
            )

            fun navigateToAlbum(albumName: String) {
                if (albumName.isNotBlank()) {
                    if (uiState.isNowPlayingExpanded) {
                        shouldReopenNowPlayingOnBack = true
                    }
                    viewModel.setNowPlayingExpanded(false)
                    viewModel.setSongAction(null)
                    navController.navigate("album/${Uri.encode(albumName.trim())}")
                }
            }

            fun navigateToArtist(artistName: String) {
                if (artistName.isNotBlank()) {
                    if (uiState.isNowPlayingExpanded) {
                        shouldReopenNowPlayingOnBack = true
                    }
                    viewModel.setNowPlayingExpanded(false)
                    viewModel.setSongAction(null)
                    navController.navigate("artist/${Uri.encode(artistName.trim())}")
                }
            }

            val navBarInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            val isCustomDark = uiState.appThemeMode == AppThemeMode.DARK && uiState.darkThemeStyle == DarkThemeStyle.AMOLED_CUSTOM_ACCENT
            val isCustomLight = uiState.appThemeMode == AppThemeMode.LIGHT && uiState.lightThemeStyle == LightThemeStyle.PURE_WHITE_CUSTOM_ACCENT

            val effectivePalette = remember(uiState.palette, uiState.appThemeMode, uiState.darkThemeStyle, uiState.lightThemeStyle, uiState.customAccentColor) {
                if (isCustomDark || isCustomLight) {
                    uiState.palette.copy(
                        accent = uiState.customAccentColor,
                        lightAccent = uiState.customAccentColor,
                        secondary = uiState.customAccentColor
                    )
                } else {
                    uiState.palette
                }
            }

            LiquidMusicTheme(
                palette = effectivePalette,
                themeMode = uiState.appThemeMode,
                darkStyle = uiState.darkThemeStyle,
                lightStyle = uiState.lightThemeStyle,
                customAccent = uiState.customAccentColor
            ) {
                val isPureAmoled = uiState.appThemeMode == AppThemeMode.DARK
                val isLightMode = uiState.appThemeMode == AppThemeMode.LIGHT

                val rootBgColor = when {
                    isPureAmoled -> Color.Black
                    isLightMode -> Color(0xFFF7F8FC)
                    else -> effectivePalette.darkBackground
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(rootBgColor)
                ) {
                    if (!isPureAmoled && !isLightMode) {
                        DynamicAtmosphereBackground(
                            albumArtUri = uiState.currentSong?.albumArtUri,
                            palette = effectivePalette,
                            isPlaying = uiState.isPlaying
                        )
                    }

                    val miniPlayerBottomPadding by animateDpAsState(
                        targetValue = if (isNavBarVisible) 64.dp + navBarInsetBottom + 8.dp else navBarInsetBottom + 12.dp,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                        label = "miniPlayerBottomPadding"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(backgroundBlurRadius)
                            .nestedScroll(nestedScrollConnection)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = {
                                fadeIn(animationSpec = tween(280, easing = SmoothEasing)) +
                                        slideIntoContainer(
                                            AnimatedContentTransitionScope.SlideDirection.Start,
                                            animationSpec = tween(340, easing = SmoothEasing),
                                            initialOffset = { it / 6 }
                                        )
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(220, easing = SmoothEasing)) +
                                        slideOutOfContainer(
                                            AnimatedContentTransitionScope.SlideDirection.Start,
                                            animationSpec = tween(340, easing = SmoothEasing),
                                            targetOffset = { -it / 6 }
                                        )
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(280, easing = SmoothEasing)) +
                                        slideIntoContainer(
                                            AnimatedContentTransitionScope.SlideDirection.End,
                                            animationSpec = tween(340, easing = SmoothEasing),
                                            initialOffset = { -it / 6 }
                                        )
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(220, easing = SmoothEasing)) +
                                        slideOutOfContainer(
                                            AnimatedContentTransitionScope.SlideDirection.End,
                                            animationSpec = tween(340, easing = SmoothEasing),
                                            targetOffset = { it / 6 }
                                        )
                            }
                        ) {
                            composable("home") {
                                HomeScreen(
                                    songs = uiState.songs,
                                    quickPickSongs = uiState.quickPickSongs,
                                    recentlyPlayedSongs = uiState.recentlyPlayedSongs,
                                    recentlyAddedSongs = uiState.recentlyAddedSongs,
                                    suggestedAlbums = uiState.suggestedAlbums,
                                    featuredHeroAlbums = uiState.featuredHeroAlbums,
                                    suggestedArtists = uiState.suggestedArtists,
                                    playlists = uiState.playlists,
                                    quickPickMode = uiState.quickPickMode,
                                    heroRefreshHours = uiState.heroRefreshHours,
                                    palette = effectivePalette,
                                    onQuickPickModeSelected = { mode -> viewModel.setQuickPickMode(mode) },
                                    onSongClick = { song, queue -> viewModel.playSong(song, queue = queue, autoExpand = false) },
                                    onSongLongClick = { song -> viewModel.setSongAction(song) },
                                    onHeroAlbumPlay = { album -> viewModel.playAll(album.songs, autoExpand = false) },
                                    onPlayAll = { viewModel.playAll(autoExpand = false) },
                                    onShuffleAll = { viewModel.shuffleAll(autoExpand = false) },
                                    onSeeAllRecentlyPlayed = { navController.navigate("recently_played") },
                                    onSeeAllRecentlyAdded = { navController.navigate("recently_added") },
                                    onSeeAllQuickPicks = { navController.navigate("quick_picks") },
                                    onAlbumClick = { album -> navigateToAlbum(album) },
                                    onArtistClick = { artist -> navigateToArtist(artist) },
                                    onPlaylistClick = { playlist -> navController.navigate("playlist/${playlist.id}") },
                                    onCreatePlaylistWithSongs = { name, desc, songIds ->
                                        viewModel.createPlaylistWithSongs(name, desc, songIds)
                                    },
                                    onOpenStats = {
                                        viewModel.loadStats()
                                        showStatsDialog = true
                                    },
                                    onOpenSettings = { showSettingsDialog = true }
                                )
                            }
                            composable("library") {
                                LibraryScreen(
                                    songs = uiState.songs,
                                    playlists = uiState.playlists,
                                    albums = uiState.suggestedAlbums,
                                    artists = uiState.allArtists.ifEmpty { uiState.suggestedArtists },
                                    detectedFolders = uiState.detectedFolders,
                                    selectedFolders = uiState.selectedFolders,
                                    onSongClick = { song -> viewModel.playSong(song, queue = uiState.songs, autoExpand = false) },
                                    onSongLongClick = { song -> viewModel.setSongAction(song) },
                                    onPlaylistClick = { playlist -> navController.navigate("playlist/${playlist.id}") },
                                    onCreatePlaylist = { showCreatePlaylistDialog = true },
                                    onAlbumClick = { album -> navigateToAlbum(album) },
                                    onArtistClick = { artist -> navigateToArtist(artist) },
                                    onFolderClick = { folder ->
                                        val songsInFolder = viewModel.getSongsInFolderRecursively(folder.path)
                                        selectedFolderSongsView = Pair(folder.name, songsInFolder)
                                        navController.navigate("folder_detail")
                                    },
                                    onNavigateToFolders = { navController.navigate("folders") }
                                )
                            }
                            composable("search") {
                                SearchScreen(
                                    songs = uiState.songs,
                                    albums = uiState.suggestedAlbums,
                                    artists = uiState.allArtists.ifEmpty { uiState.suggestedArtists },
                                    playlists = uiState.playlists,
                                    query = uiState.searchQuery,
                                    searchHistory = uiState.searchHistory,
                                    onQueryChange = { q -> viewModel.setSearchQuery(q) },
                                    onSongClick = { song, candidateMatches ->
                                        viewModel.playSongFromSearch(song, candidateMatches)
                                    },
                                    onAlbumClick = { album -> navigateToAlbum(album) },
                                    onArtistClick = { artist -> navigateToArtist(artist) },
                                    onPlaylistClick = { playlist -> navController.navigate("playlist/${playlist.id}") },
                                    onRecordHistory = { q -> viewModel.recordSearchQuery(q) },
                                    onDeleteHistoryItem = { q -> viewModel.deleteSearchQuery(q) },
                                    onClearHistory = { viewModel.clearSearchHistory() },
                                    onBack = { handleBackPress() }
                                )
                            }
                            composable(
                                route = "album/{albumName}",
                                arguments = listOf(navArgument("albumName") { type = NavType.StringType })
                            ) { backStack ->
                                val encodedAlbum = backStack.arguments?.getString("albumName") ?: ""
                                val albumName = Uri.decode(encodedAlbum)
                                AlbumDetailScreen(
                                    albumName = albumName,
                                    allSongs = uiState.songs,
                                    onBack = { handleBackPress() },
                                    onSongClick = { song -> viewModel.playSong(song, autoExpand = false) },
                                    onSongLongClick = { song -> viewModel.setSongAction(song) },
                                    onPlayAll = { songs -> viewModel.playAll(songs, autoExpand = false) },
                                    onShuffleAll = { songs -> viewModel.shuffleAll(songs, autoExpand = false) },
                                    onAddToQueue = { songs -> viewModel.addSongsToQueue(songs) }
                                )
                            }
                            composable(
                                route = "artist/{artistName}",
                                arguments = listOf(navArgument("artistName") { type = NavType.StringType })
                            ) { backStack ->
                                val encodedArtist = backStack.arguments?.getString("artistName") ?: ""
                                val artistName = Uri.decode(encodedArtist)
                                ArtistDetailScreen(
                                    artistName = artistName,
                                    allSongs = uiState.songs,
                                    onBack = { handleBackPress() },
                                    onSongClick = { song, queue -> viewModel.playSong(song, queue = queue, autoExpand = false) },
                                    onSongLongClick = { song -> viewModel.setSongAction(song) },
                                    onPlayAll = { songs -> viewModel.playAll(songs, autoExpand = false) },
                                    onShuffleAll = { songs -> viewModel.shuffleAll(songs, autoExpand = false) },
                                    onAlbumClick = { album -> navigateToAlbum(album) }
                                )
                            }
                            composable("playlist/{playlistId}") { backStack ->
                                val playlistId = backStack.arguments?.getString("playlistId") ?: ""
                                val playlist = uiState.playlists.find { it.id == playlistId }
                                val playlistSongs = remember(playlist, uiState.songs) {
                                    val map = uiState.songs.associateBy { it.id }
                                    playlist?.songIds?.mapNotNull { map[it] } ?: emptyList()
                                }
                                PlaylistDetailScreen(
                                    playlist = playlist,
                                    allSongs = uiState.songs,
                                    onBack = { handleBackPress() },
                                    onSongClick = { song -> viewModel.playSong(song, queue = playlistSongs, autoExpand = false) },
                                    onSongLongClick = { song -> viewModel.setSongAction(song) },
                                    onPlayAll = { songs -> viewModel.playAll(songs, autoExpand = false) },
                                    onShuffleAll = { songs -> viewModel.shuffleAll(songs, autoExpand = false) },
                                    onEditPlaylist = { p -> playlistToEdit = p },
                                    onDeletePlaylist = { p -> viewModel.deletePlaylist(p) },
                                    onAddSongs = { p -> playlistToAddSongsTo = p },
                                    onRemoveSong = { p, song ->
                                        viewModel.removeSongFromPlaylist(p.id, song.id)
                                    },
                                    onReorderSong = { p, fromIndex, toIndex ->
                                        viewModel.reorderPlaylistSong(p.id, fromIndex, toIndex)
                                    }
                                )
                            }
                            composable("recently_played") {
                                FilteredSongListScreen(
                                    title = "Recently Played",
                                    songs = uiState.recentlyPlayedSongs,
                                    onBack = { handleBackPress() },
                                    onSongClick = { song -> viewModel.playSong(song, queue = uiState.recentlyPlayedSongs, autoExpand = false) },
                                    onSongLongClick = { song -> viewModel.setSongAction(song) }
                                )
                            }
                            composable("recently_added") {
                                FilteredSongListScreen(
                                    title = "Recently Added",
                                    songs = uiState.recentlyAddedSongs,
                                    onBack = { handleBackPress() },
                                    onSongClick = { song -> viewModel.playSong(song, queue = uiState.recentlyAddedSongs, autoExpand = false) },
                                    onSongLongClick = { song -> viewModel.setSongAction(song) }
                                )
                            }
                            composable("quick_picks") {
                                FilteredSongListScreen(
                                    title = "Quick Picks",
                                    songs = uiState.quickPickSongs,
                                    onBack = { handleBackPress() },
                                    onSongClick = { song -> viewModel.playSong(song, queue = uiState.quickPickSongs, autoExpand = false) },
                                    onSongLongClick = { song -> viewModel.setSongAction(song) }
                                )
                            }
                            composable("folders") {
                                FolderPickerScreen(
                                    detectedFolders = uiState.detectedFolders,
                                    selectedFolders = uiState.selectedFolders,
                                    onToggleFolder = { path -> viewModel.toggleFolderSelection(path) },
                                    onClearAll = { viewModel.clearFolderFilters() },
                                    getChildFolders = { parent -> viewModel.getChildFolders(parent) },
                                    onBack = { handleBackPress() }
                                )
                            }
                            composable("folder_detail") {
                                val currentFolder = selectedFolderSongsView
                                FilteredSongListScreen(
                                    title = currentFolder?.first ?: "Folder Tracks",
                                    songs = currentFolder?.second ?: emptyList(),
                                    onBack = { handleBackPress() },
                                    onSongClick = { song ->
                                        currentFolder?.second?.let { queue ->
                                            viewModel.playSong(song, queue = queue, autoExpand = false)
                                        }
                                    },
                                    onSongLongClick = { song -> viewModel.setSongAction(song) }
                                )
                            }
                        }

                        // Floating Bottom Dock (MiniPlayer + Frosted Glass Navbar)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val currentPlayingSong = uiState.currentSong
                            if (currentPlayingSong != null) {
                                MiniPlayer(
                                    song = currentPlayingSong,
                                    isPlaying = uiState.isPlaying,
                                    progressProvider = { playbackProgress },
                                    primaryColor = Color.White,
                                    onPlayPause = { viewModel.togglePlayPause() },
                                    onFavorite = { viewModel.toggleFavorite(currentPlayingSong) },
                                    onClick = { viewModel.setNowPlayingExpanded(true) },
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp)
                                        .padding(bottom = miniPlayerBottomPadding)
                                )
                            }

                            AnimatedVisibility(
                                visible = isNavBarVisible,
                                enter = slideInVertically(
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                                    initialOffsetY = { it }
                                ) + fadeIn(animationSpec = tween(180, easing = SmoothEasing)),
                                exit = slideOutVertically(
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                                    targetOffsetY = { it }
                                ) + fadeOut(animationSpec = tween(150, easing = SmoothEasing))
                            ) {
                                UniversalBottomNavigation(
                                    currentRoute = currentRoute,
                                    palette = effectivePalette,
                                    isLightMode = uiState.appThemeMode == AppThemeMode.LIGHT,
                                    onNavigate = { route: String ->
                                        if (currentRoute != route) {
                                            navController.navigate(route) {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Fullscreen Now Playing Overlay with Zero-Pop Container Exit
                    AnimatedVisibility(
                        visible = uiState.isNowPlayingExpanded,
                        enter = slideInVertically(
                            animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
                            initialOffsetY = { it }
                        ) + fadeIn(animationSpec = tween(220, easing = SmoothEasing)),
                        exit = fadeOut(animationSpec = tween(60, easing = SmoothEasing))
                    ) {
                        NowPlayingScreen(
                            song = uiState.currentSong,
                            songsQueue = if (uiState.currentQueue.isNotEmpty()) uiState.currentQueue else uiState.songs,
                            isPlaying = uiState.isPlaying,
                            currentPosition = uiState.currentPosition,
                            duration = uiState.duration,
                            isShuffle = uiState.isShuffleOn,
                            repeatMode = uiState.repeatMode,
                            palette = effectivePalette,
                            isActionSheetOpen = isAnyDialogOrActionActive,
                            artworkScalePercent = uiState.artworkScalePercent,
                            artworkCornerRadiusDp = uiState.artworkCornerRadiusDp,
                            onPlayPause = { viewModel.togglePlayPause() },
                            onSkipNext = { viewModel.skipNext() },
                            onSkipPrev = { viewModel.skipPrevious() },
                            onSeek = { pos: Long -> viewModel.seekTo(pos) },
                            onShuffle = { viewModel.toggleShuffle() },
                            onRepeat = { viewModel.toggleRepeat() },
                            onFavorite = { song: Song -> viewModel.toggleFavorite(song) },
                            onOptionsClick = { song: Song -> viewModel.setSongAction(song) },
                            onViewArtist = { artist -> navigateToArtist(artist) },
                            onSelectQueueItem = { song: Song -> viewModel.playSong(song, queue = uiState.currentQueue, autoExpand = false) },
                            onMoveQueueItem = { from: Int, to: Int -> viewModel.moveQueueItem(from, to) },
                            onRemoveFromQueue = { song: Song -> viewModel.removeFromQueue(song) },
                            onClearQueue = { viewModel.clearQueue() },
                            onCollapse = { viewModel.setNowPlayingExpanded(false) }
                        )
                    }

                    // Universal Context Sheet
                    SongActionSheet(
                        song = uiState.activeSongAction,
                        onDismiss = { viewModel.setSongAction(null) },
                        onPlay = { song -> viewModel.playSong(song, autoExpand = false) },
                        onPlayNext = { song -> viewModel.playNext(song) },
                        onAddToQueue = { song -> viewModel.addToQueue(song) },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
                        onAddToPlaylist = { song -> songToAddToPlaylist = song },
                        onAutoFetchMetadata = { song ->
                            viewModel.searchMetadataCandidates(song.title, song.artist) { candidates ->
                                if (candidates.isNotEmpty()) {
                                    songForCandidatePicker = Pair(song, candidates)
                                }
                            }
                        },
                        onEditMetadata = { song -> songToEditMetadata = song },
                        onViewAlbum = { album -> navigateToAlbum(album) },
                        onViewArtist = { artist -> navigateToArtist(artist) },
                        onShowDetails = {}
                    )

                    // Candidate Picker Modal (Album vs Single vs Deluxe)
                    songForCandidatePicker?.let { pair ->
                        val targetSong = pair.first
                        val candidates = pair.second
                        OnlineMatchPickerDialog(
                            song = targetSong,
                            candidates = candidates,
                            palette = effectivePalette,
                            onSelectCandidate = { selected ->
                                viewModel.applyChosenCandidate(targetSong, selected)
                                songForCandidatePicker = null
                            },
                            onDismiss = { songForCandidatePicker = null }
                        )
                    }

                    songToEditMetadata?.let { song ->
                        EditSongMetadataDialog(
                            song = song,
                            palette = effectivePalette,
                            onDismiss = { songToEditMetadata = null },
                            onSaveMetadata = { id, title, artist, album, onComplete ->
                                viewModel.updateSongMetadata(
                                    id,
                                    title,
                                    artist,
                                    album,
                                    onComplete
                                )
                            }
                        )
                    }

                    if (showCreatePlaylistDialog) {
                        CreateEditPlaylistDialog(
                            onDismiss = { showCreatePlaylistDialog = false },
                            onConfirm = { name, desc, cover -> viewModel.createPlaylist(name, desc, cover) }
                        )
                    }

                    playlistToEdit?.let { playlist ->
                        CreateEditPlaylistDialog(
                            playlistToEdit = playlist,
                            onDismiss = { playlistToEdit = null },
                            onConfirm = { name, desc, cover -> viewModel.editPlaylist(playlist.id, name, desc, cover) }
                        )
                    }

                    songToAddToPlaylist?.let { song ->
                        AddToPlaylistDialog(
                            song = song,
                            playlists = uiState.playlists,
                            onDismiss = { songToAddToPlaylist = null },
                            onPlaylistSelected = { playlist, s -> viewModel.addSongToPlaylist(playlist.id, s.id) },
                            onCreateNewPlaylist = { showCreatePlaylistDialog = true }
                        )
                    }

                    playlistToAddSongsTo?.let { playlist ->
                        MultiSelectSongPickerDialog(
                            allSongs = uiState.songs,
                            existingSongIds = playlist.songIds.toSet(),
                            onDismiss = { playlistToAddSongsTo = null },
                            onSongsConfirmed = { ids -> viewModel.addMultipleSongsToPlaylist(playlist.id, ids) }
                        )
                    }

                    if (showStatsDialog) {
                        ModernPlaybackStatsDialog(
                            selectedTimeFrame = uiState.selectedStatsTimeFrame,
                            totalListeningTimeMs = uiState.totalListeningTimeMs,
                            listeningSummary = uiState.listeningSummary,
                            mostPlayed = uiState.mostPlayedStats,
                            leastPlayed = uiState.leastPlayedStats,
                            topArtists = uiState.topArtistStats,
                            palette = effectivePalette,
                            onTimeFrameSelected = { timeFrame: StatsTimeFrame -> viewModel.setStatsTimeFrame(timeFrame) },
                            onExportInsights = {
                                insightsExportLauncher.launch("OreoTunes_Insights.json")
                            },
                            onShareInsights = {
                                shareInsightsAsImage(
                                    timeFrameLabel = uiState.selectedStatsTimeFrame.label,
                                    totalListeningTimeMs = uiState.totalListeningTimeMs,
                                    totalPlays = uiState.listeningSummary.totalPlays,
                                    fullPlays = uiState.listeningSummary.fullPlays,
                                    skips = uiState.listeningSummary.skips,
                                    uniqueTracks = uiState.listeningSummary.uniqueTracks,
                                    mostPlayedTitle = uiState.mostPlayedStats.firstOrNull()?.song?.title,
                                    mostPlayedArtist = uiState.mostPlayedStats.firstOrNull()?.song?.artist,
                                    mostPlayedCount = uiState.mostPlayedStats.firstOrNull()?.count ?: 0,
                                    topArtists = uiState.topArtistStats
                                )
                            },
                            onImportInsights = {
                                insightsImportLauncher.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/json",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            onDismiss = { showStatsDialog = false }
                        )
                    }

                    updateInfo?.let { info ->
                        UpdateDialog(
                            updateInfo = info,
                            currentVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown",
                            onRemindLater = {
                                GitHubUpdateChecker.markReminded(this@MainActivity, info.versionName)
                                updateInfo = null
                            }
                        )
                    }


                    if (showSettingsDialog) {
                        SettingsDialog(
                            crossfadeEnabled = uiState.crossfadeEnabled,
                            crossfadeDuration = uiState.crossfadeDurationSec,
                            heroRefreshHours = uiState.heroRefreshHours,
                            volumeNormalizationEnabled = uiState.volumeNormalizationEnabled,
                            hiFiBypassEnabled = uiState.hiFiBypassEnabled,
                            isExternalDacConnected = uiState.isExternalDacConnected,
                            isBluetoothConnected = uiState.isBluetoothAudioConnected,
                            connectedDeviceName = uiState.connectedAudioDeviceName,
                            selectedFoldersCount = uiState.selectedFolders.size,
                            palette = effectivePalette,
                            appThemeMode = uiState.appThemeMode,
                            darkThemeStyle = uiState.darkThemeStyle,
                            lightThemeStyle = uiState.lightThemeStyle,
                            customAccentColor = uiState.customAccentColor,
                            artworkScalePercent = uiState.artworkScalePercent,
                            artworkCornerRadiusDp = uiState.artworkCornerRadiusDp,
                            currentVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown",
                            updateInfo = updateInfo,
                            isCheckingForUpdate = isCheckingForUpdate,
                            supportUrl = "https://buymeacoffee.com/kurixutian",
                              onCheckForUpdates = {
                                  if (!isCheckingForUpdate) {
                                      isCheckingForUpdate = true
                                      coroutineScope.launch {
                                          try {
                                              val result = GitHubUpdateChecker.checkForUpdate(
                                                  this@MainActivity,
                                                  ignoreReminder = true
                                              )
                                              updateInfo = result
                                              if (result == null) {
                                                  android.widget.Toast.makeText(
                                                      this@MainActivity,
                                                      "OreoTunes is up to date",
                                                      android.widget.Toast.LENGTH_SHORT
                                                  ).show()
                                              }
                                          } catch (_: Exception) {
                                              updateInfo = null
                                              android.widget.Toast.makeText(
                                                  this@MainActivity,
                                                  "Unable to check for updates",
                                                  android.widget.Toast.LENGTH_SHORT
                                              ).show()
                                          } finally {
                                              isCheckingForUpdate = false
                                          }
                                      }
                                  }
                              },
                            onToggleCrossfade = { enabled -> viewModel.toggleCrossfade(enabled) },
                            onCrossfadeDurationChange = { dur -> viewModel.setCrossfadeDuration(dur) },
                            onHeroRefreshHoursChange = { hours -> viewModel.setHeroRefreshHours(hours) },
                            onToggleVolumeNormalization = { enabled -> viewModel.toggleVolumeNormalization(enabled) },
                            onToggleHiFiBypass = { enabled -> viewModel.toggleHiFiBypass(enabled) },
                            onPromptDacPermission = { viewModel.checkAndPromptUsbDacPermission() },
                            onAppThemeModeChange = { mode -> viewModel.setAppThemeMode(mode) },
                            onDarkThemeStyleChange = { style -> viewModel.setDarkThemeStyle(style) },
                            onLightThemeStyleChange = { style -> viewModel.setLightThemeStyle(style) },
                            onCustomAccentColorChange = { color -> viewModel.setCustomAccentColor(color) },
                            onArtworkScaleChange = { percent -> viewModel.setArtworkScalePercent(percent) },
                            onArtworkCornerRadiusChange = { radius -> viewModel.setArtworkCornerRadiusDp(radius) },
                            onManageFolders = { navController.navigate("folders") },
                            onScanLibrary = { viewModel.loadMusicLibrary(force = true) },
                            onDismiss = { showSettingsDialog = false }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestStoragePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        }
    }
}

@Composable
fun UniversalBottomNavigation(
    currentRoute: String,
    palette: ArtworkPalette? = null,
    isLightMode: Boolean = false,
    onNavigate: (String) -> Unit
) {
    val navBarInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val activeColor: Color = if (isLightMode) (palette?.lightAccent ?: Color(0xFF181A24)) else (palette?.accent ?: Color(0xFF64D2FF))

    val navBgBrush = if (isLightMode) {
        Brush.verticalGradient(
            listOf(
                activeColor.copy(alpha = 0.10f),
                activeColor.copy(alpha = 0.18f),
                activeColor.copy(alpha = 0.24f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                activeColor.copy(alpha = 0.12f),
                activeColor.copy(alpha = 0.21f),
                activeColor.copy(alpha = 0.30f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(navBgBrush)
            .border(
                width = 0.8.dp,
                color = if (isLightMode) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.09f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(bottom = navBarInsetBottom)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SimpNavItem(
                icon = Icons.Rounded.Home,
                label = "Home",
                selected = currentRoute == "home",
                activeColor = activeColor,
                isLightMode = isLightMode,
                onClick = { onNavigate("home") },
                modifier = Modifier.weight(1f)
            )
            SimpNavItem(
                icon = Icons.Rounded.LibraryMusic,
                label = "Library",
                selected = currentRoute == "library",
                activeColor = activeColor,
                isLightMode = isLightMode,
                onClick = { onNavigate("library") },
                modifier = Modifier.weight(1f)
            )
            SimpNavItem(
                icon = Icons.Rounded.Search,
                label = "Search",
                selected = currentRoute == "search",
                activeColor = activeColor,
                isLightMode = isLightMode,
                onClick = { onNavigate("search") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SimpNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    activeColor: Color = Color.White,
    isLightMode: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inactiveColor = if (isLightMode) Color(0xFF141724).copy(alpha = 0.50f) else Color.White.copy(alpha = 0.50f)
    val indicatorBg = if (selected) {
        activeColor.copy(alpha = if (isLightMode) 0.22f else 0.28f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(indicatorBg)
                .padding(horizontal = 18.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeColor else inactiveColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = Manrope,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) activeColor else inactiveColor
        )
    }
}