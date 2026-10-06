/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive.archiver

import androidx.preference.PreferenceManager
import java8.nio.channels.SeekableByteChannel
import java8.nio.charset.StandardCharsets
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.common.DelegateForceableSeekableByteChannel
import me.zhanghai.android.files.provider.common.DelegateInputStream
import me.zhanghai.android.files.provider.common.DelegateNonForceableSeekableByteChannel
import me.zhanghai.android.files.provider.common.ForceableChannel
import me.zhanghai.android.files.provider.common.PosixFileMode
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.provider.common.newByteChannel
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.root.isRunningAsRoot
import me.zhanghai.android.files.provider.root.rootContext
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

object ArchiveReader {
    @Throws(IOException::class)
    fun readEntries(
        file: Path,
        passwords: List<String>,
        rootPath: Path
    ): Pair<Map<Path, ReadArchive.Entry>, Map<Path, List<Path>>> {
        val entries = mutableMapOf<Path, ReadArchive.Entry>()
        val rawEntries = readEntries(file, passwords)
        for (entry in rawEntries) {
            var path = rootPath.resolve(entry.name)
            // Normalize an absolute path to prevent path traversal attack.
            if (!path.isAbsolute) {
                // TODO: Will this actually happen?
                throw AssertionError("Path must be absolute: $path")
            }
            if (path.nameCount > 0) {
                path = path.normalize()
                if (path.nameCount == 0) {
                    // Don't allow a path to become the root path only after normalization.
                    continue
                }
            } else {
                if (!entry.isDirectory) {
                    // Ignore a root path that's not a directory
                    continue
                }
            }
            entries.getOrPut(path) { entry }
        }
        entries.getOrPut(rootPath) { createDirectoryEntry("") }
        val tree = mutableMapOf<Path, MutableList<Path>>()
        tree[rootPath] = mutableListOf()
        val paths = entries.keys.toList()
        for (path in paths) {
            var path = path
            while (true) {
                val parentPath = path.parent ?: break
                val entry = entries[path]!!
                if (entry.isDirectory) {
                    tree.getOrPut(path) { mutableListOf() }
                }
                tree.getOrPut(parentPath) { mutableListOf() }.add(path)
                val parentEntry = entries[parentPath]
                if (parentEntry != null) {
                    // A 0-byte file entry can occupy the same path as a directory (common when a
                    // zip stores "name/" plus "name/file"). Keep the directory so it can be opened.
                    if (!parentEntry.isDirectory) {
                        entries[parentPath] = createDirectoryEntry(parentEntry.name.trimEnd('/'))
                        tree.getOrPut(parentPath) { mutableListOf() }
                    }
                    break
                }
                entries[parentPath] = createDirectoryEntry(parentPath.toString())
                path = parentPath
            }
        }
        return entries to tree
    }

    private fun createDirectoryEntry(name: String): ReadArchive.Entry {
        require(!name.endsWith("/")) { "name $name should not end with a slash" }
        return ReadArchive.Entry(
            name, false, null, null, null, PosixFileType.DIRECTORY, 0, null, null,
            PosixFileMode.DIRECTORY_DEFAULT, null
        )
    }

    @Throws(IOException::class)
    private fun readEntries(file: Path, passwords: List<String>): List<ReadArchive.Entry> {
        val charset = archiveFileNameCharset
        val (archive, closeable) = openArchive(file, passwords)
        return closeable.use {
            buildList {
                while (true) {
                    this += archive.readEntry(charset) ?: break
                }
            }
        }
    }

    private val sequentialLock = Any()

    private var sequentialArchive: SequentialArchive? = null

    @Throws(IOException::class)
    fun newInputStream(file: Path, passwords: List<String>, entry: ReadArchive.Entry): InputStream? {
        val charset = archiveFileNameCharset
        synchronized(sequentialLock) {
            // A gzip or xz archive cannot seek, so reopening it decompresses from the start.
            // Keep the last archive open and continue when the next entry is still ahead.
            continueSequential(file, passwords, charset, entry.name)?.let { return it }
            val (archive, closeable) = openArchive(file, passwords)
            var successful = false
            try {
                if (!archive.findEntry(entry.name, charset)) {
                    return null
                }
                val sequential = sequentialArchive
                if (sequential != null && !sequential.inUse) {
                    dropSequential(sequential)
                }
                if (sequentialArchive == null) {
                    val opened = SequentialArchive(file, passwords, charset, archive, closeable)
                    opened.inUse = true
                    sequentialArchive = opened
                    successful = true
                    return SequentialEntryInputStream(opened)
                }
                successful = true
                return CloseableInputStream(archive.newDataInputStream(), closeable)
            } finally {
                if (!successful) {
                    closeable.close()
                }
            }
        }
    }

