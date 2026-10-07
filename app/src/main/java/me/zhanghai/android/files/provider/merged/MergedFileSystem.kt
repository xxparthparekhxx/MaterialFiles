/*
 * Copyright (c) 2025 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.merged

import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.ClosedFileSystemException
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.NotDirectoryException
import java8.nio.file.Path
import java8.nio.file.PathMatcher
import java8.nio.file.WatchService
import java8.nio.file.attribute.UserPrincipalLookupService
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.compat.writeParcelableListCompat
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringBuilder
import me.zhanghai.android.files.provider.common.ByteStringListPathCreator
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.util.readParcelableListCompat
import java.io.IOException

internal class MergedFileSystem(
    private val provider: MergedFileSystemProvider,
    val sources: List<Path>
) : FileSystem(), ByteStringListPathCreator, Parcelable {
    val rootDirectory = MergedPath(this, SEPARATOR_BYTE_STRING)

    init {
        if (!rootDirectory.isAbsolute) {
            throw AssertionError("Root directory $rootDirectory must be absolute")
        }
        if (rootDirectory.nameCount != 0) {
            throw AssertionError("Root directory $rootDirectory must contain no names")
        }
    }

    val defaultDirectory: MergedPath
        get() = rootDirectory

    private val lock = Any()

    private var isOpen = true

    @Throws(IOException::class)
    fun getDirectoryChildren(directory: Path): List<Path> =
        synchronized(lock) {
            if (!isOpen) {
                throw ClosedFileSystemException()
            }
            if (directory != rootDirectory) {
                throw NotDirectoryException(directory.toString())
            }
            // The listing contains the real paths of the sources' top-level entries, so all
            // file operations on them behave like in a regular directory. A source that cannot
            // be listed is skipped.
            val children = mutableListOf<Path>()
            for (source in sources) {
                try {
                    source.newDirectoryStream().use { stream ->
                        for (child in stream) {
                            children.add(child)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            children
        }

    override fun provider(): FileSystemProvider = provider

    override fun close() {
        synchronized(lock) {
            if (!isOpen) {
                return
            }
            provider.removeFileSystem(this)
            isOpen = false
        }
    }

    override fun isOpen(): Boolean = synchronized(lock) { isOpen }

    override fun isReadOnly(): Boolean = true

    override fun getSeparator(): String = SEPARATOR_STRING

    override fun getRootDirectories(): Iterable<Path> = listOf(rootDirectory)

    override fun getFileStores(): Iterable<FileStore> {
        // TODO
        throw UnsupportedOperationException()
    }

    override fun supportedFileAttributeViews(): Set<String> =
        MergedFileAttributeView.SUPPORTED_NAMES

    override fun getPath(first: String, vararg more: String): MergedPath {
        val path = ByteStringBuilder(first.toByteString())
            .apply { more.forEach { append(SEPARATOR).append(it.toByteString()) } }
            .toByteString()
        return MergedPath(this, path)
    }

    override fun getPath(first: ByteString, vararg more: ByteString): MergedPath {
        val path = ByteStringBuilder(first)
            .apply { more.forEach { append(SEPARATOR).append(it) } }
            .toByteString()
        return MergedPath(this, path)
    }

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
        throw UnsupportedOperationException()
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newWatchService(): WatchService {
        // TODO
        throw UnsupportedOperationException()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        other as MergedFileSystem
        return sources == other.sources
    }

    override fun hashCode(): Int = sources.hashCode()

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        @Suppress("UNCHECKED_CAST")
        dest.writeParcelableListCompat(sources as List<Parcelable?>, flags)
    }

    companion object {
        const val SEPARATOR = '/'.code.toByte()
        private val SEPARATOR_BYTE_STRING = SEPARATOR.toByteString()
        private const val SEPARATOR_STRING = SEPARATOR.toInt().toChar().toString()

        @JvmField
        val CREATOR = object : Parcelable.Creator<MergedFileSystem> {
            override fun createFromParcel(source: Parcel): MergedFileSystem {
                @Suppress("UNCHECKED_CAST")
                val sources = source.readParcelableListCompat<Parcelable>() as List<Path>
                return MergedFileSystemProvider.getOrNewFileSystem(sources)
            }

            override fun newArray(size: Int): Array<MergedFileSystem?> = arrayOfNulls(size)
        }
    }
}
