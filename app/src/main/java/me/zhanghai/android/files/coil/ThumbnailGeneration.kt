/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.coil

import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * Thumbnail reads keep going after the app is left, which stalls a remote file that is open in
 * another app. Close those reads once every activity has stopped, and let the file list start
 * them again when the app is visible.
 */
internal object ThumbnailGeneration {
    private val lock = Any()
    private val reads = HashSet<Closeable>()

    @Volatile
    private var startedActivities = 0

    @Volatile
    var epoch: Int = 0
        private set

    fun onActivityStarted() {
        synchronized(lock) {
            startedActivities++
        }
    }

    fun onActivityStopped() {
        val closing = synchronized(lock) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            if (startedActivities > 0) {
                emptyList()
            } else {
                epoch++
                val copy = reads.toList()
                reads.clear()
                copy
            }
        }
        for (read in closing) {
            runCatching { read.close() }
        }
    }

    fun checkEnabled() {
        if (startedActivities == 0) {
            throw CancellationException("Thumbnails paused")
        }
    }

    fun track(closeable: Closeable) {
        val reject = synchronized(lock) {
            if (startedActivities == 0) {
                true
            } else {
                reads.add(closeable)
                false
            }
        }
        if (reject) {
            runCatching { closeable.close() }
            throw CancellationException("Thumbnails paused")
        }
    }

    fun untrack(closeable: Closeable) {
        synchronized(lock) {
            reads.remove(closeable)
        }
    }

    fun trackStream(stream: InputStream): InputStream {
        val tracked = TrackedInputStream(stream)
        track(tracked)
        return tracked
    }

    private class TrackedInputStream(delegate: InputStream) : FilterInputStream(delegate) {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) {
                return
            }
            untrack(this)
            super.close()
        }
    }
}

internal class ReleasingCloseable(private val release: () -> Unit) : Closeable {
    private val released = AtomicBoolean(false)

    override fun close() {
        if (released.compareAndSet(false, true)) {
            runCatching { release() }
        }
    }
}
