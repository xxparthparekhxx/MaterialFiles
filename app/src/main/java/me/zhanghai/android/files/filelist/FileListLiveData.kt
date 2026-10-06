/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.AsyncTask
import java8.nio.file.DirectoryIteratorException
import java8.nio.file.Path
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.provider.common.newDirectoryStream
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

class FileListLiveData(private val path: Path) : CloseableLiveData<Stateful<List<FileItem>>>() {
    private var future: Future<Unit>? = null
    private var currentTask: LoadTask? = null

    private val observer: PathObserver

    @Volatile
    private var isChangedWhileInactive = false

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
                    Success(fileList as List<FileItem>)
                }
            } catch (e: Exception) {
                if (task.isCancelled || Thread.currentThread().isInterrupted ||
                    e.findCauseByClass<InterruptedException>() != null
                ) {
                    return@submit
                }
                Failure(valueCompat.value, e)
            }
            if (!task.isCancelled) {
                postValue(value)
            }
        }
    }

    private fun onChangeObserved() {
        if (hasActiveObservers()) {
            loadValue()
        } else {
            isChangedWhileInactive = true
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
        observer.close()
        currentTask?.isCancelled = true
        future?.cancel(false)
    }
}
