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
        val timeRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[ti:") ||
                trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") ||
                trimmed.startsWith("[by:") ||
                trimmed.startsWith("[offset:") ||
                trimmed.startsWith("[length:") ||
                trimmed.startsWith("[re:") ||
                trimmed.startsWith("[ve:")
            ) {
                continue
            }

            val matches = timeRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val text = trimmed.replace(timeRegex, "").trim()
                for (match in matches) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val msRaw = match.groupValues[3]
                    val ms = when (msRaw.length) {
                        1 -> (msRaw.toLongOrNull() ?: 0L) * 100
                        2 -> (msRaw.toLongOrNull() ?: 0L) * 10
                        3 -> msRaw.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    val totalMs = (min * 60 * 1000) + (sec * 1000) + ms
                    result.add(LyricLine(timestampMs = totalMs, text = text))
                }
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("[")) {
                result.add(LyricLine(timestampMs = -1L, text = trimmed))
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

        val text = String(buffer, 0, read, Charsets.ISO_8859_1)
        val usltIndex = text.indexOf("USLT")
        if (usltIndex != -1 && usltIndex + 10 < text.length) {
            val raw = text.substring(usltIndex + 4, (usltIndex + 4000).coerceAtMost(text.length))
            val clean = raw.filter { it.code in 32..126 || it == '\n' || it == '\r' }.trim()
            if (clean.length > 20) return clean
        }

        val ultIndex = text.indexOf("ULT")
        if (ultIndex != -1 && ultIndex + 6 < text.length) {
            val raw = text.substring(ultIndex + 3, (ultIndex + 4000).coerceAtMost(text.length))
            val clean = raw.filter { it.code in 32..126 || it == '\n' || it == '\r' }.trim()
            if (clean.length > 20) return clean
        }

        return null
    }
}
