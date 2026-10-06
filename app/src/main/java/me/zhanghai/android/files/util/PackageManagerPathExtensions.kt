/*
 * Copyright (c) 2022 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java8.nio.file.Path
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.getPackageArchiveInfoCompat
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.document.isDocumentPath
import me.zhanghai.android.files.provider.document.resolver.DocumentResolver
import me.zhanghai.android.files.provider.linux.isLinuxPath
import java.io.Closeable
import java.io.File
import java.io.IOException

val Path.isGetPackageArchiveInfoCompatible: Boolean
    get() = isLinuxPath || isDocumentPath

fun PackageManager.getPackageArchiveInfoCompat(
    path: Path,
    flags: Int
): Pair<PackageInfo?, Closeable?> {
    val archiveFilePath: String
    val closeable: Closeable?
    when {
        path.isLinuxPath -> {
            archiveFilePath = path.toFile().path
            closeable = null
        }
        path.isDocumentPath -> {
            val pfd = DocumentResolver.openParcelFileDescriptor(path as DocumentResolver.Path, "r")
            archiveFilePath = "/proc/self/fd/${pfd.fd}"
            closeable = pfd
        }
        else -> throw IllegalArgumentException(path.toString())
    }
    val directInfo = try {
        readPackageArchiveInfo(archiveFilePath, flags)
    } catch (e: Exception) {
        closeable?.close()
        throw e
    }
    if (directInfo?.applicationInfo != null) {
        return directInfo to closeable
    }
    closeable?.close()
    // The package parser cannot read some external-storage paths, including a document fd.
    // A private copy is readable.
    val tempFile = try {
        copyToCache(path)
    } catch (e: IOException) {
        return null to null
    }
    return try {
        readPackageArchiveInfo(tempFile.path, flags) to TempFileCloseable(tempFile)
    } catch (e: Exception) {
        tempFile.delete()
        throw e
    }
}

private fun PackageManager.readPackageArchiveInfo(archiveFilePath: String, flags: Int): PackageInfo? =
    getPackageArchiveInfoCompat(archiveFilePath, flags)?.apply {
        applicationInfo?.apply {
            sourceDir = archiveFilePath
            publicSourceDir = archiveFilePath
        }
    }

@Throws(IOException::class)
private fun copyToCache(path: Path): File {
    val file = File.createTempFile("apk-", ".apk", application.cacheDir)
    try {
        path.newInputStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.setReadable(true, false)
    } catch (e: IOException) {
        file.delete()
        throw e
    }
    return file
}

private class TempFileCloseable(private val file: File) : Closeable {
    override fun close() {
        file.delete()
    }
}
