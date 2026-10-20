/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.compare

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.FileCompareFragmentBinding
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.viewModels

class FileCompareFragment : Fragment(), FileCompareAdapter.Listener {
    private lateinit var binding: FileCompareFragmentBinding

    private lateinit var paths: List<Path>
    private lateinit var adapter: FileCompareAdapter

    private val viewModel by viewModels {
        { FileCompareViewModel(paths[0], paths[1]) }
    }

    private val leftPath: Path
        get() = paths[0]

    private val rightPath: Path
        get() = paths[1]

    private val leftName: String
        get() = leftPath.fileName?.toString() ?: leftPath.toString()

    private val rightName: String
        get() = rightPath.fileName?.toString() ?: rightPath.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val paths = requireActivity().intent.extraPathList
        if (paths.size != 2) {
            finish()
            return
        }
        this.paths = paths
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FileCompareFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!this::paths.isInitialized) {
            return
        }

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar!!.title = getString(R.string.file_compare_title)
        binding.toolbar.inflateMenu(R.menu.file_compare)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_refresh) {
                viewModel.reload()
                true
            } else {
                false
            }
        }

        adapter = FileCompareAdapter(leftName, rightName, this)
        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        binding.recyclerView.adapter = adapter

        viewModel.state.observe(viewLifecycleOwner) { onStateChanged(it) }
    }

    override fun onEntryMenu(entry: FolderCompareEntry, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.inflate(R.menu.file_compare_item)
        val menu = popup.menu
        val left = entry.left
        val right = entry.right
        val isTypeMismatch = left != null && right != null && left.isDirectory != right.isDirectory
        menu.findItem(R.id.action_compare_contents).isVisible =
            left != null && right != null && !left.isDirectory && !right.isDirectory &&
                left.size == right.size &&
                (entry.contentState == ContentCompareState.UNKNOWN ||
                    entry.contentState == ContentCompareState.ERROR)
        menu.findItem(R.id.action_copy_to_left).apply {
            isVisible = right != null && !isTypeMismatch
            title = getString(R.string.file_compare_action_copy_to_left_format, leftName)
        }
        menu.findItem(R.id.action_copy_to_right).apply {
            isVisible = left != null && !isTypeMismatch
            title = getString(R.string.file_compare_action_copy_to_right_format, rightName)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_compare_contents -> viewModel.compareContents(entry)
                R.id.action_copy_to_left -> copyEntry(right, rightPath, leftPath)
                R.id.action_copy_to_right -> copyEntry(left, leftPath, rightPath)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun copyEntry(source: FolderCompareFileInfo?, sourceRoot: Path, targetRoot: Path) {
        if (source == null) {
            return
        }
        if (Settings.READ_ONLY_MODE.valueCompat) {
            showToast(R.string.file_list_read_only_mode_blocked)
            return
        }
        if (targetRoot.fileSystem.isReadOnly) {
            showToast(R.string.file_compare_error_target_read_only)
            return
        }
        val relativePath = sourceRoot.relativize(source.path)
        var targetDirectory = targetRoot
        for (index in 0 until relativePath.nameCount - 1) {
            targetDirectory = targetDirectory.resolve(relativePath.getName(index).toString())
        }
        FileJobService.copy(listOf(source.path), targetDirectory, requireContext())
        showToast(R.string.file_compare_copy_started)
    }

    private fun onStateChanged(state: Stateful<FileCompareResult>) {
        binding.progress.isVisible = state is Loading
        binding.errorText.isVisible = false
        binding.emptyView.isVisible = false
        binding.recyclerView.isVisible = false
        binding.diffScrollView.isVisible = false
        when (state) {
            is Loading -> Unit
            is Failure -> {
                val throwable = state.throwable
                binding.errorText.text = if (throwable is FileCompareException) {
                    getString(throwable.messageRes, *throwable.formatArgs)
                } else {
                    throwable.toString()
                }
                binding.errorText.isVisible = true
            }
            is Success -> when (val result = state.value) {
                is FileCompareResult.Folders -> {
                    adapter.replace(result.entries)
                    if (result.entries.isEmpty()) {
                        binding.emptyView.setText(R.string.file_compare_no_differences)
                        binding.emptyView.isVisible = true
                    } else {
                        binding.recyclerView.isVisible = true
                    }
                }
                is FileCompareResult.Text -> {
                    if (result.isIdentical) {
                        binding.emptyView.setText(R.string.file_compare_files_identical)
                        binding.emptyView.isVisible = true
                    } else {
                        binding.diffText.text = result.diff
                        binding.diffScrollView.isVisible = true
                    }
                }
            }
        }
    }
}
