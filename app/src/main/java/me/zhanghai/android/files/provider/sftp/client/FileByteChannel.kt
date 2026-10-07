/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp.client

import me.zhanghai.android.files.provider.common.AbstractFileByteChannel
import me.zhanghai.android.files.provider.common.EMPTY
import me.zhanghai.android.files.util.closeSafe
import me.zhanghai.android.files.util.findCauseByClass
import net.schmizz.concurrent.Promise
import net.schmizz.sshj.sftp.PacketType
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.RemoteFileAccessor
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedByInterruptException
import java.util.ArrayDeque
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class FileByteChannel(
    private val file: RemoteFile,
    isAppend: Boolean,
    private val sessionLock: Any,
    private val onSessionBroken: () -> Unit
) : AbstractFileByteChannel(isAppend) {
    private val unconfirmedWrites = ArrayDeque<Promise<Response, SFTPException>>()
    private var writeOffset = 0L
    private val maxPacketPayloadSize = RemoteFileAccessor.getMaxWritePacketPayloadSize(file)

    override fun onReadAsync(position: Long, size: Int, timeoutMillis: Long): Future<ByteBuffer> {
        // The shared SFTP client is not safe to use while a directory listing is in
        // flight. Read and copy the bytes before releasing the session.
        val buffer = synchronized(sessionLock) {
            readAt(position, size, timeoutMillis)
        }
        return CompletedFuture(buffer)
    }

    @Throws(IOException::class)
    private fun readAt(position: Long, size: Int, timeoutMillis: Long): ByteBuffer {
        flushWrites()
        val response = try {
            RemoteFileAccessor.asyncRead(file, position, size)
                .retrieve(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: IOException) {
            throw e.maybeToSpecificException()
        }
        try {
            val dataLength: Int
            when (response.type) {
                PacketType.STATUS -> {
                    response.ensureStatusIs(Response.StatusCode.EOF)
                    return ByteBuffer::class.EMPTY
                }
                PacketType.DATA -> dataLength = response.readUInt32AsInt()
                else -> throw SFTPException("Unexpected packet type ${response.type}")
            }
            if (dataLength == 0) {
                return ByteBuffer::class.EMPTY
            }
            val length = dataLength.coerceAtMost(size)
            val bytes = ByteArray(length)
            val offset = response.rpos()
            if (offset < 0 || length > response.array().size - offset) {
                throw IOException("Short SFTP read")
            }
            System.arraycopy(response.array(), offset, bytes, 0, length)
            return ByteBuffer.wrap(bytes)
        } catch (e: RuntimeException) {
            onSessionBroken()
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun onWrite(position: Long, source: ByteBuffer) {
        val totalToWrite = source.remaining()
        if (totalToWrite == 0) {
            return
        }
        synchronized(sessionLock) {
            if (unconfirmedWrites.isNotEmpty() && position != writeOffset) {
                flushWrites()
            }
            writeOffset = position

            val hasArray = source.hasArray()
            val array = if (hasArray) source.array() else null
            var arrayOffset = if (hasArray) source.arrayOffset() + source.position() else 0
            var tempBuffer: ByteArray? = null

            try {
                while (source.hasRemaining()) {
                    while (unconfirmedWrites.size >= MAX_UNCONFIRMED_WRITES) {
                        val promise = unconfirmedWrites.removeFirst()
                        RemoteFileAccessor.checkWriteResponse(file, promise)
                    }

                    val chunkSize = minOf(source.remaining(), maxPacketPayloadSize)
                    val writeBytes: ByteArray
                    val writeOffsetInArray: Int
                    if (hasArray) {
                        writeBytes = array!!
                        writeOffsetInArray = arrayOffset
                        arrayOffset += chunkSize
                        source.position(source.position() + chunkSize)
                    } else {
                        if (tempBuffer == null || tempBuffer.size < chunkSize) {
                            tempBuffer = ByteArray(chunkSize)
                        }
                        source.get(tempBuffer, 0, chunkSize)
                        writeBytes = tempBuffer
                        writeOffsetInArray = 0
                    }

                    val promise = RemoteFileAccessor.asyncWrite(
                        file, writeOffset, writeBytes, writeOffsetInArray, chunkSize
                    )
                    unconfirmedWrites.add(promise)
                    writeOffset += chunkSize
                }
            } catch (e: IOException) {
                unconfirmedWrites.clear()
                throw e.maybeToSpecificException()
            } catch (e: RuntimeException) {
                unconfirmedWrites.clear()
                onSessionBroken()
                throw IOException(e)
            }
        }
    }

    @Throws(IOException::class)
    private fun flushWrites() {
        while (unconfirmedWrites.isNotEmpty()) {
            val promise = unconfirmedWrites.removeFirst()
            try {
                RemoteFileAccessor.checkWriteResponse(file, promise)
            } catch (e: IOException) {
                unconfirmedWrites.clear()
                throw e.maybeToSpecificException()
            } catch (e: RuntimeException) {
                unconfirmedWrites.clear()
                onSessionBroken()
                throw IOException(e)
            }
        }
    }

    @Throws(IOException::class)
    override fun onForce(metaData: Boolean) {
        synchronized(sessionLock) {
            flushWrites()
        }
    }

    @Throws(IOException::class)
    override fun onTruncate(size: Long) {
        synchronized(sessionLock) {
            flushWrites()
            try {
                file.setLength(size)
            } catch (e: IOException) {
                throw e.maybeToSpecificException()
            }
        }
    }

    @Throws(IOException::class)
    override fun onSize(): Long =
        synchronized(sessionLock) {
            flushWrites()
            try {
                file.length()
            } catch (e: IOException) {
                throw e.maybeToSpecificException()
            }
        }

    private fun IOException.maybeToSpecificException(): IOException =
        when {
            this is SFTPException && statusCode == Response.StatusCode.INVALID_HANDLE -> {
                setClosed()
                AsynchronousCloseException().apply { initCause(this@maybeToSpecificException) }
            }
            findCauseByClass<InterruptedException>() != null -> {
                closeSafe()
                ClosedByInterruptException().apply { initCause(this@maybeToSpecificException) }
            }
            else -> this
        }

    @Throws(IOException::class)
    override fun onClose() {
        try {
            synchronized(sessionLock) {
                try {
                    flushWrites()
                } finally {
                    file.close()
                }
            }
        } catch (e: SFTPException) {
            // NO_SUCH_FILE is returned when canceling an in-progress copy to SFTP server.
            if (e.statusCode != Response.StatusCode.NO_SUCH_FILE) {
                throw e
            }
        }
    }
}

private const val MAX_UNCONFIRMED_WRITES = 16

private class CompletedFuture<T>(private val value: T) : Future<T> {
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

    override fun isCancelled(): Boolean = false

    override fun isDone(): Boolean = true

    override fun get(): T = value

    override fun get(timeout: Long, unit: TimeUnit): T = value
}
