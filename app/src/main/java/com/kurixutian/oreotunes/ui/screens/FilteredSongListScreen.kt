package com.kurixutian.oreotunes.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.components.AlphabeticalSongRow
import com.kurixutian.oreotunes.ui.components.GlassIconButton
import com.kurixutian.oreotunes.ui.components.ModernGlassScrollBar
import com.kurixutian.oreotunes.ui.theme.Manrope

@Composable
fun FilteredSongListScreen(
    title: String,
    songs: List<Song>,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

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
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${songs.size} tracks",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope),
                        color = Color.White.copy(alpha = 0.50f)
                    )
                }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 180.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (songs.isEmpty()) {
                    item {
                        Text(
                            text = "No songs found in this list.",
                            fontFamily = Manrope,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(songs, key = { it.id }) { song ->
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

        if (songs.size > 8) {
            ModernGlassScrollBar(
                listState = listState,
                itemsList = songs.map { it.title },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 65.dp, bottom = 140.dp)
            )
        }
    }
}
