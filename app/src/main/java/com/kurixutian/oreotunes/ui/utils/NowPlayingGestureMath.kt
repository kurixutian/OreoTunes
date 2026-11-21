package com.kurixutian.oreotunes.ui.utils

enum class VerticalGestureOutcome {
    COLLAPSE_NOW_PLAYING,
    OPEN_LYRICS,
    CLOSE_LYRICS,
    SETTLE_MAIN
}

/**
 * Resolves the final state after a vertical drag ends in Now Playing or Lyrics.
 */
fun resolveVerticalGestureOutcome(
    dismissOffsetPx: Float,
    screenHeightPx: Float,
    lyricsProgress: Float,
    startedInLyrics: Boolean
): VerticalGestureOutcome {
    val dismissThreshold = screenHeightPx * 0.20f
    if (dismissOffsetPx > dismissThreshold) {
        return VerticalGestureOutcome.COLLAPSE_NOW_PLAYING
    }
    return if (startedInLyrics) {
        if (lyricsProgress < 0.82f) VerticalGestureOutcome.CLOSE_LYRICS
        else VerticalGestureOutcome.OPEN_LYRICS
    } else {
        if (lyricsProgress > 0.18f) VerticalGestureOutcome.OPEN_LYRICS
        else VerticalGestureOutcome.SETTLE_MAIN
    }
}
