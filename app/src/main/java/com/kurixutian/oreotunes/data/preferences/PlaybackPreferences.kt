package com.kurixutian.oreotunes.data.preferences

import android.content.Context
import org.json.JSONArray

/**
 * Persists the user's playback session so OreoTunes can restore it
 * after the app is restarted.
 */
class PlaybackPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "oreo_playback_state_v1"

        private const val KEY_CURRENT_SONG_URI = "current_song_uri"
        private const val KEY_CURRENT_POSITION = "current_position_ms"
        private const val KEY_QUEUE = "queue_uris"
        private const val KEY_SHUFFLE = "shuffle_enabled"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_WAS_PLAYING = "was_playing"
        private const val KEY_SAVED_AT = "saved_at_ms"

        private const val MAX_QUEUE_SIZE = 500
    }

    fun savePlaybackState(
        currentSongUri: String?,
        currentPositionMs: Long,
        queueUris: List<String>,
        shuffleEnabled: Boolean,
        repeatMode: Int,
        wasPlaying: Boolean
    ) {
        val queue = JSONArray()

        queueUris
            .asSequence()
            .filter { it.isNotBlank() }
            .take(MAX_QUEUE_SIZE)
            .forEach { queue.put(it) }

        prefs.edit()
            .putString(KEY_CURRENT_SONG_URI, currentSongUri)
            .putLong(KEY_CURRENT_POSITION, currentPositionMs.coerceAtLeast(0L))
            .putString(KEY_QUEUE, queue.toString())
            .putBoolean(KEY_SHUFFLE, shuffleEnabled)
            .putInt(KEY_REPEAT_MODE, repeatMode)
            .putBoolean(KEY_WAS_PLAYING, wasPlaying)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getCurrentSongUri(): String? {
        return prefs.getString(KEY_CURRENT_SONG_URI, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun getCurrentPositionMs(): Long {
        return prefs.getLong(KEY_CURRENT_POSITION, 0L)
            .coerceAtLeast(0L)
    }

    fun getQueueUris(): List<String> {
        val json = prefs.getString(KEY_QUEUE, null)
            ?: return emptyList()

        return try {
            val array = JSONArray(json)

            buildList {
                for (i in 0 until array.length()) {
                    val uri = array.optString(i, "")
                    if (uri.isNotBlank()) {
                        add(uri)
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isShuffleEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHUFFLE, false)
    }

    fun getRepeatMode(): Int {
        return prefs.getInt(KEY_REPEAT_MODE, 0)
    }

    fun wasPlaying(): Boolean {
        return prefs.getBoolean(KEY_WAS_PLAYING, false)
    }

    fun getSavedAtMs(): Long {
        return prefs.getLong(KEY_SAVED_AT, 0L)
    }

    fun hasSavedPlaybackState(): Boolean {
        return getCurrentSongUri() != null || getQueueUris().isNotEmpty()
    }

    fun clearPlaybackState() {
        prefs.edit().clear().apply()
    }
}
