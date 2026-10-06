/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.text.TextUtils

/**
 * Preference values are stored as this enum's ordinals. Keep the first four entries aligned with
 * [TextUtils.TruncateAt] so existing "Display long file name" settings stay valid.
 */
enum class FileNameEllipsize {
    START,
    MIDDLE,
    END,
    MARQUEE,
    WRAP;

    fun toTruncateAt(): TextUtils.TruncateAt =
        when (this) {
            START -> TextUtils.TruncateAt.START
            MIDDLE -> TextUtils.TruncateAt.MIDDLE
            END, WRAP -> TextUtils.TruncateAt.END
            MARQUEE -> TextUtils.TruncateAt.MARQUEE
        }
}
