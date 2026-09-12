package com.kurixutian.oreotunes.ui.screens

import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kurixutian.oreotunes.data.model.Playlist
import com.kurixutian.oreotunes.data.repository.AlbumGroup
import com.kurixutian.oreotunes.data.repository.ArtistGroup
import com.kurixutian.oreotunes.data.repository.ArtworkPalette
import com.kurixutian.oreotunes.data.repository.GeminiMixResult
import com.kurixutian.oreotunes.data.repository.GeminiMoodEngine
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.components.AlphabeticalSongRow
import com.kurixutian.oreotunes.ui.components.ArtworkThumbnail
import com.kurixutian.oreotunes.ui.components.CompactTrackRow
import com.kurixutian.oreotunes.ui.components.GlassIconButton
import com.kurixutian.oreotunes.ui.components.ModernGlassScrollBar
import com.kurixutian.oreotunes.ui.components.SongCardItem
import com.kurixutian.oreotunes.ui.theme.Manrope
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    quickPickSongs: List<Song>,
    recentlyPlayedSongs: List<Song>,
    recentlyAddedSongs: List<Song>,
    suggestedAlbums: List<AlbumGroup>,
    featuredHeroAlbums: List<AlbumGroup>,
    suggestedArtists: List<ArtistGroup>,
    playlists: List<Playlist> = emptyList(),
    quickPickMode: String,
    heroRefreshHours: Int,
    palette: ArtworkPalette? = null,
    onQuickPickModeSelected: (String) -> Unit,
    onSongClick: (Song, List<Song>?) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onHeroAlbumPlay: (AlbumGroup) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onSeeAllRecentlyPlayed: () -> Unit,
    onSeeAllRecentlyAdded: () -> Unit,
    onSeeAllQuickPicks: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit = {},
    onCreatePlaylistWithSongs: (String, String, List<Long>) -> Unit = { _, _, _ -> },
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val moodEngine = remember { GeminiMoodEngine(context) }

    var showModeDropdown by remember { mutableStateOf(false) }
    var showMoodMixDialog by remember { mutableStateOf(false) }
    val modes = listOf("Recently Played", "Favorites", "Random", "Most Played", "Least Played")
    val homeListState = rememberLazyListState()

    val sortedSongs = remember(songs) { songs.sortedBy { it.title.lowercase() } }
    val heroAlbums = if (featuredHeroAlbums.isNotEmpty()) featuredHeroAlbums else suggestedAlbums.take(6)

    val isLight = MaterialTheme.colorScheme.background.red > 0.6f
    val dynamicPrimary = if (isLight) (palette?.lightAccent ?: Color(0xFF181A24)) else (palette?.accent ?: MaterialTheme.colorScheme.primary)
    val contentTextColor = MaterialTheme.colorScheme.onBackground
    val subtleTextColor = contentTextColor.copy(alpha = 0.65f)

    val cardBg = if (isLight) Color.Black.copy(alpha = 0.05f) else (palette?.surfaceColor ?: Color(0xFF1C233A)).copy(alpha = 0.50f)

    val headerCount = remember(heroAlbums, playlists, recentlyAddedSongs, suggestedArtists) {
        var count = 3
        if (heroAlbums.isNotEmpty()) count++
        if (playlists.isNotEmpty()) count++
        if (recentlyAddedSongs.isNotEmpty()) count++
        if (suggestedArtists.isNotEmpty()) count++
        count++
        count
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            state = homeListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. Header Bar with Shuffle All & Play All Buttons
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discover",
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = contentTextColor
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(icon = Icons.Rounded.Shuffle, contentDescription = "Shuffle All", size = 38.dp, iconSize = 18.dp, onClick = onShuffleAll)
                        GlassIconButton(
                            icon = Icons.Rounded.PlayArrow,
                            contentDescription = "Play All",
                            size = 38.dp,
                            iconSize = 20.dp,
                            isPrimary = true,
                            primaryColor = dynamicPrimary,
                            onClick = onPlayAll
                        )
                        GlassIconButton(
                            icon = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Mix",
                            size = 38.dp,
                            iconSize = 18.dp,
                            onClick = { showMoodMixDialog = true }
                        )
                        GlassIconButton(icon = Icons.Rounded.BarChart, contentDescription = "Stats", size = 38.dp, iconSize = 18.dp, onClick = onOpenStats)
                        GlassIconButton(icon = Icons.Rounded.Settings, contentDescription = "Settings", size = 38.dp, iconSize = 18.dp, onClick = onOpenSettings)
                    }
                }
            }

            // 2. Featured Big Card Carousel
            if (heroAlbums.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(heroAlbums, key = { it.title }) { album ->
                            Box(
                                modifier = Modifier
                                    .width(270.dp)
                                    .height(175.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFF1E2438).copy(alpha = 0.6f))
                                    .clickable { onAlbumClick(album.title) }
                            ) {
                                ArtworkThumbnail(
                                    model = album.albumArtUri,
                                    contentDescription = album.title,
                                    shape = RoundedCornerShape(22.dp),
                                    targetSizeDp = 270.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.20f),
                                                    Color.Black.copy(alpha = 0.75f)
                                                )
                                            )
                                        )
                                )
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = album.title,
                                            fontFamily = Manrope,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${album.artist} • ${album.songCount} tracks",
                                            fontFamily = Manrope,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FilledIconButton(
                                        onClick = { onHeroAlbumPlay(album) },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color.White,
                                            contentColor = Color.Black
                                        ),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Album")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Dedicated Gemini AI Mood & Trip Mix Card
            item {
                val mixCardBg = if (isLight) {
                    Brush.linearGradient(
                        listOf(
                            dynamicPrimary.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.03f),
                            Color.Black.copy(alpha = 0.06f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            dynamicPrimary.copy(alpha = 0.25f),
                            (palette?.dominant ?: Color(0xFF181820)).copy(alpha = 0.35f),
                            (palette?.surfaceColor ?: Color(0xFF141724)).copy(alpha = 0.85f)
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(mixCardBg)
                        .clickable { showMoodMixDialog = true }
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(dynamicPrimary.copy(alpha = if (isLight) 0.15f else 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = dynamicPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Gemini Mood & Trip Mix",
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = contentTextColor
                                    )
                                    Text(
                                        text = "AI creates custom playlists from your tracks",
                                        fontFamily = Manrope,
                                        fontSize = 12.sp,
                                        color = subtleTextColor
                                    )
                                }
                            }

                            FilledIconButton(
                                onClick = { showMoodMixDialog = true },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isLight) dynamicPrimary else Color.White,
                                    contentColor = if (isLight) Color.White else Color.Black
                                ),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Create Mix",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val quickVibes = listOf("Night Drive", "Chill Vibe", "Gym Pump", "Roadtrip", "Lofi Focus")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quickVibes) { vibe ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f))
                                        .clickable { showMoodMixDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "✨ $vibe",
                                        fontFamily = Manrope,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = contentTextColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Quick Picks Carousel
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showModeDropdown = true }
                        ) {
                            Text(
                                text = "Quick picks",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontFamily = Manrope),
                                fontWeight = FontWeight.Bold,
                                color = contentTextColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(dynamicPrimary.copy(alpha = if (isLight) 0.12f else 0.20f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$quickPickMode ▾",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = Manrope),
                                    color = dynamicPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            DropdownMenu(
                                expanded = showModeDropdown,
                                onDismissRequest = { showModeDropdown = false },
                                modifier = Modifier.background(if (isLight) Color.White else (palette?.surfaceColor ?: Color(0xFF1A2136)))
                            ) {
                                modes.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode, color = contentTextColor, fontFamily = Manrope, fontWeight = FontWeight.SemiBold) },
                                        onClick = {
                                            onQuickPickModeSelected(mode)
                                            showModeDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = "See all →",
                            fontFamily = Manrope,
                            fontSize = 12.sp,
                            color = subtleTextColor,
                            modifier = Modifier.clickable(onClick = onSeeAllQuickPicks)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (quickPickSongs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (quickPickMode) {
                                    "Recently Played" -> "No recently played tracks yet."
                                    "Favorites" -> "No favorite songs added yet."
                                    "Most Played" -> "No played songs recorded yet."
                                    "Least Played" -> "No unplayed/skipped tracks recorded yet."
                                    else -> "No tracks available."
                                },
                                fontFamily = Manrope,
                                color = subtleTextColor,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val chunkedPicks = remember(quickPickSongs) {
                            quickPickSongs.chunked(4)
                        }

                        val pagerState = key(quickPickMode, chunkedPicks.size) {
                            rememberPagerState(pageCount = { maxOf(1, chunkedPicks.size) })
                        }

                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(end = if (chunkedPicks.size > 1) 28.dp else 0.dp),
                            pageSpacing = 12.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            val columnSongs = chunkedPicks.getOrNull(page) ?: return@HorizontalPager
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(cardBg)
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                columnSongs.forEach { song ->
                                    CompactTrackRow(
                                        song = song,
                                        onClick = { onSongClick(song, quickPickSongs) },
                                        onLongClick = { onSongLongClick(song) },
                                        onOptionsClick = { onSongLongClick(song) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Playlists Carousel
            if (playlists.isNotEmpty()) {
                item {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = contentTextColor
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            val isFav = playlist.name.equals("Favorites", ignoreCase = true)
                            val playlistCover = playlist.coverUri?.let { android.net.Uri.parse(it) }
                                ?: songs.find { it.id in playlist.songIds }?.albumArtUri

                            Column(
                                modifier = Modifier
                                    .width(135.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onPlaylistClick(playlist) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(135.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isFav) Brush.linearGradient(listOf(Color(0xFF8B1E3F), Color(0xFFC92A4E)))
                                            else Brush.linearGradient(listOf(Color(0xFF222B46), Color(0xFF161B2E)))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (playlistCover != null && !isFav) {
                                        ArtworkThumbnail(
                                            model = playlistCover,
                                            contentDescription = playlist.name,
                                            shape = RoundedCornerShape(16.dp),
                                            targetSizeDp = 135.dp,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isFav) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = playlist.name,
                                            tint = Color.White,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, fontFamily = Manrope),
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${playlist.songIds.size} songs",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, fontFamily = Manrope),
                                    color = subtleTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 6. Recently Added
            if (recentlyAddedSongs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recently Added",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontFamily = Manrope),
                            fontWeight = FontWeight.Bold,
                            color = contentTextColor
                        )
                        Text(
                            text = "See all →",
                            fontFamily = Manrope,
                            fontSize = 12.sp,
                            color = subtleTextColor,
                            modifier = Modifier.clickable(onClick = onSeeAllRecentlyAdded)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(recentlyAddedSongs.take(10), key = { it.id }) { song ->
                            SongCardItem(
                                song = song,
                                onClick = { onSongClick(song, recentlyAddedSongs) },
                                onLongClick = { onSongLongClick(song) }
                            )
                        }
                    }
                }
            }

            // 7. Suggested Artists
            if (suggestedArtists.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Artists",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = contentTextColor
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(suggestedArtists.take(10), key = { it.name }) { artist ->
                            ArtistCardItem(artist = artist, onClick = { onArtistClick(artist.name) })
                        }
                    }
                }
            }

            // 8. Complete Library Tracks Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Tracks (${sortedSongs.size})",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = contentTextColor
                    )
                }
            }

            items(sortedSongs, key = { it.id }) { song ->
                AlphabeticalSongRow(
                    song = song,
                    onClick = { onSongClick(song, sortedSongs) },
                    onLongClick = { onSongLongClick(song) },
                    onOptionsClick = { onSongLongClick(song) }
                )
            }
        }

        if (sortedSongs.size > 8) {
            ModernGlassScrollBar(
                listState = homeListState,
                headerOffsetCount = headerCount,
                itemsList = sortedSongs.map { it.title },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.65f)
                    .padding(end = 2.dp)
            )
        }
    }

    // Gemini Mood & Trip Mix Dialog
    if (showMoodMixDialog) {
        GeminiMoodDialog(
            availableSongs = songs,
            moodEngine = moodEngine,
            palette = palette,
            onPlayMix = { mixResult: GeminiMixResult ->
                mixResult.songs.firstOrNull()?.let { firstSong ->
                    onSongClick(firstSong, mixResult.songs)
                }
                showMoodMixDialog = false
            },
            onSaveAsPlaylist = { mixResult: GeminiMixResult ->
                onCreatePlaylistWithSongs(
                    mixResult.title,
                    mixResult.description,
                    mixResult.songs.map { it.id }
                )
            },
            onDismiss = { showMoodMixDialog = false }
        )
    }
}

@Composable
fun GeminiMoodDialog(
    availableSongs: List<Song>,
    moodEngine: GeminiMoodEngine,
    palette: ArtworkPalette? = null,
    onPlayMix: (GeminiMixResult) -> Unit,
    onSaveAsPlaylist: (GeminiMixResult) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var inputVibe by remember { mutableStateOf("") }
    var inputApiKey by remember { mutableStateOf(moodEngine.getApiKey()) }
    var isApiKeySaved by remember { mutableStateOf(moodEngine.getApiKey().isNotBlank()) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generatedResult by remember { mutableStateOf<GeminiMixResult?>(null) }
    var isSavedToLibrary by remember { mutableStateOf(false) }

    val presetVibes = listOf(
        "Late Night City Drive",
        "Acoustic Coffeehouse",
        "High Energy Workout",
        "Rainy Nostalgia",
        "Sunny Summer Roadtrip",
        "Deep Focus & Coding"
    )

    val dynamicPrimary = palette?.accent ?: Color(0xFF64D2FF)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                window.setDimAmount(0.55f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes.blurBehindRadius = 48
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background((palette?.surfaceColor ?: Color(0xFF141724)).copy(alpha = 0.95f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(22.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Gemini Mood DJ",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontFamily = Manrope),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "AI creates custom playlists from your tracks",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFamily = Manrope),
                                color = Color.White.copy(alpha = 0.50f)
                            )
                        }

                        GlassIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Close",
                            size = 38.dp,
                            iconSize = 18.dp,
                            onClick = onDismiss
                        )
                    }
                }

                if (!isApiKeySaved || moodEngine.getApiKey().isBlank()) {
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "Enter Gemini API Key",
                                fontFamily = Manrope,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = inputApiKey,
                                onValueChange = { inputApiKey = it },
                                placeholder = { Text("Paste Gemini API Key...", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = dynamicPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    moodEngine.saveApiKey(inputApiKey)
                                    isApiKeySaved = inputApiKey.isNotBlank()
                                },
                                enabled = inputApiKey.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = dynamicPrimary, contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Save Key", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputVibe,
                        onValueChange = {
                            inputVibe = it
                            errorMessage = null
                        },
                        placeholder = { Text("e.g. Midnight highway drive, chill lofi...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = dynamicPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "POPULAR VIBES",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.40f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presetVibes) { vibe ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { inputVibe = vibe }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = vibe,
                                    fontFamily = Manrope,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFF6584),
                            fontFamily = Manrope,
                            fontSize = 12.sp
                        )
                    }
                }

                generatedResult?.let { result ->
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.title,
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = dynamicPrimary
                                    )
                                    Text(
                                        text = result.description,
                                        fontFamily = Manrope,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.70f)
                                    )
                                }
                                Text(
                                    text = "${result.songs.size} tracks",
                                    fontFamily = Manrope,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onPlayMix(result) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = dynamicPrimary,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play Mix", fontFamily = Manrope, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        onSaveAsPlaylist(result)
                                        isSavedToLibrary = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSavedToLibrary) Color(0xFF34C759) else Color.White.copy(alpha = 0.15f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        if (isSavedToLibrary) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.PlaylistAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isSavedToLibrary) "Saved ✓" else "Save Playlist", fontFamily = Manrope, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = {
                            if (inputVibe.isNotBlank()) {
                                isLoading = true
                                errorMessage = null
                                isSavedToLibrary = false
                                coroutineScope.launch {
                                    val result = moodEngine.generateMoodMix(inputVibe, availableSongs)
                                    isLoading = false
                                    result.fold(
                                        onSuccess = { mixResult ->
                                            generatedResult = mixResult
                                        },
                                        onFailure = { err ->
                                            errorMessage = err.message ?: "Failed to generate mix."
                                        }
                                    )
                                }
                            }
                        },
                        enabled = !isLoading && inputVibe.isNotBlank() && moodEngine.getApiKey().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dynamicPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini is curating...", fontFamily = Manrope, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate AI Playlist", fontFamily = Manrope, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistCardItem(artist: ArtistGroup, onClick: () -> Unit) {
    val contentTextColor = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier
            .width(105.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArtworkThumbnail(
            model = artist.albumArtUri,
            contentDescription = artist.name,
            shape = CircleShape,
            targetSizeDp = 90.dp,
            modifier = Modifier.size(90.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, fontFamily = Manrope),
            fontWeight = FontWeight.Bold,
            color = contentTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
