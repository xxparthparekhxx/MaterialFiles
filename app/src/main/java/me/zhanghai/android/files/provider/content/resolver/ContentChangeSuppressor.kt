/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.content.resolver

import android.os.SystemClock

internal object ContentChangeSuppressor {
    private val lock = Any()

    private var depth = 0

    private var suppressUntilUptimeMillis = 0L

    fun <T> suppressing(block: () -> T): T {
        synchronized(lock) { ++depth }
        try {
            return block()
        } finally {
            synchronized(lock) {
                --depth
                if (depth == 0) {
                    suppressUntilUptimeMillis = SystemClock.uptimeMillis() + GRACE_MILLIS
                }
            }
        }
    }

    fun isSuppressed(): Boolean =
        synchronized(lock) {
            depth > 0 || SystemClock.uptimeMillis() < suppressUntilUptimeMillis
        }

    private const val GRACE_MILLIS = 500L
}
