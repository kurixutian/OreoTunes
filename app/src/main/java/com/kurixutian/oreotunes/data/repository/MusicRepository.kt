package com.kurixutian.oreotunes.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
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

    return rawArtist
        .split(
            Regex(
                "[,&/]|\\bfeat\\.\\b|\\bft\\.\\b|\\bfeaturing\\b|;|\\bx\\b",
                RegexOption.IGNORE_CASE
            )
        )
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(rawArtist.trim()) }
}

fun matchesArtist(songArtist: String, targetArtist: String): Boolean {
    val artists = splitArtists(songArtist)

    return artists.any {
        it.equals(targetArtist.trim(), ignoreCase = true)
    } || songArtist.contains(
        targetArtist.trim(),
        ignoreCase = true
    )
}

class MusicRepository(private val context: Context) {

    /**
     * Fingerprint representing the current MediaStore music library.
     *
     * The fingerprint intentionally includes identity plus the pieces of
     * MediaStore metadata that can change when a track is renamed, moved,
     * edited, replaced, or otherwise updated.
     *
     * Android 10+ uses RELATIVE_PATH.
     * Android 9 and below use DATA.
     *
     * SIZE and DATE_MODIFIED are included so changes to the underlying file
     * can invalidate the fingerprint even when the MediaStore ID remains
     * unchanged.
     */
    data class LibraryFingerprint(
        val songCount: Int,
        val hash: Long
    )

    suspend fun getLibraryFingerprint(): LibraryFingerprint =
        withContext(Dispatchers.IO) {

            val collection =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL
                    )
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

