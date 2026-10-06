/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.compat

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KClass

val KClass<MediaMetadataRetriever>.METADATA_KEY_SAMPLERATE: Int
    @RequiresApi(Build.VERSION_CODES.Q)
    get() = 38

fun MediaMetadataRetriever.getFrameAtTimeCompat(
    timeUs: Long,
    option: Int,
    params: MediaMetadataRetriever.BitmapParams?
): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && params != null) {
        getFrameAtTime(timeUs, option, params)
    } else {
        getFrameAtTime(timeUs, option)
    }

@RequiresApi(Build.VERSION_CODES.O_MR1)
fun MediaMetadataRetriever.getScaledFrameAtTimeCompat(
    timeUs: Long,
    option: Int,
    dstWidth: Int,
    dstHeight: Int,
    params: MediaMetadataRetriever.BitmapParams?
): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && params != null) {
        getScaledFrameAtTime(timeUs, option, dstWidth, dstHeight, params)
    } else {
        getScaledFrameAtTime(timeUs, option, dstWidth, dstHeight)
    }

@OptIn(ExperimentalContracts::class)
inline fun <R> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    // One retriever at a time. A folder of videos otherwise leaves native retrievers for the
    // finalizer, and MediaMetadataRetriever.finalize() can block long enough for the
    // finalizer watchdog to kill the process.
    synchronized(retrieverLock) {
        try {
            return block(this)
        } finally {
            try {
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            clearNativeContext()
        }
    }
}

@PublishedApi
internal val retrieverLock = Any()

@PublishedApi
internal fun MediaMetadataRetriever.clearNativeContext() {
    try {
        val field = MediaMetadataRetriever::class.java.getDeclaredField("mNativeContext")
        field.isAccessible = true
        field.setLong(this, 0)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
