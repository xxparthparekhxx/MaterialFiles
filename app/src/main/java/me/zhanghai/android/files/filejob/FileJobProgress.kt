/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

data class FileJobProgress(
    val id: Int,
    val title: String,
    val text: String?,
    val fileName: String?,
    val fileText: String?,
    val speedText: String?,
    val max: Int,
    val progress: Int,
    val indeterminate: Boolean,
    val fileMax: Int,
    val fileProgress: Int,
    val showFileProgress: Boolean
)

object FileJobProgresses {
    private val lock = Any()
    private val progresses = LinkedHashMap<Int, FileJobProgress>()
    private val mutableLiveData = MutableLiveData<List<FileJobProgress>>(emptyList())

    val liveData: LiveData<List<FileJobProgress>> = mutableLiveData

    fun update(progress: FileJobProgress) {
        val snapshot = synchronized(lock) {
            progresses.remove(progress.id)
            progresses[progress.id] = progress
            progresses.values.toList()
        }
        mutableLiveData.postValue(snapshot)
    }

    fun remove(id: Int) {
        val snapshot = synchronized(lock) {
            if (progresses.remove(id) == null) {
                return
            }
            progresses.values.toList()
        }
        mutableLiveData.postValue(snapshot)
    }
}
