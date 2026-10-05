/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import java8.nio.file.Path
import me.zhanghai.android.files.provider.common.PathObservable
import me.zhanghai.android.files.provider.common.observe
import me.zhanghai.android.files.util.closeSafe
import java.io.Closeable
import java.io.IOException

class PathObserver(private val path: Path, @MainThread private val onChange: () -> Unit) : Closeable {
    private var pathObservable: PathObservable? = null

    private var isObserving = false
    private var isUnsupported = false
    private var closed = false
    private val lock = Any()

    init {
        observe()
    }

    fun observe() {
        synchronized(lock) {
            if (closed || isUnsupported || pathObservable != null || isObserving) {
                return
            }
            isObserving = true
        }
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val observable = try {
                path.observe(THROTTLE_INTERVAL_MILLIS)
            } catch (e: UnsupportedOperationException) {
                synchronized(lock) {
                    isUnsupported = true
                    isObserving = false
                }
                return@execute
            } catch (e: IOException) {
                e.printStackTrace()
                synchronized(lock) {
                    isObserving = false
                }
                return@execute
            }
            val mainHandler = Handler(Looper.getMainLooper())
            observable.addObserver {
                mainHandler.post {
                    synchronized(lock) {
                        if (closed) {
                            return@post
                        }
                    }
                    onChange()
                }
            }
            val shouldClose = synchronized(lock) {
                if (closed) {
                    true
                } else {
                    pathObservable = observable
                    false
                }
            }
            if (shouldClose) {
                observable.closeSafe()
            }
            synchronized(lock) {
                isObserving = false
            }
        }
    }

    override fun close() {
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val observableToClose = synchronized(lock) {
                if (closed) {
                    return@execute
                }
                closed = true
                val observable = pathObservable
                pathObservable = null
                observable
            }
            observableToClose?.closeSafe()
        }
    }

    companion object {
        private const val THROTTLE_INTERVAL_MILLIS = 1000L
    }
}
