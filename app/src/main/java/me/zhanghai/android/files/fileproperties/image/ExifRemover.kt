/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.image

import androidx.exifinterface.media.ExifInterface
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java8.nio.file.Path
import java8.nio.file.StandardOpenOption
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.newOutputStream

object ExifRemover {
    @Throws(IOException::class)
    fun removeExif(path: Path) {
        val cacheDir = application.cacheDir
        val tempInputFile = File.createTempFile("exif_in_", ".tmp", cacheDir)
        val tempOutputFile = File.createTempFile("exif_out_", ".tmp", cacheDir)
        try {
            path.newInputStream().buffered().use { input ->
                tempInputFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }

            stripMetadata(tempInputFile, tempOutputFile)

            tempOutputFile.inputStream().buffered().use { tempIn ->
                path.newOutputStream(
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                ).use { pathOut ->
                    tempIn.copyTo(pathOut)
                }
            }
        } finally {
            tempInputFile.delete()
            tempOutputFile.delete()
        }
    }

    @Throws(IOException::class)
    private fun stripMetadata(inputFile: File, outputFile: File) {
        inputFile.inputStream().buffered().use { input ->
            input.mark(16)
            val header = ByteArray(16)
            val read = input.read(header)
            input.reset()

            when {
                read >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> {
                    // JPEG
                    val orientation = try {
                        ExifInterface(inputFile.absolutePath)
                            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } catch (e: Exception) {
                        ExifInterface.ORIENTATION_NORMAL
                    }
                    if (orientation != ExifInterface.ORIENTATION_NORMAL &&
                        orientation != ExifInterface.ORIENTATION_UNDEFINED
                    ) {
                        // Preserves orientation while stripping all other EXIF tags
                        stripUsingExifInterface(inputFile, outputFile, preserveOrientation = true)
                    } else {
                        outputFile.outputStream().buffered().use { output ->
                            stripJpeg(input, output)
                        }
                    }
                }
                read >= 8 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> {
                    // PNG
                    outputFile.outputStream().buffered().use { output ->
                        stripPng(input, output)
                    }
                }
                read >= 12 && header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
                    header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte() -> {
                    // WebP
                    outputFile.outputStream().buffered().use { output ->
                        stripWebp(input, output)
                    }
                }
                else -> {
                    // Fallback for other formats supported by ExifInterface (TIFF, etc.)
                    stripUsingExifInterface(inputFile, outputFile, preserveOrientation = false)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun stripUsingExifInterface(
        inputFile: File,
        outputFile: File,
        preserveOrientation: Boolean
    ) {
        inputFile.copyTo(outputFile, overwrite = true)
        val exif = ExifInterface(outputFile.absolutePath)
        for (tag in ALL_EXIF_TAGS) {
            if (preserveOrientation && tag == ExifInterface.TAG_ORIENTATION) {
                continue
            }
            try {
                exif.setAttribute(tag, null)
            } catch (ignored: Exception) {}
        }
        exif.saveAttributes()
    }

    @Throws(IOException::class)
    private fun stripJpeg(input: InputStream, output: OutputStream) {
        val dataInput = DataInputStream(input)
        val dataOutput = DataOutputStream(output)

        val soi1 = dataInput.readUnsignedByte()
        val soi2 = dataInput.readUnsignedByte()
        if (soi1 != 0xFF || soi2 != 0xD8) {
            throw IOException("Not a valid JPEG image: missing SOI marker")
        }
        dataOutput.writeByte(0xFF)
        dataOutput.writeByte(0xD8)

        val buffer = ByteArray(8192)
        while (true) {
            val b = try {
                dataInput.readUnsignedByte()
            } catch (e: EOFException) {
                break
            }
            if (b != 0xFF) {
                throw IOException("Expected 0xFF marker prefix, found 0x${Integer.toHexString(b)}")
            }
            var marker = dataInput.readUnsignedByte()
            while (marker == 0xFF) {
                marker = dataInput.readUnsignedByte()
            }

            when (marker) {
                0xD9 -> { // EOI (End of Image)
                    dataOutput.writeByte(0xFF)
                    dataOutput.writeByte(marker)
                    break
                }
                0xDA -> { // SOS (Start of Scan)
                    dataOutput.writeByte(0xFF)
                    dataOutput.writeByte(marker)
                    val length = dataInput.readUnsignedShort()
                    dataOutput.writeShort(length)
                    val remainingHeader = length - 2
                    if (remainingHeader > 0) {
                        val headerBytes = ByteArray(remainingHeader)
                        dataInput.readFully(headerBytes)
                        dataOutput.write(headerBytes)
                    }
                    while (true) {
                        val read = dataInput.read(buffer)
                        if (read < 0) break
                        dataOutput.write(buffer, 0, read)
                    }
                    break
                }
                0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0x01 -> {
                    // Standalone markers
                    dataOutput.writeByte(0xFF)
                    dataOutput.writeByte(marker)
                }
                else -> {
                    val length = dataInput.readUnsignedShort()
                    if (length < 2) {
                        throw IOException("Invalid segment length: $length")
                    }
                    val payloadLength = length - 2
                    val payload = ByteArray(payloadLength)
                    dataInput.readFully(payload)

                    // Check for Exif / XMP APP1, IPTC APP13, COM
                    val isExif = marker == 0xE1 && payloadLength >= 6 &&
                        payload[0] == 'E'.code.toByte() && payload[1] == 'x'.code.toByte() &&
                        payload[2] == 'i'.code.toByte() && payload[3] == 'f'.code.toByte() &&
                        payload[4] == 0.toByte() && payload[5] == 0.toByte()
                    val isXmp = marker == 0xE1 && payloadLength >= 19 &&
                        payload.startsWith(IDENTIFIER_XMP)
                    val isIptc = marker == 0xED && payloadLength >= 14 &&
                        payload.startsWith(IDENTIFIER_PHOTOSHOP)
                    val isComment = marker == 0xFE

                    if (isExif || isXmp || isIptc || isComment) {
                        continue
                    }

                    dataOutput.writeByte(0xFF)
                    dataOutput.writeByte(marker)
                    dataOutput.writeShort(length)
                    dataOutput.write(payload)
                }
            }
        }
        dataOutput.flush()
    }

    @Throws(IOException::class)
    private fun stripPng(input: InputStream, output: OutputStream) {
        val dataInput = DataInputStream(input)
        val dataOutput = DataOutputStream(output)

        val signature = ByteArray(8)
        dataInput.readFully(signature)
        val expectedPngSig = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        if (!signature.contentEquals(expectedPngSig)) {
            throw IOException("Not a valid PNG image: signature mismatch")
        }
        dataOutput.write(signature)

        val buffer = ByteArray(8192)
        while (true) {
            val chunkLength = try {
                dataInput.readInt()
            } catch (e: EOFException) {
                break
            }
            val chunkType = ByteArray(4)
            dataInput.readFully(chunkType)
            val typeStr = String(chunkType, Charsets.US_ASCII)

            val isMetadata = typeStr == "eXIf" || typeStr == "tEXt" ||
                typeStr == "zTXt" || typeStr == "iTXt"

            if (isMetadata) {
                var toSkip = chunkLength.toLong() + 4 // include CRC
                while (toSkip > 0) {
                    val skipped = dataInput.skip(toSkip)
                    if (skipped <= 0) {
                        dataInput.readByte()
                        toSkip--
                    } else {
                        toSkip -= skipped
                    }
                }
            } else {
                dataOutput.writeInt(chunkLength)
                dataOutput.write(chunkType)
                var remaining = chunkLength + 4 // include CRC
                while (remaining > 0) {
                    val toRead = minOf(remaining, buffer.size)
                    dataInput.readFully(buffer, 0, toRead)
                    dataOutput.write(buffer, 0, toRead)
                    remaining -= toRead
                }
            }
            if (typeStr == "IEND") {
                break
            }
        }
        dataOutput.flush()
    }

    @Throws(IOException::class)
    private fun stripWebp(input: InputStream, output: OutputStream) {
        val bytes = input.readBytes()
        if (bytes.size < 12) {
            throw IOException("Not a valid WebP image: too short")
        }
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte() ||
            bytes[8] != 'W'.code.toByte() || bytes[9] != 'E'.code.toByte() ||
            bytes[10] != 'B'.code.toByte() || bytes[11] != 'P'.code.toByte()
        ) {
            throw IOException("Not a valid WebP image: missing RIFF/WEBP header")
        }

        val chunksOut = ByteArrayOutputStream()
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val fourCC = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = (bytes[offset + 4].toInt() and 0xFF) or
                ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 7].toInt() and 0xFF) shl 24)
            val paddedChunkSize = if (chunkSize % 2 == 1) chunkSize + 1 else chunkSize
            val nextOffset = offset + 8 + paddedChunkSize
            if (nextOffset > bytes.size) {
                chunksOut.write(bytes, offset, bytes.size - offset)
                break
            }

            if (fourCC == "EXIF" || fourCC == "XMP ") {
                // Skip metadata chunk!
            } else if (fourCC == "VP8X" && chunkSize >= 10) {
                val chunkCopy = bytes.copyOfRange(offset, nextOffset)
                val flagsIndex = 8
                chunkCopy[flagsIndex] = (chunkCopy[flagsIndex].toInt() and 0x0C.inv()).toByte()
                chunksOut.write(chunkCopy)
            } else {
                chunksOut.write(bytes, offset, nextOffset - offset)
            }
            offset = nextOffset
        }

        val body = chunksOut.toByteArray()
        val totalRiffSize = body.size + 4

        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        output.write(
            byteArrayOf(
                (totalRiffSize and 0xFF).toByte(),
                ((totalRiffSize shr 8) and 0xFF).toByte(),
                ((totalRiffSize shr 16) and 0xFF).toByte(),
                ((totalRiffSize shr 24) and 0xFF).toByte()
            )
        )
        output.write("WEBP".toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private val IDENTIFIER_XMP = "http://ns.adobe.com/".toByteArray(Charsets.UTF_8)
    private val IDENTIFIER_PHOTOSHOP = "Photoshop 3.0\u0000".toByteArray(Charsets.UTF_8)
}
