package com.kurixutian.oreotunes.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurixutian.oreotunes.data.model.Playlist
import com.kurixutian.oreotunes.data.repository.AlbumGroup
import com.kurixutian.oreotunes.data.repository.ArtistGroup
import com.kurixutian.oreotunes.data.repository.splitArtists
import com.kurixutian.oreotunes.domain.model.Song
import com.kurixutian.oreotunes.ui.components.AlphabeticalSongRow
import com.kurixutian.oreotunes.ui.components.ArtworkThumbnail
import com.kurixutian.oreotunes.ui.components.GlassIconButton
import com.kurixutian.oreotunes.ui.components.ModernGlassScrollBar
import com.kurixutian.oreotunes.ui.theme.Manrope

@Composable
fun SearchScreen(
    songs: List<Song>,
    albums: List<AlbumGroup>,
    artists: List<ArtistGroup>,
    playlists: List<Playlist>,
    query: String,
    searchHistory: List<String>,
    onQueryChange: (String) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onRecordHistory: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val cleanQuery = query.trim()

    /*
     * Search normalization:
     *
     * - Case-insensitive
     * - Punctuation-insensitive
     * - Supports multiple search terms
     * - Allows terms to match across title, artist and album
     *
     * Example:
     * "weeknd blinding" can match a song where
     * "Weeknd" is the artist and "Blinding Lights" is the title.
     */
    fun normalizeSearchText(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    val normalizedQuery = remember(cleanQuery) {
        normalizeSearchText(cleanQuery)
    }

    val searchTokens = remember(normalizedQuery) {
        normalizedQuery
            .split(" ")
            .filter { it.isNotBlank() }
            .distinct()
    }

    /*
     * Small, conservative Levenshtein distance.
     *
     * This is intentionally used only for reasonably sized words so a
     * typo does not turn into an unrelated result.
     */
    fun searchEditDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }

        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1

            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1

                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }

            previous = current
        }

        return previous[b.length]
    }

    fun isAcceptableTypo(queryToken: String, candidateWord: String): Boolean {
        if (queryToken.length < 4 || candidateWord.length < 4) return false

        val maxDistance = when {
            queryToken.length >= 8 -> 2
            queryToken.length >= 5 -> 1
            else -> 1
        }

        /*
         * Do not allow very different word lengths to become fuzzy matches.
         */
        if (kotlin.math.abs(queryToken.length - candidateWord.length) > maxDistance) {
            return false
        }

        return searchEditDistance(queryToken, candidateWord) <= maxDistance
    }

    fun tokenMatchesField(token: String, field: String): Boolean {
        if (field.contains(token)) return true

        /*
         * Compare against individual normalized words rather than the whole
         * field. This keeps fuzzy matching conservative.
         */
        return field
            .split(" ")
            .filter { it.length >= 4 }
            .any { word ->
                isAcceptableTypo(token, word)
            }
    }

    val matchedSongs = remember(normalizedQuery, searchTokens, songs) {
        if (normalizedQuery.isBlank() || searchTokens.isEmpty()) {
            emptyList()
        } else {
            songs.mapNotNull { song ->
                val title = normalizeSearchText(song.title)
                val artist = normalizeSearchText(song.artist)
                val album = normalizeSearchText(song.album)

                val titleWords = title.split(" ").filter { it.isNotBlank() }
                val artistWords = artist.split(" ").filter { it.isNotBlank() }
                val albumWords = album.split(" ").filter { it.isNotBlank() }

                /*
                 * Every search token must match normally or through a small
                 * typo-tolerant word comparison.
                 *
                 * Reuse the already-split fields below so we do not repeatedly
                 * allocate the same word lists during scoring.
                 */
                fun matches(token: String, field: String, words: List<String>): Boolean {
                    if (field.contains(token)) return true
                    return words
                        .asSequence()
                        .filter { it.length >= 4 }
                        .any { isAcceptableTypo(token, it) }
                }

                val allTokensMatch = searchTokens.all { token ->
                    matches(token, title, titleWords) ||
                    matches(token, artist, artistWords) ||
                    matches(token, album, albumWords)
                }

                if (!allTokensMatch) {
                    return@mapNotNull null
                }

                var score = 0

                /*
                 * Exact and normal substring matches remain substantially
                 * stronger than fuzzy matches.
                 */
                if (title == normalizedQuery) score += 1000
                if (artist == normalizedQuery) score += 900
                if (album == normalizedQuery) score += 800

                if (title.startsWith(normalizedQuery)) score += 700
                if (artist.startsWith(normalizedQuery)) score += 600
                if (album.startsWith(normalizedQuery)) score += 500

                if (title.contains(normalizedQuery)) score += 400
                if (artist.contains(normalizedQuery)) score += 300
                if (album.contains(normalizedQuery)) score += 200

                searchTokens.forEach { token ->
                    val titleExact = titleWords.any { it == token }
                    val artistExact = artistWords.any { it == token }
                    val albumExact = albumWords.any { it == token }

                    if (titleExact) score += 60
                    else if (matches(token, title, titleWords)) score += 40

                    if (artistExact) score += 50
                    else if (matches(token, artist, artistWords)) score += 30

                    if (albumExact) score += 40
                    else if (matches(token, album, albumWords)) score += 20
                }

                if (title.isNotBlank()) score += 1

                song to score
            }
                .sortedWith(
                    compareByDescending<Pair<Song, Int>> { it.second }
                        .thenBy { it.first.title.lowercase() }
                        .thenBy { it.first.artist.lowercase() }
                )
                .map { it.first }
        }
    }

    val matchedArtists = remember(cleanQuery, artists, songs) {
        if (cleanQuery.isBlank()) emptyList()
        else {
            val fromArtists = artists.filter { it.name.contains(cleanQuery, ignoreCase = true) }
            if (fromArtists.isNotEmpty()) fromArtists
            else {
                songs.flatMap { splitArtists(it.artist) }
                    .filter { it.contains(cleanQuery, ignoreCase = true) }
                    .distinct()
                    .map { name ->
                        val matchingSongs = songs.filter { it.artist.contains(name, ignoreCase = true) }
                        ArtistGroup(
                            name = name,
                            albumArtUri = matchingSongs.firstOrNull()?.albumArtUri,
                            songCount = matchingSongs.size,
                            songs = matchingSongs
                        )
                    }
            }
        }
    }

    val matchedAlbums = remember(cleanQuery, albums, songs) {
        if (cleanQuery.isBlank()) emptyList()
        else {
            val fromAlbums = albums.filter {
                it.title.contains(cleanQuery, ignoreCase = true) || it.artist.contains(cleanQuery, ignoreCase = true)
            }
            if (fromAlbums.isNotEmpty()) fromAlbums
            else {
                songs.groupBy { it.album.ifBlank { "Unknown Album" } }
                    .filter { it.key.contains(cleanQuery, ignoreCase = true) }
                    .map { (title, songsList) ->
                        AlbumGroup(
                            title = title,
                            artist = songsList.firstOrNull()?.artist ?: "Unknown",
                            albumArtUri = songsList.firstOrNull()?.albumArtUri,
                            songCount = songsList.size,
                            songs = songsList
                        )
                    }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
        ) {
            // Search Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
                Spacer(modifier = Modifier.width(10.dp))
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text("Search songs, artists, albums...", color = Color.White.copy(alpha = 0.45f), fontFamily = Manrope)
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onRecordHistory(query)
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1B1F32).copy(alpha = 0.70f),
                        unfocusedContainerColor = Color(0xFF1B1F32).copy(alpha = 0.50f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Search History
                if (cleanQuery.isBlank() && searchHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent Searches", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Clear all", style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope), color = Color(0xFFFF6584), modifier = Modifier.clickable(onClick = onClearHistory))
                        }
                    }
                    items(searchHistory) { historyItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onQueryChange(historyItem) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(historyItem, fontFamily = Manrope, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                            }
                            IconButton(onClick = { onDeleteHistoryItem(historyItem) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                /*
                 * Search results are intentionally grouped in this order:
                 * Artists -> Albums -> Tracks.
                 *
                 * This gives users a predictable discovery path while the
                 * underlying matching/ranking logic continues to determine
                 * which results appear inside each section.
                 */
                // Matched Artists Row
                if (matchedArtists.isNotEmpty()) {
                    item {
                        Text("Artists", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(matchedArtists, key = { it.name }) { artist ->
                                Column(
                                    modifier = Modifier
                                        .width(95.dp)
                                        .clickable {
                                            onRecordHistory(cleanQuery)
                                            onArtistClick(artist.name)
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    ArtworkThumbnail(
                                        model = artist.albumArtUri,
                                        contentDescription = artist.name,
                                        shape = CircleShape,
                                        targetSizeDp = 80.dp,
                                        modifier = Modifier.size(80.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(artist.name, style = MaterialTheme.typography.titleSmall.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // Matched Albums Row
                if (matchedAlbums.isNotEmpty()) {
                    item {
                        Text("Albums", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(matchedAlbums, key = { it.title }) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clickable {
                                            onRecordHistory(cleanQuery)
                                            onAlbumClick(album.title)
                                        }
                                ) {
                                    ArtworkThumbnail(
                                        model = album.albumArtUri,
                                        contentDescription = album.title,
                                        shape = RoundedCornerShape(16.dp),
                                        targetSizeDp = 130.dp,
                                        modifier = Modifier.size(130.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(album.title, style = MaterialTheme.typography.titleSmall.copy(fontFamily = Manrope), fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(album.artist, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope), color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // Matched Songs
                if (matchedSongs.isNotEmpty()) {
                    item {
                        Text(
                            "Tracks (${matchedSongs.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Manrope),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    items(
                        matchedSongs,
                        key = { it.id }
                    ) { song ->
                        AlphabeticalSongRow(
                            song = song,
                            onClick = {
                                onRecordHistory(cleanQuery)
                                onSongClick(song, matchedSongs)
                            },
                            onLongClick = {},
                            onOptionsClick = {}
                        )
                    }
                }

                /*
                 * Explicit empty state.
                 *
                 * Only show this after the user has actually entered a
                 * search query. A blank search continues to show history
                 * normally.
                 */
                if (
                    cleanQuery.isNotBlank() &&
                    matchedArtists.isEmpty() &&
                    matchedAlbums.isEmpty() &&
                    matchedSongs.isEmpty()
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 24.dp,
                                    vertical = 48.dp
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.size(48.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                "No results found",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = Manrope
                                ),
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                "Try a different song, artist, or album.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = Manrope
                                ),
                                color = Color.White.copy(alpha = 0.55f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        if (matchedSongs.size > 8) {
            ModernGlassScrollBar(
                listState = listState,
                itemsList = matchedSongs.map { it.title },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 80.dp, bottom = 140.dp)
            )
        }
    }
}
