package com.kurixutian.oreotunes.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.kurixutian.oreotunes.domain.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class StatsTimeFrame(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    ALL_TIME("All Time")
}

data class SongPlayStat(
    val song: Song,
    val count: Int
)

data class ArtistPlayStat(
    val artistName: String,
    val count: Int
)

data class PlaySessionEvent(
    val id: Long,
    val title: String,
    val artist: String,
    val timestamp: Long,
    val listenedMs: Long,
    val isFullPlay: Boolean,
    val isSkip: Boolean
)

class PlaybackStatsTracker(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("playback_stats_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PLAY_EVENTS_LOG = "playback_events_log_v2"
        private const val KEY_RECENT_PLAYS = "chronological_recent_plays_v2"
        private const val MAX_EVENT_LOG_SIZE = 2500
    }

    private fun normalizeKey(text: String): String = text.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")

    private fun getStartTimeForTimeFrame(timeFrame: StatsTimeFrame): Long {
        val calendar = Calendar.getInstance()
        return when (timeFrame) {
            StatsTimeFrame.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            StatsTimeFrame.THIS_WEEK -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.timeInMillis
            }
            StatsTimeFrame.THIS_MONTH -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.timeInMillis
            }
            StatsTimeFrame.ALL_TIME -> 0L
        }
    }

    private fun loadAllEvents(): List<PlaySessionEvent> {
        val rawJson = prefs.getString(KEY_PLAY_EVENTS_LOG, "[]") ?: "[]"
        return try {
            val array = JSONArray(rawJson)
            val events = ArrayList<PlaySessionEvent>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                events.add(
                    PlaySessionEvent(
                        id = obj.optLong("id", -1L),
                        title = obj.optString("title", ""),
                        artist = obj.optString("artist", ""),
                        timestamp = obj.optLong("timestamp", 0L),
                        listenedMs = obj.optLong("listenedMs", 0L),
                        isFullPlay = obj.optBoolean("isFullPlay", false),
                        isSkip = obj.optBoolean("isSkip", false)
                    )
                )
            }
            events
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun appendEvent(event: PlaySessionEvent) {
        val currentEvents = loadAllEvents().toMutableList()
        currentEvents.add(0, event)
        val trimmed = if (currentEvents.size > MAX_EVENT_LOG_SIZE) currentEvents.take(MAX_EVENT_LOG_SIZE) else currentEvents

        val array = JSONArray()
        for (e in trimmed) {
            val obj = JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("artist", e.artist)
                put("timestamp", e.timestamp)
                put("listenedMs", e.listenedMs)
                put("isFullPlay", e.isFullPlay)
                put("isSkip", e.isSkip)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLAY_EVENTS_LOG, array.toString()).apply()
    }

    fun recordPlay(song: Song, listenedMs: Long) {
        val now = System.currentTimeMillis()
        val isEligiblePlay = listenedMs >= 60000L

        // 1. Maintain chronological recent plays list
        val historyJson = prefs.getString(KEY_RECENT_PLAYS, "[]") ?: "[]"
        val historyArray = try { JSONArray(historyJson) } catch (_: Exception) { JSONArray() }
        val newHistory = JSONArray()

        val currentEntry = JSONObject().apply {
            put("id", song.id)
            put("title", song.title)
            put("artist", song.artist)
            put("timestamp", now)
        }
        newHistory.put(currentEntry)

        for (i in 0 until historyArray.length()) {
            val obj = historyArray.optJSONObject(i) ?: continue
            val id = obj.optLong("id", -1L)
            val title = obj.optString("title", "")
            val artist = obj.optString("artist", "")

            val isDuplicate = (id == song.id) ||
                    (title.equals(song.title, ignoreCase = true) && artist.equals(song.artist, ignoreCase = true))

            if (!isDuplicate && newHistory.length() < 100) {
                newHistory.put(obj)
            }
        }
        prefs.edit().putString(KEY_RECENT_PLAYS, newHistory.toString()).apply()

        // 2. Append timestamped event log
        appendEvent(
            PlaySessionEvent(
                id = song.id,
                title = song.title,
                artist = song.artist,
                timestamp = now,
                listenedMs = listenedMs,
                isFullPlay = isEligiblePlay,
                isSkip = false
            )
        )
    }

    fun recordSkip(song: Song, listenedMs: Long = 0L) {
        val now = System.currentTimeMillis()
        appendEvent(
            PlaySessionEvent(
                id = song.id,
                title = song.title,
                artist = song.artist,
                timestamp = now,
                listenedMs = listenedMs,
                isFullPlay = false,
                isSkip = true
            )
        )
    }

    fun getRecentlyPlayed(allSongs: List<Song>, limit: Int = 20): List<Song> {
        if (allSongs.isEmpty()) return emptyList()
        val historyJson = prefs.getString(KEY_RECENT_PLAYS, "[]") ?: "[]"
        val historyArray = try { JSONArray(historyJson) } catch (_: Exception) { JSONArray() }
        val songMapById = allSongs.associateBy { it.id }
        val result = mutableListOf<Song>()
        val seenIds = mutableSetOf<Long>()

        for (i in 0 until historyArray.length()) {
            val obj = historyArray.optJSONObject(i) ?: continue
            val id = obj.optLong("id", -1L)
            val title = obj.optString("title", "")
            val artist = obj.optString("artist", "")

            val matchedSong = songMapById[id] ?: allSongs.find {
                it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
            }

            if (matchedSong != null && seenIds.add(matchedSong.id)) {
                result.add(matchedSong)
            }
            if (result.size >= limit) break
        }

        return result
    }

    fun getMostPlayed(
        allSongs: List<Song>,
        timeFrame: StatsTimeFrame = StatsTimeFrame.ALL_TIME,
        limit: Int = 20
    ): List<SongPlayStat> {
        if (allSongs.isEmpty()) return emptyList()
        val minTimestamp = getStartTimeForTimeFrame(timeFrame)
        val filteredEvents = loadAllEvents().filter { it.timestamp >= minTimestamp && it.isFullPlay }

        val countById = mutableMapOf<Long, Int>()
        val countByName = mutableMapOf<String, Int>()

        for (e in filteredEvents) {
            if (e.id > 0) {
                countById[e.id] = (countById[e.id] ?: 0) + 1
            }
            val key = "${normalizeKey(e.title)}_${normalizeKey(e.artist)}"
            countByName[key] = (countByName[key] ?: 0) + 1
        }

        return allSongs
            .map { song ->
                val idCount = countById[song.id] ?: 0
                val nameKey = "${normalizeKey(song.title)}_${normalizeKey(song.artist)}"
                val nameCount = countByName[nameKey] ?: 0
                SongPlayStat(song, maxOf(idCount, nameCount))
            }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
            .take(limit)
    }

    fun getLeastPlayed(
        allSongs: List<Song>,
        timeFrame: StatsTimeFrame = StatsTimeFrame.ALL_TIME,
        limit: Int = 20
    ): List<SongPlayStat> {
        if (allSongs.isEmpty()) return emptyList()
        val minTimestamp = getStartTimeForTimeFrame(timeFrame)
        val filteredEvents = loadAllEvents().filter { it.timestamp >= minTimestamp }

        val playsById = mutableMapOf<Long, Int>()
        val skipsById = mutableMapOf<Long, Int>()
        val playsByName = mutableMapOf<String, Int>()
        val skipsByName = mutableMapOf<String, Int>()

        for (e in filteredEvents) {
            val nameKey = "${normalizeKey(e.title)}_${normalizeKey(e.artist)}"
            if (e.isFullPlay) {
                if (e.id > 0) playsById[e.id] = (playsById[e.id] ?: 0) + 1
                playsByName[nameKey] = (playsByName[nameKey] ?: 0) + 1
            } else if (e.isSkip) {
                if (e.id > 0) skipsById[e.id] = (skipsById[e.id] ?: 0) + 1
                skipsByName[nameKey] = (skipsByName[nameKey] ?: 0) + 1
            }
        }

        val unplayedSongs = allSongs.filter { song ->
            val nameKey = "${normalizeKey(song.title)}_${normalizeKey(song.artist)}"
            val fullPlays = maxOf(playsById[song.id] ?: 0, playsByName[nameKey] ?: 0)
            fullPlays == 0
        }.map { song ->
            val nameKey = "${normalizeKey(song.title)}_${normalizeKey(song.artist)}"
            val skips = maxOf(skipsById[song.id] ?: 0, skipsByName[nameKey] ?: 0)
            SongPlayStat(song, skips)
        }.sortedByDescending { it.count }

        if (unplayedSongs.isNotEmpty()) {
            return unplayedSongs.take(limit)
        }

        return allSongs
            .map { song ->
                val nameKey = "${normalizeKey(song.title)}_${normalizeKey(song.artist)}"
                val skips = maxOf(skipsById[song.id] ?: 0, skipsByName[nameKey] ?: 0)
                val plays = maxOf(playsById[song.id] ?: 0, playsByName[nameKey] ?: 0)
                Pair(SongPlayStat(song, skips), plays)
            }
            .filter { it.first.count > 0 && it.second <= 1 }
            .sortedByDescending { it.first.count }
            .map { it.first }
            .take(limit)
    }

    fun getTopArtists(
        timeFrame: StatsTimeFrame = StatsTimeFrame.ALL_TIME,
        limit: Int = 15
    ): List<ArtistPlayStat> {
        val minTimestamp = getStartTimeForTimeFrame(timeFrame)
        val filteredEvents = loadAllEvents().filter { it.timestamp >= minTimestamp && it.isFullPlay && it.artist.isNotBlank() }

        val artistCounts = mutableMapOf<String, Int>()
        val artistDisplayNames = mutableMapOf<String, String>()

        for (e in filteredEvents) {
            val normalized = normalizeKey(e.artist)
            artistCounts[normalized] = (artistCounts[normalized] ?: 0) + 1
            if (!artistDisplayNames.containsKey(normalized)) {
                artistDisplayNames[normalized] = e.artist
            }
        }

        return artistCounts.map { (key, count) ->
            ArtistPlayStat(
                artistName = artistDisplayNames[key] ?: key,
                count = count
            )
        }.sortedByDescending { it.count }.take(limit)
    }

    fun getTotalListeningTimeMs(timeFrame: StatsTimeFrame = StatsTimeFrame.ALL_TIME): Long {
        val minTimestamp = getStartTimeForTimeFrame(timeFrame)
        return loadAllEvents()
            .filter { it.timestamp >= minTimestamp }
            .sumOf { it.listenedMs }
    }
}
