/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SftpSocksProxy(
    val serverId: Long,
    val host: String,
    val port: Int
) : Parcelable
