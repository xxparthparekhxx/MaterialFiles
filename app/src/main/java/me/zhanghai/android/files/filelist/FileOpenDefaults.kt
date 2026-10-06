/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.ComponentName
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

/**
 * One file type at a time. The system default applies to every type an activity accepts, so
 * forgetting JPG would also forget every other image.
 */
object FileOpenDefaults {
    private const val SEPARATOR = "\t"

    fun componentFor(mimeType: MimeType): ComponentName? {
        val entry = Settings.FILE_OPEN_DEFAULTS.valueCompat.firstOrNull {
            it.substringBefore(SEPARATOR) == mimeType.value
        } ?: return null
        return ComponentName.unflattenFromString(entry.substringAfter(SEPARATOR))
    }

    fun remember(mimeType: MimeType, component: ComponentName) {
        val entries = Settings.FILE_OPEN_DEFAULTS.valueCompat.toMutableSet()
        entries.removeAll { it.substringBefore(SEPARATOR) == mimeType.value }
        entries.add(mimeType.value + SEPARATOR + component.flattenToString())
        Settings.FILE_OPEN_DEFAULTS.putValue(entries)
    }

    fun forget(mimeType: String) {
        val entries = Settings.FILE_OPEN_DEFAULTS.valueCompat.toMutableSet()
        if (entries.removeAll { it.substringBefore(SEPARATOR) == mimeType }) {
            Settings.FILE_OPEN_DEFAULTS.putValue(entries)
        }
    }

    fun clear() {
        Settings.FILE_OPEN_DEFAULTS.putValue(emptySet())
    }

    fun entries(): List<Pair<String, ComponentName>> =
        Settings.FILE_OPEN_DEFAULTS.valueCompat.mapNotNull { entry ->
            val mimeType = entry.substringBefore(SEPARATOR)
            val component = ComponentName.unflattenFromString(entry.substringAfter(SEPARATOR))
                ?: return@mapNotNull null
            mimeType to component
        }.sortedBy { it.first }
}
