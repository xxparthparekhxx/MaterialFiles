/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import java8.nio.file.Paths
import me.zhanghai.android.files.provider.archive.isArchivePath
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.FtpFile
import org.apache.ftpserver.ftplet.User
import java.net.URI

class ProviderFileSystemView(private val user: User) : FileSystemView {
    private val multiRoot = FtpServerRoots.isMultiRootEnabled(user)
    private val homeDirectory: FtpFile
    private var workingDirectory: FtpFile

    init {
        homeDirectory = if (multiRoot) {
            RootIndexFtpFile(user)
        } else {
            val homeDirectoryPath = Paths.get(URI.create(user.homeDirectory))
            ProviderFtpFile(
                homeDirectoryPath, homeDirectoryPath.relativize(homeDirectoryPath), user
            )
        }
        workingDirectory = homeDirectory
    }

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
        if (!multiRoot) {
            val home = homeDirectory as ProviderFtpFile
            val isAbsolute = fileString.startsWith("/")
            val homeDirectoryPath = home.physicalFile as java8.nio.file.Path
            val parentPath =
                if (isAbsolute) {
                    homeDirectoryPath
                } else {
                    (workingDirectory as ProviderFtpFile).physicalFile as java8.nio.file.Path
                }
            val relativeFileString = if (isAbsolute) fileString.drop(1) else fileString
            val filePath = parentPath.resolve(relativeFileString).normalize()
            if (!filePath.startsWith(homeDirectoryPath)) {
                return homeDirectory
            }
            return ProviderFtpFile(filePath, homeDirectoryPath.relativize(filePath), user)
        }
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
        val root =
            FtpServerRoots.get(user).find { it.name == parts.first() } ?: return homeDirectory
        val rest = parts.drop(1)
        val filePath = rest.fold(root.path) { path, name -> path.resolve(name) }.normalize()
        if (!filePath.startsWith(root.path)) {
            return homeDirectory
        }
        val relativePath =
            rest.fold(Paths.get(root.name) as java8.nio.file.Path) { path, name ->
                path.resolve(name)
            }
        return ProviderFtpFile(filePath, relativePath, user)
    }

    override fun isRandomAccessible(): Boolean {
        if (!multiRoot) {
            val home = homeDirectory as ProviderFtpFile
            // TODO: Better way of determining if the provider is random accessible.
            return !(home.physicalFile as java8.nio.file.Path).isArchivePath
        }
        return FtpServerRoots.get(user).none { it.path.isArchivePath }
    }

    override fun dispose() {}
}
