/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp.client

import android.content.Context
import me.zhanghai.android.files.fileaction.SftpPasswordDialogActivity
import me.zhanghai.android.files.fileaction.SftpPasswordDialogFragment
import me.zhanghai.android.files.provider.common.UserAction
import me.zhanghai.android.files.provider.common.UserActionRequiredException
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class SshPasswordRequiredException(
    private val authority: Authority
) : UserActionRequiredException(authority.toString(), null, "Password required") {
    override fun getUserAction(continuation: Continuation<Boolean>, context: Context): UserAction =
        UserAction(
            SftpPasswordDialogActivity::class.createIntent().putArgs(
                SftpPasswordDialogFragment.Args(authority) { continuation.resume(it) }
            ),
            SftpPasswordDialogFragment.getTitle(context),
            SftpPasswordDialogFragment.getMessage(authority, context)
        )
}
