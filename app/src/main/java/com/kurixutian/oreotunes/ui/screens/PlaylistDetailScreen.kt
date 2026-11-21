package com.kurixutian.oreotunes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurixutian.oreotunes.data.model.Playlist
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.components.AlphabeticalSongRow
import com.kurixutian.oreotunes.ui.components.ArtworkThumbnail
import com.kurixutian.oreotunes.ui.components.GlassIconButton
import com.kurixutian.oreotunes.ui.components.LiquidGlassModal
import com.kurixutian.oreotunes.ui.components.ModernGlassScrollBar
import com.kurixutian.oreotunes.ui.components.liquidGlassSurface
import com.kurixutian.oreotunes.ui.theme.Manrope

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    allSongs: List<Song>,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onEditPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onAddSongs: (Playlist) -> Unit,
    onRemoveSong: (Playlist, Song) -> Unit,
    onReorderSong: (Playlist, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlist == null) return

    val listState = rememberLazyListState()
    val songPendingRemoval = remember { mutableStateOf<Song?>(null) }

    val playlistSongs = remember(playlist, allSongs) {
        val map = allSongs.associateBy { it.id }
        playlist.songIds.mapNotNull { map[it] }
    }
    val coverUri = playlist.coverUri ?: playlistSongs.firstOrNull()?.albumArtUri?.toString()

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!playlist.name.equals("Favorites", ignoreCase = true)) {
                            GlassIconButton(
                                icon = Icons.Rounded.Edit,
                                contentDescription = "Edit Playlist",
                                onClick = { onEditPlaylist(playlist) }
                            )
                            GlassIconButton(
                                icon = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete Playlist",
                                onClick = { onDeletePlaylist(playlist) }
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (coverUri != null) {
                        ArtworkThumbnail(
                            model = android.net.Uri.parse(coverUri),
                            contentDescription = playlist.name,
                            shape = RoundedCornerShape(24.dp),
                            targetSizeDp = 190.dp,
                            modifier = Modifier.size(190.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playlist.description.isNotBlank()) {
                        Text(
                            text = playlist.description,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Manrope),
                            color = Color.White.copy(alpha = 0.60f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${playlistSongs.size} tracks",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope),
                        color = Color.White.copy(alpha = 0.50f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPlayAll(playlistSongs) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play All",
                                fontFamily = Manrope,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                        }

                        Button(
                            onClick = { onShuffleAll(playlistSongs) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(19.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shuffle All",
                                fontFamily = Manrope,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                        }

                        if (!playlist.name.equals("Favorites", ignoreCase = true)) {
                            FilledIconButton(
                                onClick = { onAddSongs(playlist) },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.14f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add Songs", modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }

            if (playlistSongs.isEmpty()) {
                item {
                    Text(
                        text = "This playlist is empty. Tap '+' to add tracks.",
                        fontFamily = Manrope,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(
                    items = playlistSongs,
                    key = { it.id },
                    contentType = { "playlist_song_row" }
                ) { song ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlphabeticalSongRow(
                            song = song,
                            onClick = { onSongClick(song) },
                            onLongClick = { onSongLongClick(song) },
                            onOptionsClick = {
                                if (playlist.name.equals("Favorites", ignoreCase = true)) {
                                    onSongLongClick(song)
                                } else {
                                    songPendingRemoval.value = song
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val index = playlistSongs.indexOf(song)

                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        onReorderSong(playlist, index, index - 1)
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                    tint = if (index > 0) {
                                        Color.White.copy(alpha = 0.85f)
                                    } else {
                                        Color.White.copy(alpha = 0.20f)
                                    }
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (index < playlistSongs.lastIndex) {
                                        onReorderSong(playlist, index, index + 1)
                                    }
                                },
                                enabled = index < playlistSongs.lastIndex,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    tint = if (index < playlistSongs.lastIndex) {
                                        Color.White.copy(alpha = 0.85f)
                                    } else {
                                        Color.White.copy(alpha = 0.20f)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (playlistSongs.size > 8) {
            ModernGlassScrollBar(
                listState = listState,
                headerOffsetCount = 2,
                titleProvider = { idx -> if (idx in playlistSongs.indices) playlistSongs[idx].title else "" },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 80.dp, bottom = 140.dp)
            )
        }
    }

    songPendingRemoval.value?.let { song ->
        val isLight = MaterialTheme.colorScheme.background.red > 0.6f
        val dialogTextColor = if (isLight) Color(0xFF121520) else Color.White
        LiquidGlassModal(
            onDismissRequest = { songPendingRemoval.value = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .liquidGlassSurface(isLight = isLight)
                    .padding(22.dp)
            ) {
                Text(
                    text = "Remove from playlist?",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    color = dialogTextColor
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "\"${song.title}\" will be removed from \"${playlist.name}\". The song will remain in your library.",
                    fontFamily = Manrope,
                    color = dialogTextColor.copy(alpha = 0.78f)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { songPendingRemoval.value = null }) {
                        Text(text = "Cancel", fontFamily = Manrope)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onRemoveSong(playlist, song)
                            songPendingRemoval.value = null
                        }
                    ) {
                        Text(text = "Remove", fontFamily = Manrope)
                    }
                }
            }
        }
    }
}
