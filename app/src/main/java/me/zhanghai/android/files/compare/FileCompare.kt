/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.compare

import androidx.annotation.StringRes
import java.io.IOException
import java8.nio.file.Path

enum class FolderCompareStatus {
    ONLY_LEFT,
    ONLY_RIGHT,
    DIFFERENT
}

enum class ContentCompareState {
    UNKNOWN,
    COMPARING,
    SAME,
    DIFFERENT,
    ERROR
}

data class FolderCompareFileInfo(
    val path: Path,
    val isDirectory: Boolean,
    val size: Long,
    val lastModifiedMillis: Long
)

data class FolderCompareEntry(
    val relativePath: String,
    val left: FolderCompareFileInfo?,
    val right: FolderCompareFileInfo?,
    val status: FolderCompareStatus,
    val contentState: ContentCompareState = ContentCompareState.UNKNOWN
)

sealed class FileCompareResult {
    data class Folders(val entries: List<FolderCompareEntry>) : FileCompareResult()

    data class Text(val diff: String, val isIdentical: Boolean) : FileCompareResult()
}

class FileCompareException(
    @StringRes val messageRes: Int,
    vararg val formatArgs: Any?
) : IOException()
