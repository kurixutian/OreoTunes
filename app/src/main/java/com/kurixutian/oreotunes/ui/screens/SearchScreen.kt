package com.kurixutian.oreotunes.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurixutian.oreotunes.data.model.Playlist
import com.kurixutian.oreotunes.data.repository.AlbumGroup
import com.kurixutian.oreotunes.data.repository.ArtistGroup
import com.kurixutian.oreotunes.data.repository.splitArtists
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.components.AlphabeticalSongRow
import com.kurixutian.oreotunes.ui.components.ArtworkThumbnail
import com.kurixutian.oreotunes.ui.components.GlassIconButton
import com.kurixutian.oreotunes.ui.components.ModernGlassScrollBar
import com.kurixutian.oreotunes.ui.theme.Manrope

@Composable
fun SearchScreen(
    songs: List<Song>,
    albums: List<AlbumGroup>,
    artists: List<ArtistGroup>,
    playlists: List<Playlist>,
    query: String,
    searchHistory: List<String>,
    onQueryChange: (String) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onRecordHistory: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val cleanQuery = query.trim()

    val matchedSongs = remember(cleanQuery, songs) {
        if (cleanQuery.isBlank()) emptyList()
        else songs.filter {
            it.title.contains(cleanQuery, ignoreCase = true) ||
            it.artist.contains(cleanQuery, ignoreCase = true) ||
            it.album.contains(cleanQuery, ignoreCase = true)
        }
    }

    val matchedArtists = remember(cleanQuery, artists, songs) {
        if (cleanQuery.isBlank()) emptyList()
        else {
            val fromArtists = artists.filter { it.name.contains(cleanQuery, ignoreCase = true) }
            if (fromArtists.isNotEmpty()) fromArtists
            else {
                songs.flatMap { splitArtists(it.artist) }
                    .filter { it.contains(cleanQuery, ignoreCase = true) }
                    .distinct()
                    .map { name ->
                        val matchingSongs = songs.filter { it.artist.contains(name, ignoreCase = true) }
                        ArtistGroup(
                            name = name,
                            albumArtUri = matchingSongs.firstOrNull()?.albumArtUri,
                            songCount = matchingSongs.size,
                            songs = matchingSongs
                        )
                    }
            }
        }
    }

    val matchedAlbums = remember(cleanQuery, albums, songs) {
        if (cleanQuery.isBlank()) emptyList()
        else {
            val fromAlbums = albums.filter {
                it.title.contains(cleanQuery, ignoreCase = true) || it.artist.contains(cleanQuery, ignoreCase = true)
            }
            if (fromAlbums.isNotEmpty()) fromAlbums
            else {
                songs.groupBy { it.album.ifBlank { "Unknown Album" } }
                    .filter { it.key.contains(cleanQuery, ignoreCase = true) }
                    .map { (title, songsList) ->
                        AlbumGroup(
                            title = title,
                            artist = songsList.firstOrNull()?.artist ?: "Unknown",
                            albumArtUri = songsList.firstOrNull()?.albumArtUri,
                            songCount = songsList.size,
                            songs = songsList
                        )
                    }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
        ) {
            // Search Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
                Spacer(modifier = Modifier.width(10.dp))
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text("Search songs, artists, albums...", color = Color.White.copy(alpha = 0.45f), fontFamily = Manrope)
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1B1F32).copy(alpha = 0.70f),
                        unfocusedContainerColor = Color(0xFF1B1F32).copy(alpha = 0.50f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Search History
                if (cleanQuery.isBlank() && searchHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent Searches", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Clear all", style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope), color = Color(0xFFFF6584), modifier = Modifier.clickable(onClick = onClearHistory))
                        }
                    }
                    items(searchHistory) { historyItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onQueryChange(historyItem) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(historyItem, fontFamily = Manrope, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                            }
                            IconButton(onClick = { onDeleteHistoryItem(historyItem) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Matched Artists Row
                if (matchedArtists.isNotEmpty()) {
                    item {
                        Text("Artists", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(matchedArtists, key = { it.name }) { artist ->
                                Column(
                                    modifier = Modifier
                                        .width(95.dp)
                                        .clickable {
                                            onRecordHistory(cleanQuery)
                                            onArtistClick(artist.name)
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    ArtworkThumbnail(
                                        model = artist.albumArtUri,
                                        contentDescription = artist.name,
                                        shape = CircleShape,
                                        targetSizeDp = 80.dp,
                                        modifier = Modifier.size(80.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(artist.name, style = MaterialTheme.typography.titleSmall.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // Matched Albums Row
                if (matchedAlbums.isNotEmpty()) {
                    item {
                        Text("Albums", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(matchedAlbums, key = { it.title }) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clickable {
                                            onRecordHistory(cleanQuery)
                                            onAlbumClick(album.title)
                                        }
                                ) {
                                    ArtworkThumbnail(
                                        model = album.albumArtUri,
                                        contentDescription = album.title,
                                        shape = RoundedCornerShape(16.dp),
                                        targetSizeDp = 130.dp,
                                        modifier = Modifier.size(130.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(album.title, style = MaterialTheme.typography.titleSmall.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(album.artist, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope), color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // Matched Songs
                if (matchedSongs.isNotEmpty()) {
                    item {
                        Text("Tracks (${matchedSongs.size})", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    items(matchedSongs, key = { it.id }) { song ->
                        AlphabeticalSongRow(
                            song = song,
                            onClick = {
                                onRecordHistory(cleanQuery)
                                onSongClick(song, matchedSongs)
                            },
                            onLongClick = {},
                            onOptionsClick = {}
                        )
                    }
                }
            }
        }

        if (matchedSongs.size > 8) {
            ModernGlassScrollBar(
                listState = listState,
                itemsList = matchedSongs.map { it.title },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 80.dp, bottom = 140.dp)
            )
        }
    }
}
