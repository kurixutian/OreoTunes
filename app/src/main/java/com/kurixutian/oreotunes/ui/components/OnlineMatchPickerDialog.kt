package com.kurixutian.oreotunes.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import com.kurixutian.oreotunes.data.repository.ArtworkPalette
import com.kurixutian.oreotunes.data.repository.OnlineMetadataResult
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.theme.Manrope

@Composable
fun OnlineMatchPickerDialog(
    song: Song,
    candidates: List<OnlineMetadataResult>,
    palette: ArtworkPalette? = null,
    onSelectCandidate: (OnlineMetadataResult) -> Unit,
    onDismiss: () -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.6f
    val dynamicPrimary = MaterialTheme.colorScheme.primary
    val dialogBg = if (isLight) Color.White else (palette?.surfaceColor ?: Color(0xFF141724)).copy(alpha = 0.96f)
    val contentTextColor = if (isLight) Color(0xFF121520) else Color.White
    val subtleTextColor = contentTextColor.copy(alpha = 0.65f)

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
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(24.dp))
                .background(dialogBg)
                .border(
                    width = if (isLight) 1.dp else 0.dp,
                    color = Color.Black.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Edition",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = contentTextColor
                        )
                        Text(
                            text = "Choose between Single, Album or Deluxe release",
                            fontFamily = Manrope,
                            fontSize = 12.sp,
                            color = subtleTextColor
                        )
                    }

                    GlassIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Close",
                        size = 36.dp,
                        iconSize = 18.dp,
                        onClick = onDismiss
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(candidates) { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.06f))
                                .clickable { onSelectCandidate(candidate) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1B1622))
                            ) {
                                AsyncImage(
                                    model = candidate.previewArtUrl ?: candidate.highResArtUrl,
                                    contentDescription = candidate.album,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(dynamicPrimary.copy(alpha = if (isLight) 0.15f else 0.25f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = candidate.releaseType,
                                            fontFamily = Manrope,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = dynamicPrimary
                                        )
                                    }
                                    if (!candidate.year.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• ${candidate.year}",
                                            fontFamily = Manrope,
                                            fontSize = 11.5.sp,
                                            color = subtleTextColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = candidate.album.ifBlank { candidate.title },
                                    fontFamily = Manrope,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = contentTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = candidate.artist,
                                    fontFamily = Manrope,
                                    fontSize = 12.sp,
                                    color = subtleTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.Done,
                                contentDescription = "Select",
                                tint = dynamicPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
