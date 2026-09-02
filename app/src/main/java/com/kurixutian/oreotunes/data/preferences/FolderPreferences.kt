package com.kurixutian.oreotunes.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "folder_preferences")

class FolderPreferences(private val context: Context) {
    companion object {
        private val KEY_SELECTED_FOLDERS = stringSetPreferencesKey("selected_folders")
        private val KEY_FAVORITE_SONGS = stringSetPreferencesKey("favorite_songs")
    }

    val selectedFoldersFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_FOLDERS] ?: emptySet()
    }

    val favoriteIdsFlow: Flow<Set<Long>> = context.dataStore.data.map { preferences ->
        val rawSet = preferences[KEY_FAVORITE_SONGS] ?: emptySet()
        rawSet.mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun saveSelectedFolders(folders: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_FOLDERS] = folders
        }
    }

    suspend fun toggleFavorite(songId: Long) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_FAVORITE_SONGS]?.toMutableSet() ?: mutableSetOf()
            val idStr = songId.toString()
            if (current.contains(idStr)) {
                current.remove(idStr)
            } else {
                current.add(idStr)
            }
            preferences[KEY_FAVORITE_SONGS] = current
        }
    }
}
