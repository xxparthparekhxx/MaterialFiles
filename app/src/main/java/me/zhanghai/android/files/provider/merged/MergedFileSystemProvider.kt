/*
 * Copyright (c) 2025 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.merged

import java8.nio.channels.FileChannel
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessDeniedException
import java8.nio.file.AccessMode
import java8.nio.file.CopyOption
import java8.nio.file.DirectoryStream
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.NotLinkException
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.ProviderMismatchException
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.FileSystemCache
import me.zhanghai.android.files.provider.common.IsDirectoryException
import me.zhanghai.android.files.provider.common.OpenOptions
import me.zhanghai.android.files.provider.common.PathListDirectoryStream
import me.zhanghai.android.files.provider.common.PathObservable
import me.zhanghai.android.files.provider.common.PathObservableProvider
import me.zhanghai.android.files.provider.common.ReadOnlyFileSystemException
import me.zhanghai.android.files.provider.common.Searchable
import me.zhanghai.android.files.provider.common.WalkFileTreeSearchable
import me.zhanghai.android.files.provider.common.decodedPathByteString
import me.zhanghai.android.files.provider.common.decodedQueryByteString
import me.zhanghai.android.files.provider.common.isSameFile
import me.zhanghai.android.files.provider.common.toAccessModes
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.common.toOpenOptions
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * Exposes the top levels of multiple directories as a single read-only directory. This backs
 * multi-directory bookmarks.
 */
object MergedFileSystemProvider : FileSystemProvider(), PathObservableProvider, Searchable {
    private const val SCHEME = "merged"

    private val fileSystems = FileSystemCache<List<Path>, MergedFileSystem>()

