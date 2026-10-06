/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.saveas

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.lifecycle.lifecycleScope
import java8.nio.file.Files
import java8.nio.file.LinkOption
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
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.isDirectoryView
import me.zhanghai.android.files.util.saveAsPath
import me.zhanghai.android.files.util.saveAsPaths
import me.zhanghai.android.files.util.showToast
import java.io.File
import java.io.IOException

class SaveAsActivity : AppActivity() {
    private val createFileLauncher =
        registerForActivityResult(FileListActivity.CreateFileContract(), ::onCreateFileResult)

    private val openDirectoryLauncher =
        registerForActivityResult(FileListActivity.OpenDirectoryContract(), ::onOpenDirectoryResult)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            if (intent.saveAsPaths.isEmpty()) {
                showToast(R.string.save_as_error)
                finish()
                return
            }
            val initialPath =
                Paths.get(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ).path
                )
            openDirectoryLauncher.launch(initialPath)
            return
        }
        if (savedInstanceState == null && intent.isDirectoryView()) {
            startActivity(
                FileListActivity::class.createIntent().apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(intent.data, intent.type)
                    addFlags(
                        intent.flags and (
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                            )
                    )
                }
            )
            finish()
            return
        }
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

    private fun onOpenDirectoryResult(result: Path?) {
        if (result == null) {
            finish()
            return
        }
        val sources = intent.saveAsPaths
        // The incoming URI grants are dropped when this activity finishes, so copy the bytes
        // first.
        lifecycleScope.launch {
            val failures = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                for (source in sources) {
                    try {
                        val snapshot = snapshotSource(source)
                        val target = getUniqueTarget(result, source)
                        FileJobService.save(snapshot, target, this@SaveAsActivity)
                    } catch (e: IOException) {
                        failures += e.toString()
                    }
                }
            }
            failures.firstOrNull()?.let { showToast(it) }
            finish()
        }
    }

    // Never silently replace an existing file when saving several files at once.
    private fun getUniqueTarget(directory: Path, source: Path): Path {
        val name = source.fileName?.toString()?.takeIf { it.isNotEmpty() } ?: "file"
        val dotIndex = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val baseName = name.substring(0, dotIndex)
        val extension = name.substring(dotIndex)
        var target = directory.resolve(name)
        var i = 1
        while (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            target = directory.resolve("$baseName ($i)$extension")
            ++i
        }
        return target
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
