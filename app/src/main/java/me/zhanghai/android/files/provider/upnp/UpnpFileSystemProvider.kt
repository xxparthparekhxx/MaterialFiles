/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.upnp

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java8.nio.file.AccessMode
import java8.nio.file.CopyOption
import java8.nio.file.DirectoryStream
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.provider.common.PathListDirectoryStream
import me.zhanghai.android.files.provider.common.toByteString
import java.util.Locale

/**
 * A read-only file system provider for UPnP ContentDirectory services, which are used by DLNA
 * media servers.
 *
 * The path of a file is made of the titles of the items, so that the names shown to the user are
 * meaningful. The object ID of an item is found by browsing its parent and looking for the item
 * with the matching title.
 */
object UpnpFileSystemProvider : FileSystemProvider() {
    private const val SCHEME = "upnp"

    private val fileSystems = mutableMapOf<String, UpnpFileSystem>()

    private val devices = mutableMapOf<String, UpnpDevice>()

    private val lock = Any()

    override fun getScheme(): String = SCHEME

    override fun newFileSystem(uri: java.net.URI, env: Map<String, *>): FileSystem {
        uri.requireSameScheme()
        val location = uri.authority ?: throw IllegalArgumentException("URI must have an authority")
        synchronized(lock) {
            if (fileSystems[location] != null) {
                throw java8.nio.file.FileSystemAlreadyExistsException(location)
            }
            return newFileSystemLocked(location, "")
        }
    }

    override fun getFileSystem(uri: java.net.URI): FileSystem {
        uri.requireSameScheme()
        val location = uri.authority ?: throw IllegalArgumentException("URI must have an authority")
        return synchronized(lock) { fileSystems[location] }
            ?: throw java8.nio.file.FileSystemNotFoundException(location)
    }

    override fun getPath(uri: java.net.URI): Path {
        uri.requireSameScheme()
        val location = uri.authority ?: throw IllegalArgumentException("URI must have an authority")
        val path = uri.path ?: "/"
        return getOrNewFileSystem(location, "").getPath(path)
    }

    internal fun getOrNewFileSystem(location: String, friendlyName: String): UpnpFileSystem =
        synchronized(lock) { fileSystems[location] } ?: newFileSystemLocked(location, friendlyName)

    /**
     * Reads the description document of the device, caching the result.
     */
    @Throws(IOException::class)
    internal fun getDevice(location: String): UpnpDevice =
        synchronized(lock) { devices[location] } ?: UpnpClient.getDevice(location).also {
            synchronized(lock) { devices[location] = it }
        }

    private fun newFileSystemLocked(location: String, friendlyName: String): UpnpFileSystem {
        val fileSystem = UpnpFileSystem(this, location, friendlyName)
        synchronized(lock) { fileSystems[location] = fileSystem }
        return fileSystem
    }

    private fun java.net.URI.requireSameScheme() {
        val scheme = scheme
        require(scheme == SCHEME) { "URI scheme $scheme must be $SCHEME" }
    }

