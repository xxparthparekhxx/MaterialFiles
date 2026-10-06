/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.provider.archive.isArchivePath
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.FtpFile
import org.apache.ftpserver.ftplet.User

class ProviderFileSystemView(private val user: User) : FileSystemView {
    private val homeDirectory: FtpFile = RootIndexFtpFile(user)
    private var workingDirectory: FtpFile = homeDirectory

    override fun getHomeDirectory(): FtpFile = homeDirectory

    override fun getWorkingDirectory(): FtpFile = workingDirectory

    override fun changeWorkingDirectory(directoryString: String): Boolean {
        val directory = getFile(directoryString)
        if (!directory.isDirectory) {
            return false
        }
        workingDirectory = directory
        return true
    }

    override fun getFile(fileString: String): FtpFile {
        val combined = if (fileString.startsWith("/")) {
            fileString
        } else {
            val base = workingDirectory.absolutePath.removePrefix("/")
            if (base.isEmpty()) fileString else "$base/$fileString"
        }
        val parts = ArrayDeque<String>()
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        if (parts.isEmpty()) {
            return homeDirectory
        }
        val root = FtpServerRoots.get().find { it.name == parts.first() } ?: return homeDirectory
        val rest = parts.drop(1)
        val filePath = rest.fold(root.path) { path, name -> path.resolve(name) }.normalize()
        if (!filePath.startsWith(root.path)) {
            return homeDirectory
        }
        val relativePath = rest.fold(Paths.get(root.name) as Path) { path, name -> path.resolve(name) }
        return ProviderFtpFile(filePath, relativePath, user)
    }

    override fun isRandomAccessible(): Boolean =
        FtpServerRoots.get().none { it.path.isArchivePath }

    override fun dispose() {}
}
