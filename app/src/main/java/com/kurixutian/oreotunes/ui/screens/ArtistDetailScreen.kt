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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurixutian.oreotunes.data.repository.matchesArtist
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.components.AlphabeticalSongRow
import com.kurixutian.oreotunes.ui.components.ArtworkThumbnail
import com.kurixutian.oreotunes.ui.components.GlassIconButton
import com.kurixutian.oreotunes.ui.theme.Manrope

@Composable
fun ArtistDetailScreen(
    artistName: String,
    allSongs: List<Song>,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val artistSongs = allSongs.filter { matchesArtist(it.artist, artistName) }
    val firstSong = artistSongs.firstOrNull()
    val distinctAlbums = artistSongs.map { it.album }.filter { it.isNotBlank() }.distinct()

    val isLight = MaterialTheme.colorScheme.background.red > 0.6f
    val contentTextColor = MaterialTheme.colorScheme.onBackground
    val subtleTextColor = contentTextColor.copy(alpha = 0.65f)
    val primaryAccent = MaterialTheme.colorScheme.primary

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
            // 1. Top Navigation Bar
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        size = 38.dp,
                        iconSize = 18.dp,
                        onClick = onBack
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Artist",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = contentTextColor
                    )
                }
            }

            // 2. Artist Profile Avatar & Bio Header
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ArtworkThumbnail(
                        model = firstSong?.albumArtUri,
                        contentDescription = artistName,
                        shape = CircleShape,
                        targetSizeDp = 130.dp,
                        modifier = Modifier.size(130.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = artistName,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = contentTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${artistSongs.size} songs • ${distinctAlbums.size} albums",
                        fontFamily = Manrope,
                        fontSize = 13.sp,
                        color = subtleTextColor
                    )
                }
            }

            // 3. Play & Shuffle Buttons Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPlayAll(artistSongs) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLight) primaryAccent else Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = { onShuffleAll(artistSongs) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLight) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.12f),
                            contentColor = contentTextColor
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // 4. Albums Horizontal Scroll
            if (distinctAlbums.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Albums",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = contentTextColor
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(distinctAlbums) { albumTitle ->
                            val albumFirstSong = artistSongs.find { it.album.equals(albumTitle, ignoreCase = true) }
                            Column(
                                modifier = Modifier
                                    .width(115.dp)
                                    .clickable { onAlbumClick(albumTitle) }
                            ) {
                                ArtworkThumbnail(
                                    model = albumFirstSong?.albumArtUri,
                                    contentDescription = albumTitle,
                                    shape = RoundedCornerShape(14.dp),
                                    targetSizeDp = 115.dp,
                                    modifier = Modifier.size(115.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = albumTitle,
                                    fontFamily = Manrope,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = contentTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 5. Songs Header
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Songs",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontFamily = Manrope),
                    fontWeight = FontWeight.Bold,
                    color = contentTextColor
                )
            }

            // 6. Songs List
            items(artistSongs, key = { it.id }) { song ->
                AlphabeticalSongRow(
                    song = song,
                    onClick = { onSongClick(song, artistSongs) },
                    onLongClick = { onSongLongClick(song) },
                    onOptionsClick = { onSongLongClick(song) }
                )
            }
        }
    }
}
