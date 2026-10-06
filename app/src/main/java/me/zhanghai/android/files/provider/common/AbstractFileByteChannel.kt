/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.channels.SeekableByteChannel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import me.zhanghai.android.files.util.closeSafe
import java.io.Closeable
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonReadableChannelException
import java.util.ArrayDeque
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

abstract class AbstractFileByteChannel(
    private val isAppend: Boolean,
    private val shouldCancelRead: Boolean = true,
    private val joinCancelledRead: Boolean = false,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    private val readAhead: Int = 1
) : ForceableChannel, SeekableByteChannel {
    private var position = 0L
    private val readBuffer = ReadBuffer()
    private val ioLock = Any()

    private var isOpen = true
    private val closeLock = Any()

    @Throws(IOException::class)
    final override fun read(destination: ByteBuffer): Int {
        ensureOpen()
        if (isAppend) {
            throw NonReadableChannelException()
        }
        val remaining = destination.remaining()
        if (remaining == 0) {
            return 0
        }
        return synchronized(ioLock) {
            readBuffer.read(destination).also {
                if (it != -1) {
                    position += it
                }
            }
        }
    }

    protected open fun onReadAsync(
        position: Long,
        size: Int,
        timeoutMillis: Long
    ): Future<ByteBuffer> =
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.async(Dispatchers.IO) {
            withTimeout(timeoutMillis) {
                runInterruptible {
                    onRead(position, size)
                }
            }
        }
            .asFuture()

    @Throws(IOException::class)
    protected open fun onRead(position: Long, size: Int): ByteBuffer {
        throw NotImplementedError()
    }

    @Throws(IOException::class)
    final override fun write(source: ByteBuffer): Int {
        ensureOpen()
        val remaining = source.remaining()
        if (remaining == 0) {
            return 0
        }
        synchronized(ioLock) {
            if (isAppend) {
                onAppend(source)
                position = onSize()
            } else {
                onWrite(position, source)
                position += remaining - source.remaining()
            }
            return remaining
        }
    }

    @Throws(IOException::class)
    protected abstract fun onWrite(position: Long, source: ByteBuffer)

    @Throws(IOException::class)
    protected open fun onAppend(source: ByteBuffer) {
        val position = onSize()
        onWrite(position, source)
    }

    @Throws(IOException::class)
    final override fun position(): Long {
        ensureOpen()
        synchronized(ioLock) {
            if (isAppend) {
                position = onSize()
            }
            return position
        }
    }

    final override fun position(newPosition: Long): SeekableByteChannel {
        ensureOpen()
        if (isAppend) {
            // Ignored.
            return this
        }
        synchronized(ioLock) {
            readBuffer.reposition(position, newPosition)
            position = newPosition
        }
        return this
    }

    @Throws(IOException::class)
    final override fun size(): Long {
        ensureOpen()
        return onSize()
    }

    @Throws(IOException::class)
    final override fun truncate(size: Long): SeekableByteChannel {
        ensureOpen()
        require(size >= 0)
        synchronized(ioLock) {
            val currentSize = onSize()
            if (size >= currentSize) {
                return this
            }
            onTruncate(size)
            position = position.coerceAtMost(size)
        }
        return this
    }

    @Throws(IOException::class)
    protected abstract fun onTruncate(size: Long)

    @Throws(IOException::class)
    protected abstract fun onSize(): Long

    @Throws(IOException::class)
    final override fun force(metaData: Boolean) {
        ensureOpen()
        synchronized(ioLock) {
            onForce(metaData)
        }
    }

    @Throws(IOException::class)
    protected open fun onForce(metaData: Boolean) {}

    @Throws(ClosedChannelException::class)
    private fun ensureOpen() {
        synchronized(closeLock) {
            if (!isOpen) {
                throw ClosedChannelException()
            }
        }
    }

    final override fun isOpen(): Boolean = synchronized(closeLock) { isOpen }

    @Throws(IOException::class)
    final override fun close() {
        synchronized(closeLock) {
            if (!isOpen) {
                return
            }
            isOpen = false
            synchronized(ioLock) {
                readBuffer.closeSafe()
                onClose()
            }
        }
    }

    protected fun setClosed() {
        synchronized(closeLock) {
            isOpen = false
        }
    }

    @Throws(IOException::class)
    protected open fun onClose() {}

    private inner class ReadBuffer : Closeable {
        private var buffer = ByteBuffer::class.EMPTY
        private val pendingReads = ArrayDeque<PendingRead>()
        private var nextOffset = 0L
        private var inFlightLimit = 1
        private var endOfFile = false
        private val pendingReadLock = Any()

        @Throws(IOException::class)
        fun read(destination: ByteBuffer): Int {
            if (!buffer.hasRemaining()) {
                readIntoBuffer()
                if (!buffer.hasRemaining()) {
                    return -1
                }
            }
            val length = destination.remaining().coerceAtMost(buffer.remaining())
            val bufferLimit = buffer.limit()
            buffer.limit(buffer.position() + length)
            destination.put(buffer)
            buffer.limit(bufferLimit)
            return length
        }

        @Throws(IOException::class)
        private fun readIntoBuffer() {
            if (endOfFile) {
                buffer = ByteBuffer::class.EMPTY
                return
            }
            val pending = synchronized(pendingReadLock) {
                fillAhead()
                if (pendingReads.isEmpty()) {
                    null
                } else {
                    pendingReads.removeFirst()
                }
            } ?: return
            val chunk = await(pending.future)
            val received = chunk.remaining()
            buffer = chunk
            if (received == bufferSize) {
                // The server filled the chunk, so the offsets already queued line up.
                inFlightLimit = readAhead
                synchronized(pendingReadLock) {
                    fillAhead()
                }
                return
            }
            // A short read means any request queued past this chunk is aimed at the wrong
            // offset. Drop those and keep reading from the byte that actually arrived.
            dropPending()
            inFlightLimit = 1
            if (received == 0) {
                endOfFile = true
                return
            }
            nextOffset = pending.offset + received
            synchronized(pendingReadLock) {
                fillAhead()
            }
        }

        private fun fillAhead() {
            if (endOfFile) {
                return
            }
            while (pendingReads.size < inFlightLimit) {
                val offset = nextOffset
                nextOffset += bufferSize
                pendingReads.add(
                    PendingRead(offset, onReadAsync(offset, bufferSize, TIMEOUT_MILLIS))
                )
            }
        }

        private fun await(future: Future<ByteBuffer>): ByteBuffer =
            try {
                future.get()
            } catch (e: CancellationException) {
                throw InterruptedIOException().apply { initCause(e) }
            } catch (e: InterruptedException) {
                throw InterruptedIOException().apply { initCause(e) }
            } catch (e: ExecutionException) {
                val exception = e.cause ?: e
                if (exception is IOException) {
                    throw exception
                } else {
                    throw IOException(exception)
                }
            }

        fun reposition(oldPosition: Long, newPosition: Long) {
            if (newPosition == oldPosition) {
                return
            }
            val newBufferPosition = buffer.position() + (newPosition - oldPosition)
            if (newBufferPosition in 0..buffer.limit()) {
                buffer.position(newBufferPosition.toInt())
            } else {
                dropPending()
                endOfFile = false
                buffer = ByteBuffer::class.EMPTY
                nextOffset = newPosition
            }
        }

        override fun close() {
            dropPending()
        }

        private fun dropPending() {
            synchronized(pendingReadLock) {
                if (shouldCancelRead) {
                    for (pending in pendingReads) {
                        pending.future.cancel(true)
                        if (joinCancelledRead) {
                            try {
                                pending.future.get()
                            } catch (e: Exception) {
                                // Ignored
                            }
                        }
                    }
                }
                pendingReads.clear()
            }
        }
    }

    private class PendingRead(val offset: Long, val future: Future<ByteBuffer>)

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 1024 * 1024
        private const val TIMEOUT_MILLIS = 15_000L
    }
}
