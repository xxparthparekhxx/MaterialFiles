/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.upnp

import android.os.Parcelable
import java8.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.provider.common.AbstractBasicFileAttributes
import me.zhanghai.android.files.provider.common.BasicFileType
import me.zhanghai.android.files.provider.common.FileTimeParceler

@Parcelize
internal data class UpnpFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val lastAccessTime: @WriteWith<FileTimeParceler> FileTime,
    override val creationTime: @WriteWith<FileTimeParceler> FileTime,
    override val type: BasicFileType,
    override val size: Long,
    override val fileKey: Parcelable
) : AbstractBasicFileAttributes() {
    companion object {
        fun from(item: UpnpItem): UpnpFileAttributes {
            val lastModifiedTime = FileTime.fromMillis(item.lastModified ?: 0L)
            return UpnpFileAttributes(
                lastModifiedTime, lastModifiedTime, lastModifiedTime,
                if (item.isContainer) BasicFileType.DIRECTORY else BasicFileType.REGULAR_FILE,
                item.size, UpnpFileKey(item.id)
            )
        }
    }

    @Parcelize
    data class UpnpFileKey(val id: String) : Parcelable
}
