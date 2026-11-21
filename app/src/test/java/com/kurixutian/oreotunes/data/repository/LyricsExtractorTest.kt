package com.kurixutian.oreotunes.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsExtractorTest {

    @Test
    fun parseLrc_standardTimestamps_parsesCorrectly() {
        val lrc = """
            [00:12.50]Line 1
            [01:05.00]Line 2
            [02:30.999]Line 3
        """.trimIndent()

        val parsed = LyricsExtractor.parseLrc(lrc)
        assertEquals(3, parsed.size)

        assertEquals(12500L, parsed[0].timestampMs)
        assertEquals("Line 1", parsed[0].text)

        assertEquals(65000L, parsed[1].timestampMs)
        assertEquals("Line 2", parsed[1].text)

        assertEquals(150999L, parsed[2].timestampMs)
        assertEquals("Line 3", parsed[2].text)
    }

    @Test
    fun parseLrc_ignoresMetadataHeaders() {
        val lrc = """
            [ti:Song Title]
            [ar:Artist Name]
            [al:Album Name]
            [by:Author]
            [00:05.00]First lyric
        """.trimIndent()

        val parsed = LyricsExtractor.parseLrc(lrc)
        assertEquals(1, parsed.size)
        assertEquals(5000L, parsed[0].timestampMs)
        assertEquals("First lyric", parsed[0].text)
    }

    @Test
    fun parseLrc_emptyOrBlank_returnsEmptyList() {
        assertTrue(LyricsExtractor.parseLrc("").isEmpty())
        assertTrue(LyricsExtractor.parseLrc("   \n\t  ").isEmpty())
    }

    @Test
    fun parseLrc_repeatedChorusTimestamps_producesSortedLines() {
        val lrc = """
            [00:10.00][01:10.00]Chorus repeats
        """.trimIndent()

        val parsed = LyricsExtractor.parseLrc(lrc)
        assertEquals(2, parsed.size)
        assertEquals(10000L, parsed[0].timestampMs)
        assertEquals("Chorus repeats", parsed[0].text)
        assertEquals(70000L, parsed[1].timestampMs)
        assertEquals("Chorus repeats", parsed[1].text)
    }

    @Test
    fun parseLrc_enhancedLrcWithInlineTimestampsAndVoiceTags_stripsMarkup() {
        val lrc = """
            [tool:apple-music-ttml]
            [00:05.308]v1: <00:05.308>If <00:05.408><00:05.408>you <00:05.626><00:05.626>ever <00:08.146><00:08.146>sea<00:09.201>
            [00:10.713]v2: <00:10.713>I'll <00:10.841><00:10.841>sail [bg:<00:11.000>the world]
            <00:15.250>Bracketless <00:15.800>word
        """.trimIndent()

        val parsed = LyricsExtractor.parseLrc(lrc)
        assertEquals(3, parsed.size)

        assertEquals(5308L, parsed[0].timestampMs)
        assertEquals("If you ever sea", parsed[0].text)

        assertEquals(10713L, parsed[1].timestampMs)
        assertEquals("I'll sail the world", parsed[1].text)

        assertEquals(15250L, parsed[2].timestampMs)
        assertEquals("Bracketless word", parsed[2].text)
    }
}
