package com.kurixutian.oreotunes.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.theme.Manrope

@Composable
fun ArtworkThumbnail(
    model: Uri?,
    contentDescription: String?,
    shape: Shape = RoundedCornerShape(10.dp),
    targetSizeDp: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF1B1622)),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(targetSizeDp * 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlphabeticalSongRow(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = primaryText.copy(alpha = 0.65f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkThumbnail(
            model = song.albumArtUri,
            contentDescription = song.title,
            shape = RoundedCornerShape(10.dp),
            targetSizeDp = 48.dp,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (song.album.isNotBlank()) "${song.artist} • ${song.album}" else song.artist,
                fontFamily = Manrope,
                fontSize = 12.5.sp,
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onOptionsClick) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = secondaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactTrackRow(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = primaryText.copy(alpha = 0.65f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkThumbnail(
            model = song.albumArtUri,
            contentDescription = song.title,
            shape = RoundedCornerShape(8.dp),
            targetSizeDp = 42.dp,
            modifier = Modifier.size(42.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                fontFamily = Manrope,
                fontSize = 12.sp,
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onOptionsClick) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = secondaryText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongCardItem(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = primaryText.copy(alpha = 0.65f)

    Column(
        modifier = modifier
            .width(135.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        ArtworkThumbnail(
            model = song.albumArtUri,
            contentDescription = song.title,
            shape = RoundedCornerShape(16.dp),
            targetSizeDp = 135.dp,
            modifier = Modifier.size(135.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = song.title,
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            fontFamily = Manrope,
            fontSize = 11.5.sp,
            color = secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
