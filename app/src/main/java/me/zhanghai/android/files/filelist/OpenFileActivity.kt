/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import java8.nio.file.Path
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.valueCompat

class OpenFileActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val path = intent.extraPath
        val mimeType = intent.type?.asMimeTypeOrNull()
        if (path != null && mimeType != null) {
            openFile(path, mimeType)
        }
        finish()
    }

    private fun openFile(path: Path, mimeType: MimeType) {
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, false, this)
        } else {
            val intent = path.fileProviderUri.createViewIntent(mimeType)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .apply {
                    extraPath = path
                    if (Settings.OPEN_FILES_IN_NEW_TASK.valueCompat) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            startActivitySafe(intent)
        }
    }

    companion object {
        private const val ACTION_OPEN_FILE = "me.zhanghai.android.files.intent.action.OPEN_FILE"

        fun createIntent(path: Path, mimeType: MimeType): Intent =
            Intent(ACTION_OPEN_FILE)
                .setComponent(ComponentName(application, OpenFileActivity::class.java))
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setType(mimeType.value)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { extraPath = path }
    }
}
