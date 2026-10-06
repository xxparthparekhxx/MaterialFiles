/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import androidx.lifecycle.MutableLiveData

object NavigationStorageRefreshLiveData : MutableLiveData<Long>(0L) {
    fun notifyChanged() {
        postValue((value ?: 0L) + 1L)
    }
}
