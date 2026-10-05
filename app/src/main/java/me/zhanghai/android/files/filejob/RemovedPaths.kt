/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.os.Handler
import android.os.Looper
import java8.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Paths that were just deleted. Listeners are called on the main thread with one batch, and a new
 * listener does not receive paths removed before it was added.
 */
object RemovedPaths {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<Path>()
    private val listeners = CopyOnWriteArrayList<(List<Path>) -> Unit>()
    private val flush = Runnable {
        val paths = synchronized(pending) {
            if (pending.isEmpty()) {
                return@Runnable
            }
            val copy = pending.toList()
            pending.clear()
            copy
        }
        for (listener in listeners) {
            listener(paths)
        }
    }

    fun addListener(listener: (List<Path>) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (List<Path>) -> Unit) {
        listeners -= listener
    }

    fun notifyRemoved(path: Path) {
        synchronized(pending) {
            val schedule = pending.isEmpty()
            pending.add(path)
            if (schedule) {
                handler.post(flush)
            }
        }
    }
}
