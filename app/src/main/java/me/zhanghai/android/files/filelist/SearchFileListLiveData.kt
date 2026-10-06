/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.AsyncTask
import java8.nio.file.Path
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.provider.common.search
import me.zhanghai.android.files.util.CloseableLiveData
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.findCauseByClass
import me.zhanghai.android.files.util.valueCompat
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class SearchFileListLiveData(
    private val path: Path,
    private val query: String
) : CloseableLiveData<Stateful<List<FileItem>>>() {
    private var future: Future<Unit>? = null
    private var currentTask: LoadTask? = null

    private val observer: PathObserver

    private class LoadTask {
        @Volatile
        var isCancelled = false
    }

    init {
        observer = PathObserver(path) { onChangeObserved() }
        loadValue()
    }

    fun loadValue() {
        observer.observe()
        currentTask?.isCancelled = true
        future?.cancel(false)
        val task = LoadTask()
        currentTask = task
        value = Loading(value?.value ?: emptyList())
        future = (AsyncTask.THREAD_POOL_EXECUTOR as ExecutorService).submit<Unit> {
            if (task.isCancelled) {
                return@submit
            }
            val fileList = mutableListOf<FileItem>()
            try {
                path.search(query, INTERVAL_MILLIS) { paths: List<Path> ->
                    if (task.isCancelled) {
                        return@search
                    }
                    for (path in paths) {
                        if (task.isCancelled) {
                            return@search
                        }
                        val fileItem = try {
                            path.loadFileItem()
                        } catch (e: IOException) {
                            e.printStackTrace()
                            // TODO: Support file without information.
                            continue
                        }
                        fileList.add(fileItem)
                    }
                    if (!task.isCancelled) {
                        postValue(Loading(fileList.toList()))
                    }
                }
                if (!task.isCancelled) {
                    postValue(Success(fileList))
                }
            } catch (e: Exception) {
                if (task.isCancelled || Thread.currentThread().isInterrupted ||
                    e.findCauseByClass<InterruptedException>() != null
                ) {
                    return@submit
                }
                // TODO: Retrieval of previous value is racy.
                postValue(Failure(valueCompat.value, e))
            }
        }
    }

    override fun close() {
        observer.close()
        currentTask?.isCancelled = true
        future?.cancel(false)
    }

    override fun onInactive() {
        observer.pause()
    }

    override fun onActive() {
        observer.observe()
    }

    private fun onChangeObserved() {
        // Renames and deletions inside the searched tree previously left stale results until
        // the query changed; re-run the search like a fresh load.
        loadValue()
    }

    companion object {
        private const val INTERVAL_MILLIS = 500L
    }
}
