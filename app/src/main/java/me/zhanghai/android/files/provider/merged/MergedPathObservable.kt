/*
 * Copyright (c) 2025 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.merged

import me.zhanghai.android.files.provider.common.AbstractPathObservable
import me.zhanghai.android.files.provider.common.PathObservable
import me.zhanghai.android.files.provider.common.observe
import java.io.IOException

internal class MergedPathObservable(
    fileSystem: MergedFileSystem,
    intervalMillis: Long
) : AbstractPathObservable(intervalMillis) {
    private val observables: List<PathObservable>

    init {
        val created = mutableListOf<PathObservable>()
        var successful = false
        try {
            for (source in fileSystem.sources) {
                val observable = source.observe(intervalMillis)
                observable.addObserver { notifyObservers() }
                created += observable
            }
            successful = true
        } finally {
            if (!successful) {
                created.forEach { it.close() }
            }
        }
        observables = created
    }

    @Throws(IOException::class)
    override fun onCloseLocked() {
        observables.forEach { it.close() }
    }
}
