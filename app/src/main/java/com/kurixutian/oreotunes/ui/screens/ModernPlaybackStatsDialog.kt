package com.kurixutian.oreotunes.ui.screens

import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kurixutian.oreotunes.data.preferences.ArtistPlayStat
import com.kurixutian.oreotunes.data.preferences.SongPlayStat
import com.kurixutian.oreotunes.data.preferences.StatsTimeFrame
import com.kurixutian.oreotunes.data.repository.ArtworkPalette
import com.kurixutian.oreotunes.ui.components.ArtworkThumbnail
import com.kurixutian.oreotunes.ui.components.GlassIconButton
import com.kurixutian.oreotunes.ui.theme.Manrope

@Composable
fun ModernPlaybackStatsDialog(
    selectedTimeFrame: StatsTimeFrame,
    totalListeningTimeMs: Long,
    listeningSummary: com.kurixutian.oreotunes.data.preferences.ListeningSummary,
    mostPlayed: List<SongPlayStat>,
    leastPlayed: List<SongPlayStat>,
    topArtists: List<ArtistPlayStat>,
    palette: ArtworkPalette? = null,
    onTimeFrameSelected: (StatsTimeFrame) -> Unit,
    onExportInsights: () -> Unit,
    onShareInsights: () -> Unit,
    onImportInsights: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isLight = MaterialTheme.colorScheme.background.red > 0.6f

    val dynamicPrimary = if (isLight) {
        palette?.lightAccent ?: Color(0xFF181A24)
    } else {
        palette?.accent ?: MaterialTheme.colorScheme.primary
    }

    val dynamicSecondary = palette?.secondary ?: MaterialTheme.colorScheme.secondary

    val animatedPrimary by animateColorAsState(
        targetValue = dynamicPrimary,
        animationSpec = tween(400),
        label = "statsPrimary"
    )

    val animatedSecondary by animateColorAsState(
        targetValue = dynamicSecondary,
        animationSpec = tween(400),
        label = "statsSecondary"
    )

    val dialogBg = if (isLight) {
        Color.White
    } else {
        (palette?.surfaceColor ?: Color(0xFF141724)).copy(alpha = 0.95f)
    }

    val contentTextColor = if (isLight) Color(0xFF121520) else Color.White
    val subtleTextColor = contentTextColor.copy(alpha = 0.65f)

    val totalSec = totalListeningTimeMs / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60

    val formattedListeningTime = when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "0m"
    }

    val topSong = mostPlayed.firstOrNull()
    val topArtist = topArtists.firstOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        val dialogWindow =
            (LocalView.current.parent as? DialogWindowProvider)?.window

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
                .background(dialogBg)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Listening Insights",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 22.sp,
                                        fontFamily = Manrope
                                    ),
                                    color = contentTextColor,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = selectedTimeFrame.label,
                                    fontFamily = Manrope,
                                    fontSize = 12.sp,
                                    color = subtleTextColor,
                                    fontWeight = FontWeight.Medium
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onExportInsights,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 8.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = animatedPrimary.copy(alpha = 0.18f),
                                    contentColor = animatedPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Export",
                                    fontFamily = Manrope,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = onShareInsights,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 8.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = animatedSecondary.copy(alpha = 0.18f),
                                    contentColor = animatedSecondary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Share",
                                    fontFamily = Manrope,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = onImportInsights,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 8.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLight) {
                                        Color.Black.copy(alpha = 0.06f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    },
                                    contentColor = contentTextColor
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Import",
                                    fontFamily = Manrope,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(StatsTimeFrame.values()) { timeFrame ->
                            val isSelected = timeFrame == selectedTimeFrame

                            val chipBg by animateColorAsState(
                                targetValue = if (isSelected) {
                                    animatedPrimary
                                } else {
                                    if (isLight) {
                                        Color.Black.copy(alpha = 0.06f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    }
                                },
                                label = "chipBg"
                            )

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(chipBg)
                                    .clickable {
                                        onTimeFrameSelected(timeFrame)
                                    }
                                    .padding(
                                        horizontal = 14.dp,
                                        vertical = 7.dp
                                    )
                            ) {
                                Text(
                                    text = timeFrame.label,
                                    fontFamily = Manrope,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                                    color = if (isSelected) {
                                        if (isLight && animatedPrimary.red < 0.5f) {
                                            Color.White
                                        } else {
                                            Color.Black
                                        }
                                    } else {
                                        contentTextColor
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatsSummaryCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Headphones,
                            label = "Listening",
                            value = formattedListeningTime,
                            accent = animatedPrimary,
                            isLight = isLight
                        )

                        StatsSummaryCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.MusicNote,
                            label = "Top Track",
                            value = if (topSong != null) {
                                "${topSong.count} plays"
                            } else {
                                "—"
                            },
                            accent = animatedPrimary,
                            isLight = isLight
                        )

                        StatsSummaryCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Person,
                            label = "Top Artist",
                            value = if (topArtist != null) {
                                "${topArtist.count} plays"
                            } else {
                                "—"
                            },
                            accent = animatedSecondary,
                            isLight = isLight
                        )
                    }
                }

                if (topSong != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            animatedPrimary.copy(
                                                alpha = if (isLight) 0.12f else 0.22f
                                            ),
                                            if (isLight) {
                                                Color.Black.copy(alpha = 0.04f)
                                            } else {
                                                Color.White.copy(alpha = 0.05f)
                                            }
                                        )
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkThumbnail(
                                    model = topSong.song.albumArtUri,
                                    contentDescription = topSong.song.title,
                                    shape = RoundedCornerShape(12.dp),
                                    targetSizeDp = 64.dp,
                                    modifier = Modifier.size(64.dp)
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "MOST PLAYED",
                                        fontFamily = Manrope,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = animatedPrimary,
                                        letterSpacing = 1.sp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = topSong.song.title,
                                        fontFamily = Manrope,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = contentTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = topSong.song.artist,
                                        fontFamily = Manrope,
                                        fontSize = 12.sp,
                                        color = subtleTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "${topSong.count} qualifying plays",
                                        fontFamily = Manrope,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = animatedPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Listening Overview",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontFamily = Manrope
                            ),
                            color = animatedPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ListeningMetricCard(
                                value = listeningSummary.totalPlays,
                                label = "Total Plays",
                                modifier = Modifier.weight(1f),
                                accent = animatedPrimary,
                                isLight = isLight
                            )

                            ListeningMetricCard(
                                value = listeningSummary.fullPlays,
                                label = "Full Plays",
                                modifier = Modifier.weight(1f),
                                accent = animatedSecondary,
                                isLight = isLight
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ListeningMetricCard(
                                value = listeningSummary.skips,
                                label = "Skips",
                                modifier = Modifier.weight(1f),
                                accent = Color(0xFFFF6584),
                                isLight = isLight
                            )

                            ListeningMetricCard(
                                value = listeningSummary.uniqueTracks,
                                label = "Unique Tracks",
                                modifier = Modifier.weight(1f),
                                accent = animatedPrimary,
                                isLight = isLight
                            )
                        }

                        ListeningTimeMetricCard(
                            totalListeningTimeMs = totalListeningTimeMs,
                            accent = animatedSecondary,
                            isLight = isLight
                        )
                    }
                }

                item {
                    Text(
                        text = "Top 5 Most Played (≥ 1 min)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontFamily = Manrope
                        ),
                        color = animatedPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (mostPlayed.isEmpty()) {
                    item {
                        Text(
                            text = "No full plays (≥ 1 min) recorded yet.",
                            fontFamily = Manrope,
                            color = subtleTextColor,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    itemsIndexed(mostPlayed.take(5)) { index, stat ->
                        StatSongCardRow(
                            rank = index + 1,
                            stat = stat,
                            countLabel = "plays",
                            countColor = animatedPrimary,
                            isLight = isLight
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Top 5 Least Played (Unplayed / < 30s)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontFamily = Manrope
                        ),
                        color = Color(0xFFFF6584),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (leastPlayed.isEmpty()) {
                    item {
                        Text(
                            text = "No unplayed tracks.",
                            fontFamily = Manrope,
                            color = subtleTextColor,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    itemsIndexed(leastPlayed.take(5)) { index, stat ->
                        val countLabel =
                            if (stat.count == 0) "unplayed" else "skips"

                        StatSongCardRow(
                            rank = index + 1,
                            stat = stat,
                            countLabel = countLabel,
                            countColor = Color(0xFFFF6584),
                            isLight = isLight
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Top 5 Artists",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontFamily = Manrope
                        ),
                        color = animatedSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (topArtists.isEmpty()) {
                    item {
                        Text(
                            text = "No artist stats yet.",
                            fontFamily = Manrope,
                            color = subtleTextColor,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    itemsIndexed(topArtists.take(5)) { index, stat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isLight) {
                                        Color.Black.copy(alpha = 0.05f)
                                    } else {
                                        Color.White.copy(alpha = 0.06f)
                                    }
                                )
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 12.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    fontFamily = Manrope,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = animatedSecondary,
                                    modifier = Modifier.width(30.dp)
                                )

                                Text(
                                    text = stat.artistName,
                                    fontFamily = Manrope,
                                    color = contentTextColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "${stat.count} plays",
                                fontFamily = Manrope,
                                color = subtleTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun StatsSummaryCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
    isLight: Boolean
) {
    val cardBackground = if (isLight) {
        Color.Black.copy(alpha = 0.04f)
    } else {
        Color.White.copy(alpha = 0.06f)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardBackground)
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            fontFamily = Manrope,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (isLight) {
                Color(0xFF4A5068)
            } else {
                Color.White.copy(alpha = 0.6f)
            },
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = value,
            fontFamily = Manrope,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isLight) {
                Color(0xFF121520)
            } else {
                Color.White
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatSongCardRow(
    rank: Int,
    stat: SongPlayStat,
    countLabel: String,
    countColor: Color,
    isLight: Boolean = false
) {
    val titleColor = if (isLight) {
        Color(0xFF121520)
    } else {
        Color.White
    }

    val artistColor = if (isLight) {
        Color(0xFF4A5068)
    } else {
        Color.White.copy(alpha = 0.65f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isLight) {
                    Color.Black.copy(alpha = 0.04f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                }
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            fontFamily = Manrope,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = countColor,
            modifier = Modifier.width(28.dp)
        )

        ArtworkThumbnail(
            model = stat.song.albumArtUri,
            contentDescription = stat.song.title,
            shape = RoundedCornerShape(8.dp),
            targetSizeDp = 44.dp,
            modifier = Modifier.size(44.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stat.song.title,
                fontFamily = Manrope,
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = stat.song.artist,
                fontFamily = Manrope,
                color = artistColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "${stat.count} $countLabel",
            fontFamily = Manrope,
            color = countColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ListeningTimeMetricCard(
    totalListeningTimeMs: Long,
    accent: Color,
    isLight: Boolean
) {
    val textColor = if (isLight) {
        Color(0xFF121520)
    } else {
        Color.White
    }

    val secondaryTextColor = textColor.copy(alpha = 0.62f)

    val totalSeconds = totalListeningTimeMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L

    val formattedTime = when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m"
        totalSeconds > 0L -> "${totalSeconds}s"
        else -> "0m"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isLight) {
                    Color.Black.copy(alpha = 0.04f)
                } else {
                    Color.White.copy(alpha = 0.055f)
                }
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = formattedTime,
                    fontFamily = Manrope,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Total Listening Time",
                    fontFamily = Manrope,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryTextColor
                )
            }

            Text(
                text = "TIME",
                fontFamily = Manrope,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accent.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun ListeningMetricCard(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color,
    isLight: Boolean
) {
    val textColor = if (isLight) {
        Color(0xFF121520)
    } else {
        Color.White
    }

    val secondaryTextColor = textColor.copy(alpha = 0.62f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isLight) {
                    Color.Black.copy(alpha = 0.04f)
                } else {
                    Color.White.copy(alpha = 0.055f)
                }
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = value.toString(),
                fontFamily = Manrope,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                fontFamily = Manrope,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
