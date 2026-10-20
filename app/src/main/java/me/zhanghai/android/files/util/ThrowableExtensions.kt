/*
 * Copyright (c) 2022 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.content.Context
import androidx.annotation.StringRes
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.common.InvalidFileNameException
import me.zhanghai.android.files.provider.common.IsDirectoryException
import me.zhanghai.android.files.provider.common.ReadOnlyFileSystemException
import me.zhanghai.android.files.provider.common.UserActionRequiredException
import java.io.FileNotFoundException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java8.nio.file.AccessDeniedException
import java8.nio.file.DirectoryIteratorException
import java8.nio.file.DirectoryNotEmptyException
import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.FileSystemLoopException
import java8.nio.file.NoSuchFileException
import java8.nio.file.NotDirectoryException

inline fun <reified T : Throwable> Throwable.findCauseByClass(): T? {
    var current: Throwable? = this
    do {
        if (current is T) {
            return current
        }
        current = current!!.cause
    } while (current != null)
    return null
}

/**
 * Returns a localized message describing this throwable, which is suitable for showing to the
 * user. Exception text is not localized and usually contains internal class names and system
 * messages, so it should not be shown directly.
 */
fun Throwable.toUserFriendlyMessage(context: Context): String =
    context.getString(findUserFriendlyMessageRes() ?: R.string.error_unknown)

// The root and Shizuku file services signal unavailability with an internal English message.
private const val ROOT_UNAVAILABLE_MESSAGE = "Root isn't available"

private const val SHIZUKU_UNAVAILABLE_MESSAGE = "Shizuku isn't available"

// The order of these checks matters: a more specific cause should win over a more general one.
@StringRes
private fun Throwable.findUserFriendlyMessageRes(): Int? {
    if (hasCauseMessage(ROOT_UNAVAILABLE_MESSAGE)) {
        return R.string.error_root_unavailable
    }
    if (hasCauseMessage(SHIZUKU_UNAVAILABLE_MESSAGE)) {
        return R.string.error_shizuku_unavailable
    }
    return findCauseMessageRes<UserActionRequiredException>(R.string.error_user_action_required)
        ?: findCauseMessageRes<InterruptedIOException>(R.string.error_operation_canceled)
        ?: findCauseMessageRes<AccessDeniedException>(R.string.error_permission_denied)
        ?: findCauseMessageRes<SecurityException>(R.string.error_permission_denied)
        ?: findCauseMessageRes<ReadOnlyFileSystemException>(R.string.error_read_only_file_system)
        ?: findCauseMessageRes<FileAlreadyExistsException>(R.string.error_file_already_exists)
        ?: findCauseMessageRes<DirectoryNotEmptyException>(R.string.error_directory_not_empty)
        ?: findCauseMessageRes<NoSuchFileException>(R.string.error_file_not_found)
        ?: findCauseMessageRes<FileNotFoundException>(R.string.error_file_not_found)
        ?: findCauseMessageRes<NotDirectoryException>(R.string.error_not_a_directory)
        ?: findCauseMessageRes<IsDirectoryException>(R.string.error_is_a_directory)
        ?: findCauseMessageRes<InvalidFileNameException>(R.string.error_invalid_file_name)
        ?: findCauseMessageRes<DirectoryIteratorException>(R.string.error_could_not_read_folder)
        ?: findCauseMessageRes<FileSystemLoopException>(R.string.error_symbolic_links_too_deep)
        ?: findCauseMessageRes<UnknownHostException>(R.string.error_host_not_found)
        ?: findCauseMessageRes<ConnectException>(R.string.error_connection_failed)
        ?: findCauseMessageRes<SocketTimeoutException>(R.string.error_connection_failed)
        ?: findCauseMessageRes<PortUnreachableException>(R.string.error_connection_failed)
        ?: findCauseMessageRes<NoRouteToHostException>(R.string.error_connection_failed)
}

@StringRes
private inline fun <reified T : Throwable> Throwable.findCauseMessageRes(
    @StringRes messageRes: Int
): Int? = if (findCauseByClass<T>() != null) messageRes else null

private fun Throwable.hasCauseMessage(message: String): Boolean =
    generateSequence(this) { it.cause }.any { it.message == message }
