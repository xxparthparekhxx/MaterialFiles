/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.util.ACTION_PICK_DIRECTORY_ESTRONGS
import me.zhanghai.android.files.util.ACTION_PICK_DIRECTORY_OI
import me.zhanghai.android.files.util.DIRECTORY_MIME_TYPES
import me.zhanghai.android.files.util.EXTRA_ABSOLUTE_PATH_OI
import me.zhanghai.android.files.util.EXTRA_DIR_PATH_OI
import me.zhanghai.android.files.util.EXTRA_TITLE_OI
import me.zhanghai.android.files.util.EXTRA_WRITEABLE_OI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickDirectoryIntentTest {
    @Test
    fun testIntentActionConstants() {
        assertEquals("org.openintents.action.PICK_DIRECTORY", ACTION_PICK_DIRECTORY_OI)
        assertEquals("com.estrongs.action.PICK_DIRECTORY", ACTION_PICK_DIRECTORY_ESTRONGS)
        assertEquals("org.openintents.extra.TITLE", EXTRA_TITLE_OI)
        assertEquals("org.openintents.extra.WRITE_ABLE", EXTRA_WRITEABLE_OI)
        assertEquals("org.openintents.extra.DIR_PATH", EXTRA_DIR_PATH_OI)
        assertEquals("org.openintents.extra.ABSOLUTE_PATH", EXTRA_ABSOLUTE_PATH_OI)
    }

    @Test
    fun testDirectoryMimeTypes() {
        assertTrue(DIRECTORY_MIME_TYPES.contains("inode/directory"))
        assertTrue(DIRECTORY_MIME_TYPES.contains("resource/folder"))
        assertTrue(DIRECTORY_MIME_TYPES.contains("vnd.android.cursor.dir/*"))
        assertTrue(DIRECTORY_MIME_TYPES.contains("vnd.android.document/directory"))
        assertTrue(DIRECTORY_MIME_TYPES.contains("vnd.android.document/root"))
        assertFalse(DIRECTORY_MIME_TYPES.contains("image/jpeg"))
        assertFalse(DIRECTORY_MIME_TYPES.contains("text/plain"))
        assertFalse(DIRECTORY_MIME_TYPES.contains("application/pdf"))
    }
}
