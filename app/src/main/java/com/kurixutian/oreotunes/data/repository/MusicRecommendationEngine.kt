package com.kurixutian.oreotunes.data.repository

import com.kurixutian.oreotunes.data.model.Playlist
import com.kurixutian.oreotunes.data.preferences.PlaybackStatsTracker
import com.kurixutian.oreotunes.data.preferences.StatsTimeFrame
import com.kurixutian.oreotunes.domain.model.Song

class MusicRecommendationEngine(
    private val statsTracker: PlaybackStatsTracker
) {

    fun generateQuickPicks(
        allSongs: List<Song>,
        mode: String,
        recentlyPlayed: List<Song>,
        playlists: List<Playlist> = emptyList()
    ): List<Song> {
        if (allSongs.isEmpty()) return emptyList()

        return when (mode) {
            "Favorites" -> {
                allSongs.filter { it.isFavorite }
            }
            "Recently Played" -> {
                val recent = statsTracker.getRecentlyPlayed(allSongs, limit = 20)
                if (recent.isNotEmpty()) recent else recentlyPlayed
            }
            "Most Played" -> {
                statsTracker.getMostPlayed(allSongs, StatsTimeFrame.ALL_TIME, limit = 20)
                    .map { it.song }
            }
            "Least Played" -> {
                statsTracker.getLeastPlayed(allSongs, StatsTimeFrame.ALL_TIME, limit = 20)
                    .map { it.song }
            }
            "Random" -> {
                allSongs.shuffled().take(20)
            }
            else -> {
                statsTracker.getMostPlayed(allSongs, StatsTimeFrame.ALL_TIME, limit = 20)
                    .map { it.song }
            }
        }
    }

    fun getRecentlyAdded(allSongs: List<Song>, limit: Int = 15): List<Song> {
        return allSongs.sortedByDescending { it.id }.take(limit)
    }

    fun getSuggestedAlbums(allSongs: List<Song>): List<AlbumGroup> {
        return allSongs
            .filter { it.album.isNotBlank() }
            .groupBy { it.album.trim() }
            .map { (albumTitle, songList) ->
                val firstSong = songList.first()
                AlbumGroup(
                    title = albumTitle,
                    artist = firstSong.artist.ifBlank { "Unknown Artist" },
                    albumArtUri = songList.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                    songCount = songList.size,
                    songs = songList
                )
            }
            .sortedByDescending { it.songCount }
    }

    fun getSuggestedArtists(allSongs: List<Song>): List<ArtistGroup> {
        return allSongs
            .filter { it.artist.isNotBlank() }
            .groupBy { it.artist.trim() }
            .map { (artistName, songList) ->
                ArtistGroup(
                    name = artistName,
                    albumArtUri = songList.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                    songCount = songList.size,
                    songs = songList
                )
            }
            .sortedByDescending { it.songCount }
    }
}
