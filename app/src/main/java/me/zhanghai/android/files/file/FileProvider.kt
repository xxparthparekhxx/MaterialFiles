/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.StrictMode
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessDeniedException
import java8.nio.file.FileSystemException
import java8.nio.file.FileSystemLoopException
import java8.nio.file.NoSuchFileException
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.StandardOpenOption
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.app.storageManager
import me.zhanghai.android.files.compat.ProxyFileDescriptorCallbackCompat
import me.zhanghai.android.files.compat.openProxyFileDescriptorCompat
import me.zhanghai.android.files.provider.common.InvalidFileNameException
import me.zhanghai.android.files.provider.common.IsDirectoryException
import me.zhanghai.android.files.provider.common.force
import me.zhanghai.android.files.provider.common.getLastModifiedTime
import me.zhanghai.android.files.provider.common.isDirectory
import me.zhanghai.android.files.provider.common.isForceable
import androidx.exifinterface.media.ExifInterface
import me.zhanghai.android.files.provider.common.newByteChannel
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.size
import me.zhanghai.android.files.provider.document.documentUri
import me.zhanghai.android.files.provider.document.isDocumentPath
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.provider.linux.syscall.SyscallException
import me.zhanghai.android.files.util.hasBits
import me.zhanghai.android.files.util.withoutPenaltyDeathOnNetwork
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.zhanghai.android.files.util.closeSafe

class FileProvider : ContentProvider() {
    private lateinit var callbackThread: HandlerThread
    private lateinit var callbackHandler: Handler

    override fun onCreate(): Boolean {
        callbackThread = HandlerThread("FileProvider.CallbackThread")
        callbackThread.start()
        callbackHandler = Handler(callbackThread.looper)
        return true
    }

