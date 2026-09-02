package com.kurixutian.oreotunes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.components.AlphabeticalSongRow
import com.kurixutian.oreotunes.ui.components.ArtworkThumbnail
import com.kurixutian.oreotunes.ui.components.GlassIconButton
import com.kurixutian.oreotunes.ui.theme.Manrope

@Composable
fun AlbumDetailScreen(
    albumName: String,
    allSongs: List<Song>,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onAddToQueue: (List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    val albumSongs = allSongs.filter { it.album.trim().equals(albumName.trim(), ignoreCase = true) }
    val firstSong = albumSongs.firstOrNull()
    val artistName = firstSong?.artist ?: "Unknown Artist"

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        text = "Album",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = contentTextColor
                    )
                }
            }

            // 2. Album Hero Header
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkThumbnail(
                        model = firstSong?.albumArtUri,
                        contentDescription = albumName,
                        shape = RoundedCornerShape(20.dp),
                        targetSizeDp = 120.dp,
                        modifier = Modifier.size(120.dp)
                    )

                    Spacer(modifier = Modifier.width(18.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = albumName,
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = contentTextColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = artistName,
                            fontFamily = Manrope,
                            fontSize = 14.sp,
                            color = subtleTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${albumSongs.size} tracks",
                            fontFamily = Manrope,
                            fontSize = 12.sp,
                            color = subtleTextColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 3. Play & Shuffle Buttons Row
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPlayAll(albumSongs) },
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
                        onClick = { onShuffleAll(albumSongs) },
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

            // 4. Tracks Header
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tracks",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontFamily = Manrope),
                    fontWeight = FontWeight.Bold,
                    color = contentTextColor
                )
            }

            // 5. Track Items
            items(albumSongs, key = { it.id }) { song ->
                AlphabeticalSongRow(
                    song = song,
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song) },
                    onOptionsClick = { onSongLongClick(song) }
                )
            }
        }
    }
}
