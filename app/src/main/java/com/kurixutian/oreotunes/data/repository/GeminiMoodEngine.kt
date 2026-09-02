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

    private val prefs: SharedPreferences = context.getSharedPreferences("gemini_settings", Context.MODE_PRIVATE)

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
            return@withContext Result.failure(Exception("Gemini API key is missing. Please enter your API key in Settings."))
        }
        if (availableSongs.isEmpty()) {
            return@withContext Result.failure(Exception("No songs available in your music library."))
        }

        try {
            val randomSeed = UUID.randomUUID().toString().take(8)

            val songCatalog = JSONArray()
            val sampleCount = minOf(350, availableSongs.size)
            for (i in 0 until sampleCount) {
                val song = availableSongs[i]
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

                Curation Architecture:
                1. Dynamic Track Count: Select between 5 and 50 tracks from the catalog that best match the vibe.
                2. Dynamic Energy Arc:
                   - Ramp / Intro: Open with 1-2 tracks establishing tone and tempo.
                   - Peak / Core Vibe: Build into the main energetic rhythm.
                   - Outro / Wind-down: Smooth resolution in the final tracks.
                3. Anti-Monotony Constraints:
                   - Cap consecutive tracks by the same primary artist at 1.
                   - Balance known thematic anchors with adjacent sub-genres.
                4. Cohesive Harmonic Transitions: Ensure BPM, key, and instrumentation do not clash track-to-track.

                Output Format:
                Return ONLY a valid JSON object:
                {
                  "title": "Creative, evocative playlist title",
                  "description": "One vivid sentence capturing the soundscape, tempo, and mood.",
                  "songIds": [12345, 67890]
                }
            """.trimIndent()

            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"
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
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", parts)
                    }
                    put(partObj)
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.7)
                })
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val rawError = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).readText() } ?: ""
                val cleanError = try {
                    val errObj = JSONObject(rawError).optJSONObject("error")
                    errObj?.optString("message", rawError) ?: rawError
                } catch (_: Exception) {
                    rawError.ifBlank { "HTTP Error $responseCode" }
                }
                return@withContext Result.failure(Exception(cleanError))
            }

            val responseText = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            val responseJson = JSONObject(responseText)
            val candidates = responseJson.optJSONArray("candidates")
            val rawContent = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text") ?: "{}"

            val startIdx = rawContent.indexOfFirst { it == '{' || it == '[' }
            val endIdx = rawContent.indexOfLast { it == '}' || it == ']' }
            val cleanJson = if (startIdx != -1 && endIdx != -1 && endIdx >= startIdx) {
                rawContent.substring(startIdx, endIdx + 1).trim()
            } else {
                rawContent.trim()
            }

            var generatedTitle = "$vibeOrMood Mix"
            var generatedDesc = "An algorithmic mix curated for $vibeOrMood"
            val parsedIds = mutableListOf<Long>()

            if (cleanJson.startsWith("{")) {
                val obj = JSONObject(cleanJson)
                generatedTitle = obj.optString("title", generatedTitle)
                generatedDesc = obj.optString("description", generatedDesc)
                val idArray = obj.optJSONArray("songIds")
                if (idArray != null) {
                    for (i in 0 until idArray.length()) {
                        val id = idArray.optLong(i, -1L)
                        if (id != -1L) parsedIds.add(id)
                    }
                }
            } else if (cleanJson.startsWith("[")) {
                val array = JSONArray(cleanJson)
                for (i in 0 until array.length()) {
                    val id = array.optLong(i, -1L)
                    if (id != -1L) parsedIds.add(id)
                }
            }

            val songMap = availableSongs.associateBy { it.id }
            val matchedSongs = parsedIds.mapNotNull { songMap[it] }

            if (matchedSongs.isEmpty()) {
                val fallback = availableSongs.filter {
                    it.title.contains(vibeOrMood, ignoreCase = true) ||
                            it.artist.contains(vibeOrMood, ignoreCase = true) ||
                            it.album.contains(vibeOrMood, ignoreCase = true)
                }.take(20)

                if (fallback.isNotEmpty()) {
                    Result.success(
                        GeminiMixResult(
                            title = generatedTitle,
                            description = generatedDesc,
                            songs = fallback
                        )
                    )
                } else {
                    Result.failure(Exception("No matching tracks found in library for '$vibeOrMood'."))
                }
            } else {
                Result.success(
                    GeminiMixResult(
                        title = generatedTitle,
                        description = generatedDesc,
                        songs = matchedSongs
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to curate mix"))
        }
    }
}
