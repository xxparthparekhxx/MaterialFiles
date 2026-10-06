/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp.client

import me.zhanghai.android.files.provider.common.AbstractFileByteChannel
import me.zhanghai.android.files.provider.common.EMPTY
import me.zhanghai.android.files.util.closeSafe
import me.zhanghai.android.files.util.findCauseByClass
import net.schmizz.sshj.sftp.PacketType
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.RemoteFileAccessor
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedByInterruptException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class FileByteChannel(
    private val file: RemoteFile,
    isAppend: Boolean,
    private val sessionLock: Any,
    private val onSessionBroken: () -> Unit
) : AbstractFileByteChannel(isAppend) {
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
        // I don't think we are using native or read-only ByteBuffer, so just call array() here.
        synchronized(sessionLock) {
            try {
                file.write(
                    position, source.array(), source.arrayOffset() + source.position(),
                    source.remaining()
                )
            } catch (e: IOException) {
                throw e.maybeToSpecificException()
            }
        }
        source.position(source.limit())
    }

    @Throws(IOException::class)
    override fun onTruncate(size: Long) {
        synchronized(sessionLock) {
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
                file.close()
            }
        } catch (e: SFTPException) {
            // NO_SUCH_FILE is returned when canceling an in-progress copy to SFTP server.
            if (e.statusCode != Response.StatusCode.NO_SUCH_FILE) {
                throw e
            }
        }
    }
}

private class CompletedFuture<T>(private val value: T) : Future<T> {
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

    override fun isCancelled(): Boolean = false

    override fun isDone(): Boolean = true

    override fun get(): T = value

    override fun get(timeout: Long, unit: TimeUnit): T = value
}
