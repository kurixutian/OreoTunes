package com.kurixutian.oreotunes.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineMetadataResult(
    val title: String,
    val artist: String,
    val album: String,
    val releaseType: String, // "Album", "Single", "EP", "Deluxe"
    val year: String?,
    val highResArtUrl: String?,
    val previewArtUrl: String?,
    val artworkBytes: ByteArray? = null
)

class OnlineMetadataMatcher(private val context: Context) {

    suspend fun searchMetadataCandidates(title: String, artist: String): Result<List<OnlineMetadataResult>> = withContext(Dispatchers.IO) {
        try {
            val candidates = mutableListOf<OnlineMetadataResult>()

            // 1. Primary: MusicBrainz Recording Search with Release Metadata
            val mbCandidates = queryMusicBrainzCandidates(title, artist)
            candidates.addAll(mbCandidates)

            // 2. Secondary: iTunes fallback/supplement
            if (candidates.size < 5) {
                val itunesCandidates = queryITunesCandidates(title, artist)
                candidates.addAll(itunesCandidates)
            }

            val distinctCandidates = candidates.distinctBy { "${it.album.lowercase()}_${it.releaseType}" }
            if (distinctCandidates.isNotEmpty()) {
                return@withContext Result.success(distinctCandidates)
            }

            Result.failure(Exception("No matching releases found on MusicBrainz or iTunes."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryMusicBrainzCandidates(title: String, artist: String): List<OnlineMetadataResult> {
        val list = mutableListOf<OnlineMetadataResult>()
        try {
            val cleanTitle = title.replace(Regex("[^A-Za-z0-9 ]"), " ").trim()
            val cleanArtist = artist.replace(Regex("[^A-Za-z0-9 ]"), " ").trim()
            val query = URLEncoder.encode("recording:\"$cleanTitle\" AND artist:\"$cleanArtist\"", "UTF-8")
            val mbUrl = "https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=10"

            val conn = (URL(mbUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "OreoTunesMusicPlayer/2.3 ( contact@oreotunes.app )")
            }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val recordings = root.optJSONArray("recordings")
                if (recordings != null) {
                    for (i in 0 until recordings.length()) {
                        val rec = recordings.getJSONObject(i)
                        val recTitle = rec.optString("title", title)
                        val releases = rec.optJSONArray("releases")
                        if (releases != null) {
                            for (j in 0 until releases.length()) {
                                val release = releases.getJSONObject(j)
                                val releaseId = release.optString("id", "")
                                val releaseTitle = release.optString("title", "")
                                val date = release.optString("date", "")
                                val year = if (date.length >= 4) date.substring(0, 4) else null

                                val releaseGroup = release.optJSONObject("release-group")
                                val primaryType = releaseGroup?.optString("primary-type", "Album") ?: "Album"
                                val secondaryTypes = releaseGroup?.optJSONArray("secondary-types")
                                val isDeluxe = secondaryTypes != null && (0 until secondaryTypes.length()).any {
                                    secondaryTypes.optString(it).contains("deluxe", ignoreCase = true)
                                }

                                val releaseType = when {
                                    isDeluxe -> "Deluxe Edition"
                                    primaryType.equals("Single", ignoreCase = true) -> "Single"
                                    primaryType.equals("EP", ignoreCase = true) -> "EP"
                                    else -> "Album"
                                }

                                if (releaseId.isNotBlank() && releaseTitle.isNotBlank()) {
                                    val caaHighRes = "https://coverartarchive.org/release/$releaseId/front-1200"
                                    val caaThumb = "https://coverartarchive.org/release/$releaseId/front-250"

                                    list.add(
                                        OnlineMetadataResult(
                                            title = recTitle,
                                            artist = artist,
                                            album = releaseTitle,
                                            releaseType = releaseType,
                                            year = year,
                                            highResArtUrl = caaHighRes,
                                            previewArtUrl = caaThumb
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun queryITunesCandidates(title: String, artist: String): List<OnlineMetadataResult> {
        val list = mutableListOf<OnlineMetadataResult>()
        try {
            val queryTerm = "$title $artist".trim()
            val encodedQuery = URLEncoder.encode(queryTerm, "UTF-8")
            val itunesUrl = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=6"

            val conn = (URL(itunesUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "OreoTunes/2.3 (Android)")
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val rootJson = JSONObject(responseText)
                val resultsArray = rootJson.optJSONArray("results")

                if (resultsArray != null) {
                    for (i in 0 until resultsArray.length()) {
                        val matchObj = resultsArray.getJSONObject(i)
                        val matchedTitle = matchObj.optString("trackName", title)
                        val matchedArtist = matchObj.optString("artistName", artist)
                        val matchedAlbum = matchObj.optString("collectionName", "")
                        val releaseDate = matchObj.optString("releaseDate", "")
                        val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else null

                        val collectionCensoredName = matchObj.optString("collectionCensoredName", "").lowercase()
                        val releaseType = when {
                            collectionCensoredName.contains("single") -> "Single"
                            collectionCensoredName.contains("deluxe") || collectionCensoredName.contains("expanded") -> "Deluxe Edition"
                            collectionCensoredName.contains("ep") -> "EP"
                            else -> "Album"
                        }

                        val rawArtUrl = matchObj.optString("artworkUrl100", "")
                        val highResArtUrl = if (rawArtUrl.isNotBlank()) {
                            rawArtUrl.replace("100x100bb.jpg", "1200x1200bb.jpg")
                                .replace("100x100bb.png", "1200x1200bb.png")
                        } else null

                        list.add(
                            OnlineMetadataResult(
                                title = matchedTitle,
                                artist = matchedArtist,
                                album = matchedAlbum,
                                releaseType = releaseType,
                                year = year,
                                highResArtUrl = highResArtUrl,
                                previewArtUrl = rawArtUrl
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    suspend fun downloadArtworkBytes(imageUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            var targetUrl = imageUrl
            var conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "OreoTunesMusicPlayer/2.3 ( contact@oreotunes.app )")
            }

            // Handle Cover Art Archive HTTP 307 / 302 Redirects
            if (conn.responseCode == 301 || conn.responseCode == 302 || conn.responseCode == 307) {
                val redirectUrl = conn.getHeaderField("Location")
                if (!redirectUrl.isNullOrBlank()) {
                    targetUrl = redirectUrl
                    conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 10000
                        setRequestProperty("User-Agent", "OreoTunesMusicPlayer/2.3 ( contact@oreotunes.app )")
                    }
                }
            }

            if (conn.responseCode == 200) {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var bytesRead: Int
                conn.inputStream.use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
                val downloaded = output.toByteArray()
                if (downloaded.isNotEmpty()) downloaded else null
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