            /*
             * Keep the fingerprint query lightweight while still including
             * enough information to detect meaningful library changes.
             */
            val projection =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.DATE_MODIFIED
                    )
                } else {
                    arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.DATE_MODIFIED
                    )
                }

            val selection =
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                        "${MediaStore.Audio.Media.DURATION} >= 15000"

            var count = 0
            var hash = 17L

            /*
             * A null cursor is a failed MediaStore read, not an empty
             * library. Throw so PlayerViewModel can safely abort the refresh
             * without replacing the existing library.
             */
            val cursor = try {
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media._ID} ASC"
                )
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Unable to read the MediaStore music library fingerprint",
                    e
                )
            } ?: throw IllegalStateException(
                "MediaStore returned a null music cursor while calculating the library fingerprint"
            )

            cursor.use {
                val idCol =
                    it.getColumnIndexOrThrow(
                        MediaStore.Audio.Media._ID
                    )

                val titleCol =
                    it.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.TITLE
                    )

                val artistCol =
                    it.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.ARTIST
                    )

                val albumCol =
                    it.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.ALBUM
                    )

                val durationCol =
                    it.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DURATION
                    )

                val albumIdCol =
                    it.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.ALBUM_ID
                    )

                val displayNameCol =
                    it.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DISPLAY_NAME
                    )

                val sizeCol =
                    it.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.SIZE
                    )

                val modifiedCol =
                    it.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DATE_MODIFIED
                    )

                val relativePathCol =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        it.getColumnIndexOrThrow(
                            MediaStore.Audio.Media.RELATIVE_PATH
                        )
                    } else {
                        -1
                    }

                val dataCol =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        it.getColumnIndexOrThrow(
                            MediaStore.Audio.Media.DATA
                        )
                    } else {
                        -1
                    }

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)

                    val title =
                        it.getString(titleCol) ?: ""

                    val artist =
                        it.getString(artistCol) ?: ""

                    val album =
                        it.getString(albumCol) ?: ""

                    val duration =
                        it.getLong(durationCol)

                    val albumId =
                        it.getLong(albumIdCol)

                    val displayName =
                        it.getString(displayNameCol) ?: ""

                    val size =
                        it.getLong(sizeCol)

                    val dateModified =
                        it.getLong(modifiedCol)

                    val location =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            it.getString(relativePathCol) ?: ""
                        } else {
                            it.getString(dataCol) ?: ""
                        }

                    count++

                    /*
                     * Include every relevant value in deterministic order.
                     *
                     * This detects:
                     * - added/removed tracks
                     * - renamed tracks
                     * - moved tracks/folders
                     * - changed title/artist/album metadata
                     * - duration changes
                     * - album changes
                     * - file size changes
                     * - file modification changes
                     */
                    hash = 31L * hash + id
                    hash = 31L * hash + title.hashCode().toLong()
                    hash = 31L * hash + artist.hashCode().toLong()
                    hash = 31L * hash + album.hashCode().toLong()
                    hash = 31L * hash + duration
                    hash = 31L * hash + albumId
                    hash = 31L * hash + location.hashCode().toLong()
                    hash = 31L * hash + displayName.hashCode().toLong()
                    hash = 31L * hash + size
                    hash = 31L * hash + dateModified
                }
            }

            LibraryFingerprint(
                songCount = count,
                hash = hash
            )
        }

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()

        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

        /*
         * Android 10+:
         * RELATIVE_PATH is the supported way to determine the
         * MediaStore folder without relying on the deprecated DATA path.
         *
         * Android 9 and below:
         * DATA remains available and is used as the fallback.
         */
        val projection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.DISPLAY_NAME
                )
            } else {
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DISPLAY_NAME
                )
            }

        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                    "${MediaStore.Audio.Media.DURATION} >= 15000"

        val sortOrder =
            "${MediaStore.Audio.Media.TITLE} ASC"

        /*
         * A scan failure must propagate to PlayerViewModel.
         *
         * Returning an empty list here would make a temporary MediaStore
         * failure indistinguishable from a genuinely empty library.
         */
        val cursor = try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Unable to read the MediaStore music library",
                e
            )
        } ?: throw IllegalStateException(
            "MediaStore returned a null music cursor while loading songs"
        )

        cursor.use {
            val idCol =
                it.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID
                )

            val titleCol =
                it.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE
                )

            val artistCol =
                it.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST
                )

            val albumCol =
                it.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ALBUM
                )

            val durationCol =
                it.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DURATION
                )

            val albumIdCol =
                it.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ALBUM_ID
                )

            val relativePathCol =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.getColumnIndex(
                        MediaStore.Audio.Media.RELATIVE_PATH
                    )
                } else {
                    -1
                }

            val dataCol =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    it.getColumnIndex(
                        MediaStore.Audio.Media.DATA
                    )
                } else {
                    -1
                }

            while (it.moveToNext()) {
                val id =
                    it.getLong(idCol)

                val title =
                    it.getString(titleCol)
                        ?.takeIf { value -> value.isNotBlank() }
                        ?: "Unknown Title"

                val rawArtist =
                    it.getString(artistCol)
                        ?.takeIf { value -> value.isNotBlank() }
                        ?: "Unknown Artist"

                val rawAlbum =
                    it.getString(albumCol)
                        ?.takeIf { value -> value.isNotBlank() }
                        ?: "Unknown Album"

                val duration =
                    it.getLong(durationCol)

                val albumId =
                    it.getLong(albumIdCol)

                val artist =
                    if (rawArtist.contains("<unknown>", true)) {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    }

                val album =
                    if (rawAlbum.contains("<unknown>", true)) {
                        "Unknown Album"
                    } else {
                        rawAlbum
                    }

                /*
                 * Build a stable MediaStore content URI.
                 */
                val contentUri =
                    ContentUris.withAppendedId(
                        collection,
                        id
                    )

                /*
                 * Album artwork URI.
                 */
                val albumArtUri =
                    if (albumId > 0L) {
                        ContentUris.withAppendedId(
                            Uri.parse(
                                "content://media/external/audio/albumart"
                            ),
                            albumId
                        )
                    } else {
                        null
                    }

                /*
                 * Determine folder path.
                 *
                 * RELATIVE_PATH normally looks like:
                 *
                 * Music/
                 * Music/Artist/
                 * Music/Artist/Album/
                 *
                 * OreoTunes historically works with absolute paths,
                 * so prefix it with /storage/emulated/0.
                 */
                val folderPath =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                        val relativePath =
                            if (relativePathCol >= 0) {
                                it.getString(relativePathCol)
                                    ?: ""
                            } else {
                                ""
                            }

                        if (relativePath.isNotBlank()) {
                            val cleanRelative =
                                relativePath
                                    .trim()
                                    .trimStart('/')
                                    .trimEnd('/')

                            if (cleanRelative.isNotBlank()) {
                                "/storage/emulated/0/$cleanRelative"
                            } else {
                                "/storage/emulated/0"
                            }
                        } else {
                            "/storage/emulated/0"
                        }

                    } else {

                        val filePath =
                            if (dataCol >= 0) {
                                it.getString(dataCol)
                                    ?: ""
                            } else {
                                ""
                            }

                        if (
                            filePath.isNotBlank() &&
                            filePath.contains('/')
                        ) {
                            filePath.substringBeforeLast('/')
                        } else {
                            ""
                        }
                    }

                songsList.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        contentUri = contentUri,
                        albumArtUri = albumArtUri,
                        folderPath = folderPath
                    )
                )
            }
        }

        songsList
    }
}
