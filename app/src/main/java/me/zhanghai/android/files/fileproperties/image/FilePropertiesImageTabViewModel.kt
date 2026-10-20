/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.image

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.util.Stateful

class FilePropertiesImageTabViewModel(val path: Path, val mimeType: MimeType) : ViewModel() {
    private val _imageInfoLiveData = ImageInfoLiveData(path, mimeType)
    val imageInfoLiveData: LiveData<Stateful<ImageInfo>>
        get() = _imageInfoLiveData

    fun reload() {
        _imageInfoLiveData.loadValue()
    }

    suspend fun removeExif(): Result<Unit> {
        val result = withContext(Dispatchers.IO) {
            try {
                ExifRemover.removeExif(path)
                Result.success(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
        if (result.isSuccess) {
            // The value of the live data can only be set on the main thread.
            withContext(Dispatchers.Main) {
                reload()
            }
        }
        return result
    }

    override fun onCleared() {
        _imageInfoLiveData.close()
    }
}
