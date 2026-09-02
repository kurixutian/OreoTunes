package com.kurixutian.oreotunes.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import coil.compose.AsyncImage
import com.kurixutian.oreotunes.data.repository.ArtworkPalette
import com.kurixutian.oreotunes.data.repository.OnlineMetadataMatcher
import com.kurixutian.oreotunes.data.repository.OnlineMetadataResult
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.theme.Manrope
import kotlinx.coroutines.launch

@Composable
fun EditSongMetadataDialog(
    song: Song,
    palette: ArtworkPalette? = null,
    onDismiss: () -> Unit,
    onSaveMetadata: (Long, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val onlineMatcher = remember { OnlineMetadataMatcher(context) }

    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }

    var isFetching by remember { mutableStateOf(false) }
    var fetchStatus by remember { mutableStateOf<String?>(null) }
    var candidateEditions by remember { mutableStateOf<List<OnlineMetadataResult>>(emptyList()) }

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
                .fillMaxHeight(if (candidateEditions.isNotEmpty()) 0.82f else 0.68f)
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
                            text = "Edit Song Info",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = contentTextColor
                        )
                        Text(
                            text = "Updates file tags & high-resolution album art",
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

                Spacer(modifier = Modifier.height(14.dp))

                // Auto Match Button
                Button(
                    onClick = {
                        isFetching = true
                        fetchStatus = "Searching matching editions..."
                        candidateEditions = emptyList()
                        coroutineScope.launch {
                            val result = onlineMatcher.searchMetadataCandidates(title, artist)
                            isFetching = false
                            result.fold(
                                onSuccess = { list ->
                                    if (list.size == 1) {
                                        val singleMatch = list.first()
                                        title = singleMatch.title
                                        artist = singleMatch.artist
                                        if (singleMatch.album.isNotBlank()) album = singleMatch.album
                                        fetchStatus = "Auto-filled with ${singleMatch.releaseType} info!"
                                    } else if (list.isNotEmpty()) {
                                        candidateEditions = list
                                        fetchStatus = "Found ${list.size} editions. Tap one to auto-fill:"
                                    } else {
                                        fetchStatus = "No online editions found."
                                    }
                                },
                                onFailure = {
                                    fetchStatus = "No online editions found."
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dynamicPrimary.copy(alpha = if (isLight) 0.14f else 0.22f),
                        contentColor = dynamicPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = dynamicPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Auto-Match Online Editions", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (fetchStatus != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = fetchStatus ?: "",
                        fontFamily = Manrope,
                        fontSize = 11.5.sp,
                        color = dynamicPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (candidateEditions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(candidateEditions) { edition ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.06f))
                                    .clickable {
                                        title = edition.title
                                        artist = edition.artist
                                        if (edition.album.isNotBlank()) album = edition.album
                                        fetchStatus = "Applied ${edition.releaseType} tags!"
                                        candidateEditions = emptyList()
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1B1622))
                                ) {
                                    AsyncImage(
                                        model = edition.previewArtUrl ?: edition.highResArtUrl,
                                        contentDescription = edition.album,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = edition.album.ifBlank { edition.title },
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = contentTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${edition.releaseType} ${if (!edition.year.isNullOrBlank()) "• ${edition.year}" else ""}",
                                        fontFamily = Manrope,
                                        fontSize = 11.sp,
                                        color = dynamicPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title", fontFamily = Manrope, color = subtleTextColor) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = contentTextColor,
                        unfocusedTextColor = contentTextColor,
                        focusedBorderColor = dynamicPrimary,
                        unfocusedBorderColor = contentTextColor.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist", fontFamily = Manrope, color = subtleTextColor) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = contentTextColor,
                        unfocusedTextColor = contentTextColor,
                        focusedBorderColor = dynamicPrimary,
                        unfocusedBorderColor = contentTextColor.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album", fontFamily = Manrope, color = subtleTextColor) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = contentTextColor,
                        unfocusedTextColor = contentTextColor,
                        focusedBorderColor = dynamicPrimary,
                        unfocusedBorderColor = contentTextColor.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.weight(1f, fill = false))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", fontFamily = Manrope, color = subtleTextColor)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSaveMetadata(song.id, title.trim(), artist.trim(), album.trim())
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = dynamicPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Changes", fontFamily = Manrope, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
