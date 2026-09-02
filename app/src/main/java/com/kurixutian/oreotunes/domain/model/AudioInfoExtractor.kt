package com.kurixutian.oreotunes.domain.model

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.util.Locale

data class AudioTrackSpecs(
    val badgeLabel: String = "AUDIO",
    val technicalDetails: String = "Standard Audio"
)

object AudioInfoExtractor {
    fun extractSpecs(context: Context, contentUri: Uri?): AudioTrackSpecs {
        if (contentUri == null) return AudioTrackSpecs("MP3", "MP3 • 320 kbps")

        var mimeType = ""
        var bitrate = 0L
        var sampleRate = ""
        var fileSizeMb = 0.0

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, contentUri)

            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            bitrate = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L) / 1000
            sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE) ?: ""

            retriever.release()

            context.contentResolver.openFileDescriptor(contentUri, "r")?.use { pfd ->
                fileSizeMb = pfd.statSize / (1024.0 * 1024.0)
            }
        } catch (_: Exception) { }

        val format = when {
            mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("opus", ignoreCase = true) -> "OPUS"
            mimeType.contains("wav", ignoreCase = true) -> "WAV"
            mimeType.contains("aac", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "AAC"
            mimeType.contains("webm", ignoreCase = true) -> "WEBM"
            mimeType.contains("ogg", ignoreCase = true) -> "OGG"
            else -> "MP3"
        }

        val badge = when (format) {
            "FLAC", "WAV" -> "ılı Lossless"
            "OPUS", "AAC" -> "ılı Hi-Fi ($format)"
            else -> "ılı $format"
        }

        val formattedSampleRate = if (sampleRate.isNotEmpty()) {
            val khz = sampleRate.toDoubleOrNull()?.div(1000.0) ?: 44.1
            "%.1f kHz".format(Locale.US, khz)
        } else "44.1 kHz"

        val formattedBitrate = if (bitrate > 0) "$bitrate kbps" else "VBR"
        val formattedSize = if (fileSizeMb > 0) "%.1f MB".format(Locale.US, fileSizeMb) else ""

        val detailString = listOf(format, formattedSampleRate, formattedBitrate, formattedSize)
            .filter { it.isNotEmpty() }
            .joinToString("  •  ")

        return AudioTrackSpecs(badgeLabel = badge, technicalDetails = detailString)
    }
}
