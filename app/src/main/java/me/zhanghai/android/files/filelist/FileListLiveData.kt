/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java8.nio.file.DirectoryIteratorException
import java8.nio.file.Path
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.util.CloseableLiveData
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import com.hierynomus.smbj.common.SMBRuntimeException
import me.zhanghai.android.files.util.findCauseByClass
import me.zhanghai.android.files.util.valueCompat
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class FileListLiveData(private val path: Path) : CloseableLiveData<Stateful<List<FileItem>>>() {
    private var future: Future<Unit>? = null
    private var currentTask: LoadTask? = null

    private val observer: PathObserver

    @Volatile
    private var isChangedWhileInactive = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastReloadUptimeMillis = 0L
    private val debouncedReloadRunnable = Runnable {
        if (hasActiveObservers()) {
            loadValue()
        } else {
            isChangedWhileInactive = true
        }
    }

    private class LoadTask {
        @Volatile
        var isCancelled = false
    }

    init {
        observer = PathObserver(path) { onChangeObserved() }
        loadValue()
    }

    fun loadValue() {
        lastReloadUptimeMillis = SystemClock.uptimeMillis()
        mainHandler.removeCallbacks(debouncedReloadRunnable)
        observer.observe()
        currentTask?.isCancelled = true
        future?.cancel(false)
        val task = LoadTask()
        currentTask = task
        value = Loading(value?.value)
        future = (AsyncTask.THREAD_POOL_EXECUTOR as ExecutorService).submit<Unit> {
            if (task.isCancelled) {
                return@submit
            }
            val value = try {
                path.newDirectoryStream().use { directoryStream ->
                    val fileList = mutableListOf<FileItem>()
                    for (path in directoryStream) {
                        if (task.isCancelled) {
                            return@submit
                        }
                        try {
                            fileList.add(path.loadFileItem())
                        } catch (e: DirectoryIteratorException) {
                            // TODO: Ignoring such a file can be misleading and we need to support
                            //  files without information.
                            e.printStackTrace()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    if (task.isCancelled) {
                        return@submit
                    }
                    Success(applyHiddenFile(fileList))
                }
            } catch (e: Exception) {
                if (task.isCancelled || Thread.currentThread().isInterrupted ||
                    e.findCauseByClass<InterruptedException>() != null
                ) {
                    return@submit
                }
                // A transfer refreshes this folder and the share can fail the listing while
                // the copy itself succeeds. Keep the files already shown instead of toasting
                // the same SMB error on every refresh.
                if (valueCompat.value != null &&
                    (e is SMBRuntimeException || e.findCauseByClass<SMBRuntimeException>() != null)
                ) {
                    e.printStackTrace()
                    return@submit
                }
                Failure(valueCompat.value, e)
            }
            if (!task.isCancelled) {
                postValue(value)
            }
        }
    }

    /**
     * Like GIO file managers, treat the names listed in a `.hidden` file (one per line) in the
     * directory as hidden.
     */
    private fun applyHiddenFile(fileList: List<FileItem>): List<FileItem> {
        val hiddenFile = fileList.find { it.name == HIDDEN_FILE_NAME && it.attributes.isRegularFile }
            ?: return fileList
        if (hiddenFile.attributes.size() > MAX_HIDDEN_FILE_SIZE) {
            return fileList
        }
        val hiddenNames = try {
            hiddenFile.path.newInputStream().bufferedReader().use { reader ->
                reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return fileList
        }
        return fileList.map {
            if (!it.isHidden && it.name in hiddenNames) it.copy(isHidden = true) else it
        }
    }

    fun reobserve() {
        observer.reobserve()
    }

    private fun onChangeObserved() {
        // Directory changes arrive in bursts during transfers (observer already throttles to
        // ~1s). Reloading on every burst restarts the load, re-stats every file and re-diffs
        // the list, which janks scrolling. Coalesce to at most one reload per interval with a
        // trailing reload so the final state is still shown.
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastReloadUptimeMillis
        if (elapsed >= RELOAD_DEBOUNCE_MILLIS) {
            if (hasActiveObservers()) {
                loadValue()
            } else {
                isChangedWhileInactive = true
            }
        } else {
            mainHandler.removeCallbacks(debouncedReloadRunnable)
            mainHandler.postDelayed(debouncedReloadRunnable, RELOAD_DEBOUNCE_MILLIS - elapsed)
        }
    }

    override fun onActive() {
        if (isChangedWhileInactive) {
            loadValue()
            isChangedWhileInactive = false
        } else {
            observer.observe()
        }
    }

    override fun close() {
        mainHandler.removeCallbacks(debouncedReloadRunnable)
        observer.close()
        currentTask?.isCancelled = true
        future?.cancel(false)
    }

    companion object {
        private const val RELOAD_DEBOUNCE_MILLIS = 2000L
        private const val HIDDEN_FILE_NAME = ".hidden"
        private const val MAX_HIDDEN_FILE_SIZE = 1024 * 1024L
    }
}
