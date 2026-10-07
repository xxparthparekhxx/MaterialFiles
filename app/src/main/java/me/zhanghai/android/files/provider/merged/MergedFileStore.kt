/*
 * Copyright (c) 2025 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.merged

import java8.nio.file.attribute.FileAttributeView
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.guessFromPath
import me.zhanghai.android.files.provider.common.PosixFileStore
import java.io.IOException

internal class MergedFileStore(private val fileSystem: MergedFileSystem) : PosixFileStore() {
    override fun refresh() {}

    override fun name(): String =
        fileSystem.sources.firstOrNull()?.toString() ?: MergedFileSystemProvider.getScheme()

    override fun type(): String = MimeType.guessFromPath(name()).value

    override fun isReadOnly(): Boolean = true

    @Throws(IOException::class)
    override fun setReadOnly(readOnly: Boolean) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getTotalSpace(): Long = 0

    override fun getUsableSpace(): Long = 0

    override fun getUnallocatedSpace(): Long = 0

    override fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean =
        MergedFileSystemProvider.supportsFileAttributeView(type)

    override fun supportsFileAttributeView(name: String): Boolean =
        name in MergedFileAttributeView.SUPPORTED_NAMES
}
