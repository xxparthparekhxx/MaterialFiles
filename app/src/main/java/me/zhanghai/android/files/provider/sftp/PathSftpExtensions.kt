/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp

import java8.nio.file.Path
import me.zhanghai.android.files.provider.sftp.client.Authority

fun Authority.createSftpRootPath(): Path =
    SftpFileSystemProvider.getOrNewFileSystem(this).rootDirectory

fun Authority.createSftpPath(path: String): Path {
    val fileSystem = SftpFileSystemProvider.getOrNewFileSystem(this)
    val remotePath = when (val trimmed = path.trim()) {
        "", "~", "." -> "."
        else -> {
            if (trimmed.startsWith("~/")) {
                trimmed.removePrefix("~/").ifEmpty { "." }
            } else {
                trimmed
            }
        }
    }
    return fileSystem.getPath(remotePath)
}
