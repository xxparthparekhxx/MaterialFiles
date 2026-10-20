/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExifRemoverTest {
    @Test
    fun testStripJpegRemovesExifSegment() {
        val out = ByteArrayOutputStream()
        // SOI (FF D8)
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))

        // APP1 Exif segment
        val exifHeader = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val dummyData = byteArrayOf(1, 2, 3, 4)
        val exifLen = 2 + exifHeader.size + dummyData.size
        out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
        out.write(byteArrayOf((exifLen shr 8).toByte(), (exifLen and 0xFF).toByte()))
        out.write(exifHeader)
        out.write(dummyData)

        // APP0 JFIF segment (should be preserved)
        val jfifHeader = "JFIF\u0000".toByteArray(Charsets.US_ASCII)
        val jfifLen = 2 + jfifHeader.size
        out.write(byteArrayOf(0xFF.toByte(), 0xE0.toByte()))
        out.write(byteArrayOf((jfifLen shr 8).toByte(), (jfifLen and 0xFF).toByte()))
        out.write(jfifHeader)

        // SOS (FF DA)
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte()))
        out.write(byteArrayOf(0, 2)) // length 2
        // Scan data
        out.write(byteArrayOf(0x12, 0x34))

        // EOI (FF D9)
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))

        val inputBytes = out.toByteArray()
        val strippedOut = ByteArrayOutputStream()

        val stripJpegMethod = ExifRemover::class.java.getDeclaredMethod(
            "stripJpeg",
            java.io.InputStream::class.java,
            java.io.OutputStream::class.java
        ).apply { isAccessible = true }

        stripJpegMethod.invoke(ExifRemover, ByteArrayInputStream(inputBytes), strippedOut)

        val result = strippedOut.toByteArray()
        // Must contain SOI
        assertEquals(0xFF.toByte(), result[0])
        assertEquals(0xD8.toByte(), result[1])
        // Must not contain 0xFF 0xE1
        var hasApp1 = false
        for (i in 0 until result.size - 1) {
            if (result[i] == 0xFF.toByte() && result[i + 1] == 0xE1.toByte()) {
                hasApp1 = true
                break
            }
        }
        assertFalse("Result should not contain APP1 segment", hasApp1)
        // Must contain APP0
        var hasApp0 = false
        for (i in 0 until result.size - 1) {
            if (result[i] == 0xFF.toByte() && result[i + 1] == 0xE0.toByte()) {
                hasApp0 = true
                break
            }
        }
        assertTrue("Result should keep APP0 segment", hasApp0)
    }

    @Test
    fun testStripPngRemovesExifChunk() {
        val out = ByteArrayOutputStream()
        // Signature
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        // IHDR chunk
        out.write(byteArrayOf(0, 0, 0, 4)) // length
        out.write("IHDR".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(1, 2, 3, 4)) // data
        out.write(byteArrayOf(0, 0, 0, 0)) // crc

        // eXIf chunk
        out.write(byteArrayOf(0, 0, 0, 4))
        out.write("eXIf".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(5, 6, 7, 8))
        out.write(byteArrayOf(0, 0, 0, 0))

        // IEND chunk
        out.write(byteArrayOf(0, 0, 0, 0))
        out.write("IEND".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, 0, 0, 0))

        val inputBytes = out.toByteArray()
        val strippedOut = ByteArrayOutputStream()

        val stripPngMethod = ExifRemover::class.java.getDeclaredMethod(
            "stripPng",
            java.io.InputStream::class.java,
            java.io.OutputStream::class.java
        ).apply { isAccessible = true }

        stripPngMethod.invoke(ExifRemover, ByteArrayInputStream(inputBytes), strippedOut)

        val result = strippedOut.toByteArray()
        val resultStr = String(result, Charsets.US_ASCII)
        assertTrue(resultStr.contains("IHDR"))
        assertTrue(resultStr.contains("IEND"))
        assertFalse(resultStr.contains("eXIf"))
    }

    @Test
    fun testStripWebpRemovesExifChunk() {
        val out = ByteArrayOutputStream()
        val dummyData = byteArrayOf(1, 2, 3, 4)
        val riffBody = ByteArrayOutputStream()
        // VP8X chunk
        val vp8xData = ByteArray(10).apply {
            this[0] = 0x08.toByte() // EXIF flag set
        }
        riffBody.write("VP8X".toByteArray(Charsets.US_ASCII))
        riffBody.write(byteArrayOf(10, 0, 0, 0))
        riffBody.write(vp8xData)

        // EXIF chunk
        riffBody.write("EXIF".toByteArray(Charsets.US_ASCII))
        riffBody.write(byteArrayOf(4, 0, 0, 0))
        riffBody.write(dummyData)

        // VP8  chunk
        riffBody.write("VP8 ".toByteArray(Charsets.US_ASCII))
        riffBody.write(byteArrayOf(4, 0, 0, 0))
        riffBody.write(dummyData)

        val riffBodyBytes = riffBody.toByteArray()
        val riffLen = riffBodyBytes.size + 4

        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(
            (riffLen and 0xFF).toByte(),
            ((riffLen shr 8) and 0xFF).toByte(),
            ((riffLen shr 16) and 0xFF).toByte(),
            ((riffLen shr 24) and 0xFF).toByte()
        ))
        out.write("WEBP".toByteArray(Charsets.US_ASCII))
        out.write(riffBodyBytes)

        val inputBytes = out.toByteArray()
        val strippedOut = ByteArrayOutputStream()

        val stripWebpMethod = ExifRemover::class.java.getDeclaredMethod(
            "stripWebp",
            java.io.InputStream::class.java,
            java.io.OutputStream::class.java
        ).apply { isAccessible = true }

        stripWebpMethod.invoke(ExifRemover, ByteArrayInputStream(inputBytes), strippedOut)

        val result = strippedOut.toByteArray()
        val resultStr = String(result, Charsets.US_ASCII)
        assertTrue(resultStr.contains("RIFF"))
        assertTrue(resultStr.contains("WEBP"))
        assertTrue(resultStr.contains("VP8X"))
        assertTrue(resultStr.contains("VP8 "))
        assertFalse(resultStr.contains("EXIF"))
    }
}

