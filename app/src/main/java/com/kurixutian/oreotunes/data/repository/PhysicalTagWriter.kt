package com.kurixutian.oreotunes.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import com.kurixutian.oreotunes.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
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

                if (physicalFile.exists() && physicalFile.isFile && physicalFile.canWrite()) {
                    when (physicalFile.extension.lowercase()) {
                        "flac" -> {
                            fileModified = rewriteFlacMetadata(
                                file = physicalFile,
                                title = newTitle,
                                artist = newArtist,
                                album = newAlbum,
                                artworkBytes = artworkBytes
                            )
                        }

                        "mp3" -> {
                            fileModified = rewriteMp3Metadata(
                                file = physicalFile,
                                title = newTitle,
                                artist = newArtist,
                                album = newAlbum,
                                artworkBytes = artworkBytes
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            fileModified = false
        }

        // Cache artwork for OreoTunes local UI usage.
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            try {
                val albumArtDir = File(context.filesDir, "album_covers")
                    .apply { if (!exists()) mkdirs() }

                val targetCoverFile = File(albumArtDir, "cover_${song.id}.jpg")

                FileOutputStream(targetCoverFile).use { output ->
                    output.write(artworkBytes)
                    output.flush()
                }
            } catch (_: Exception) {
                // Artwork caching failure must not invalidate a successful tag write.
            }
        }

        // Keep MediaStore metadata synchronized with the physical file.
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, newTitle)
            put(MediaStore.Audio.Media.ARTIST, newArtist)
            put(MediaStore.Audio.Media.ALBUM, newAlbum)
        }

        val mediaStoreUpdated = try {
            context.contentResolver.update(
                song.contentUri,
                values,
                null,
                null
            ) > 0
        } catch (_: Exception) {
            false
        }

        // Ask Android's media scanner to refresh the modified file.
        if (fileModified && song.folderPath.isNotBlank()) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(song.folderPath),
                    arrayOf("audio/*"),
                    null
                )
            } catch (_: Exception) {
                // Scanning failure does not invalidate the physical write.
            }
        }

        fileModified || mediaStoreUpdated
    }

    // -------------------------------------------------------------------------
    // FLAC
    // -------------------------------------------------------------------------

    /**
     * Rewrites FLAC metadata while preserving all metadata blocks that OreoTunes
     * does not own.
     *
     * Existing VORBIS_COMMENT and PICTURE blocks are replaced because those are
     * the blocks this editor manages. STREAMINFO, SEEKTABLE, CUESHEET,
     * APPLICATION, PADDING, etc. are preserved.
     */
    private fun rewriteFlacMetadata(
        file: File,
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray?
    ): Boolean {

        val originalBytes = file.readBytes()

        if (!isFlac(originalBytes)) {
            return false
        }

        var offset = 4
        var foundLastBlock = false
        val preservedBlocks = mutableListOf<ByteArray>()
        var audioStartOffset = -1

        while (offset + 4 <= originalBytes.size) {
            val headerByte = originalBytes[offset].toInt() and 0xFF
            val isLast = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F

            val length =
                ((originalBytes[offset + 1].toInt() and 0xFF) shl 16) or
                        ((originalBytes[offset + 2].toInt() and 0xFF) shl 8) or
                        (originalBytes[offset + 3].toInt() and 0xFF)

            val blockEnd = offset + 4 + length

            if (blockEnd > originalBytes.size) {
                return false
            }

            /*
             * Preserve every block except:
             *
             * 4 = VORBIS_COMMENT
             * 6 = PICTURE
             *
             * Type 1 (PADDING) is intentionally preserved as well.
             */
            if (blockType != 4 && blockType != 6) {
                val preserved = originalBytes.copyOfRange(offset, blockEnd)

                // Clear the old isLast flag. We assign it again after adding
                // our managed metadata blocks.
                preserved[0] = (blockType and 0x7F).toByte()

                preservedBlocks.add(preserved)
            }

            offset = blockEnd

            if (isLast) {
                foundLastBlock = true
                audioStartOffset = offset
                break
            }
        }

        if (!foundLastBlock || audioStartOffset < 0) {
            return false
        }

        /*
         * Preserve the audio frames exactly as they were.
         */
        val audioFrames = originalBytes.copyOfRange(
            audioStartOffset,
            originalBytes.size
        )

        /*
         * Add the new Vorbis Comment block.
         */
        val vorbisPayload = buildVorbisCommentPayload(
            title = title,
            artist = artist,
            album = album
        )

        preservedBlocks.add(
            buildFlacMetadataBlock(
                blockType = 4,
                payload = vorbisPayload
            )
        )

        /*
         * If new artwork is supplied, replace the managed PICTURE block.
         *
         * If artworkBytes is null, existing artwork has already been removed
         * above because PICTURE is an OreoTunes-managed block.
         *
         * This matches the existing editor behavior where a normal metadata
         * save does not provide artwork and an online match can provide it.
         */
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            preservedBlocks.add(
                buildFlacMetadataBlock(
                    blockType = 6,
                    payload = buildFlacPicturePayload(artworkBytes)
                )
            )
        }

        if (preservedBlocks.isEmpty()) {
            return false
        }

        /*
         * Only the final metadata block receives the isLast flag.
         */
        preservedBlocks.forEach { block ->
            block[0] = (block[0].toInt() and 0x7F).toByte()
        }

        val finalBlock = preservedBlocks.last()
        finalBlock[0] = (finalBlock[0].toInt() or 0x80).toByte()

        val temporaryFile = File(
            file.parentFile,
            ".${file.name}.oreotunes_tmp"
        )

        return try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(
                    "fLaC".toByteArray(StandardCharsets.ISO_8859_1)
                )

                preservedBlocks.forEach { block ->
                    output.write(block)
                }

                output.write(audioFrames)
                output.flush()
                output.fd.sync()
            }

            if (!temporaryFile.exists() || temporaryFile.length() <= 4L) {
                temporaryFile.delete()
                false
            } else {
                replaceFileAtomically(
                    temporaryFile = temporaryFile,
                    targetFile = file
                )
            }
        } catch (_: Exception) {
            temporaryFile.delete()
            false
        }
    }

    private fun isFlac(data: ByteArray): Boolean {
        return data.size >= 4 &&
                data[0] == 'f'.code.toByte() &&
                data[1] == 'L'.code.toByte() &&
                data[2] == 'a'.code.toByte() &&
                data[3] == 'C'.code.toByte()
    }

    private fun buildVorbisCommentPayload(
        title: String,
        artist: String,
        album: String
    ): ByteArray {

        val out = ByteArrayOutputStream()

        val vendorString =
            "OreoTunes FLAC TagEngine".toByteArray(StandardCharsets.UTF_8)

        writeLittleEndianInt(out, vendorString.size)
        out.write(vendorString)

        val comments = mutableListOf<String>()

        if (title.isNotBlank()) {
            comments.add("TITLE=${title.trim()}")
        }

        if (artist.isNotBlank()) {
            comments.add("ARTIST=${artist.trim()}")
        }

        if (album.isNotBlank()) {
            comments.add("ALBUM=${album.trim()}")
        }

        writeLittleEndianInt(out, comments.size)

        comments.forEach { comment ->
            val bytes = comment.toByteArray(StandardCharsets.UTF_8)

            writeLittleEndianInt(out, bytes.size)
            out.write(bytes)
        }

        return out.toByteArray()
    }

    private fun buildFlacPicturePayload(
        imageBytes: ByteArray
    ): ByteArray {

        val out = ByteArrayOutputStream()

        val mime =
            "image/jpeg".toByteArray(StandardCharsets.ISO_8859_1)

        val description =
            ByteArray(0)

        // Picture type: 3 = Front Cover.
        writeBigEndianInt(out, 3)

        // MIME type.
        writeBigEndianInt(out, mime.size)
        out.write(mime)

        // Description.
        writeBigEndianInt(out, description.size)
        out.write(description)

        /*
         * We intentionally do not hard-code 1200x1200 anymore.
         *
         * FLAC requires these values to describe the actual image. Since
         * OreoTunes receives arbitrary artwork bytes, derive them when
         * possible. If decoding is unavailable here, zero is valid for
         * unknown dimensions.
         */
        val dimensions = readImageDimensions(imageBytes)

        writeBigEndianInt(out, dimensions.first)
        writeBigEndianInt(out, dimensions.second)

        // 24-bit RGB is the normal value for JPEG album artwork.
        writeBigEndianInt(out, 24)

        // Number of colors. Zero means not applicable.
        writeBigEndianInt(out, 0)

        // Image data.
        writeBigEndianInt(out, imageBytes.size)
        out.write(imageBytes)

        return out.toByteArray()
    }

    private fun buildFlacMetadataBlock(
        blockType: Int,
        payload: ByteArray
    ): ByteArray {

        require(payload.size <= 0xFFFFFF) {
            "FLAC metadata block is too large"
        }

        val block = ByteArray(4 + payload.size)

        block[0] = (blockType and 0x7F).toByte()
        block[1] = ((payload.size shr 16) and 0xFF).toByte()
        block[2] = ((payload.size shr 8) and 0xFF).toByte()
        block[3] = (payload.size and 0xFF).toByte()

        System.arraycopy(
            payload,
            0,
            block,
            4,
            payload.size
        )

        return block
    }

    // -------------------------------------------------------------------------
    // MP3 / ID3
    // -------------------------------------------------------------------------

    /**
     * Rewrites only the managed ID3 frames while preserving every other frame.
     *
     * Preserved examples:
     * TIT1, TBPM, TCOM, TCON, TDRC, TRCK, TPOS, COMM, USLT,
     * SYLT, UFID, TXXX, PRIV, custom frames, etc.
     *
     * Managed frames:
     * TIT2 = Title
     * TPE1 = Artist
     * TALB = Album
     * APIC = Album artwork
     */
    private fun rewriteMp3Metadata(
        file: File,
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray?
    ): Boolean {

        val originalBytes = file.readBytes()

        if (originalBytes.isEmpty()) {
            return false
        }

        val parsed = parseExistingId3Tag(originalBytes)

        val preservedFrames = mutableListOf<ByteArray>()

        if (parsed != null) {
            parsed.frames.forEach { frame ->
                /*
                 * Replace the frames OreoTunes owns.
                 *
                 * APIC is also replaced when artwork is supplied. When
                 * artworkBytes is null, existing APIC frames are preserved.
                 */
                val shouldRemove = when (frame.id) {
                    "TIT2", "TPE1", "TALB" -> true
                    "APIC" -> artworkBytes != null
                    else -> false
                }

                if (!shouldRemove) {
                    preservedFrames.add(frame.rawBytes)
                }
            }
        }

        /*
         * Add updated managed frames.
         */
        val newFrames = ByteArrayOutputStream()

        writeTextFrame("TIT2", title, newFrames)
        writeTextFrame("TPE1", artist, newFrames)
        writeTextFrame("TALB", album, newFrames)

        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            writeApicFrame(
                imageBytes = artworkBytes,
                out = newFrames
            )
        }

        /*
         * Existing preserved frames are written first, followed by the
         * updated OreoTunes-owned frames.
         */
        val finalFrames = ByteArrayOutputStream()

        preservedFrames.forEach { frame ->
            finalFrames.write(frame)
        }

        finalFrames.write(newFrames.toByteArray())

        val frameBytes = finalFrames.toByteArray()

        /*
         * ID3v2.3 uses a 10-byte header followed by frames.
         */
        val id3Header = buildId3v2Header(frameBytes.size)

        /*
         * Everything after the existing ID3 tag is treated as the actual MP3
         * payload. This preserves the MPEG audio bytes exactly.
         */
        val audioOffset = parsed?.tagEndOffset ?: 0

        if (audioOffset > originalBytes.size) {
            return false
        }

        val audioPayload = originalBytes.copyOfRange(
            audioOffset,
            originalBytes.size
        )

        val temporaryFile = File(
            file.parentFile,
            ".${file.name}.oreotunes_tmp"
        )

        return try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(id3Header)
                output.write(frameBytes)
                output.write(audioPayload)
                output.flush()
                output.fd.sync()
            }

            if (!temporaryFile.exists() || temporaryFile.length() <= 10L) {
                temporaryFile.delete()
                false
            } else {
                replaceFileAtomically(
                    temporaryFile = temporaryFile,
                    targetFile = file
                )
            }
        } catch (_: Exception) {
            temporaryFile.delete()
            false
        }
    }

    private data class ParsedId3Tag(
        val frames: List<ParsedId3Frame>,
        val tagEndOffset: Int
    )

    private data class ParsedId3Frame(
        val id: String,
        val rawBytes: ByteArray
    )

    private fun parseExistingId3Tag(
        data: ByteArray
    ): ParsedId3Tag? {

        if (data.size < 10) {
            return null
        }

        if (
            data[0] != 'I'.code.toByte() ||
            data[1] != 'D'.code.toByte() ||
            data[2] != '3'.code.toByte()
        ) {
            return null
        }

        val majorVersion = data[3].toInt() and 0xFF

        /*
         * We can safely preserve v2.3 and v2.4 frames.
         *
         * Other versions are left untouched by treating the file as having
         * no editable ID3 tag.
         */
        if (majorVersion != 3 && majorVersion != 4) {
            return null
        }

        val flags = data[5].toInt() and 0xFF

        val tagSize = readSynchsafeInt(
            data[6],
            data[7],
            data[8],
            data[9]
        )

        val tagEnd = 10 + tagSize

        if (tagEnd > data.size) {
            return null
        }

        /*
         * Extended headers complicate frame offsets. Rather than risk
         * corrupting a valid tag, preserve the complete tag if one is present
         * by returning no parsed frames. The caller will create a fresh tag
         * only if necessary.
         */
        val hasExtendedHeader = (flags and 0x40) != 0

        if (hasExtendedHeader) {
            return ParsedId3Tag(
                frames = emptyList(),
                tagEndOffset = tagEnd
            )
        }

        val frames = mutableListOf<ParsedId3Frame>()

        var offset = 10

        while (offset + 10 <= tagEnd) {
            val frameIdBytes = data.copyOfRange(
                offset,
                offset + 4
            )

            val frameId =
                String(
                    frameIdBytes,
                    StandardCharsets.ISO_8859_1
                )

            /*
             * Padding starts with zero bytes.
             */
            if (frameIdBytes.all { it.toInt() == 0 }) {
                break
            }

            /*
             * Invalid frame identifiers indicate padding or an unsupported
             * layout. Stop rather than guessing.
             */
            if (!isValidId3FrameId(frameId)) {
                break
            }

            val frameSize = if (majorVersion == 4) {
                readSynchsafeInt(
                    data[offset + 4],
                    data[offset + 5],
                    data[offset + 6],
                    data[offset + 7]
                )
            } else {
                readBigEndianInt(
                    data[offset + 4],
                    data[offset + 5],
                    data[offset + 6],
                    data[offset + 7]
                )
            }

            if (frameSize < 0) {
                break
            }

            val frameEnd = offset + 10 + frameSize

            if (frameEnd > tagEnd || frameEnd > data.size) {
                break
            }

            frames.add(
                ParsedId3Frame(
                    id = frameId,
                    rawBytes = data.copyOfRange(
                        offset,
                        frameEnd
                    )
                )
            )

            offset = frameEnd
        }

        return ParsedId3Tag(
            frames = frames,
            tagEndOffset = tagEnd
        )
    }

    private fun isValidId3FrameId(id: String): Boolean {
        if (id.length != 4) {
            return false
        }

        return id.all { char ->
            char.code in 0x30..0x39 ||
                    char.code in 0x41..0x5A
        }
    }

    private fun writeTextFrame(
        frameId: String,
        text: String,
        out: OutputStream
    ) {

        if (text.isBlank()) {
            return
        }

        val textBytes =
            text.trim().toByteArray(StandardCharsets.UTF_8)

        /*
         * Encoding 3 = UTF-8.
         */
        val framePayload =
            ByteArray(1 + textBytes.size)

        framePayload[0] = 3.toByte()

        System.arraycopy(
            textBytes,
            0,
            framePayload,
            1,
            textBytes.size
        )

        writeId3v23Frame(
            frameId = frameId,
            payload = framePayload,
            out = out
        )
    }

    private fun writeApicFrame(
        imageBytes: ByteArray,
        out: OutputStream
    ) {

        val mimeType =
            "image/jpeg".toByteArray(StandardCharsets.ISO_8859_1)

        val description =
            ByteArray(0)

        val apicPayload = ByteArrayOutputStream()

        /*
         * Text encoding:
         * 0 = ISO-8859-1.
         *
         * The MIME and description are ASCII-compatible.
         */
        apicPayload.write(0)

        // MIME type.
        apicPayload.write(mimeType)
        apicPayload.write(0)

        // Picture type: 3 = Front Cover.
        apicPayload.write(3)

        // Empty description.
        apicPayload.write(description)
        apicPayload.write(0)

        // Image bytes.
        apicPayload.write(imageBytes)

        writeId3v23Frame(
            frameId = "APIC",
            payload = apicPayload.toByteArray(),
            out = out
        )
    }

    private fun writeId3v23Frame(
        frameId: String,
        payload: ByteArray,
        out: OutputStream
    ) {

        require(frameId.length == 4)

        out.write(
            frameId.toByteArray(
                StandardCharsets.ISO_8859_1
            )
        )

        /*
         * ID3v2.3 frame size is a normal big-endian 32-bit integer.
         */
        val sizeBytes =
            ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(payload.size)
                .array()

        out.write(sizeBytes)

        // Frame status flags.
        out.write(0)
        out.write(0)

        out.write(payload)
    }

    private fun buildId3v2Header(
        tagSize: Int
    ): ByteArray {

        require(tagSize >= 0)
        require(tagSize <= 0x0FFFFFFF)

        val header = ByteArray(10)

        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()

        // ID3v2.3.0.
        header[3] = 3.toByte()
        header[4] = 0.toByte()

        // Flags.
        header[5] = 0.toByte()

        writeSynchsafeInt(
            value = tagSize,
            target = header,
            offset = 6
        )

        return header
    }

    // -------------------------------------------------------------------------
    // Shared binary helpers
    // -------------------------------------------------------------------------

    private fun readSynchsafeInt(
        b0: Byte,
        b1: Byte,
        b2: Byte,
        b3: Byte
    ): Int {

        return ((b0.toInt() and 0x7F) shl 21) or
                ((b1.toInt() and 0x7F) shl 14) or
                ((b2.toInt() and 0x7F) shl 7) or
                (b3.toInt() and 0x7F)
    }

    private fun writeSynchsafeInt(
        value: Int,
        target: ByteArray,
        offset: Int
    ) {

        target[offset] =
            ((value shr 21) and 0x7F).toByte()

        target[offset + 1] =
            ((value shr 14) and 0x7F).toByte()

        target[offset + 2] =
            ((value shr 7) and 0x7F).toByte()

        target[offset + 3] =
            (value and 0x7F).toByte()
    }

    private fun readBigEndianInt(
        b0: Byte,
        b1: Byte,
        b2: Byte,
        b3: Byte
    ): Int {

        return ((b0.toInt() and 0xFF) shl 24) or
                ((b1.toInt() and 0xFF) shl 16) or
                ((b2.toInt() and 0xFF) shl 8) or
                (b3.toInt() and 0xFF)
    }

    private fun writeLittleEndianInt(
        out: OutputStream,
        value: Int
    ) {

        out.write(
            ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array()
        )
    }

    private fun writeBigEndianInt(
        out: OutputStream,
        value: Int
    ) {

        out.write(
            ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(value)
                .array()
        )
    }

    // -------------------------------------------------------------------------
    // Artwork dimensions
    // -------------------------------------------------------------------------

    /**
     * Reads common JPEG/PNG dimensions without introducing another image
     * dependency into the repository layer.
     *
     * Returns 0x0 when the dimensions cannot be determined.
     */
    private fun readImageDimensions(
        data: ByteArray
    ): Pair<Int, Int> {

        if (data.size >= 24 &&
            data[0] == 0x89.toByte() &&
            data[1] == 0x50.toByte() &&
            data[2] == 0x4E.toByte() &&
            data[3] == 0x47.toByte()
        ) {
            val width =
                readBigEndianInt(
                    data[16],
                    data[17],
                    data[18],
                    data[19]
                )

            val height =
                readBigEndianInt(
                    data[20],
                    data[21],
                    data[22],
                    data[23]
                )

            return width to height
        }

        if (data.size >= 4 &&
            data[0] == 0xFF.toByte() &&
            data[1] == 0xD8.toByte()
        ) {
            var offset = 2

            while (offset + 9 < data.size) {

                if (data[offset] != 0xFF.toByte()) {
                    offset++
                    continue
                }

                val marker = data[offset + 1].toInt() and 0xFF

                if (marker == 0xD8 || marker == 0xD9) {
                    offset += 2
                    continue
                }

                if (offset + 4 > data.size) {
                    break
                }

                val segmentLength =
                    ((data[offset + 2].toInt() and 0xFF) shl 8) or
                            (data[offset + 3].toInt() and 0xFF)

                if (segmentLength < 2 ||
                    offset + 2 + segmentLength > data.size
                ) {
                    break
                }

                /*
                 * SOF markers containing image dimensions.
                 */
                val isStartOfFrame =
                    marker in 0xC0..0xC3 ||
                            marker in 0xC5..0xC7 ||
                            marker in 0xC9..0xCB ||
                            marker in 0xCD..0xCF

                if (isStartOfFrame &&
                    offset + 9 < data.size
                ) {

                    val height =
                        ((data[offset + 5].toInt() and 0xFF) shl 8) or
                                (data[offset + 6].toInt() and 0xFF)

                    val width =
                        ((data[offset + 7].toInt() and 0xFF) shl 8) or
                                (data[offset + 8].toInt() and 0xFF)

                    return width to height
                }

                offset += 2 + segmentLength
            }
        }

        return 0 to 0
    }

    // -------------------------------------------------------------------------
    // Safe replacement
    // -------------------------------------------------------------------------

    /**
     * Replaces the original file only after the new file has been completely
     * written and flushed.
     *
     * The original is renamed to a temporary backup first. If the replacement
     * fails, the original can be restored.
     */
    private fun replaceFileAtomically(
        temporaryFile: File,
        targetFile: File
    ): Boolean {

        val backupFile = File(
            targetFile.parentFile,
            ".${targetFile.name}.oreotunes_backup"
        )

        return try {

            if (backupFile.exists()) {
                backupFile.delete()
            }

            /*
             * Move original aside.
             */
            if (!targetFile.renameTo(backupFile)) {
                temporaryFile.delete()
                return false
            }

            /*
             * Move the completed replacement into place.
             */
            if (!temporaryFile.renameTo(targetFile)) {

                // Attempt recovery.
                backupFile.renameTo(targetFile)

                temporaryFile.delete()

                false
            } else {

                /*
                 * Replacement succeeded, so the backup is no longer needed.
                 */
                backupFile.delete()

                true
            }

        } catch (_: Exception) {

            /*
             * Best-effort recovery.
             */
            if (!targetFile.exists() && backupFile.exists()) {
                backupFile.renameTo(targetFile)
            }

            temporaryFile.delete()

            false
        }
    }
}