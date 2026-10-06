/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileaction

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs

class SftpPasswordDialogActivity : AppActivity() {
    private val args by args<SftpPasswordDialogFragment.Args>()

    private lateinit var fragment: SftpPasswordDialogFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            fragment = SftpPasswordDialogFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, SftpPasswordDialogFragment::class.java.name)
            }
        } else {
            fragment = supportFragmentManager.findFragmentByTag(
                SftpPasswordDialogFragment::class.java.name
            ) as SftpPasswordDialogFragment
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            fragment.onFinish()
        }
    }
}
