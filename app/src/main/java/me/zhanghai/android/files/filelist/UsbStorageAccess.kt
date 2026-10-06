package me.zhanghai.android.files.filelist

import android.os.Build
import android.os.Environment
import java8.nio.file.Path
import me.zhanghai.android.files.compat.isPrimaryCompat
import me.zhanghai.android.files.compat.pathCompat
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.storage.StorageVolumeListLiveData
import me.zhanghai.android.files.util.supportsExternalStorageManager
import me.zhanghai.android.files.util.valueCompat

fun Path.isOnNonPrimaryVolume(): Boolean {
    if (!isLinuxPath) {
        return false
    }
    val filePath = toString()
    val volumes = try {
        StorageVolumeListLiveData.valueCompat
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
    for (volume in volumes) {
        try {
            if (volume.isPrimaryCompat) {
                continue
            }
            val volumePath = volume.pathCompat
            if (filePath == volumePath || filePath.startsWith("$volumePath/")) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            continue
        }
    }
    return false
}

fun shouldRequestAllFilesAccessForPath(path: Path): Boolean {
    if (!Environment::class.supportsExternalStorageManager()) {
        return false
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return false
    }
    if (Environment.isExternalStorageManager()) {
        return false
    }
    return path.isOnNonPrimaryVolume()
}
