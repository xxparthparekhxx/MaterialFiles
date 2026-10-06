/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Parcelable
import java8.nio.file.Path
import java8.nio.file.Paths
import java.net.URI
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

@Parcelize
data class RecentFile(
    val uri: String,
    val name: String,
    val mimeType: String,
    val openedAt: Long
) : Parcelable

object RecentFiles {
    fun add(file: FileItem) {
        if (file.attributes.isDirectory) {
            return
        }
        val uri = try {
            file.path.toUri().toString()
        } catch (e: Exception) {
            return
        }
        val entry = RecentFile(
            uri, file.name, file.mimeType.value, System.currentTimeMillis()
        )
        val updated = listOf(entry) + Settings.RECENT_FILES.valueCompat.filter { it.uri != uri }
        Settings.RECENT_FILES.putValue(updated.take(MAX_ENTRIES))
    }

    fun remove(uri: String) {
        Settings.RECENT_FILES.putValue(
            Settings.RECENT_FILES.valueCompat.filter { it.uri != uri }
        )
    }

    fun clear() {
        Settings.RECENT_FILES.putValue(emptyList())
    }

    fun pathOf(entry: RecentFile): Path? =
        try {
            Paths.get(URI.create(entry.uri))
        } catch (e: Exception) {
            null
        }

    private const val MAX_ENTRIES = 50
}
