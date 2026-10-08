/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.linux

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class LinuxFileKey(
    val deviceId: Long,
    val inodeNumber: Long
) : Parcelable
