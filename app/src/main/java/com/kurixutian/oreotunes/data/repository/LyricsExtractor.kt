package com.kurixutian.oreotunes.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

object LyricsExtractor {

    suspend fun extractEmbeddedLyrics(context: Context, uri: Uri?): List<LyricLine> = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext emptyList()

        var rawLyrics: String? = null

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                rawLyrics = parseFlacVorbisLyrics(stream)
            }
        } catch (_: Exception) {}

        if (rawLyrics.isNullOrBlank()) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    rawLyrics = parseId3Lyrics(stream)
                }
            } catch (_: Exception) {}
        }

        if (rawLyrics.isNullOrBlank()) return@withContext emptyList()

        parseLrc(rawLyrics ?: return@withContext emptyList())
    }

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = lrcContent.lines()
        val result = mutableListOf<LyricLine>()
        val lineTimeRegex = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        val inlineTimeRegex = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")
        val bgTagRegex = Regex("\\[bg:\\s*(.*?)\\]", RegexOption.IGNORE_CASE)
        val voicePrefixRegex = Regex("^(?:v\\d+|bg)\\s*:\\s*", RegexOption.IGNORE_CASE)
        val multiSpaceRegex = Regex(" {2,}")

        fun toMillis(minStr: String, secStr: String, msRaw: String): Long {
            val min = minStr.toLongOrNull() ?: 0L
            val sec = secStr.toLongOrNull() ?: 0L
            val ms = when (msRaw.length) {
                1 -> (msRaw.toLongOrNull() ?: 0L) * 100
                2 -> (msRaw.toLongOrNull() ?: 0L) * 10
                3 -> msRaw.toLongOrNull() ?: 0L
                else -> 0L
            }
            return (min * 60 * 1000) + (sec * 1000) + ms
        }

        fun cleanLyricText(raw: String): String =
            raw.replace(lineTimeRegex, "")
                .replace(inlineTimeRegex, "")
                .replace(bgTagRegex) { it.groupValues[1] }
                .trim()
                .replace(voicePrefixRegex, "")
                .replace(multiSpaceRegex, " ")
                .trim()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val matches = lineTimeRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val text = cleanLyricText(trimmed)
                for (match in matches) {
                    val totalMs = toMillis(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                    result.add(LyricLine(timestampMs = totalMs, text = text))
                }
            } else if (!trimmed.startsWith("[")) {
                val firstInline = inlineTimeRegex.find(trimmed)
                val timestampMs = if (firstInline != null) {
                    toMillis(firstInline.groupValues[1], firstInline.groupValues[2], firstInline.groupValues[3])
                } else {
                    -1L
                }
                val text = cleanLyricText(trimmed)
                if (text.isNotBlank() || timestampMs >= 0L) {
                    result.add(LyricLine(timestampMs = timestampMs, text = text))
                }
            }
        }

        return result.sortedBy { it.timestampMs }
    }

    private fun parseFlacVorbisLyrics(inputStream: InputStream): String? {
        val header = ByteArray(4)
        if (inputStream.read(header) != 4) return null
        if (header[0] != 'f'.code.toByte() || header[1] != 'L'.code.toByte() ||
            header[2] != 'a'.code.toByte() || header[3] != 'C'.code.toByte()
        ) {
            return null
        }

        var isLast = false
        while (!isLast) {
            val blockHeader = ByteArray(4)
            if (inputStream.read(blockHeader) != 4) break
            val headerInt = ((blockHeader[0].toInt() and 0xFF) shl 24) or
                    ((blockHeader[1].toInt() and 0xFF) shl 16) or
                    ((blockHeader[2].toInt() and 0xFF) shl 8) or
                    (blockHeader[3].toInt() and 0xFF)

            isLast = (headerInt and 0x80000000.toInt()) != 0
            val blockType = (headerInt shr 24) and 0x7F
            val blockLength = headerInt and 0x00FFFFFF

            if (blockType == 4) { // VORBIS_COMMENT block
                val data = ByteArray(blockLength)
                var readTotal = 0
                while (readTotal < blockLength) {
                    val count = inputStream.read(data, readTotal, blockLength - readTotal)
                    if (count <= 0) break
                    readTotal += count
                }
                return parseVorbisCommentData(data)
            } else {
                var toSkip = blockLength.toLong()
                while (toSkip > 0) {
                    val skipped = inputStream.skip(toSkip)
                    if (skipped <= 0) break
                    toSkip -= skipped
                }
            }
        }
        return null
    }

    private fun parseVorbisCommentData(data: ByteArray): String? {
        if (data.size < 8) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val vendorLength = buffer.int
        if (vendorLength < 0 || vendorLength > data.size - 8) return null
        buffer.position(buffer.position() + vendorLength)

        val userCommentCount = buffer.int
        val targetTags = listOf("LYRICS=", "UNSYNCEDLYRICS=", "UNSYNCED LYRICS=", "TEXT=", "DESCRIPTION=")

        for (i in 0 until userCommentCount) {
            if (buffer.remaining() < 4) break
            val commentLength = buffer.int
            if (commentLength < 0 || commentLength > buffer.remaining()) break

            val commentBytes = ByteArray(commentLength)
            buffer.get(commentBytes)
            val comment = String(commentBytes, Charsets.UTF_8)

            for (tag in targetTags) {
                if (comment.startsWith(tag, ignoreCase = true)) {
                    val content = comment.substring(tag.length).trim()
                    if (content.isNotBlank()) return content
                }
            }
        }
        return null
    }

    private fun parseId3Lyrics(inputStream: InputStream): String? {
        val buffer = ByteArray(65536)
        val read = inputStream.read(buffer)
        if (read <= 0) return null

        fun extractFrameLyrics(tagBytes: ByteArray, frameId: String): String? {
            val frameBytes = frameId.toByteArray(Charsets.ISO_8859_1)
            var idx = -1
            for (i in 0 until read - frameBytes.size - 10) {
                var found = true
                for (j in frameBytes.indices) {
                    if (tagBytes[i + j] != frameBytes[j]) {
                        found = false
                        break
                    }
                }
                if (found) {
                    idx = i
                    break
                }
            }
            if (idx == -1) return null

            val dataStart = idx + 10
            if (dataStart >= read) return null
            val encoding = tagBytes[dataStart].toInt() and 0xFF
            val charset = when (encoding) {
                1 -> Charsets.UTF_16
                2 -> Charsets.UTF_16BE
                3 -> Charsets.UTF_8
                else -> Charsets.ISO_8859_1
            }
            val contentStart = (dataStart + 4).coerceAtMost(read)
            val raw = String(tagBytes, contentStart, (read - contentStart).coerceAtMost(8192), charset)
            val clean = raw.filter { !it.isISOControl() || it == '\n' || it == '\r' || it == '\t' }.trim()
            return if (clean.length > 10) clean else null
        }

        return extractFrameLyrics(buffer, "USLT") ?: extractFrameLyrics(buffer, "ULT")
    }
}
