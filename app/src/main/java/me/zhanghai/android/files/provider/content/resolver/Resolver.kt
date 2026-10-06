/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.content.resolver

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import me.zhanghai.android.files.app.contentResolver
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.util.closeSafe
import me.zhanghai.android.files.util.takeIfNotEmpty
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object Resolver {
    @Throws(ResolverException::class)
    fun checkExistence(uri: Uri) {
        val rowCount = query(uri, emptyArray(), null, null, null).use { cursor -> cursor.count }
        if (rowCount < 1) {
            throw ResolverException(FileNotFoundException("Row count $rowCount is less than 1"))
        }
    }

    @Throws(ResolverException::class)
    fun delete(uri: Uri) {
        val deletedRowCount = try {
            contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            throw ResolverException(e)
        }
        if (deletedRowCount < 1) {
            throw ResolverException(
                FileNotFoundException("Deleted row count $deletedRowCount is less than 1")
            )
        }
    }

    fun exists(uri: Uri): Boolean =
        try {
            checkExistence(uri)
            true
        } catch (e: ResolverException) {
            false
        }

    @Throws(ResolverException::class)
    fun getDisplayName(uri: Uri): String? =
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            cursor.moveToFirstOrThrow()
            cursor.getString(OpenableColumns.DISPLAY_NAME)
        }?.takeIfNotEmpty()

    @Throws(ResolverException::class)
    fun getSize(uri: Uri): Long? =
        query(uri, arrayOf(OpenableColumns.SIZE), null, null, null).use { cursor ->
            cursor.moveToFirstOrThrow()
            cursor.getLong(OpenableColumns.SIZE)
        }

    @Throws(ResolverException::class)
    fun getMimeType(uri: Uri): String? =
        try {
            contentResolver.getType(uri)
        } catch (e: Exception) {
            throw ResolverException(e)
        }?.takeIf { it.isNotEmpty() && it != MimeType.GENERIC.value }

    @Throws(ResolverException::class)
    fun openAssetFileDescriptor(uri: Uri, mode: String): AssetFileDescriptor =
        try {
            contentResolver.openAssetFileDescriptor(uri, mode)
        } catch (e: Exception) {
            throw ResolverException(e)
        } ?: throw ResolverException(
            "ContentResolver.openAssetFileDescriptor() with $uri returned null"
        )

    @Throws(ResolverException::class)
    fun openInputStream(uri: Uri, mode: String): InputStream {
        val descriptor = openAssetFileDescriptor(uri, mode)
        return try {
            descriptor.createInputStream()
        } catch (e: IOException) {
            descriptor.closeSafe()
            throw ResolverException(e)
        }
    }

    @Throws(ResolverException::class)
    fun openOutputStream(uri: Uri, mode: String): OutputStream {
        val descriptor = openAssetFileDescriptor(uri, mode)
        return try {
            if (descriptor.length > 0) {
                descriptor.createOutputStream()
            } else {
                // A new document often reports length 0. The asset stream then accepts no bytes,
                // so a copy onto a USB device stays empty.
                UnlimitedAssetOutputStream(descriptor)
            }
        } catch (e: IOException) {
            descriptor.closeSafe()
            throw ResolverException(e)
        }
    }

    @Throws(ResolverException::class)
    fun openParcelFileDescriptor(uri: Uri, mode: String): ParcelFileDescriptor =
        try {
            contentResolver.openFileDescriptor(
                uri, mode
            )
        } catch (e: Exception) {
            throw ResolverException(e)
        } ?: throw ResolverException("ContentResolver.openFileDescriptor() with $uri returned null")

    @Throws(ResolverException::class)
    fun query(
        uri: Uri,
        projection: Array<out String?>?,
        selection: String?,
        selectionArgs: Array<out String?>?,
        sortOrder: String?
    ): Cursor =
        try {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        } catch (e: Exception) {
            throw ResolverException(e)
        } ?: throw ResolverException("ContentResolver.query() with $uri returned null")
}

private class UnlimitedAssetOutputStream(
    private val descriptor: AssetFileDescriptor
) : OutputStream() {
    private val stream = NonClosingFileOutputStream(descriptor.parcelFileDescriptor.fileDescriptor)
    private var closed = false

    @Throws(IOException::class)
    override fun write(b: Int) {
        stream.write(b)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        stream.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun flush() {
        stream.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        if (closed) {
            return
        }
        closed = true
        try {
            stream.flush()
        } finally {
            descriptor.close()
        }
    }
}

private class NonClosingFileOutputStream(fd: FileDescriptor) : FileOutputStream(fd) {
    @Throws(IOException::class)
    override fun close() {
        flush()
    }
}
