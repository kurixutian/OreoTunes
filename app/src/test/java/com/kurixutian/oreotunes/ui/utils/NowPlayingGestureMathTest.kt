package com.kurixutian.oreotunes.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingGestureMathTest {

    @Test
    fun upwardDragFromMainOpensLyricsPastThreshold() {
        assertEquals(
            VerticalGestureOutcome.OPEN_LYRICS,
            resolveVerticalGestureOutcome(
                dismissOffsetPx = 0f,
                screenHeightPx = 1000f,
                lyricsProgress = 0.25f,
                startedInLyrics = false
            )
        )
    }

    @Test
    fun downwardDragPastThresholdCollapsesNowPlaying() {
        assertEquals(
            VerticalGestureOutcome.COLLAPSE_NOW_PLAYING,
            resolveVerticalGestureOutcome(
                dismissOffsetPx = 250f,
                screenHeightPx = 1000f,
                lyricsProgress = 0f,
                startedInLyrics = false
            )
        )
    }

    @Test
    fun downwardDragFromLyricsClosesLyricsBelowThreshold() {
        assertEquals(
            VerticalGestureOutcome.CLOSE_LYRICS,
            resolveVerticalGestureOutcome(
                dismissOffsetPx = 0f,
                screenHeightPx = 1000f,
                lyricsProgress = 0.70f,
                startedInLyrics = true
            )
        )
    }
}
