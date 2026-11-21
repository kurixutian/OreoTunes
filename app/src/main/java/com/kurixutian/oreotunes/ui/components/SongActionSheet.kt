package com.kurixutian.oreotunes.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.theme.Manrope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionSheet(
    song: Song?,
    onDismiss: () -> Unit,
    onPlay: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onEditMetadata: (Song) -> Unit,
    onAutoFetchMetadata: ((Song) -> Unit)? = null,
    onViewAlbum: (String) -> Unit,
    onViewArtist: (String) -> Unit,
    onShowDetails: (Song) -> Unit
) {
    if (song == null) return

    val coroutineScope = rememberCoroutineScope()
    val isLight = MaterialTheme.colorScheme.background.red > 0.6f
    val modalState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetBg = if (isLight) Color.White.copy(alpha = 0.96f) else Color(0xFF141724).copy(alpha = 0.94f)
    val contentTextColor = if (isLight) Color(0xFF121520) else Color.White
    val subtleTextColor = contentTextColor.copy(alpha = 0.65f)

    val hasInWindowBackdrop = LocalLiquidGlassBackdrop.current != null

    fun dismissGracefully(action: () -> Unit) {
        if (hasInWindowBackdrop) {
            onDismiss()
            action()
        } else {
            coroutineScope.launch {
                try {
                    modalState.hide()
                } finally {
                    onDismiss()
                    action()
                }
            }
        }
    }

    val sheetContainer: @Composable (@Composable () -> Unit) -> Unit = { inner ->
        if (hasInWindowBackdrop) {
            BackHandler(onBack = onDismiss)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(50f)
                    .background(Color.Black.copy(alpha = 0.36f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                inner()
            }
        } else {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = modalState,
                containerColor = Color.Transparent,
                scrimColor = Color.Black.copy(alpha = 0.35f),
                dragHandle = null
            ) {
                inner()
            }
        }
    }

    sheetContainer {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    isLight = isLight,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .navigationBarsPadding()
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 14.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(contentTextColor.copy(alpha = 0.20f))
                        .align(Alignment.CenterHorizontally)
                )

                // Song Header Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkThumbnail(
                        model = song.albumArtUri,
                        contentDescription = song.title,
                        shape = RoundedCornerShape(12.dp),
                        targetSizeDp = 52.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = contentTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (song.album.isNotBlank()) "${song.artist} • ${song.album}" else song.artist,
                            fontFamily = Manrope,
                            fontSize = 12.5.sp,
                            color = subtleTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Playback & Playlist Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickActionPill(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "Play Next",
                        contentTextColor = contentTextColor,
                        onClick = { dismissGracefully { onPlayNext(song) } }
                    )
                    QuickActionPill(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "To Queue",
                        contentTextColor = contentTextColor,
                        onClick = { dismissGracefully { onAddToQueue(song) } }
                    )
                    QuickActionPill(
                        icon = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = if (song.isFavorite) "Favorited" else "Favorite",
                        contentTextColor = if (song.isFavorite) Color(0xFFFF4B72) else contentTextColor,
                        iconTint = if (song.isFavorite) Color(0xFFFF4B72) else contentTextColor,
                        onClick = { dismissGracefully { onToggleFavorite(song) } }
                    )
                    QuickActionPill(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        label = "Playlist",
                        contentTextColor = contentTextColor,
                        onClick = { dismissGracefully { onAddToPlaylist(song) } }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = contentTextColor.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(6.dp))

                // Actions List
                ActionRow(Icons.Rounded.PlayArrow, "Play Now", contentTextColor) { dismissGracefully { onPlay(song) } }

                if (onAutoFetchMetadata != null) {
                    ActionRow(Icons.Rounded.AutoAwesome, "Auto-Fetch High-Res Cover & Tags", MaterialTheme.colorScheme.primary) {
                        dismissGracefully { onAutoFetchMetadata(song) }
                    }
                }

                ActionRow(Icons.Rounded.Edit, "Edit Info & Tags", contentTextColor) { dismissGracefully { onEditMetadata(song) } }
                ActionRow(Icons.Rounded.Info, "Song Details", contentTextColor) { dismissGracefully { onShowDetails(song) } }

                if (song.album.isNotBlank()) {
                    ActionRow(Icons.Rounded.Album, "View Album", contentTextColor) { dismissGracefully { onViewAlbum(song.album) } }
                }
                if (song.artist.isNotBlank()) {
                    ActionRow(Icons.Rounded.Person, "View Artist", contentTextColor) { dismissGracefully { onViewArtist(song.artist) } }
                }
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    contentTextColor: Color,
    iconTint: Color = contentTextColor,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(contentTextColor.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontFamily = Manrope,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = contentTextColor,
            maxLines = 1
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    textColor: Color,
    iconTint: Color = textColor,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            fontFamily = Manrope,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
