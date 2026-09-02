package com.kurixutian.oreotunes.domain.model

import android.net.Uri

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
    val isFavorite: Boolean = false
)

data class FolderInfo(
    val path: String,
    val name: String,
    val songCount: Int
)

data class AlbumInfo(
    val name: String,
    val artist: String,
    val albumArtUri: Uri?,
    val songCount: Int
)

data class ArtistInfo(
    val name: String,
    val songCount: Int,
    val albumCount: Int
)
