/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.smb.client

import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.io.ByteChunkProvider
import com.hierynomus.smbj.share.File
import com.hierynomus.smbj.share.FileAccessor
import me.zhanghai.android.files.provider.common.AbstractFileByteChannel
import me.zhanghai.android.files.provider.common.EMPTY
import me.zhanghai.android.files.provider.common.map
import me.zhanghai.android.files.util.closeSafe
import me.zhanghai.android.files.util.findCauseByClass
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedByInterruptException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

class FileByteChannel(
    private val file: File,
    isAppend: Boolean
// Cancelling reads leads to TransportException: Received response with unknown sequence number
) : AbstractFileByteChannel(
    isAppend,
    shouldCancelRead = false,
    bufferSize = Client.SMB_IO_BUFFER_SIZE,
    readAhead = Client.SMB_READ_AHEAD
) {
    private var wrote = false

    @Throws(IOException::class)
    override fun onReadAsync(position: Long, size: Int, timeoutMillis: Long): Future<ByteBuffer> =
        try {
            FileAccessor.readAsync(file, position, size)
        } catch (e: SMBRuntimeException) {
            throw e.toIOException()
        }
            .map(
                { response ->
                    when (response.header.statusCode) {
                        NtStatus.STATUS_END_OF_FILE.value -> {
                            return@map ByteBuffer::class.EMPTY
                        }
                        NtStatus.STATUS_SUCCESS.value -> {}
                        else -> throw SMBApiException(response.header, "Read failed for $this")
                            .toIOException()
                    }
                    val data = response.data
                    if (data.isEmpty()) {
                        return@map ByteBuffer::class.EMPTY
                    }
                    val length = data.size.coerceAtMost(size)
                    ByteBuffer.wrap(data, 0, length)
                }, { e ->
                    ExecutionException(SMBRuntimeException(e).toIOException())
                }
            )

    @Throws(IOException::class)
    override fun onWrite(position: Long, source: ByteBuffer) {
        wrote = true
        val sourcePosition = source.position()
        val bytesWritten = try {
            file.write(ByteBufferChunkProvider(source, position))
        } catch (e: SMBRuntimeException) {
            throw e.toIOException()
        }
        source.position(sourcePosition + bytesWritten)
    }

    @Throws(IOException::class)
    override fun onTruncate(size: Long) {
        wrote = true
        try {
            file.setLength(size)
        } catch (e: SMBRuntimeException) {
            throw e.toIOException()
        }
    }

    @Throws(IOException::class)
    override fun onSize(): Long =
        try {
            file.getFileInformation(FileStandardInformation::class.java).endOfFile
        } catch (e: SMBRuntimeException) {
            throw e.toIOException()
        }

    @Throws(IOException::class)
    override fun onForce(metaData: Boolean) {
        try {
            file.flush()
        } catch (e: SMBRuntimeException) {
            throw e.toIOException()
        }
    }

    private fun SMBRuntimeException.toIOException(): IOException =
        when {
            findCauseByClass<SMBApiException>()
                .let { it != null && it.status == NtStatus.STATUS_FILE_CLOSED } -> {
                setClosed()
                AsynchronousCloseException().apply { initCause(this@toIOException) }
            }
            findCauseByClass<InterruptedException>() != null -> {
                closeSafe()
                ClosedByInterruptException().apply { initCause(this@toIOException) }
            }
            else -> IOException(this)
        }

    @Throws(IOException::class)
    override fun onClose() {
        try {
            file.close()
        } catch (e: SMBRuntimeException) {
            if (e.findCauseByClass<InterruptedException>() != null) {
                throw InterruptedIOException().apply { initCause(e) }
            }
            // A download already has every byte. A late close reply must not fail the copy and
            // delete the finished file. An upload still needs the server to accept the close.
            if (!wrote && e.isTimeout()) {
                e.printStackTrace()
                return
            }
            throw IOException(e)
        }
    }

    private fun SMBRuntimeException.isTimeout(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current.message?.contains("Timeout", ignoreCase = true) == true) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private class ByteBufferChunkProvider(
        private val buffer: ByteBuffer,
        offset: Long
    ) : ByteChunkProvider() {
        init {
            this.offset = offset
        }

        override fun isAvailable(): Boolean = buffer.hasRemaining()

        override fun bytesLeft(): Int = buffer.remaining()

        override fun prepareWrite(maxBytesToPrepare: Int) {}

        override fun getChunk(chunk: ByteArray): Int {
            val length = chunk.size.coerceAtMost(buffer.remaining())
            buffer.get(chunk, 0, length)
            return length
        }
    }
}
