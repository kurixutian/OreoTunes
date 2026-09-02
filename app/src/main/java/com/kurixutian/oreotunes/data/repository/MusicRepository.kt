package com.kurixutian.oreotunes.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.kurixutian.oreotunes.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AlbumGroup(
    val title: String,
    val artist: String,
    val albumArtUri: Uri?,
    val songCount: Int,
    val songs: List<Song> = emptyList()
)

data class ArtistGroup(
    val name: String,
    val albumArtUri: Uri?,
    val songCount: Int,
    val songs: List<Song> = emptyList()
)

fun splitArtists(rawArtist: String): List<String> {
    if (rawArtist.isBlank()) return listOf("Unknown Artist")
    return rawArtist.split(Regex("[,&/]|\\bfeat\\.\\b|\\bft\\.\\b|\\bfeaturing\\b|;|\\bx\\b", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(rawArtist.trim()) }
}

fun matchesArtist(songArtist: String, targetArtist: String): Boolean {
    val artists = splitArtists(songArtist)
    return artists.any { it.equals(targetArtist.trim(), ignoreCase = true) } ||
            songArtist.contains(targetArtist.trim(), ignoreCase = true)
}

class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 15000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Title"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getLong(durationCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val filePath = cursor.getString(dataCol) ?: ""

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )

                    val folderPath = if (filePath.isNotBlank() && filePath.contains('/')) {
                        filePath.substringBeforeLast('/')
                    } else ""

                    songsList.add(
                        Song(
                            id = id,
                            title = title,
                            artist = if (artist.contains("<unknown>", true)) "Unknown Artist" else artist,
                            album = if (album.contains("<unknown>", true)) "Unknown Album" else album,
                            duration = duration,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri,
                            folderPath = folderPath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songsList
    }
}
