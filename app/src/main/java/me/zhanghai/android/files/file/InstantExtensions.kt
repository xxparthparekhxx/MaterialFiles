/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Time
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/* @see com.android.documentsui.base.Shared#formatTime(Context, long) */
@Suppress("DEPRECATION")
fun Instant.formatShort(context: Context): String {
    if (Settings.ISO_DATE_FORMAT.valueCompat) {
        return ISO_SHORT_FORMATTER.format(this)
    }
    val time = toEpochMilli()
    val then = Time().apply { set(time) }
    val now = Time().apply { setToNow() }
    val flags = DateUtils.FORMAT_NO_NOON or DateUtils.FORMAT_NO_MIDNIGHT or
        DateUtils.FORMAT_ABBREV_ALL or when {
            then.year != now.year -> DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_DATE
            then.yearDay != now.yearDay -> DateUtils.FORMAT_SHOW_DATE
            else -> DateUtils.FORMAT_SHOW_TIME
        }
    return DateUtils.formatDateTime(context, time, flags)
}

fun Instant.formatLong(): String =
    if (Settings.ISO_DATE_FORMAT.valueCompat) {
        ISO_LONG_FORMATTER.format(this)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(this)
    }

private val ISO_SHORT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())

private val ISO_LONG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
