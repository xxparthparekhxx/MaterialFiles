/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Context
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import java8.nio.file.Path
import java8.nio.file.Paths
import kotlinx.parcelize.Parcelize

@Parcelize
data class StandardDirectory internal constructor(
    @DrawableRes val iconRes: Int,
    @StringRes private val titleRes: Int,
    private val customTitle: String?,
    val relativePath: String,
    private val customPath: String?,
    val isEnabled: Boolean
) : Parcelable {
    constructor(
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        relativePath: String,
        enabled: Boolean
    ) : this(iconRes, titleRes, null, relativePath, null, enabled)

    val id: Long
        get() = relativePath.hashCode().toLong()

    val key: String
        get() = relativePath

    val defaultPath: Path
        get() = Paths.get(getExternalStorageDirectory(relativePath))

    val targetPath: Path
        get() = customPath?.takeIf { it.isNotEmpty() }?.let { Paths.get(it) } ?: defaultPath

    fun getTitle(context: Context): String =
        if (!customTitle.isNullOrEmpty()) customTitle else context.getString(titleRes)

    fun withSettings(settings: StandardDirectorySettings): StandardDirectory =
        StandardDirectory(
            iconRes, titleRes, settings.customTitle, relativePath, settings.customPath,
            settings.isEnabled
        )

    fun toSettings(): StandardDirectorySettings =
        StandardDirectorySettings(relativePath, customTitle, isEnabled, customPath)
}
