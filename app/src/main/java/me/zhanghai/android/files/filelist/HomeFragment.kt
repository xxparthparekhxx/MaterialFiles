/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.HomeFragmentBinding
import me.zhanghai.android.files.navigation.StandardDirectory
import me.zhanghai.android.files.navigation.StandardDirectoriesLiveData
import me.zhanghai.android.files.navigation.getExternalStorageDirectory
import me.zhanghai.android.files.navigation.getStorageSubtitle
import me.zhanghai.android.files.navigation.isShownInNavigation
import me.zhanghai.android.files.provider.root.RootAvailability
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.storage.FileSystemRoot
import me.zhanghai.android.files.storage.Storage
import me.zhanghai.android.files.util.startActivitySafe

class HomeFragment : Fragment(), HomeAdapter.Listener {
    private lateinit var binding: HomeFragmentBinding
    private lateinit var adapter: HomeAdapter

    private var standardDirectories: List<StandardDirectory> = emptyList()

    private var storages: List<Storage> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        HomeFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        binding.toolbar.title = getString(R.string.home_title)

        binding.recyclerView.layoutManager = GridLayoutManager(activity, 2)
        adapter = HomeAdapter(this)
        binding.recyclerView.adapter = adapter

        StandardDirectoriesLiveData.observe(viewLifecycleOwner) { directories ->
            standardDirectories = directories.filter { it.isEnabled }
            updateItems()
        }
        Settings.STORAGES.observe(viewLifecycleOwner) { values ->
            storages = values
            updateItems()
        }
    }

    private fun updateItems() {
        val context = context ?: return
        val items = mutableListOf<HomeItem>()
        for (standardDirectory in standardDirectories) {
            items += HomeItem(
                standardDirectory.iconRes,
                standardDirectory.getTitle(context),
                null,
                Paths.get(getExternalStorageDirectory(standardDirectory.relativePath)),
                null,
            )
        }
        for (storage in storages) {
            if (!storage.isVisible || !storage.isShownInNavigation) {
                continue
            }
            if (storage is FileSystemRoot && !RootAvailability.isAvailable) {
                continue
            }
            val subtitle = storage.linuxPath?.let { getStorageSubtitle(it, context) }
            if (storage.path != null) {
                items += HomeItem(
                    storage.iconRes, storage.getName(context), subtitle, storage.path, null
                )
            } else {
                storage.createIntent()?.let { intent ->
                    items += HomeItem(
                        storage.iconRes, storage.getName(context), subtitle, null, intent
                    )
                }
            }
        }
        adapter.replace(items)
    }

    override fun onItemClick(item: HomeItem) {
        val intent = item.path?.let { FileListActivity.createViewIntent(it) } ?: item.intent ?: return
        startActivitySafe(intent)
    }
}
