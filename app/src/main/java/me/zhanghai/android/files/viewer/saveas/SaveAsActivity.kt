/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.saveas

import android.os.Bundle
import android.os.Environment
import androidx.lifecycle.lifecycleScope
import java8.nio.file.Path
import java8.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.util.saveAsPath
import me.zhanghai.android.files.util.showToast
import java.io.File
import java.io.IOException

class SaveAsActivity : AppActivity() {
    private val createFileLauncher =
        registerForActivityResult(FileListActivity.CreateFileContract(), ::onCreateFileResult)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val mimeType = intent.type?.asMimeTypeOrNull() ?: MimeType.ANY
        val path = intent.saveAsPath
        if (path == null) {
            showToast(R.string.save_as_error)
            finish()
            return
        }
        val title = path.fileName.toString()
        val initialPath =
            Paths.get(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
            )
        createFileLauncher.launch(Triple(mimeType, title, initialPath))
    }

    private fun onCreateFileResult(result: Path?) {
        if (result == null) {
            finish()
            return
        }
        val source = intent.saveAsPath
        if (source == null) {
            showToast(R.string.save_as_error)
            finish()
            return
        }
        // The incoming URI grant is dropped when this activity finishes, so copy the bytes first.
        lifecycleScope.launch {
            val snapshot = try {
                withContext(Dispatchers.IO) { snapshotSource(source) }
            } catch (e: IOException) {
                showToast(e.toString())
                finish()
                return@launch
            }
            FileJobService.save(snapshot, result, this@SaveAsActivity)
            finish()
        }
    }

    @Throws(IOException::class)
    private fun snapshotSource(source: Path): Path {
        val directory = File(cacheDir, "save_as").apply { mkdirs() }
        val name = source.fileName?.toString()?.takeIf { it.isNotEmpty() } ?: "file"
        val file = File(directory, "${System.currentTimeMillis()}-$name")
        source.newInputStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return Paths.get(file.path)
    }
}