    private fun continueSequential(
        file: Path,
        passwords: List<String>,
        charset: Charset,
        entryName: String
    ): InputStream? {
        val sequential = sequentialArchive ?: return null
        if (sequential.inUse) {
            return null
        }
        if (sequential.file != file || sequential.passwords != passwords ||
            sequential.charset != charset
        ) {
            dropSequential(sequential)
            return null
        }
        val found = try {
            sequential.archive.findEntry(entryName, charset)
        } catch (e: ArchiveException) {
            e.printStackTrace()
            dropSequential(sequential)
            return null
        }
        if (!found) {
            dropSequential(sequential)
            return null
        }
        sequential.inUse = true
        return SequentialEntryInputStream(sequential)
    }

    private fun dropSequential(sequential: SequentialArchive) {
        if (sequentialArchive === sequential) {
            sequentialArchive = null
        }
        try {
            sequential.closeable.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun openArchive(
        file: Path,
        passwords: List<String>
    ): Pair<ReadArchive, ArchiveCloseable> {
        val channel = try {
            CacheSizeSeekableByteChannel(file.newByteChannel())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        if (channel != null) {
            var successful = false
            try {
                val archive = ReadArchive(channel, passwords)
                successful = true
                return archive to ArchiveCloseable(archive, channel)
            } finally {
                if (!successful) {
                    channel.close()
                }
            }
        }
        val inputStream = file.newInputStream()
        var successful = false
        try {
            val archive = ReadArchive(inputStream, passwords)
            successful = true
            return archive to ArchiveCloseable(archive, inputStream)
        } finally {
            if (!successful) {
                inputStream.close()
            }
        }
    }

    // size() may be called repeatedly for ZIP and 7Z, so make it cached to improve performance.
    private fun CacheSizeSeekableByteChannel(channel: SeekableByteChannel): SeekableByteChannel =
        if (channel is ForceableChannel) {
            CacheSizeForceableSeekableByteChannel(channel)
        } else {
            CacheSizeNonForceableSeekableByteChannel(channel)
        }

    private class CacheSizeNonForceableSeekableByteChannel(
        channel: SeekableByteChannel
    ) : DelegateNonForceableSeekableByteChannel(channel) {
        private val size: Long by lazy { super.size() }

        override fun size(): Long = size
    }

    private class CacheSizeForceableSeekableByteChannel(
        channel: SeekableByteChannel
    ) : DelegateForceableSeekableByteChannel(channel) {
        private val size: Long by lazy { super.size() }

        override fun size(): Long = size
    }

    private val archiveFileNameCharset: Charset
        get() =
            if (isRunningAsRoot) {
                try {
                    val sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(rootContext)
                    val key = rootContext.getString(R.string.pref_key_archive_file_name_encoding)
                    val defaultValue = rootContext.getString(
                        R.string.pref_default_value_archive_file_name_encoding
                    )
                    Charset.forName(sharedPreferences.getString(key, defaultValue)!!)
                } catch (e: Exception) {
                    e.printStackTrace()
                    StandardCharsets.UTF_8
                }
            } else {
                Charset.forName(Settings.ARCHIVE_FILE_NAME_ENCODING.valueCompat)
            }

    private class ArchiveCloseable(
        private val archive: ReadArchive,
        private val closeable: Closeable
    ) : Closeable {
        override fun close() {
            @Suppress("ConvertTryFinallyToUseCall")
            try {
                archive.close()
            } finally {
                closeable.close()
            }
        }
    }

    private class SequentialArchive(
        val file: Path,
        val passwords: List<String>,
        val charset: Charset,
        val archive: ReadArchive,
        val closeable: Closeable
    ) {
        var inUse = false
    }

    private class SequentialEntryInputStream(
        private val sequential: SequentialArchive
    ) : DelegateInputStream(sequential.archive.newDataInputStream()) {
        private var closed = false

        @Throws(IOException::class)
        override fun close() {
            if (closed) {
                return
            }
            closed = true
            super.close()
            synchronized(sequentialLock) {
                sequential.inUse = false
            }
        }
    }

    private class CloseableInputStream(
        inputStream: InputStream,
        private val closeable: Closeable
    ) : DelegateInputStream(inputStream) {
        @Throws(IOException::class)
        override fun close() {
            super.close()

            closeable.close()
        }
    }
}
