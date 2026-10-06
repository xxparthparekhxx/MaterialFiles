/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.Context
import android.text.format.Formatter
import me.zhanghai.android.files.R
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.getQuantityString
import me.zhanghai.android.files.util.valueCompat
import java.util.Locale

@JvmInline
value class FileSize(val value: Long) {

    /* @see android.text.format.Formatter#formatBytes(Resources, long, int) */
    val isHumanReadableInBytes: Boolean
        get() = value <= 900

    fun formatInBytes(context: Context): String =
        context.getQuantityString(R.plurals.size_in_bytes_format, value.toInt(), value)

    fun formatHumanReadable(context: Context): String {
        if (Settings.BINARY_FILE_SIZE_UNIT.valueCompat) {
            return formatBinary()
        }
        return Formatter.formatFileSize(context, value)
    }

    private fun formatBinary(): String {
        val bytes = value
        if (bytes <= 0L) {
            return "0 B"
        }
        if (bytes < 1024L) {
            return "$bytes B"
        }
        val unit = 1024.0
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit)).toInt().coerceIn(1, 5)
        val pre = "KMGTPE"[exp - 1] + "iB"
        val count = bytes / Math.pow(unit, exp.toDouble())
        return if (count >= 100.0) {
            String.format(Locale.getDefault(), "%.0f %s", count, pre)
        } else if (count >= 10.0) {
            String.format(Locale.getDefault(), "%.1f %s", count, pre)
        } else {
            String.format(Locale.getDefault(), "%.2f %s", count, pre)
        }
    }
}

fun Long.asFileSize(): FileSize = FileSize(this)
