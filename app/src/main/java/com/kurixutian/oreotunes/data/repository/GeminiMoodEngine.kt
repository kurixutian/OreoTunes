package com.kurixutian.oreotunes.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.kurixutian.oreotunes.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class GeminiMixResult(
    val title: String,
    val description: String,
    val songs: List<Song>
)

class GeminiMoodEngine(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gemini_settings", Context.MODE_PRIVATE)

    fun saveApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun getApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    suspend fun generateMoodMix(
        vibeOrMood: String,
        availableSongs: List<Song>
    ): Result<GeminiMixResult> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Gemini API key is missing. Please enter your API key in Settings.")
            )
        }

        if (availableSongs.isEmpty()) {
            return@withContext Result.failure(
                Exception("No songs available in your music library.")
            )
        }

        try {
            val randomSeed = UUID.randomUUID().toString().take(8)

            /*
             * Choose a playlist size based on the size of the user's library.
             *
             * Small libraries:
             * - Return as many songs as are available.
             *
             * Larger libraries:
             * - 21-50 songs  -> 20 tracks
             * - 51-200      -> 30 tracks
             * - 201-500     -> 40 tracks
             * - 500+        -> 50 tracks
             */
            val targetPlaylistSize = when {
                availableSongs.size <= 20 -> availableSongs.size
                availableSongs.size <= 50 -> 20
                availableSongs.size <= 200 -> 30
                availableSongs.size <= 500 -> 40
                else -> 50
            }

            val songCatalog = JSONArray()

            /*
             * Keep the improved large-library behavior:
             * - <=350 songs: use the complete library
             * - 351-2000: use 500 representative songs
             * - 2001+: use 750 representative songs
             *
             * Shuffle so the same first 350 songs are no longer favored.
             */
            val catalogLimit = when {
                availableSongs.size <= 350 -> availableSongs.size
                availableSongs.size <= 2_000 -> 500
                else -> 750
            }

            val selectedSongs = availableSongs
                .shuffled()
                .take(catalogLimit)

            for (song in selectedSongs) {
                val item = JSONObject()
                item.put("id", song.id)
                item.put("title", song.title)
                item.put("artist", song.artist)
                item.put("album", song.album)
                songCatalog.put(item)
            }

            val prompt = """
                You are an algorithmic music intelligence and playlist architect inspired by Spotify's session generation algorithms (BaRT).

                Session Input:
                - Target Vibe / Mood / Prompt: "$vibeOrMood"
                - Session Randomization Seed: "$randomSeed"
                - Available Catalog:
                $songCatalog

                Playlist Length Requirement:
                - Target playlist size: $targetPlaylistSize tracks.
                - Return EXACTLY $targetPlaylistSize unique song IDs whenever at least $targetPlaylistSize valid songs are available in the catalog.
                - NEVER intentionally shorten the playlist to 10, 15, or another smaller number.
                - Do not stop selecting tracks merely because the strongest matches have been chosen.
                - Continue selecting additional compatible tracks until the target count is reached.
                - If fewer than $targetPlaylistSize valid songs exist in the catalog, return all valid matching songs available.
                - Every song ID must come from the provided catalog.
                - Never invent song IDs.
                - Never repeat a song ID.

                Curation Architecture:
                1. Select the requested number of tracks that best match the target vibe.
                2. Dynamic Energy Arc:
                   - Ramp / Intro: Open with 1-2 tracks establishing tone and tempo.
                   - Peak / Core Vibe: Build into the main energetic rhythm.
                   - Outro / Wind-down: Smooth resolution in the final tracks.
                3. Anti-Monotony Constraints:
                   - Cap consecutive tracks by the same primary artist at 1 whenever possible.
                   - Balance known thematic anchors with adjacent sub-genres.
                   - Avoid excessive repetition of the same artist or album.
                4. Cohesive Harmonic Transitions:
                   - Prefer compatible BPM, key, instrumentation, and overall energy where the available metadata allows.
                5. Variety:
                   - Favor a diverse but coherent selection.
                   - Do not sacrifice playlist length just to keep only the strongest few matches.

                Output Format:
                Return ONLY a valid JSON object:
                {
                  "title": "Creative, evocative playlist title",
                  "description": "One vivid sentence capturing the soundscape, tempo, and mood.",
                  "songIds": [12345, 67890]
                }
            """.trimIndent()

            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"

            val url = URL(endpoint)

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 25000
            }

            val requestBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val partObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("text", prompt)
                                }
                            )
                        }
                        put("parts", parts)
                    }
                    put(partObj)
                }

                put("contents", contents)

                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.7)
                    }
                )
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode

            if (responseCode != HttpURLConnection.HTTP_OK) {
                val rawError =
                    conn.errorStream
                        ?.let { BufferedReader(InputStreamReader(it)).readText() }
                        ?: ""

                val cleanError = try {
                    val errObj = JSONObject(rawError).optJSONObject("error")
                    errObj?.optString("message", rawError)
                        ?: rawError
                } catch (_: Exception) {
                    rawError.ifBlank { "HTTP Error $responseCode" }
                }

                return@withContext Result.failure(Exception(cleanError))
            }

            val responseText =
                BufferedReader(InputStreamReader(conn.inputStream)).readText()

            val responseJson = JSONObject(responseText)

            val candidates = responseJson.optJSONArray("candidates")

            val rawContent =
                candidates
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?: "{}"

            val startIdx =
                rawContent.indexOfFirst { it == '{' || it == '[' }

            val endIdx =
                rawContent.indexOfLast { it == '}' || it == ']' }

            val cleanJson =
                if (startIdx != -1 &&
                    endIdx != -1 &&
                    endIdx >= startIdx
                ) {
                    rawContent
                        .substring(startIdx, endIdx + 1)
                        .trim()
                } else {
                    rawContent.trim()
                }

            var generatedTitle = "$vibeOrMood Mix"

            var generatedDesc =
                "An algorithmic mix curated for $vibeOrMood"

            val parsedIds = mutableListOf<Long>()

            if (cleanJson.startsWith("{")) {
                val obj = JSONObject(cleanJson)

                generatedTitle =
                    obj.optString(
                        "title",
                        generatedTitle
                    )

                generatedDesc =
                    obj.optString(
                        "description",
                        generatedDesc
                    )

                val idArray =
                    obj.optJSONArray("songIds")

                if (idArray != null) {
                    val seenIds = mutableSetOf<Long>()

                    for (i in 0 until idArray.length()) {
                        val id =
                            idArray.optLong(i, -1L)

                        if (id != -1L && seenIds.add(id)) {
                            parsedIds.add(id)
                        }
                    }
                }
            } else if (cleanJson.startsWith("[")) {
                val array = JSONArray(cleanJson)
                val seenIds = mutableSetOf<Long>()

                for (i in 0 until array.length()) {
                    val id =
                        array.optLong(i, -1L)

                    if (id != -1L && seenIds.add(id)) {
                        parsedIds.add(id)
                    }
                }
            }

            val songMap =
                availableSongs.associateBy { it.id }

            val matchedSongs =
                parsedIds.mapNotNull { songMap[it] }

            /*
             * If Gemini returns fewer songs than requested,
             * try to complete the playlist from the same catalog
             * rather than leaving the user with a very short playlist.
             *
             * We preserve Gemini's selected order first, then append
             * unused catalog songs as a local fallback.
             */
            val finalSongs = matchedSongs.toMutableList()

            if (finalSongs.size < targetPlaylistSize) {
                val alreadySelectedIds =
                    finalSongs.map { it.id }.toMutableSet()

                val additionalSongs =
                    selectedSongs
                        .shuffled()
                        .filter { it.id !in alreadySelectedIds }

                for (song in additionalSongs) {
                    if (finalSongs.size >= targetPlaylistSize) {
                        break
                    }

                    finalSongs.add(song)
                    alreadySelectedIds.add(song.id)
                }
            }

            if (finalSongs.isEmpty()) {
                val fallback =
                    availableSongs
                        .filter {
                            it.title.contains(
                                vibeOrMood,
                                ignoreCase = true
                            ) ||
                            it.artist.contains(
                                vibeOrMood,
                                ignoreCase = true
                            ) ||
                            it.album.contains(
                                vibeOrMood,
                                ignoreCase = true
                            )
                        }
                        .take(targetPlaylistSize)

                if (fallback.isNotEmpty()) {
                    Result.success(
                        GeminiMixResult(
                            title = generatedTitle,
                            description = generatedDesc,
                            songs = fallback
                        )
                    )
                } else {
                    Result.failure(
                        Exception(
                            "No matching tracks found in library for '$vibeOrMood'."
                        )
                    )
                }
            } else {
                Result.success(
                    GeminiMixResult(
                        title = generatedTitle,
                        description = generatedDesc,
                        songs = finalSongs.take(targetPlaylistSize)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    e.localizedMessage
                        ?: "Failed to curate mix"
                )
            )
        }
    }
}