package com.kurixutian.oreotunes.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?,
    val folderPath: String,
    val size: Long = 0L,
    val isFavorite: Boolean = false,
    val filePath: String = ""
)

@Immutable
data class FolderInfo(
    val path: String,
    val name: String,
    val songCount: Int
)

@Immutable
data class AlbumInfo(
    val name: String,
    val artist: String,
    val albumArtUri: Uri?,
    val songCount: Int
)

@Immutable
data class ArtistInfo(
    val name: String,
    val songCount: Int,
    val albumCount: Int
)
