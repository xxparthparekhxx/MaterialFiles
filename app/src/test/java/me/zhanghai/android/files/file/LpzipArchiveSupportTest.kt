package me.zhanghai.android.files.file

import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.util.asFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LpzipArchiveSupportTest {
    @Test
    fun lpzipIsRecognizedAsZipArchive() {
        val mimeType = MimeType.guessFromExtension("lpzip")
        assertEquals("application/zip", mimeType.value)
        assertTrue(mimeType.isSupportedArchive)
        assertEquals(MimeTypeIcon.ARCHIVE, mimeType.icon)
    }

    @Test
    fun lzIsRecognizedAsLzipArchive() {
        val mimeTypeLz = MimeType.guessFromExtension("lz")
        assertEquals("application/x-lzip", mimeTypeLz.value)
        assertTrue(mimeTypeLz.isSupportedArchive)
        assertEquals(MimeTypeIcon.ARCHIVE, mimeTypeLz.icon)

        val mimeTypeLzip = MimeType.guessFromExtension("lzip")
        assertEquals("application/x-lzip", mimeTypeLzip.value)
        assertTrue(mimeTypeLzip.isSupportedArchive)
        assertEquals(MimeTypeIcon.ARCHIVE, mimeTypeLzip.icon)
    }

    @Test
    fun tlzIsRecognizedAsLzipTarArchive() {
        val mimeTypeTlz = MimeType.guessFromExtension("tlz")
        assertEquals("application/x-lzip-compressed-tar", mimeTypeTlz.value)
        assertTrue(mimeTypeTlz.isSupportedArchive)
        assertEquals(MimeTypeIcon.ARCHIVE, mimeTypeTlz.icon)
    }

    @Test
    fun doubleExtensionTarLz() {
        val fileName = "archive.tar.lz".asFileName()
        assertEquals("tar.lz", fileName.extensions)
        assertEquals("archive", fileName.baseName)

        val byteFileName = "archive.tar.lz".toByteString().asFileName()
        assertEquals("tar.lz", byteFileName.extensions.toString())
        assertEquals("archive", byteFileName.baseName.toString())
    }
}
