/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.RecentFilesFragmentBinding
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe

class RecentFilesFragment : Fragment(), RecentFilesAdapter.Listener {
    private lateinit var binding: RecentFilesFragmentBinding
    private lateinit var adapter: RecentFilesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        RecentFilesFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.inflateMenu(R.menu.recent_files)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear) {
                RecentFiles.clear()
                true
            } else {
                false
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        adapter = RecentFilesAdapter(this)
        binding.recyclerView.adapter = adapter
        Settings.RECENT_FILES.observe(viewLifecycleOwner) { files ->
            adapter.replace(files)
            binding.emptyView.isVisible = files.isEmpty()
            binding.recyclerView.isVisible = files.isNotEmpty()
        }
    }

    override fun openRecentFile(file: RecentFile) {
        val path = RecentFiles.pathOf(file)
        if (path == null) {
            showToast(R.string.recent_files_open_error)
            return
        }
        val mimeType = file.mimeType.asMimeType()
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, false, requireContext())
            return
        }
        val intent = path.fileProviderUri.createViewIntent(mimeType)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .apply { extraPath = path }
        startActivitySafe(intent)
    }

    override fun showRecentFileInFolder(file: RecentFile) {
        val parent = RecentFiles.pathOf(file)?.parent
        if (parent == null) {
            showToast(R.string.recent_files_open_error)
            return
        }
        startActivitySafe(FileListActivity.createViewIntent(parent))
    }

    override fun removeRecentFile(file: RecentFile) {
        RecentFiles.remove(file.uri)
    }
}