    override fun getScheme(): String = SCHEME

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        uri.requireSameScheme()
        val sources = uri.sources
        return fileSystems.create(sources) { newFileSystem(sources) }
    }

    internal fun getOrNewFileSystem(sources: List<Path>): MergedFileSystem =
        fileSystems.getOrCreate(sources) { newFileSystem(sources) }

    private fun newFileSystem(sources: List<Path>): MergedFileSystem = MergedFileSystem(this, sources)

    override fun getFileSystem(uri: URI): FileSystem {
        uri.requireSameScheme()
        return fileSystems[uri.sources]
    }

    internal fun removeFileSystem(fileSystem: MergedFileSystem) {
        fileSystems.remove(fileSystem.sources, fileSystem)
    }

    override fun getPath(uri: URI): Path {
        uri.requireSameScheme()
        val sources = uri.sources
        val path = uri.decodedQueryByteString ?: ByteString.EMPTY
        return getOrNewFileSystem(sources).getPath(path)
    }

    private fun URI.requireSameScheme() {
        val scheme = scheme
        require(scheme == SCHEME) { "URI scheme $scheme must be $SCHEME" }
    }

    private val URI.sources: List<Path>
        get() {
            val path = decodedPathByteString
                ?: throw IllegalArgumentException("URI must have a path")
            // Drop the first character which is always a slash, and split the source URIs.
            val string = path.toString().drop(1)
            return string.split(UNIT_SEPARATOR).map { URI.create(it) }.map { Paths.get(it) }
        }

    @Throws(IOException::class)
    override fun newInputStream(file: Path, vararg options: OpenOption): InputStream {
        file as? MergedPath ?: throw ProviderMismatchException(file.toString())
        options.toOpenOptions().checkForMerged(file.toString())
        throw IsDirectoryException(file.toString())
    }

    override fun newFileChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): FileChannel {
        file as? MergedPath ?: throw ProviderMismatchException(file.toString())
        options.toOpenOptions().checkForMerged(file.toString())
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        throw ReadOnlyFileSystemException(file.toString())
    }

    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel {
        file as? MergedPath ?: throw ProviderMismatchException(file.toString())
        options.toOpenOptions().checkForMerged(file.toString())
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        throw ReadOnlyFileSystemException(file.toString())
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        directory as? MergedPath ?: throw ProviderMismatchException(directory.toString())
        val children = directory.fileSystem.getDirectoryChildren(directory)
        return PathListDirectoryStream(children, filter)
    }

    @Throws(IOException::class)
    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        directory as? MergedPath ?: throw ProviderMismatchException(directory.toString())
        throw ReadOnlyFileSystemException(directory.toString())
    }

    @Throws(IOException::class)
    override fun createSymbolicLink(link: Path, target: Path, vararg attributes: FileAttribute<*>) {
        link as? MergedPath ?: throw ProviderMismatchException(link.toString())
        throw ReadOnlyFileSystemException(link.toString(), target.toString(), null)
    }

    @Throws(IOException::class)
    override fun createLink(link: Path, existing: Path) {
        link as? MergedPath ?: throw ProviderMismatchException(link.toString())
        throw ReadOnlyFileSystemException(link.toString(), existing.toString(), null)
    }

    @Throws(IOException::class)
    override fun delete(path: Path) {
        path as? MergedPath ?: throw ProviderMismatchException(path.toString())
        throw ReadOnlyFileSystemException(path.toString())
    }

    @Throws(IOException::class)
    override fun readSymbolicLink(link: Path): Path {
        link as? MergedPath ?: throw ProviderMismatchException(link.toString())
        throw NotLinkException(link.toString())
    }

    @Throws(IOException::class)
    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        source as? MergedPath ?: throw ProviderMismatchException(source.toString())
        target as? MergedPath ?: throw ProviderMismatchException(target.toString())
        throw ReadOnlyFileSystemException(source.toString(), target.toString(), null)
    }

    @Throws(IOException::class)
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        source as? MergedPath ?: throw ProviderMismatchException(source.toString())
        target as? MergedPath ?: throw ProviderMismatchException(target.toString())
        throw ReadOnlyFileSystemException(source.toString(), target.toString(), null)
    }

    override fun isSameFile(path: Path, path2: Path): Boolean {
        path as? MergedPath ?: throw ProviderMismatchException(path.toString())
        if (path == path2) {
            return true
        }
        if (path2 !is MergedPath) {
            return false
        }
        val fileSystem = path.fileSystem
        val sources = fileSystem.sources
        val otherSources = path2.fileSystem.sources
        if (sources.size != otherSources.size ||
            sources.zip(otherSources).any { (source, other) -> !source.isSameFile(other) }
        ) {
            return false
        }
        return path == fileSystem.getPath(path2.toString())
    }

    override fun isHidden(path: Path): Boolean {
        path as? MergedPath ?: throw ProviderMismatchException(path.toString())
        return false
    }

    override fun getFileStore(path: Path): FileStore {
        path as? MergedPath ?: throw ProviderMismatchException(path.toString())
        return MergedFileStore(path.fileSystem)
    }

    @Throws(IOException::class)
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        path as? MergedPath ?: throw ProviderMismatchException(path.toString())
        val accessModes = modes.toAccessModes()
        if (accessModes.write || accessModes.execute) {
            throw AccessDeniedException(path.toString())
        }
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? {
        path as? MergedPath ?: throw ProviderMismatchException(path.toString())
        if (!supportsFileAttributeView(type)) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path) as V
    }

    internal fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean =
        type.isAssignableFrom(MergedFileAttributeView::class.java)

    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        path as? MergedPath ?: throw ProviderMismatchException(path.toString())
        if (!type.isAssignableFrom(MergedFileAttributes::class.java)) {
            throw UnsupportedOperationException(type.toString())
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path).readAttributes() as A
    }

    private fun getFileAttributeView(path: MergedPath): MergedFileAttributeView =
        MergedFileAttributeView(path)

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> {
        path as? MergedPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        path as? MergedPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun observe(path: Path, intervalMillis: Long): PathObservable {
        path as? MergedPath ?: throw ProviderMismatchException(path.toString())
        return MergedPathObservable(path.fileSystem, intervalMillis)
    }

    @Throws(IOException::class)
    override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
        directory as? MergedPath ?: throw ProviderMismatchException(directory.toString())
        // Search each source sequentially, so the listener still only receives batches from one
        // thread.
        for (source in directory.fileSystem.sources) {
            WalkFileTreeSearchable.search(source, query, intervalMillis, listener)
        }
    }

    private const val UNIT_SEPARATOR = 0x1F.toChar()
}

private fun OpenOptions.checkForMerged(file: String? = null) {
    if (write || append || truncateExisting || create || createNew || deleteOnClose) {
        throw ReadOnlyFileSystemException(file)
    }
}
