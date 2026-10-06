/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.file.AtomicMoveNotSupportedException
import java8.nio.file.CopyOption
import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.LinkOption
import java8.nio.file.NoSuchFileException
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.StandardCopyOption
import java8.nio.file.StandardOpenOption
import java8.nio.file.attribute.BasicFileAttributeView
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileTime
import java.io.IOException

internal object ForeignCopyMove {
    @Throws(IOException::class)
    fun copy(source: Path, target: Path, vararg options: CopyOption) {
        val copyOptions = options.toCopyOptions()
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        val linkOptions = if (copyOptions.noFollowLinks) {
            arrayOf(LinkOption.NOFOLLOW_LINKS)
        } else {
            emptyArray()
        }
        val sourceAttributes = source.readAttributes(BasicFileAttributes::class.java, *linkOptions)
        if (!(sourceAttributes.isRegularFile || sourceAttributes.isDirectory
                || sourceAttributes.isSymbolicLink)) {
            throw IOException("Cannot copy special file to foreign provider")
        }
        if (!copyOptions.replaceExisting && target.exists(LinkOption.NOFOLLOW_LINKS)) {
            throw FileAlreadyExistsException(source.toString(), target.toString(), null)
        }
        when {
            sourceAttributes.isRegularFile -> {
                val lastModifiedTime = sourceAttributes.lastModifiedTime()
                    .takeIf { it != FileTime::class.EPOCH }
                val mtimeOption = lastModifiedTime?.let {
                    MtimeOpenOption(it.toInstant().epochSecond)
                }
                // A known size lets protocols such as WebDAV send a Content-Length header.
                val contentLengthOption = sourceAttributes.size().takeIf { it >= 0 }
                    ?.let { ContentLengthOpenOption(it) }
                val createOptions = listOfNotNull(
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    mtimeOption,
                    contentLengthOption
                ).toTypedArray()
                val truncateOptions = listOfNotNull(
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    mtimeOption,
                    contentLengthOption
                ).toTypedArray()
                // Deleting a file on secondary storage and creating another with the same name
                // fails on every other attempt: the document provider still lists the old name, so
                // CREATE_NEW refuses, and the following attempt succeeds once that listing catches
                // up. Overwrite the existing file in place instead.
                if (copyOptions.replaceExisting && target.exists(LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        copyRegularFile(source, target, copyOptions, truncateOptions, false)
                    } catch (e: IOException) {
                        try {
                            target.deleteIfExists()
                        } catch (deleteError: IOException) {
                            e.addSuppressed(deleteError)
                            throw e
                        }
                        try {
                            copyRegularFile(source, target, copyOptions, createOptions, true)
                        } catch (exists: FileAlreadyExistsException) {
                            exists.addSuppressed(e)
                            copyRegularFile(source, target, copyOptions, truncateOptions, false)
                        }
                    }
                } else {
                    try {
                        copyRegularFile(source, target, copyOptions, createOptions, true)
                    } catch (exists: FileAlreadyExistsException) {
                        if (!copyOptions.replaceExisting) {
                            throw exists
                        }
                        copyRegularFile(source, target, copyOptions, truncateOptions, false)
                    }
                }
            }
            sourceAttributes.isDirectory -> {
                if (copyOptions.replaceExisting) {
                    target.deleteIfExists()
                }
                target.createDirectory()
                copyOptions.progressListener?.invoke(sourceAttributes.size())
            }
            sourceAttributes.isSymbolicLink -> {
                val sourceTarget = source.readSymbolicLink()
                try {
                    // Might throw UnsupportedOperationException, so we cannot delete beforehand.
                    target.createSymbolicLink(sourceTarget)
                } catch (e: FileAlreadyExistsException) {
                    if (!copyOptions.replaceExisting) {
                        throw e
                    }
                    target.deleteIfExists()
                    target.createSymbolicLink(sourceTarget)
                }
                copyOptions.progressListener?.invoke(sourceAttributes.size())
            }
            else -> throw AssertionError()
        }
        // We don't take error when copying attribute fatal, so errors will only be logged from
        // now on.
        val targetAttributeView = target.getFileAttributeView(BasicFileAttributeView::class.java)!!
        val lastModifiedTime = sourceAttributes.lastModifiedTime()
            .takeIf { it != FileTime::class.EPOCH }
        val lastAccessTime = if (copyOptions.copyAttributes) {
            sourceAttributes.lastAccessTime().takeIf { it != FileTime::class.EPOCH }
        } else {
            null
        }
        val creationTime = if (copyOptions.copyAttributes) {
            sourceAttributes.creationTime().takeIf { it != FileTime::class.EPOCH }
        } else {
            null
        }
        try {
            targetAttributeView.setTimes(lastModifiedTime, lastAccessTime, creationTime)
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: UnsupportedOperationException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun copyRegularFile(
        source: Path,
        target: Path,
        copyOptions: CopyOptions,
        outputOptions: Array<OpenOption>,
        deleteOnFailure: Boolean
    ) {
        val openOptions = if (copyOptions.noFollowLinks) {
            arrayOf(LinkOption.NOFOLLOW_LINKS)
        } else {
            emptyArray()
        }
        source.newInputStream(*openOptions).use { inputStream ->
            val outputStream = target.newOutputStream(*outputOptions)
            var successful = false
            try {
                inputStream.copyTo(
                    outputStream, copyOptions.progressIntervalMillis, copyOptions.progressListener
                )
                successful = true
            } finally {
                try {
                    outputStream.close()
                } finally {
                    if (!successful && deleteOnFailure) {
                        try {
                            target.deleteIfExists()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        } catch (e: UnsupportedOperationException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    fun move(source: Path, target: Path, vararg options: CopyOption) {
        val copyOptions = options.toCopyOptions()
        if (copyOptions.atomicMove) {
            throw AtomicMoveNotSupportedException(
                source.toString(), target.toString(),
                "Cannot move file atomically to foreign provider"
            )
        }
        val optionsForCopy = if (copyOptions.copyAttributes && copyOptions.noFollowLinks) {
            options
        } else {
            CopyOptions(
                copyOptions.replaceExisting, true, false, true, copyOptions.progressIntervalMillis,
                copyOptions.progressListener
            ).toArray()
        }
        copy(source, target, *optionsForCopy)
        try {
            source.delete()
        } catch (e: IOException) {
            if (e !is NoSuchFileException) {
                try {
                    target.delete()
                } catch (e2: IOException) {
                    e.addSuppressed(e2)
                } catch (e2: UnsupportedOperationException) {
                    e.addSuppressed(e2)
                }
            }
            throw e
        } catch (e: UnsupportedOperationException) {
            try {
                target.delete()
            } catch (e2: IOException) {
                e.addSuppressed(e2)
            } catch (e2: UnsupportedOperationException) {
                e.addSuppressed(e2)
            }
            throw e
        }
    }
}