    override fun shutdown() {
        callbackThread.quitSafely()
    }

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)

        if (info.exported) {
            throw SecurityException("Provider must not be exported")
        }
        if (!info.grantUriPermissions) {
            throw SecurityException("Provider must grant uri permissions")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? {
        // ContentProvider has already checked granted permissions
        val projectionColumns = projection ?: getDefaultProjection()
        val path = uri.fileProviderPath
        val columns = mutableListOf<String>()
        val values = mutableListOf<Any?>()
        StrictMode::class.withoutPenaltyDeathOnNetwork {
            runBlocking(Dispatchers.IO) {
                loop@ for (column in projectionColumns) {
                    @Suppress("DEPRECATION")
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> {
                            columns += column
                            values += path.fileName.toString()
                        }
                        OpenableColumns.SIZE -> {
                            val size = try {
                                path.size()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                            columns += column
                            values += size
                        }
                        MediaStore.MediaColumns.DATA -> {
                            val file = try {
                                path.toFile()
                            } catch (e: UnsupportedOperationException) {
                                continue@loop
                            }
                            columns += column
                            values += file.absolutePath
                        }
                        // TODO: We should actually implement a DocumentsProvider since we are handling
                        //  ACTION_OPEN_DOCUMENT.
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> {
                            columns += column
                            values += try {
                                if (path.isDirectory()) {
                                    DocumentsContract.Document.MIME_TYPE_DIR
                                } else {
                                    MimeType.guessFromPath(path.toString()).value
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                MimeType.guessFromPath(path.toString()).value
                            }
                        }
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> {
                            val lastModified = try {
                                path.getLastModifiedTime().toMillis()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                            columns += column
                            values += lastModified
                        }
                        DocumentsContract.Document.COLUMN_FLAGS -> {
                            columns += column
                            val readOnly = try {
                                path.fileSystem.isReadOnly
                            } catch (e: Exception) {
                                e.printStackTrace()
                                true
                            }
                            values += if (readOnly) {
                                0
                            } else {
                                DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                            }
                        }
                        MediaStore.Images.ImageColumns.ORIENTATION -> {
                            val orientation = try {
                                path.newInputStream().use { ExifInterface(it).rotationDegrees }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                0
                            }
                            columns += column
                            values += orientation
                        }
                    }
                }
            }
        }
        return MatrixCursor(columns.toTypedArray(), 1).apply {
            addRow(values)
        }
    }

    private fun getDefaultProjection(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            && Binder.getCallingUid() == Process.SYSTEM_UID) {
            // com.android.internal.app.ChooserActivity.queryResolver() in Q queries with a null
            // projection (meaning all columns) on main thread but only actually needs the display
            // name (and document flags). However if we do return all the columns, we may perform
            // network requests and crash it due to StrictMode. So just work around by only
            // returning the display name in this case.
            CHOOSER_ACTIVITY_DEFAULT_PROJECTION
        } else {
            DEFAULT_PROJECTION
        }

    override fun getType(uri: Uri): String? {
        val path = uri.fileProviderPath
        // Receiving apps query this to decide how to open shared content. Folders must report
        // the directory MIME type instead of falling through to octet-stream, which foreign
        // apps cannot use.
        return try {
            if (path.isDirectory()) {
                DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                MimeType.guessFromPath(path.toString()).value
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MimeType.guessFromPath(path.toString()).value
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("No external inserts")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("No external updates")
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("No external deletes")
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        // ContentProvider has already checked granted permissions
        val path = uri.fileProviderPath
        val modeBits = ParcelFileDescriptor.parseMode(mode)
        if (path.canOpenDirectly(modeBits)) {
            return ParcelFileDescriptor.open(path.toFile(), modeBits)
        }
        val options = modeBits.toOpenOptions()
        val channel = try {
            // Strict mode thread policy is passed through binder, but some apps (notably music
            // players) like to open file on their main thread.
            StrictMode::class.withoutPenaltyDeathOnNetwork {
                runBlocking(Dispatchers.IO) {
                    path.newByteChannel(options)
                }
            }
        } catch (e: Exception) {
            throw e.toFileNotFoundException()
        }
        val proxyMode = when {
            modeBits.hasBits(ParcelFileDescriptor.MODE_READ_WRITE) -> ParcelFileDescriptor.MODE_READ_WRITE
            modeBits.hasBits(ParcelFileDescriptor.MODE_WRITE_ONLY) -> ParcelFileDescriptor.MODE_WRITE_ONLY
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        return try {
            storageManager.openProxyFileDescriptorCompat(
                proxyMode, ChannelCallback(channel), callbackHandler
            )
        } catch (e: Exception) {
            channel.closeSafe()
            throw e.toFileNotFoundException()
        }
    }

    private fun Path.canOpenDirectly(mode: Int): Boolean {
        if (!isLinuxPath) {
            return false
        }
        val file = toFile()
        val isReadWrite = mode.hasBits(ParcelFileDescriptor.MODE_READ_WRITE)
        val isWriteOnly = mode.hasBits(ParcelFileDescriptor.MODE_WRITE_ONLY)
        val needRead = isReadWrite || !isWriteOnly
        val needWrite = isReadWrite || isWriteOnly
        return !((needRead && !file.canRead()) || (needWrite && !file.canWrite()))
    }

    private fun Int.toOpenOptions(): Set<OpenOption> =
        mutableSetOf<OpenOption>().apply {
            val isReadWrite = hasBits(ParcelFileDescriptor.MODE_READ_WRITE)
            val isWriteOnly = hasBits(ParcelFileDescriptor.MODE_WRITE_ONLY)
            if (isReadWrite || !isWriteOnly) {
                this += StandardOpenOption.READ
            }
            if (isReadWrite || isWriteOnly) {
                this += StandardOpenOption.WRITE
            }
            if (hasBits(ParcelFileDescriptor.MODE_CREATE)) {
                this += StandardOpenOption.CREATE
            }
            if (hasBits(ParcelFileDescriptor.MODE_TRUNCATE)) {
                this += StandardOpenOption.TRUNCATE_EXISTING
            }
            if (hasBits(ParcelFileDescriptor.MODE_APPEND)) {
                this += StandardOpenOption.APPEND
            }
        }

    private fun Exception.toFileNotFoundException(): FileNotFoundException =
        if (this is FileNotFoundException) {
            this
        } else {
            FileNotFoundException(message).apply { initCause(this@toFileNotFoundException) }
        }

    private class ChannelCallback(
        private val channel: SeekableByteChannel
    ) : ProxyFileDescriptorCallbackCompat() {
        private var offset = 0L
        private var released = false

        @Throws(ErrnoException::class)
        override fun onGetSize(): Long {
            ensureNotReleased()
            return try {
                channel.size()
            } catch (e: Exception) {
                throw e.toErrnoException()
            }
        }

        @Throws(ErrnoException::class)
        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            ensureNotReleased()
            if (this.offset != offset) {
                try {
                    channel.position(offset)
                } catch (e: Exception) {
                    throw e.toErrnoException()
                }
                this.offset = offset
            }
            val buffer = ByteBuffer.wrap(data, 0, size)
            // Unlike ReadableByteChannel which may not fill the buffer and returns -1 upon
            // end-of-stream, we need to read as much as we can unless end-of-stream is reached.
            while (buffer.hasRemaining()) {
                val channelSize = try {
                    channel.read(buffer)
                } catch (e: Exception) {
                    throw e.toErrnoException()
                }
                if (channelSize == -1) {
                    break
                }
                this.offset += channelSize
            }
            return (this.offset - offset).toInt()
        }

        @Throws(ErrnoException::class)
        override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
            ensureNotReleased()
            if (this.offset != offset) {
                try {
                    channel.position(offset)
                } catch (e: Exception) {
                    throw e.toErrnoException()
                }
                this.offset = offset
            }
            val buffer = ByteBuffer.wrap(data, 0, size)
            return try {
                channel.write(buffer)
            } catch (e: Exception) {
                throw e.toErrnoException()
            }.also { this.offset += it.toLong() }
        }

        @Throws(ErrnoException::class)
        override fun onFsync() {
            ensureNotReleased()
            if (channel.isForceable) {
                try {
                    channel.force(true)
                } catch (e: Exception) {
                    throw e.toErrnoException()
                }
            }
        }

        @Throws(ErrnoException::class)
        private fun ensureNotReleased() {
            if (released) {
                throw ErrnoException(null, OsConstants.EBADF)
            }
        }

        override fun onRelease() {
            if (released) {
                return
            }
            try {
                channel.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            released = true
        }

        private fun Throwable.toErrnoException(): ErrnoException {
            val cause = cause
            return if (this is FileSystemException && cause is SyscallException) {
                ErrnoException(cause.functionName, cause.errno, this)
            } else {
                val errno = when (this) {
                    is AccessDeniedException -> OsConstants.EPERM
                    is FileSystemLoopException -> OsConstants.ELOOP
                    is InvalidFileNameException -> OsConstants.EINVAL
                    is IsDirectoryException -> OsConstants.EISDIR
                    is NoSuchFileException -> OsConstants.ENOENT
                    is ClosedByInterruptException, is InterruptedIOException -> OsConstants.EINTR
                    else -> OsConstants.EIO
                }
                ErrnoException(message, errno, this)
            }
        }
    }

    companion object {
        private val DEFAULT_PROJECTION = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.DATA,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )

        private val CHOOSER_ACTIVITY_DEFAULT_PROJECTION = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_FLAGS
        )
    }
}

val Path.fileProviderUri: Uri
    get() {
        // Try avoid going through FUSE two times, which is bad for media playback.
        if (isDocumentPath) {
            try {
                return documentUri
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        // path() would encode this again, and the other app then cannot open the file.
        val uriPath = Uri.encode(toUri().toString())
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(BuildConfig.FILE_PROVIDIER_AUTHORITY)
            .encodedPath("/$uriPath")
            .build()
    }

private val Uri.fileProviderPath: Path
    get() {
        // Strip the prepended slash. A slash is always prepended because our Uri path starts with
        // our URI scheme, which can never start with a slash; but our Uri has an authority so its
        // path must start with a slash.
        // Uri.path is already decoded once. Links built the old way were encoded twice, so the
        // scheme separator may still be percent-encoded.
        var uriPath = checkNotNull(path).substring(1)
        if (!uriPath.contains("://")) {
            uriPath = Uri.decode(uriPath)
        }
        return Paths.get(URI.create(uriPath))
    }
