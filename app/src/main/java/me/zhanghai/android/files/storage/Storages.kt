/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.os.Environment
import me.zhanghai.android.files.compat.isPrimaryCompat
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.isMounted
import me.zhanghai.android.files.util.removeFirst
import me.zhanghai.android.files.util.supportsExternalStorageManager
import me.zhanghai.android.files.util.valueCompat

object Storages {
    fun addOrReplace(storage: Storage) {
        val storages = Settings.STORAGES.valueCompat.toMutableList().apply {
            val index = indexOfFirst { it.id == storage.id }
            if (index != -1) {
                this[index] = storage
            } else {
                this += storage
            }
        }
        Settings.STORAGES.putValue(storages)
    }

    fun replace(storage: Storage) {
        val storages = Settings.STORAGES.valueCompat.toMutableList()
            .apply { this[indexOfFirst { it.id == storage.id }] = storage }
        Settings.STORAGES.putValue(storages)
    }

    fun move(fromPosition: Int, toPosition: Int) {
        val bookmarkDirectories = Settings.STORAGES.valueCompat.toMutableList()
            .apply { add(toPosition, removeAt(fromPosition)) }
        Settings.STORAGES.putValue(bookmarkDirectories)
    }

    fun ensureExternalVolumes() {
        if (!Environment::class.supportsExternalStorageManager()) {
            return
        }
        val volumes = StorageVolumeListLiveData.valueCompat
            .filter { !it.isPrimaryCompat && it.isMounted }
        val current = Settings.STORAGES.valueCompat
        val known = current.filterIsInstance<ExternalStorageVolume>().map { it.volumeId }.toSet()
        val missing = volumes.filter { ExternalStorageVolume.volumeId(it) !in known }
        if (missing.isEmpty()) {
            return
        }
        val updated = current.toMutableList()
        var insertAt = updated.indexOfLast { it is DeviceStorage }
        for (volume in missing) {
            insertAt += 1
            updated.add(
                insertAt,
                ExternalStorageVolume(ExternalStorageVolume.volumeId(volume), null, true)
            )
        }
        Settings.STORAGES.putValue(updated)
    }

    fun remove(storage: Storage) {
        val bookmarkDirectories = Settings.STORAGES.valueCompat.toMutableList()
            .apply { removeFirst { it.id == storage.id } }
        Settings.STORAGES.putValue(bookmarkDirectories)
    }
}
