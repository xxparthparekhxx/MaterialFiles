/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.EditBookmarkDirectoryDialogBinding
import me.zhanghai.android.files.databinding.EditBookmarkDirectoryPathRowBinding
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.provider.common.isSameFile
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableListParceler
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.getState
import me.zhanghai.android.files.util.launchSafe
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.putState
import me.zhanghai.android.files.util.setTextWithSelection
import java.io.IOException

class EditBookmarkDirectoryDialogFragment : AppCompatDialogFragment() {
    private val openPathLauncher =
        registerForActivityResult(FileListActivity.OpenDirectoryContract(), ::onOpenPathResult)

    private val args by args<Args>()

    private lateinit var paths: MutableList<Path>

    // The index of the path being edited, or null when adding a new path.
    private var editingPathIndex: Int? = null

    private lateinit var binding: EditBookmarkDirectoryDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paths = (savedInstanceState?.getState<State>()?.paths ?: args.bookmarkDirectory.paths)
            .toMutableList()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.navigation_edit_bookmark_directory_title)
            .apply {
                binding = EditBookmarkDirectoryDialogBinding.inflate(context.layoutInflater)
                val bookmarkDirectory = args.bookmarkDirectory
                binding.nameLayout.placeholderText = bookmarkDirectory.defaultName
                if (savedInstanceState == null) {
                    binding.nameEdit.setTextWithSelection(bookmarkDirectory.name)
                }
                updatePaths()
                binding.addPathButton.setOnClickListener { onAddPath() }
                setView(binding.root)
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setNeutralButton(R.string.remove) { _, _ -> remove() }
            .create()
            .apply {
                window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(State(paths.toList()))
    }

    private fun onAddPath() {
        editingPathIndex = null
        openPathLauncher.launchSafe(null, this)
    }

    private fun onEditPath(index: Int) {
        editingPathIndex = index
        openPathLauncher.launchSafe(paths[index], this)
    }

    private fun onOpenPathResult(result: Path?) {
        result ?: return
        val index = editingPathIndex
        editingPathIndex = null
        if (index == null) {
            // Avoid merging the same directory twice.
            if (paths.none { isSamePath(it, result) }) {
                paths.add(result)
            }
        } else {
            paths[index] = result
        }
        updatePaths()
    }

    private fun onRemovePath(index: Int) {
        paths.removeAt(index)
        updatePaths()
    }

    private fun isSamePath(path: Path, other: Path): Boolean = try {
        path.isSameFile(other)
    } catch (e: IOException) {
        false
    }

    private fun updatePaths() {
        binding.pathLayout.removeAllViews()
        val showRemoveButton = paths.size > 1
        for (index in paths.indices) {
            val row = EditBookmarkDirectoryPathRowBinding.inflate(
                requireContext().layoutInflater, binding.pathLayout, false
            )
            row.pathText.text = paths[index].toUserFriendlyString()
            row.root.setOnClickListener { onEditPath(index) }
            row.pathRemoveButton.visibility = if (showRemoveButton) View.VISIBLE else View.GONE
            row.pathRemoveButton.setOnClickListener { onRemovePath(index) }
            binding.pathLayout.addView(row.root)
        }
    }

    private fun save() {
        val customName = binding.nameEdit.text.toString()
            .takeIf { it.isNotEmpty() && it != binding.nameLayout.placeholderText }
        val bookmarkDirectory = args.bookmarkDirectory.copy(customName = customName, paths = paths)
        BookmarkDirectories.replace(bookmarkDirectory)
        finish()
    }

    private fun remove() {
        BookmarkDirectories.remove(args.bookmarkDirectory)
        finish()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    @Parcelize
    class Args(val bookmarkDirectory: BookmarkDirectory) : ParcelableArgs

    @Parcelize
    private class State(val paths: @WriteWith<ParcelableListParceler> List<Path>) : ParcelableState
}
