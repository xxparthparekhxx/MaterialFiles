/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.os.Parcelable
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.provider.merged.MergedFileSystemProvider
import me.zhanghai.android.files.util.ParcelableListParceler
import me.zhanghai.android.files.util.takeIfNotEmpty
import java.util.Random

@Parcelize
// @see https://youtrack.jetbrains.com/issue/KT-24842
// @Parcelize throws IllegalAccessError if the primary constructor is private.
data class BookmarkDirectory internal constructor(
    val id: Long,
    val customName: String?,
    val paths: @WriteWith<ParcelableListParceler> List<Path>
) : Parcelable {
    // We cannot simply use path.hashCode() as ID because different bookmark directories may have
    // the same path.
    constructor(customName: String?, path: Path) : this(Random().nextLong(), customName, listOf(path))

    /**
     * The directory to browse for this bookmark. A single directory is browsed directly, and
     * multiple directories are merged into a single read-only directory.
     */
    val path: Path
        get() = if (paths.size == 1) {
            paths[0]
        } else {
            MergedFileSystemProvider.getOrNewFileSystem(paths).rootDirectory
        }

    val defaultName: String
        get() = paths.first().name

    val name: String
        get() = customName?.takeIfNotEmpty() ?: defaultName
}
