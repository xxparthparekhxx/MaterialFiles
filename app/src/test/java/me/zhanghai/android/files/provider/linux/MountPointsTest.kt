/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.linux

import java8.nio.file.Paths
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.MimeTypeIcon
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.file.icon
import me.zhanghai.android.files.file.isDirectoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class MountPointsTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            try {
                FileSystemProvider.installDefaultProvider(LinuxFileSystemProvider)
            } catch (e: Throwable) {
                // Ignore if already installed
            }
        }
    }

    @Test
    fun testRootIsMountPoint() {
        val rootPath = Paths.get("/")
        assertTrue("Root / should always be recognized as a mount point", rootPath.isMountPoint)
    }

    @Test
    fun testMimeTypeIsDirectory() {
        assertTrue(MimeType.DIRECTORY.isDirectoryType)
        assertTrue(MimeType.MOUNT_POINT.isDirectoryType)
        assertFalse(MimeType.GENERIC.isDirectoryType)
        assertFalse(MimeType.PDF.isDirectoryType)
    }

    @Test
    fun testMountPointMimeTypeValues() {
        assertEquals("inode/mount-point", MimeType.MOUNT_POINT.value)
        assertEquals("inode", MimeType.MOUNT_POINT.type)
        assertEquals("mount-point", MimeType.MOUNT_POINT.subtype)
    }

    @Test
    fun testMountPointIcon() {
        assertEquals(MimeTypeIcon.MOUNT_POINT, MimeType.MOUNT_POINT.icon)
        assertEquals(MimeTypeIcon.MOUNT_POINT, "inode/mount-point".asMimeType().icon)
    }

    @Test
    fun testMountEntriesRead() {
        val entries = MountPoints.refreshMountsIfNeeded()
        assertNotNull(entries)
        if (entries.isNotEmpty()) {
            val rootEntry = entries.find { it.mnt_dir.toString() == "/" }
            assertNotNull("Root mount entry should exist in mounts table", rootEntry)
        }
    }
}
