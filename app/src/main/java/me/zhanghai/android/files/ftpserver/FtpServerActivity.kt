/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.add
import androidx.fragment.app.commit
import me.zhanghai.android.files.app.AppActivity

class FtpServerActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            supportFragmentManager.commit { add<FtpServerFragment>(android.R.id.content) }
        }
        maybeStartServer(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeStartServer(intent)
    }

    private fun maybeStartServer(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START, false) == true) {
            FtpServerService.start(this)
        }
    }

    companion object {
        /**
         * When true, the FTP server is started as the activity opens.
         *
         * adb shell am start -n me.zhanghai.android.files/.ftpserver.FtpServerActivity --ez me.zhanghai.android.files.intent.extra.FTP_SERVER_START true
         */
        const val EXTRA_START = "me.zhanghai.android.files.intent.extra.FTP_SERVER_START"
    }
}
