package com.kurixutian.oreotunes.domain.model

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FlacMetadataExtractor {

    fun extractEmbeddedLyrics(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parseVorbisLyrics(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVorbisLyrics(stream: InputStream): String? {
        val magic = ByteArray(4)
        if (stream.read(magic) != 4 || String(magic) != "fLaC") return null

        var isLast = false
        while (!isLast) {
            val headerByte = stream.read()
            if (headerByte == -1) break
            isLast = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F

            val lenBytes = ByteArray(3)
            if (stream.read(lenBytes) != 3) break
            val length = (lenBytes[0].toInt() and 0xFF shl 16) or
                    (lenBytes[1].toInt() and 0xFF shl 8) or
                    (lenBytes[2].toInt() and 0xFF)

            val data = ByteArray(length)
            var read = 0
            while (read < length) {
                val r = stream.read(data, read, length - read)
                if (r == -1) break
                read += r
            }

            // Block type 4 is VORBIS_COMMENT
            if (blockType == 4) {
                val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                val vendorLength = buffer.int
                buffer.position(buffer.position() + vendorLength)

                val userCommentListLength = buffer.int
                for (i in 0 until userCommentListLength) {
                    val commentLength = buffer.int
                    val commentBytes = ByteArray(commentLength)
                    buffer.get(commentBytes)
                    val comment = String(commentBytes, Charsets.UTF_8)

                    if (comment.startsWith("LYRICS=", ignoreCase = true)) {
                        return comment.substringAfter("=")
                    }
                    if (comment.startsWith("UNSYNCEDLYRICS=", ignoreCase = true)) {
                        return comment.substringAfter("=")
                    }
                }
            }
        }
        return null
    }
}
