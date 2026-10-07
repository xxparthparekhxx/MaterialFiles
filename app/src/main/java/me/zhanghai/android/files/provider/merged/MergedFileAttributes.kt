/*
 * Copyright (c) 2025 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.merged

import android.os.Parcelable
import java8.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.provider.common.AbstractPosixFileAttributes
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.FileTimeParceler
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.provider.common.PosixGroup
import me.zhanghai.android.files.provider.common.PosixUser

@Parcelize
internal class MergedFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val lastAccessTime: @WriteWith<FileTimeParceler> FileTime,
    override val creationTime: @WriteWith<FileTimeParceler> FileTime,
    override val type: PosixFileType,
    override val size: Long,
    override val fileKey: Parcelable,
    override val owner: PosixUser?,
    override val group: PosixGroup?,
    override val mode: Set<PosixFileModeBit>?,
    override val seLinuxContext: ByteString?
) : AbstractPosixFileAttributes() {
    companion object {
        fun from(fileSystem: MergedFileSystem): MergedFileAttributes {
            val now = FileTime.fromMillis(System.currentTimeMillis())
            return MergedFileAttributes(
                now, now, now, PosixFileType.DIRECTORY, 0,
                MergedFileKey(fileSystem.sources), null, null, null, null
            )
        }
    }
}