    @Throws(IOException::class)
    override fun newFileChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): java8.nio.channels.FileChannel {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): java8.nio.channels.SeekableByteChannel {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        val path = directory as? UpnpPath ?: throw ProviderMismatchException(directory.toString())
        val items = browse(path, resolveObjectId(path))
        val paths = mutableListOf<Path>()
        for (item in items) {
            val childPath = path.resolve(item.title.encodeTitle())
            if (filter.accept(childPath)) {
                paths.add(childPath)
            }
        }
        return PathListDirectoryStream(paths, filter)
    }

    @Throws(IOException::class)
    override fun newInputStream(file: Path, vararg options: OpenOption): InputStream {
        val path = file as? UpnpPath ?: throw ProviderMismatchException(file.toString())
        val item = findItem(path)
        val resourceUrl = item.resourceUrl
            ?: throw java8.nio.file.FileSystemException(path.toString(), null, "The item has no resource")
        val connection = URL(resourceUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = HTTP_TIMEOUT_MILLIS
        connection.readTimeout = HTTP_TIMEOUT_MILLIS
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            connection.disconnect()
            throw IOException("The server returned the status code $statusCode")
        }
        return connection.inputStream
    }

    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        val upnpPath = path as? UpnpPath ?: throw ProviderMismatchException(path.toString())
        val attributes = UpnpFileAttributes.from(findItem(upnpPath))
        if (!type.isInstance(attributes)) {
            throw UnsupportedOperationException()
        }
        return type.cast(attributes)
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? = null

    @Throws(IOException::class)
    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        throw UnsupportedOperationException()
    }

    override fun getFileStore(path: Path): FileStore {
        path as? UpnpPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        val upnpPath = path as? UpnpPath ?: throw ProviderMismatchException(path.toString())
        for (mode in modes) {
            if (mode != AccessMode.READ) {
                throw java8.nio.file.AccessDeniedException(path.toString(), null,
                    "The file system is read only")
            }
        }
        // This checks the existence as well.
        findItem(upnpPath)
    }

    override fun isSameFile(path: Path, path2: Path): Boolean =
        path == path2

    override fun isHidden(path: Path): Boolean = false

    @Throws(IOException::class)
    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun createSymbolicLink(link: Path, target: Path, vararg attributes: FileAttribute<*>) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun createLink(link: Path, existing: Path) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun delete(path: Path) {
        throw UnsupportedOperationException()
    }

    override fun readSymbolicLink(link: Path): Path {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newOutputStream(path: Path, vararg options: OpenOption): java.io.OutputStream {
        throw UnsupportedOperationException()
    }

    /**
     * Finds the item of the given path by browsing its parent.
     */
    @Throws(IOException::class)
    internal fun findItem(path: UpnpPath): UpnpItem {
        if (path.nameCount == 0) {
            // The root directory.
            return UpnpItem(
                (path.fileSystem as UpnpFileSystem).device.rootObjectId, (path.fileSystem as UpnpFileSystem).device.friendlyName, true, null,
                0, null, null
            )
        }
        val parent = path.parent ?: throw java8.nio.file.NoSuchFileException(path.toString())
        val parentObjectId = resolveObjectId(parent)
        val title = path.fileName?.toString() ?: throw java8.nio.file.NoSuchFileException(
            path.toString()
        )
        val items = browse(path, parentObjectId)
        return items.firstOrNull { it.title.encodeTitle() == title }
            ?: throw java8.nio.file.NoSuchFileException(path.toString())
    }

    /**
     * Finds the object ID of the given path by browsing its ancestors.
     */
    @Throws(IOException::class)
    private fun resolveObjectId(path: UpnpPath): String {
        val names = mutableListOf<String>()
        var current: UpnpPath? = path
        while (current != null && current.nameCount > 0) {
            names.add(0, current.fileName!!.toString())
            current = current.parent
        }
        var objectId = (path.fileSystem as UpnpFileSystem).device.rootObjectId
        for (name in names) {
            val items = browse(path, objectId)
            val item = items.firstOrNull { it.isContainer && it.title.encodeTitle() == name }
                ?: throw java8.nio.file.NoSuchFileException(path.toString())
            objectId = item.id
        }
        return objectId
    }

    @Throws(IOException::class)
    private fun browse(path: UpnpPath, objectId: String): List<UpnpItem> =
        UpnpClient.browse((path.fileSystem as UpnpFileSystem).device, objectId)

    private const val HTTP_TIMEOUT_MILLIS = 15 * 1000
}

/**
 * Encodes a title so that it can be used as a path segment, because a title may contain the path
 * separator.
 */
internal fun String.encodeTitle(): String =
    replace("%", "%25").replace("/", "%2F")

internal fun String.decodeTitle(): String =
    replace("%2F", "/").replace("%25", "%")
