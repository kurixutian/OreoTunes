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
    onSaveMetadata: (Long, String, String, String, (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val onlineMatcher = remember { OnlineMetadataMatcher(context) }

    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.album) }

    var isFetching by remember { mutableStateOf(false) }
    var fetchStatus by remember { mutableStateOf<String?>(null) }
    var candidateEditions by remember {
        mutableStateOf<List<OnlineMetadataResult>>(emptyList())
    }

    var isSaving by remember { mutableStateOf(false) }

    val isLight = MaterialTheme.colorScheme.background.red > 0.6f
    val dynamicPrimary = MaterialTheme.colorScheme.primary

    val dialogBg =
        if (isLight) {
            MaterialTheme.colorScheme.surface
        } else {
            (palette?.surfaceColor ?: Color(0xFF141724)).copy(alpha = 0.96f)
        }

    val contentTextColor = MaterialTheme.colorScheme.onSurface
    val subtleTextColor = contentTextColor.copy(alpha = 0.65f)

    val fieldContainerColor =
        if (isLight) {
            Color.Black.copy(alpha = 0.025f)
        } else {
            Color.White.copy(alpha = 0.035f)
        }

    val candidateContainerColor =
        if (isLight) {
            Color.Black.copy(alpha = 0.04f)
        } else {
            Color.White.copy(alpha = 0.06f)
        }

    val hasChanges =
        title != song.title ||
        artist != song.artist ||
        album != song.album

    Dialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
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
                .fillMaxHeight(
                    if (candidateEditions.isNotEmpty()) 0.84f else 0.72f
                )
                .clip(RoundedCornerShape(24.dp))
                .background(dialogBg)
                .border(
                    width = if (isLight) 1.dp else 0.dp,
                    color = Color.Black.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
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
                            text = "Edit Song Info",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = contentTextColor
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Update tags and album artwork",
                            fontFamily = Manrope,
                            fontSize = 12.sp,
                            color = subtleTextColor
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    GlassIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Close",
                        size = 36.dp,
                        iconSize = 18.dp,
                        onClick = {
                            if (!isSaving) {
                                onDismiss()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (isFetching || isSaving) return@Button

                        isFetching = true
                        fetchStatus = "Searching matching editions..."
                        candidateEditions = emptyList()

                        coroutineScope.launch {
                            val result =
                                onlineMatcher.searchMetadataCandidates(
                                    title.trim(),
                                    artist.trim()
                                )

                            isFetching = false

                            result.fold(
                                onSuccess = { list ->
                                    when {
                                        list.size == 1 -> {
                                            val singleMatch = list.first()

                                            title = singleMatch.title
                                            artist = singleMatch.artist

                                            if (singleMatch.album.isNotBlank()) {
                                                album = singleMatch.album
                                            }

                                            fetchStatus =
                                                "Auto-filled with ${singleMatch.releaseType} info."
                                        }

                                        list.isNotEmpty() -> {
                                            candidateEditions = list
                                            fetchStatus =
                                                "Found ${list.size} editions. Select one below."
                                        }

                                        else -> {
                                            fetchStatus =
                                                "No matching editions found."
                                        }
                                    }
                                },
                                onFailure = {
                                    fetchStatus =
                                        "Unable to fetch online metadata."
                                }
                            )
                        }
                    },
                    enabled = !isFetching && !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            dynamicPrimary.copy(
                                alpha = if (isLight) 0.14f else 0.22f
                            ),
                        contentColor = dynamicPrimary,
                        disabledContainerColor =
                            dynamicPrimary.copy(
                                alpha = if (isLight) 0.08f else 0.12f
                            ),
                        disabledContentColor =
                            dynamicPrimary.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = dynamicPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text =
                            if (isFetching) {
                                "Searching..."
                            } else {
                                "Auto-Match Online Editions"
                            },
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                fetchStatus?.let { status ->
                    Spacer(modifier = Modifier.height(7.dp))

                    Text(
                        text = status,
                        fontFamily = Manrope,
                        fontSize = 11.5.sp,
                        color = dynamicPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (candidateEditions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "MATCHING EDITIONS",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = subtleTextColor,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = candidateEditions,
                            key = { edition ->
                                "${edition.title}|${edition.artist}|${edition.album}|${edition.year}"
                            }
                        ) { edition ->

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(candidateContainerColor)
                                    .clickable {
                                        if (isSaving) return@clickable

                                        title = edition.title
                                        artist = edition.artist

                                        if (edition.album.isNotBlank()) {
                                            album = edition.album
                                        }

                                        fetchStatus =
                                            "Applied ${edition.releaseType} tags."

                                        candidateEditions = emptyList()
                                    }
                                    .padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Color(0xFF1B1622))
                                ) {
                                    AsyncImage(
                                        model =
                                            edition.previewArtUrl
                                                ?: edition.highResArtUrl,
                                        contentDescription = edition.album,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text =
                                            edition.album.ifBlank {
                                                edition.title
                                            },
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = contentTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = buildString {
                                            append(edition.releaseType)

                                            if (!edition.year.isNullOrBlank()) {
                                                append(" • ")
                                                append(edition.year)
                                            }
                                        },
                                        fontFamily = Manrope,
                                        fontSize = 11.sp,
                                        color = dynamicPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                MetadataEditField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Title",
                    containerColor = fieldContainerColor,
                    contentTextColor = contentTextColor,
                    subtleTextColor = subtleTextColor,
                    dynamicPrimary = dynamicPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                MetadataEditField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = "Artist",
                    containerColor = fieldContainerColor,
                    contentTextColor = contentTextColor,
                    subtleTextColor = subtleTextColor,
                    dynamicPrimary = dynamicPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                MetadataEditField(
                    value = album,
                    onValueChange = { album = it },
                    label = "Album",
                    containerColor = fieldContainerColor,
                    contentTextColor = contentTextColor,
                    subtleTextColor = subtleTextColor,
                    dynamicPrimary = dynamicPrimary
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (!isSaving) {
                                onDismiss()
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = Manrope,
                            color = subtleTextColor
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (
                                title.isNotBlank() &&
                                hasChanges &&
                                !isSaving
                            ) {
                                isSaving = true
                                fetchStatus = "Saving metadata..."

                                onSaveMetadata(
                                    song.id,
                                    title.trim(),
                                    artist.trim(),
                                    album.trim()
                                ) { success ->
                                    isSaving = false

                                    if (success) {
                                        fetchStatus = null
                                        onDismiss()
                                    } else {
                                        fetchStatus = "Couldn’t save metadata to the file."
                                    }
                                }
                            }
                        },
                        enabled =
                            title.isNotBlank() &&
                            hasChanges &&
                            !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dynamicPrimary,
                            contentColor = Color.Black,
                            disabledContainerColor =
                                dynamicPrimary.copy(alpha = 0.28f),
                            disabledContentColor =
                                Color.Black.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )

                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text =
                                if (isSaving) {
                                    "Saving..."
                                } else {
                                    "Save Changes"
                                },
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataEditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    containerColor: Color,
    contentTextColor: Color,
    subtleTextColor: Color,
    dynamicPrimary: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                fontFamily = Manrope,
                color = subtleTextColor
            )
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = contentTextColor,
            unfocusedTextColor = contentTextColor,
            focusedBorderColor = dynamicPrimary,
            unfocusedBorderColor =
                contentTextColor.copy(alpha = 0.15f),
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            cursorColor = dynamicPrimary
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
