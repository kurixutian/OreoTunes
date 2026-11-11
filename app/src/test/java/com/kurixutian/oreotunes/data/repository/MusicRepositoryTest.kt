package com.kurixutian.oreotunes.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRepositoryTest {

    @Test
    fun splitArtists_commaSeparated_splitsCorrectly() {
        val result = splitArtists("Taylor Swift, Post Malone")
        assertEquals(listOf("Taylor Swift", "Post Malone"), result)
    }

    @Test
    fun splitArtists_featuredArtist_splitsCorrectly() {
        val result = splitArtists("The Weeknd feat. Daft Punk")
        assertEquals(listOf("The Weeknd", "Daft Punk"), result)

        val result2 = splitArtists("Drake ft. 21 Savage")
        assertEquals(listOf("Drake", "21 Savage"), result2)
    }

    @Test
    fun splitArtists_ampersandAndSlash_splitsCorrectly() {
        val result = splitArtists("Artist A & Artist B / Artist C")
        assertEquals(listOf("Artist A", "Artist B", "Artist C"), result)
    }

    @Test
    fun splitArtists_blank_returnsUnknownArtist() {
        val result = splitArtists("   ")
        assertEquals(listOf("Unknown Artist"), result)
    }

    @Test
    fun matchesArtist_exactAndSubstringMatch() {
        assertTrue(matchesArtist("The Weeknd feat. Daft Punk", "Daft Punk"))
        assertTrue(matchesArtist("Taylor Swift", "taylor swift"))
        assertFalse(matchesArtist("Radiohead", "Coldplay"))
    }
}
