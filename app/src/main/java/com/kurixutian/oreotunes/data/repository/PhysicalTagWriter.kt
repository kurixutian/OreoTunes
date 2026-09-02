package com.kurixutian.oreotunes.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import com.kurixutian.oreotunes.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class PhysicalTagWriter(private val context: Context) {

    suspend fun applyPhysicalMetadata(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        artworkBytes: ByteArray?
    ): Boolean = withContext(Dispatchers.IO) {
        var fileModified = false

        try {
            if (song.folderPath.isNotBlank()) {
                val physicalFile = File(song.folderPath)
                if (physicalFile.exists() && physicalFile.canWrite()) {
                    val ext = physicalFile.extension.lowercase()
                    when (ext) {
                        "flac" -> {
                            embedFlacMetadataAndPicture(physicalFile, newTitle, newArtist, newAlbum, artworkBytes)
                            fileModified = true
                        }
                        "mp3" -> {
                            embedId3v2Tags(physicalFile, newTitle, newArtist, newAlbum, artworkBytes)
                            fileModified = true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Cache Artwork for local UI caching
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            try {
                val albumArtDir = File(context.filesDir, "album_covers").apply { if (!exists()) mkdirs() }
                val targetCoverFile = File(albumArtDir, "cover_${song.id}.jpg")
                FileOutputStream(targetCoverFile).use { fos ->
                    fos.write(artworkBytes)
                    fos.flush()
                }
            } catch (_: Exception) {}
        }

        // Update MediaStore Index
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, newTitle)
            put(MediaStore.Audio.Media.ARTIST, newArtist)
            put(MediaStore.Audio.Media.ALBUM, newAlbum)
        }

        val rows = try {
            context.contentResolver.update(song.contentUri, values, null, null)
        } catch (_: Exception) {
            0
        }

        // Rescan modified file path
        if (song.folderPath.isNotBlank()) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(song.folderPath),
                arrayOf("audio/*"),
                null
            )
        }

        rows > 0 || fileModified
    }

    /**
     * Physical FLAC Vorbis Comment & Picture (Block Type 6) Metadata Injection
     */
    private fun embedFlacMetadataAndPicture(
        file: File,
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray?
    ) {
        val originalBytes = file.readBytes()
        if (originalBytes.size < 4 || originalBytes[0] != 'f'.code.toByte() || originalBytes[1] != 'L'.code.toByte() ||
            originalBytes[2] != 'a'.code.toByte() || originalBytes[3] != 'C'.code.toByte()
        ) {
            return // Not a valid FLAC container
        }

        var offset = 4
        var isLast = false
        val keptBlocks = mutableListOf<ByteArray>()
        var audioStartOffset = originalBytes.size

        while (offset < originalBytes.size && !isLast) {
            val headerByte = originalBytes[offset].toInt() and 0xFF
            isLast = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F

            val length = ((originalBytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((originalBytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (originalBytes[offset + 3].toInt() and 0xFF)

            val fullBlockLength = 4 + length
            val blockPayload = originalBytes.copyOfRange(offset, offset + fullBlockLength)

            // Keep STREAMINFO (0) and other non-comment/picture blocks
            if (blockType != 4 && blockType != 6 && blockType != 1) {
                // Mask out isLast flag on kept blocks
                blockPayload[0] = (blockType and 0x7F).toByte()
                keptBlocks.add(blockPayload)
            }

            offset += fullBlockLength
            if (isLast) {
                audioStartOffset = offset
                break
            }
        }

        val rawAudioFrames = originalBytes.copyOfRange(audioStartOffset, originalBytes.size)

        // Build VORBIS_COMMENT Block (Type 4)
        val vorbisPayload = buildVorbisCommentPayload(title, artist, album)
        val vorbisBlock = buildFlacMetadataBlock(4, vorbisPayload)
        keptBlocks.add(vorbisBlock)

        // Build PICTURE Block (Type 6)
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            val picPayload = buildFlacPicturePayload(artworkBytes)
            val picBlock = buildFlacMetadataBlock(6, picPayload)
            keptBlocks.add(picBlock)
        }

        // Set isLast flag on the final metadata block
        if (keptBlocks.isNotEmpty()) {
            val lastBlock = keptBlocks.last()
            lastBlock[0] = (lastBlock[0].toInt() or 0x80).toByte()
        }

        // Write rewritten FLAC file to storage
        FileOutputStream(file).use { fos ->
            fos.write("fLaC".toByteArray(StandardCharsets.ISO_8859_1))
            for (block in keptBlocks) {
                fos.write(block)
            }
            fos.write(rawAudioFrames)
            fos.flush()
        }
    }

    private fun buildVorbisCommentPayload(title: String, artist: String, album: String): ByteArray {
        val out = ByteArrayOutputStream()
        val vendorString = "OreoTunes FLAC TagEngine".toByteArray(StandardCharsets.UTF_8)

        // Vendor Length (32-bit LE)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(vendorString.size).array())
        out.write(vendorString)

        val comments = mutableListOf<String>()
        if (title.isNotBlank()) comments.add("TITLE=$title")
        if (artist.isNotBlank()) comments.add("ARTIST=$artist")
        if (album.isNotBlank()) comments.add("ALBUM=$album")

        // Comments count (32-bit LE)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(comments.size).array())

        for (comment in comments) {
            val commentBytes = comment.toByteArray(StandardCharsets.UTF_8)
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(commentBytes.size).array())
            out.write(commentBytes)
        }

        return out.toByteArray()
    }

    private fun buildFlacPicturePayload(imageBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val mime = "image/jpeg".toByteArray(StandardCharsets.ISO_8859_1)
        val desc = "".toByteArray(StandardCharsets.UTF_8)

        // Picture Type: 3 = Front Cover (32-bit BE)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(3).array())

        // MIME length & MIME string (32-bit BE)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(mime.size).array())
        out.write(mime)

        // Description length & Description (32-bit BE)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(desc.size).array())
        out.write(desc)

        // Width (32-bit BE) - 1200
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1200).array())
        // Height (32-bit BE) - 1200
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1200).array())
        // Color Depth (32-bit BE) - 24 bits
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(24).array())
        // Color count (32-bit BE) - 0
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array())

        // Image Data Length & Image Bytes (32-bit BE)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(imageBytes.size).array())
        out.write(imageBytes)

        return out.toByteArray()
    }

    private fun buildFlacMetadataBlock(blockType: Int, payload: ByteArray): ByteArray {
        val block = ByteArray(4 + payload.size)
        block[0] = (blockType and 0x7F).toByte()
        block[1] = ((payload.size shr 16) and 0xFF).toByte()
        block[2] = ((payload.size shr 8) and 0xFF).toByte()
        block[3] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, block, 4, payload.size)
        return block
    }

    /**
     * ID3v2.3 MP3 Header Injection
     */
    private fun embedId3v2Tags(
        file: File,
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray?
    ) {
        val originalBytes = file.readBytes()
        val audioDataOffset = findAudioDataOffset(originalBytes)
        val rawAudioPayload = if (audioDataOffset > 0 && audioDataOffset < originalBytes.size) {
            originalBytes.copyOfRange(audioDataOffset, originalBytes.size)
        } else {
            originalBytes
        }

        val framesOut = ByteArrayOutputStream()
        writeTextFrame("TIT2", title, framesOut)
        writeTextFrame("TPE1", artist, framesOut)
        writeTextFrame("TALB", album, framesOut)

        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            writeApicFrame(artworkBytes, framesOut)
        }

        val frameBytes = framesOut.toByteArray()
        val id3Header = buildId3v2Header(frameBytes.size)

        FileOutputStream(file).use { fos ->
            fos.write(id3Header)
            fos.write(frameBytes)
            fos.write(rawAudioPayload)
            fos.flush()
        }
    }

    private fun findAudioDataOffset(data: ByteArray): Int {
        if (data.size < 10) return 0
        if (data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()) {
            val size = (data[6].toInt() and 0x7F shl 21) or
                    (data[7].toInt() and 0x7F shl 14) or
                    (data[8].toInt() and 0x7F shl 7) or
                    (data[9].toInt() and 0x7F)
            return 10 + size
        }
        return 0
    }

    private fun buildId3v2Header(tagSize: Int): ByteArray {
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 3.toByte()
        header[4] = 0.toByte()
        header[5] = 0.toByte()
        header[6] = ((tagSize shr 21) and 0x7F).toByte()
        header[7] = ((tagSize shr 14) and 0x7F).toByte()
        header[8] = ((tagSize shr 7) and 0x7F).toByte()
        header[9] = (tagSize and 0x7F).toByte()
        return header
    }

    private fun writeTextFrame(frameId: String, text: String, out: OutputStream) {
        if (text.isBlank()) return
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        val framePayload = ByteArray(1 + textBytes.size)
        framePayload[0] = 3.toByte()
        System.arraycopy(textBytes, 0, framePayload, 1, textBytes.size)

        out.write(frameId.toByteArray(StandardCharsets.ISO_8859_1))
        val sizeBuffer = ByteBuffer.allocate(4).putInt(framePayload.size).array()
        out.write(sizeBuffer)
        out.write(0)
        out.write(0)
        out.write(framePayload)
    }

    private fun writeApicFrame(imageBytes: ByteArray, out: OutputStream) {
        val mimeType = "image/jpeg".toByteArray(StandardCharsets.ISO_8859_1)
        val description = "".toByteArray(StandardCharsets.ISO_8859_1)

        val payloadSize = 1 + mimeType.size + 1 + 1 + description.size + 1 + imageBytes.size
        val apicPayload = ByteArrayOutputStream(payloadSize)

        apicPayload.write(0)
        apicPayload.write(mimeType)
        apicPayload.write(0)
        apicPayload.write(3)
        apicPayload.write(description)
        apicPayload.write(0)
        apicPayload.write(imageBytes)

        val frameData = apicPayload.toByteArray()
        out.write("APIC".toByteArray(StandardCharsets.ISO_8859_1))
        val sizeBuffer = ByteBuffer.allocate(4).putInt(frameData.size).array()
        out.write(sizeBuffer)
        out.write(0)
        out.write(0)
        out.write(frameData)
    }
}
