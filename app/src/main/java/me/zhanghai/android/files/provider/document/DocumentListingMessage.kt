/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.document

import androidx.lifecycle.MutableLiveData

/**
 * A document provider can send a status string while a folder is still loading. The file list
 * shows that string in place of the generic loading subtitle.
 */
object DocumentListingMessage {
    val liveData = MutableLiveData<String?>()

    fun publish(message: String?) {
        liveData.postValue(message)
    }
}
