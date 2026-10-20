/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.linux

import android.system.OsConstants
import java8.nio.file.DirectoryIteratorException
import java8.nio.file.DirectoryStream
import java8.nio.file.Path
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.linux.syscall.Syscall
import me.zhanghai.android.files.provider.linux.syscall.SyscallException
import java.io.IOException

internal class LinuxDirectoryStream(
    private val directory: LinuxPath,
    private val dir: Long,
    private val filter: DirectoryStream.Filter<in Path>
) : DirectoryStream<Path> {
    private var iterator: PathIterator? = null

    private var isClosed = false

    private val lock = Any()

    // TODO: Should return Iterator<Path>
    override fun iterator(): MutableIterator<Path> {
        synchronized(lock) {
            check(!isClosed) { "This directory stream is closed" }
            check(iterator == null) { "The iterator has already been returned" }
            val iterator = PathIterator()
            this.iterator = iterator
            return iterator
        }
    }

    @Throws(IOException::class)
    override fun close() {
        synchronized(lock) {
            if (isClosed) {
                return
            }
            try {
                Syscall.closedir(dir)
            } catch (e: SyscallException) {
                throw e.toFileSystemException(directory.toString())
            }
            isClosed = true
        }
    }

    companion object {
        private val BYTE_STRING_DOT = ".".toByteString()
        private val BYTE_STRING_DOT_DOT = "..".toByteString()
    }

    // TODO: Try kotlin.sequences.iterator()?
    private inner class PathIterator : MutableIterator<Path> {
        private var nextPath: Path? = null

        private var isEndOfStreamReached = false

        override fun hasNext(): Boolean {
            synchronized(lock) {
                if (nextPath != null) {
                    return true
                }
                if (isEndOfStreamReached) {
                    return false
                }
                nextPath = getNextPathLocked()
                isEndOfStreamReached = nextPath == null
                return !isEndOfStreamReached
            }
        }

        private fun getNextPathLocked(): Path? {
                while (true) {
                    if (isClosed) {
                        return null
                    }
                    val dirent = try {
                        Syscall.readdir(dir)
                    } catch (e: SyscallException) {
                        // A FUSE/sdcardfs quirk can make readdir64 fail with "No such file or
                        // directory" mid-iteration on a perfectly valid, non-empty directory.
                        // Treat it as end of stream so the entries read so far are still shown
                        // instead of failing the whole listing.
                        if (e.errno == OsConstants.ENOENT) {
                            // The listing is incomplete, so log it to make the missing entries
                            // diagnosable.
                            e.printStackTrace()
                            return null
                        }
                        throw DirectoryIteratorException(
                            e.toFileSystemException(directory.toString())
                        )
                    } ?: return null
                    val name = dirent.d_name
                    if (name == BYTE_STRING_DOT || name == BYTE_STRING_DOT_DOT) {
                        continue
                    }
                    val path = directory.resolve(dirent.d_name)
                    val accepted = try {
                        filter.accept(path)
                    } catch (e: IOException) {
                        throw DirectoryIteratorException(e)
                    }
                    if (!accepted) {
                        continue
                    }
                    return path
                }
            }

        override fun next(): Path {
            synchronized(lock) {
                if (!hasNext()) {
                    throw NoSuchElementException()
                }
                val path = nextPath!!
                nextPath = null
                return path
            }
        }

        override fun remove() {
            throw UnsupportedOperationException()
        }
    }
}
