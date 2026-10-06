/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.spi.FileTypeDetector
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.file.forSpecialPosixFileType
import me.zhanghai.android.files.file.guessFromPath
import java.io.IOException

object AndroidFileTypeDetector : FileTypeDetector() {
    @Throws(IOException::class)
    override fun probeContentType(path: Path): String {
        val attributes = path.readAttributes(BasicFileAttributes::class.java)
        return getMimeType(path, attributes)
    }

    fun getMimeType(path: Path, attributes: BasicFileAttributes): String {
        MimeType.forSpecialPosixFileType(attributes.posixFileType)?.let { return it.value }
        if (attributes.isDirectory) {
            return MimeType.DIRECTORY.value
        }
        if (attributes is ContentProviderFileAttributes) {
            attributes.mimeType()?.let { return it }
        }
        val guessed = MimeType.guessFromPath(path.toString())
        if (guessed != MimeType.GENERIC) {
            return guessed.value
        }
        // Files without (or with unknown) extensions, e.g. images saved by some apps, would
        // otherwise all look like opaque binaries. Sniff a few well-known magic numbers so
        // they still open, thumbnail and share with the right type.
        if (attributes.isRegularFile) {
            sniffMimeType(path)?.let { return it.value }
        }
        return guessed.value
    }

    private fun sniffMimeType(path: Path): MimeType? {
        val header = try {
            path.newInputStream().use { stream ->
                val buffer = ByteArray(MAX_SNIFF_BYTES)
                var offset = 0
                while (offset < buffer.size) {
                    val read = stream.read(buffer, offset, buffer.size - offset)
                    if (read <= 0) {
                        break
                    }
                    offset += read
                }
                if (offset == 0) {
                    return null
                }
                buffer.copyOf(offset)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return sniffMimeType(header)
    }

    private fun sniffMimeType(header: ByteArray): MimeType? {
        fun match(offset: Int, vararg bytes: Byte): Boolean {
            if (header.size < offset + bytes.size) {
                return false
            }
            return bytes.indices.all { header[offset + it] == bytes[it] }
        }
        fun matchAscii(offset: Int, text: String): Boolean {
            if (header.size < offset + text.length) {
                return false
            }
            return text.indices.all { header[offset + it] == text[it].code.toByte() }
        }
        return when {
            match(0, 0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) -> "image/jpeg".asMimeType()
            match(
                0, 0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            ) -> "image/png".asMimeType()
            matchAscii(0, "GIF87a") || matchAscii(0, "GIF89a") -> "image/gif".asMimeType()
            matchAscii(0, "RIFF") && matchAscii(8, "WEBP") -> "image/webp".asMimeType()
            matchAscii(0, "BM") -> "image/bmp".asMimeType()
            matchAscii(0, "%PDF-") -> MimeType.PDF
            match(0, 0x50, 0x4B, 0x03, 0x04) -> "application/zip".asMimeType()
            matchAscii(0, "ID3") ||
                (match(0, 0xFF.toByte()) && header.size > 1 &&
                    header[1].toInt() and 0xE0 == 0xE0) -> "audio/mpeg".asMimeType()
            matchAscii(4, "ftyp") -> "video/mp4".asMimeType()
            else -> null
        }
    }

    private const val MAX_SNIFF_BYTES = 16
}
