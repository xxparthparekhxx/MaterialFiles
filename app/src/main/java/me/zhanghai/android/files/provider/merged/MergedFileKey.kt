/*
 * Copyright (c) 2025 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.merged

import android.os.Parcelable
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.util.ParcelableListParceler

@Parcelize
internal data class MergedFileKey(
    private val sources: @WriteWith<ParcelableListParceler> List<Path>
) : Parcelable
