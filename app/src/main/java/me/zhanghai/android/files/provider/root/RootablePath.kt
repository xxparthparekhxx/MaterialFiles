/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.root

import java8.nio.file.Path
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat
import java.io.IOException

interface RootablePath {
    fun isRootRequired(isAttributeAccess: Boolean): Boolean
}

private val rootStrategy: RootStrategy
    get() {
        if (isRunningAsRoot) {
            return RootStrategy.NEVER
        }
        return when (Settings.ROOT_STRATEGY.valueCompat) {
            // The root access is only used when it is available, so that the files can still be
            // read without it.
            RootStrategy.AUTOMATIC ->
                if (RootAvailability.isAvailable) RootStrategy.AUTOMATIC else RootStrategy.NEVER
            // The strategy was chosen explicitly, so it is not silently changed.
            RootStrategy.ALWAYS -> RootStrategy.ALWAYS
            RootStrategy.NEVER -> RootStrategy.NEVER
        }
    }

/**
 * Whether the file service has to be used for these paths, which is the case when the root
 * strategy asks for it or when the paths need root.
 */
private fun requiresFileService(paths: List<Path>, isAttributeAccess: Boolean): Boolean =
    when (rootStrategy) {
        RootStrategy.NEVER -> false
        RootStrategy.ALWAYS -> true
        RootStrategy.AUTOMATIC ->
            paths.any { (it as? RootablePath)?.isRootRequired(isAttributeAccess) == true }
    }

/**
 * Whether this path is accessed through the root or Shizuku file service. The provider then runs
 * in the file service process, where it never notifies the media scanner, because the media
 * scanner cannot read a file that needs root to access. The caller has to notify it instead.
 */
val Path.isAccessedByFileService: Boolean
    get() = requiresFileService(listOf(this), isAttributeAccess = false)

@Throws(IOException::class)
fun <T, R> callRootable(
    path: Path,
    isAttributeAccess: Boolean,
    localObject: T,
    rootObject: T, block: T.() -> R
): R {
    path as? RootablePath ?: throw IllegalArgumentException("$path is not a RootablePath")
    return if (requiresFileService(listOf(path), isAttributeAccess)) {
        rootObject.block()
    } else {
        localObject.block()
    }
}

@Throws(IOException::class)
fun <T, R> callRootable(
    path1: Path,
    path2: Path,
    isAttributeAccess: Boolean,
    localObject: T,
    rootObject: T,
    block: T.() -> R
): R {
    path1 as? RootablePath ?: throw IllegalArgumentException("$path1 is not a RootablePath")
    path2 as? RootablePath ?: throw IllegalArgumentException("$path2 is not a RootablePath")
    return if (requiresFileService(listOf(path1, path2), isAttributeAccess)) {
        rootObject.block()
    } else {
        localObject.block()
    }
}
