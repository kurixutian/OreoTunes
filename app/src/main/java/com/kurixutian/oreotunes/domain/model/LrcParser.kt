package com.kurixutian.oreotunes.domain.model

data class LyricLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    private val regex = Regex("""\[(\d{2}):(\d{2})\.?(\d{2,3})?\](.*)""")

    fun parse(rawLyrics: String?): List<LyricLine> {
        if (rawLyrics.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()

        rawLyrics.lines().forEach { line ->
            val match = regex.find(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val millisRaw = match.groupValues[3]
                val millis = when (millisRaw.length) {
                    2 -> (millisRaw.toLongOrNull() ?: 0L) * 10
                    3 -> millisRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timeMs = (min * 60 + sec) * 1000 + millis
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(timeMs, text))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
