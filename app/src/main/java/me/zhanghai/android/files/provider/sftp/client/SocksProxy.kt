/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp.client

data class SocksProxy(val host: String, val port: Int) {
    companion object {
        const val DEFAULT_PORT = 1080
    }
}
