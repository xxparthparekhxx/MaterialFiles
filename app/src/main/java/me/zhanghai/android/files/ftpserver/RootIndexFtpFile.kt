/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import java8.nio.file.Paths
import org.apache.ftpserver.ftplet.FtpFile
import org.apache.ftpserver.ftplet.User
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class RootIndexFtpFile(private val user: User) : FtpFile {
    override fun getAbsolutePath(): String = "/"

    override fun getName(): String = "/"

    override fun isHidden(): Boolean = false

    override fun isDirectory(): Boolean = true

    override fun isFile(): Boolean = false

    override fun doesExist(): Boolean = true

    override fun isReadable(): Boolean = true

    override fun isWritable(): Boolean = false

    override fun isRemovable(): Boolean = false

    override fun getOwnerName(): String = "user"

    override fun getGroupName(): String = "group"

    override fun getLinkCount(): Int = 3

    override fun getLastModified(): Long = 0

    override fun setLastModified(time: Long): Boolean = false

    override fun getSize(): Long = 0

    override fun getPhysicalFile(): Any? = null

    override fun mkdir(): Boolean = false

    override fun delete(): Boolean = false

    override fun move(destination: FtpFile): Boolean = false

    override fun listFiles(): List<FtpFile> =
        FtpServerRoots.get(user).map { root ->
            ProviderFtpFile(root.path, Paths.get(root.name), user)
        }.sorted()

    @Throws(IOException::class)
    override fun createOutputStream(offset: Long): OutputStream =
        throw IOException("Not writable: $absolutePath")

    @Throws(IOException::class)
    override fun createInputStream(offset: Long): InputStream =
        throw IOException("Is a directory: $absolutePath")
}
