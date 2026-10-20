/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import androidx.annotation.BoolRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import me.zhanghai.android.files.provider.root.isRunningAsRoot
import me.zhanghai.android.files.provider.root.rootContext
import me.zhanghai.android.files.util.valueCompat

/**
 * Reads the value of a boolean setting.
 *
 * The file system providers run in another process when accessing as root, in which case the
 * settings have to be read from the shared preferences directly.
 */
fun getBooleanSetting(
    @StringRes keyRes: Int,
    @BoolRes defaultValueRes: Int,
    valueIfFailed: Boolean,
    setting: SettingLiveData<Boolean>
): Boolean =
    try {
        if (isRunningAsRoot) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(rootContext)
            val key = rootContext.getString(keyRes)
            val defaultValue = rootContext.resources.getBoolean(defaultValueRes)
            sharedPreferences.getBoolean(key, defaultValue)
        } else {
            setting.valueCompat
        }
    } catch (e: Exception) {
        e.printStackTrace()
        valueIfFailed
    }
