package com.kurixutian.oreotunes.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.kurixutian.oreotunes.data.model.Playlist
import org.json.JSONArray
import org.json.JSONObject

enum class AppThemeMode {
    DEFAULT,
    DARK,
    LIGHT
}

enum class DarkThemeStyle {
    AMOLED_DYNAMIC,
    AMOLED_CUSTOM_ACCENT
}

enum class LightThemeStyle {
    PURE_WHITE_DYNAMIC,
    PURE_WHITE_CUSTOM_ACCENT
}

class PlaylistPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("music_player_playlist_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PLAYLISTS = "user_playlists_json_v1"
        private const val KEY_FAVORITES = "user_favorites_set_v1"
        private const val KEY_SELECTED_FOLDERS = "user_selected_folders_set_v1"
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration_sec"
        private const val KEY_HERO_REFRESH_HOURS = "hero_refresh_hours"
        private const val KEY_HERO_LAST_REFRESH_TIME = "hero_last_refresh_time_ms_v1"
        private const val KEY_HERO_ALBUM_TITLES = "hero_album_saved_titles_v1"
        private const val KEY_VOL_NORM_ENABLED = "vol_norm_enabled"
        private const val KEY_HI_FI_BYPASS_ENABLED = "hi_fi_bypass_enabled"
        private const val KEY_QUICK_PICK_MODE = "quick_pick_mode"
        private const val KEY_SEARCH_HISTORY = "search_history_list"
        private const val KEY_METADATA_OVERRIDES = "song_metadata_overrides_v1"

        // Theme & UI Customization
        private const val KEY_APP_THEME_MODE = "app_theme_mode_v2"
        private const val KEY_DARK_THEME_STYLE = "dark_theme_style_v2"
        private const val KEY_LIGHT_THEME_STYLE = "light_theme_style_v2"
        private const val KEY_CUSTOM_ACCENT_COLOR = "custom_accent_color_v2"
        private const val KEY_ARTWORK_SCALE = "artwork_scale_percent_v2"
        private const val KEY_ARTWORK_CORNER_RADIUS = "artwork_corner_radius_dp_v2"
    }

    fun loadPlaylists(): List<Playlist> {
        val json = prefs.getString(KEY_PLAYLISTS, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<Playlist>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val songIdsArray = obj.optJSONArray("songIds") ?: JSONArray()
            val songIds = mutableListOf<Long>()
            for (j in 0 until songIdsArray.length()) {
                songIds.add(songIdsArray.getLong(j))
            }
            list.add(
                Playlist(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    description = obj.optString("description", ""),
                    coverUri = obj.optString("coverUri", "").ifBlank { null },
                    songIds = songIds,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return list
    }

    fun savePlaylists(playlists: List<Playlist>) {
        val array = JSONArray()
        playlists.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("description", p.description)
                put("coverUri", p.coverUri ?: "")
                put("createdAt", p.createdAt)
                put("updatedAt", p.updatedAt)
                val songIdsArray = JSONArray()
                p.songIds.forEach { songIdsArray.put(it) }
                put("songIds", songIdsArray)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    fun getFavoriteSongIds(): Set<Long> {
        val set = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        return set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggleFavoriteSongId(id: Long): Boolean {
        val current = getFavoriteSongIds().toMutableSet()
        val isNowFav = if (current.contains(id)) {
            current.remove(id)
            false
        } else {
            current.add(id)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current.map { it.toString() }.toSet()).apply()
        return isNowFav
    }

    fun isSongFavorite(id: Long): Boolean = getFavoriteSongIds().contains(id)

    fun getSelectedFolders(): Set<String> = prefs.getStringSet(KEY_SELECTED_FOLDERS, emptySet()) ?: emptySet()

    fun saveSelectedFolders(folders: Set<String>) {
        prefs.edit().putStringSet(KEY_SELECTED_FOLDERS, folders).apply()
    }

    fun isCrossfadeEnabled(): Boolean = prefs.getBoolean(KEY_CROSSFADE_ENABLED, true)
    fun saveCrossfadeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_CROSSFADE_ENABLED, enabled).apply()

    fun getCrossfadeDurationSec(): Int = prefs.getInt(KEY_CROSSFADE_DURATION, 8)
    fun saveCrossfadeDurationSec(sec: Int) = prefs.edit().putInt(KEY_CROSSFADE_DURATION, sec).apply()

    fun getHeroRefreshHours(): Int = prefs.getInt(KEY_HERO_REFRESH_HOURS, 3)
    fun saveHeroRefreshHours(hours: Int) = prefs.edit().putInt(KEY_HERO_REFRESH_HOURS, hours).apply()

    fun getHeroLastRefreshTimestamp(): Long = prefs.getLong(KEY_HERO_LAST_REFRESH_TIME, 0L)
    fun saveHeroLastRefreshTimestamp(timestampMs: Long) = prefs.edit().putLong(KEY_HERO_LAST_REFRESH_TIME, timestampMs).apply()

    fun getSavedHeroAlbumTitles(): List<String> {
        val json = prefs.getString(KEY_HERO_ALBUM_TITLES, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }

    fun saveHeroAlbumTitles(titles: List<String>) {
        val array = JSONArray()
        titles.forEach { array.put(it) }
        prefs.edit().putString(KEY_HERO_ALBUM_TITLES, array.toString()).apply()
    }

    fun isVolumeNormalizationEnabled(): Boolean = prefs.getBoolean(KEY_VOL_NORM_ENABLED, true)
    fun saveVolumeNormalizationEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_VOL_NORM_ENABLED, enabled).apply()

    fun isHiFiBypassEnabled(): Boolean = prefs.getBoolean(KEY_HI_FI_BYPASS_ENABLED, false)
    fun saveHiFiBypassEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_HI_FI_BYPASS_ENABLED, enabled).apply()

    fun getQuickPickMode(): String = prefs.getString(KEY_QUICK_PICK_MODE, "Recently Played") ?: "Recently Played"
    fun saveQuickPickMode(mode: String) = prefs.edit().putString(KEY_QUICK_PICK_MODE, mode).apply()

    fun saveMetadataOverride(songId: Long, title: String, artist: String, album: String) {
        val json = prefs.getString(KEY_METADATA_OVERRIDES, "{}") ?: "{}"
        val root = JSONObject(json)
        val songObj = JSONObject().apply {
            put("title", title)
            put("artist", artist)
            put("album", album)
        }
        root.put(songId.toString(), songObj)
        prefs.edit().putString(KEY_METADATA_OVERRIDES, root.toString()).apply()
    }

    fun getMetadataOverrides(): Map<Long, Triple<String, String, String>> {
        val json = prefs.getString(KEY_METADATA_OVERRIDES, "{}") ?: "{}"
        val root = JSONObject(json)
        val map = mutableMapOf<Long, Triple<String, String, String>>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val songId = key.toLongOrNull() ?: continue
            val obj = root.optJSONObject(key) ?: continue
            val title = obj.optString("title", "")
            val artist = obj.optString("artist", "")
            val album = obj.optString("album", "")
            map[songId] = Triple(title, artist, album)
        }
        return map
    }

    fun getAppThemeMode(): AppThemeMode {
        val name = prefs.getString(KEY_APP_THEME_MODE, AppThemeMode.DEFAULT.name) ?: AppThemeMode.DEFAULT.name
        return try { AppThemeMode.valueOf(name) } catch (_: Exception) { AppThemeMode.DEFAULT }
    }
    fun saveAppThemeMode(mode: AppThemeMode) = prefs.edit().putString(KEY_APP_THEME_MODE, mode.name).apply()

    fun getDarkThemeStyle(): DarkThemeStyle {
        val name = prefs.getString(KEY_DARK_THEME_STYLE, DarkThemeStyle.AMOLED_DYNAMIC.name) ?: DarkThemeStyle.AMOLED_DYNAMIC.name
        return try { DarkThemeStyle.valueOf(name) } catch (_: Exception) { DarkThemeStyle.AMOLED_DYNAMIC }
    }
    fun saveDarkThemeStyle(style: DarkThemeStyle) = prefs.edit().putString(KEY_DARK_THEME_STYLE, style.name).apply()

    fun getLightThemeStyle(): LightThemeStyle {
        val name = prefs.getString(KEY_LIGHT_THEME_STYLE, LightThemeStyle.PURE_WHITE_DYNAMIC.name) ?: LightThemeStyle.PURE_WHITE_DYNAMIC.name
        return try { LightThemeStyle.valueOf(name) } catch (_: Exception) { LightThemeStyle.PURE_WHITE_DYNAMIC }
    }
    fun saveLightThemeStyle(style: LightThemeStyle) = prefs.edit().putString(KEY_LIGHT_THEME_STYLE, style.name).apply()

    fun getCustomAccentColor(): Long = prefs.getLong(KEY_CUSTOM_ACCENT_COLOR, 0xFF64D2FF)
    fun saveCustomAccentColor(colorLong: Long) = prefs.edit().putLong(KEY_CUSTOM_ACCENT_COLOR, colorLong).apply()

    fun getArtworkScalePercent(): Int = prefs.getInt(KEY_ARTWORK_SCALE, 100)
    fun saveArtworkScalePercent(percent: Int) = prefs.edit().putInt(KEY_ARTWORK_SCALE, percent.coerceIn(65, 100)).apply()

    fun getArtworkCornerRadiusDp(): Int = prefs.getInt(KEY_ARTWORK_CORNER_RADIUS, 28)
    fun saveArtworkCornerRadiusDp(radiusDp: Int) = prefs.edit().putInt(KEY_ARTWORK_CORNER_RADIUS, radiusDp.coerceIn(0, 36)).apply()

    fun getSearchHistory(): List<String> {
        val json = prefs.getString(KEY_SEARCH_HISTORY, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }

    fun addSearchQuery(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val current = getSearchHistory().toMutableList()
        current.remove(clean)
        current.add(0, clean)
        val trimmed = current.take(15)
        val array = JSONArray()
        trimmed.forEach { array.put(it) }
        prefs.edit().putString(KEY_SEARCH_HISTORY, array.toString()).apply()
    }

    fun deleteSearchQuery(query: String) {
        val current = getSearchHistory().toMutableList()
        current.remove(query.trim())
        val array = JSONArray()
        current.forEach { array.put(it) }
        prefs.edit().putString(KEY_SEARCH_HISTORY, array.toString()).apply()
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }
}