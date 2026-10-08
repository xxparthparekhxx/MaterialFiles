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
    get() = if (isRunningAsRoot) RootStrategy.NEVER else Settings.ROOT_STRATEGY.valueCompat

/**
 * Whether this path is accessed through the root or Shizuku file service. The provider then runs
 * in the file service process, where it never notifies the media scanner, because the media
 * scanner cannot read a file that needs root to access. The caller has to notify it instead.
 */
val Path.isAccessedByFileService: Boolean
    get() =
        when (rootStrategy) {
            RootStrategy.NEVER -> false
            RootStrategy.ALWAYS -> true
            RootStrategy.AUTOMATIC ->
                (this as? RootablePath)?.isRootRequired(isAttributeAccess = false) == true
        }

@Throws(IOException::class)
fun <T, R> callRootable(
    path: Path,
    isAttributeAccess: Boolean,
    localObject: T,
    rootObject: T, block: T.() -> R
): R {
    path as? RootablePath ?: throw IllegalArgumentException("$path is not a RootablePath")
    return when (rootStrategy) {
        RootStrategy.NEVER -> localObject.block()
        RootStrategy.AUTOMATIC ->
            if (path.isRootRequired(isAttributeAccess)) {
                rootObject.block()
            } else {
                localObject.block()
            }
        RootStrategy.ALWAYS -> rootObject.block()
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
    return when (rootStrategy) {
        RootStrategy.NEVER ->
            localObject.block()
        RootStrategy.AUTOMATIC ->
            if (path1.isRootRequired(isAttributeAccess)
                || path2.isRootRequired(isAttributeAccess)) {
                rootObject.block()
            } else {
                localObject.block()
            }
        RootStrategy.ALWAYS ->
            rootObject.block()
    }
}
