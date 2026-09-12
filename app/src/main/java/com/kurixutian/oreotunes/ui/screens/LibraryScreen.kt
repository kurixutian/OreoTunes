package com.kurixutian.oreotunes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurixutian.oreotunes.data.model.Playlist
import com.kurixutian.oreotunes.data.repository.AlbumGroup
import com.kurixutian.oreotunes.data.repository.ArtistGroup
import com.kurixutian.oreotunes.domain.model.FolderInfo
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.components.AlphabeticalSongRow
import com.kurixutian.oreotunes.ui.components.ArtworkThumbnail
import com.kurixutian.oreotunes.ui.theme.Manrope

enum class LibraryTab(val label: String) {
    TRACKS("Tracks"),
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders")
}

@Composable
fun LibraryScreen(
    songs: List<Song>,
    playlists: List<Playlist>,
    albums: List<AlbumGroup>,
    artists: List<ArtistGroup>,
    detectedFolders: List<FolderInfo>,
    selectedFolders: Set<String>,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onFolderClick: (FolderInfo) -> Unit,
    onNavigateToFolders: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.TRACKS) }
    val primaryAccent = MaterialTheme.colorScheme.primary
    val contentTextColor = MaterialTheme.colorScheme.onBackground
    val subtleTextColor = contentTextColor.copy(alpha = 0.65f)
    val isLight = MaterialTheme.colorScheme.background.red > 0.6f

    val sortedSongs = remember(songs) { songs.sortedBy { it.title.lowercase() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Title
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = contentTextColor
                    )

                    if (selectedTab == LibraryTab.PLAYLISTS) {
                        IconButton(
                            onClick = onCreatePlaylist,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Create Playlist", tint = contentTextColor)
                        }
                    } else if (selectedTab == LibraryTab.FOLDERS) {
                        TextButton(onClick = onNavigateToFolders) {
                            Text("Manage", fontFamily = Manrope, fontWeight = FontWeight.Bold, color = primaryAccent)
                        }
                    }
                }
            }

            // 2. Tab Selector Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(LibraryTab.values()) { tab ->
                        val isSelected = tab == selectedTab
                        val tabBg = if (isSelected) primaryAccent else if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)
                        val tabTextColor = if (isSelected) Color.Black else contentTextColor

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(tabBg)
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tab.label,
                                fontFamily = Manrope,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = tabTextColor
                            )
                        }
                    }
                }
            }

            // 3. Tab Content
            when (selectedTab) {
                LibraryTab.TRACKS -> {
                    if (sortedSongs.isEmpty()) {
                        item {
                            EmptyLibraryNotice(message = "No songs found in your active storage folders.")
                        }
                    } else {
                        items(sortedSongs, key = { it.id }) { song ->
                            AlphabeticalSongRow(
                                song = song,
                                onClick = { onSongClick(song) },
                                onLongClick = { onSongLongClick(song) },
                                onOptionsClick = { onSongLongClick(song) }
                            )
                        }
                    }
                }

                LibraryTab.PLAYLISTS -> {
                    if (playlists.isEmpty()) {
                        item {
                            EmptyLibraryNotice(message = "No playlists created yet. Tap '+' above to create one.")
                        }
                    } else {
                        items(playlists, key = { it.id }) { playlist ->
                            val isFav = playlist.name.equals("Favorites", ignoreCase = true)
                            val coverUri = playlist.coverUri?.let { android.net.Uri.parse(it) }
                                ?: songs.find { it.id in playlist.songIds }?.albumArtUri

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onPlaylistClick(playlist) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isFav) Brush.linearGradient(listOf(Color(0xFFFF4B72), Color(0xFFFF7A96)))
                                            else Brush.linearGradient(listOf(Color(0xFF2B334C), Color(0xFF191F32)))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (coverUri != null && !isFav) {
                                        ArtworkThumbnail(
                                            model = coverUri,
                                            contentDescription = playlist.name,
                                            shape = RoundedCornerShape(12.dp),
                                            targetSizeDp = 54.dp,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isFav) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = contentTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${playlist.songIds.size} songs",
                                        fontFamily = Manrope,
                                        fontSize = 12.5.sp,
                                        color = subtleTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = subtleTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                LibraryTab.ALBUMS -> {
                    if (albums.isEmpty()) {
                        item {
                            EmptyLibraryNotice(message = "No albums detected.")
                        }
                    } else {
                        items(albums, key = { it.title }) { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onAlbumClick(album.title) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkThumbnail(
                                    model = album.albumArtUri,
                                    contentDescription = album.title,
                                    shape = RoundedCornerShape(12.dp),
                                    targetSizeDp = 54.dp,
                                    modifier = Modifier.size(54.dp)
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.title,
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = contentTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${album.artist} • ${album.songCount} songs",
                                        fontFamily = Manrope,
                                        fontSize = 12.5.sp,
                                        color = subtleTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = subtleTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                LibraryTab.ARTISTS -> {
                    if (artists.isEmpty()) {
                        item {
                            EmptyLibraryNotice(message = "No artists detected.")
                        }
                    } else {
                        items(artists, key = { it.name }) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onArtistClick(artist.name) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkThumbnail(
                                    model = artist.albumArtUri,
                                    contentDescription = artist.name,
                                    shape = CircleShape,
                                    targetSizeDp = 54.dp,
                                    modifier = Modifier.size(54.dp)
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist.name,
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = contentTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${artist.songCount} tracks",
                                        fontFamily = Manrope,
                                        fontSize = 12.5.sp,
                                        color = subtleTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = subtleTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                LibraryTab.FOLDERS -> {
                    if (detectedFolders.isEmpty()) {
                        item {
                            EmptyLibraryNotice(message = "No folders active. Tap 'Manage' to select directories.")
                        }
                    } else {
                        items(detectedFolders, key = { it.path }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onFolderClick(folder) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(primaryAccent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = primaryAccent,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = contentTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${folder.songCount} tracks",
                                        fontFamily = Manrope,
                                        fontSize = 12.5.sp,
                                        color = subtleTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = subtleTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryNotice(message: String) {
    val contentTextColor = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontFamily = Manrope,
            fontSize = 14.sp,
            color = contentTextColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
