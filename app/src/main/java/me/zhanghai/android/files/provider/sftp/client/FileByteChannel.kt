/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp.client

import me.zhanghai.android.files.provider.common.AbstractFileByteChannel
import me.zhanghai.android.files.provider.common.EMPTY
import me.zhanghai.android.files.provider.common.asFuture
import me.zhanghai.android.files.util.closeSafe
import me.zhanghai.android.files.util.findCauseByClass
import net.schmizz.sshj.sftp.PacketType
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.RemoteFileAccessor
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedByInterruptException
import java.util.concurrent.ExecutionException

class FileByteChannel(
    private val file: RemoteFile,
    private val authority: Authority,
    isAppend: Boolean
) : AbstractFileByteChannel(isAppend) {
    @Throws(IOException::class)
    override fun onRead(position: Long, size: Int): ByteBuffer =
        try {
            Client.withSession(authority) {
                val response = try {
                    RemoteFileAccessor.asyncRead(file, position, size).asFuture().get()
                } catch (e: InterruptedException) {
                    // The request may still be in flight. Drop the session so the next call
                    // reconnects instead of reading a shifted packet.
                    Client.invalidate(authority)
                    throw InterruptedIOException().apply { initCause(e) }
                } catch (e: ExecutionException) {
                    val cause = e.cause
                    if (cause is IOException) {
                        throw cause
                    }
                    throw IOException(cause ?: e)
                }
                readResponse(response, size)
            }
        } catch (e: IOException) {
            throw e.maybeToSpecificException()
        }

    @Throws(IOException::class)
    private fun readResponse(response: Response, size: Int): ByteBuffer {
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
        val array = response.array()
        val offset = response.rpos()
        if (offset < 0 || length > array.size - offset) {
            Client.invalidate(authority)
            throw IOException("Malformed SFTP read response")
        }
        val bytes = ByteArray(length)
        System.arraycopy(array, offset, bytes, 0, length)
        return ByteBuffer.wrap(bytes)
    }

    @Throws(IOException::class)
    override fun onWrite(position: Long, source: ByteBuffer) {
        Client.withSession(authority) {
            // I don't think we are using native or read-only ByteBuffer, so just call array() here.
            try {
                file.write(
                    position, source.array(), source.arrayOffset() + source.position(),
                    source.remaining()
                )
            } catch (e: IOException) {
                throw e.maybeToSpecificException()
            }
            source.position(source.limit())
        }
    }

    @Throws(IOException::class)
    override fun onTruncate(size: Long) {
        Client.withSession(authority) {
            try {
                file.setLength(size)
            } catch (e: IOException) {
                throw e.maybeToSpecificException()
            }
        }
    }

    @Throws(IOException::class)
    override fun onSize(): Long =
        Client.withSession(authority) {
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
        Client.withSession(authority) {
            try {
                file.close()
            } catch (e: SFTPException) {
                // NO_SUCH_FILE is returned when canceling an in-progress copy to SFTP server.
                if (e.statusCode != Response.StatusCode.NO_SUCH_FILE) {
                    throw e
                }
            }
        }
    }
}
