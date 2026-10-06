/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import me.zhanghai.android.files.provider.common.OpenOptions
import me.zhanghai.android.files.provider.common.ReadOnlyFileSystemException

internal fun OpenOptions.checkForArchive(file: String? = null) {
    if (write || append || truncateExisting || create || createNew || deleteOnClose) {
        throw ReadOnlyFileSystemException(file)
    }
}
