/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import java8.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.AppListFragmentBinding
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.navigation.getExternalStorageDirectory
import me.zhanghai.android.files.provider.common.createDirectories
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import android.content.Intent
import android.os.Environment
import java.io.File

class AppListFragment : Fragment(), AppListAdapter.Listener {
    private lateinit var binding: AppListFragmentBinding
    private lateinit var adapter: AppListAdapter

    private var loadJob: Job? = null
    private var allApps: List<AppEntry> = emptyList()
    private var filter: Filter = Filter.ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        AppListFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.inflateMenu(R.menu.app_list)
        binding.toolbar.setOnMenuItemClickListener { item ->
            val newFilter = when (item.itemId) {
                R.id.action_filter_user -> Filter.USER
                R.id.action_filter_system -> Filter.SYSTEM
                else -> Filter.ALL
            }
            if (newFilter != filter) {
                filter = newFilter
                applyFilter()
            }
            item.isChecked = true
            true
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        adapter = AppListAdapter(requireContext().packageManager, this)
        binding.recyclerView.adapter = adapter
        loadApps()
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        super.onDestroyView()
    }

    private fun loadApps() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                try {
                    val packageManager = requireContext().packageManager
                    packageManager.getInstalledApplications(0).mapNotNull { info ->
                        try {
                            val size = sequenceOf(info.sourceDir)
                                .plus(info.splitSourceDirs?.asSequence() ?: emptySequence())
                                .sumOf {
                                    try {
                                        File(it).length()
                                    } catch (e: Exception) {
                                        0L
                                    }
                                }
                            AppEntry(
                                info, info.loadLabel(packageManager).toString(),
                                info.flags and ApplicationInfo.FLAG_SYSTEM != 0, size
                            )
                        } catch (e: Exception) {
                            // An app whose information cannot be read is skipped, so that the
                            // other apps are still shown.
                            e.printStackTrace()
                            null
                        }
                    }.sortedBy { it.label.lowercase() }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            if (!isAdded) {
                return@launch
            }
            if (apps != null) {
                allApps = apps
            }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val apps = when (filter) {
            Filter.ALL -> allApps
            Filter.USER -> allApps.filter { !it.isSystem }
            Filter.SYSTEM -> allApps.filter { it.isSystem }
        }
        adapter.replace(apps)
        binding.emptyView.isVisible = apps.isEmpty()
        binding.recyclerView.isVisible = apps.isNotEmpty()
    }

    private fun showAppMenu(view: View, app: AppEntry) {
        PopupMenu(requireContext(), view).apply {
            inflate(R.menu.app_list_item)
            menu.findItem(R.id.action_open).isVisible =
                requireContext().packageManager.getLaunchIntentForPackage(app.packageName) != null
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_open -> openApp(app)
                    R.id.action_backup -> backupApp(app)
                    R.id.action_uninstall -> uninstallApp(app)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    override fun openApp(app: AppEntry) {
        val intent = requireContext().packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            showToast(R.string.app_list_no_launcher)
            return
        }
        startActivitySafe(intent)
    }

    override fun backupApp(app: AppEntry) {
        @Suppress("DEPRECATION")
        val targetDirectory =
            Paths.get(getExternalStorageDirectory(Environment.DIRECTORY_DOWNLOADS))
                .resolve(app.packageName)
        val applicationInfo = app.applicationInfo
        val sources = try {
            sequenceOf(applicationInfo.sourceDir)
                .plus(applicationInfo.splitSourceDirs?.asSequence() ?: emptySequence())
                .map { Paths.get(it) }
                .toList()
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(e.toString())
            return
        }
        // The directory of the app does not exist yet, and the copy does not create it.
        try {
            targetDirectory.createDirectories()
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(e.toString())
            return
        }
        FileJobService.copy(sources, targetDirectory, requireContext())
    }

    override fun uninstallApp(app: AppEntry) {
        val intent = Intent(
            Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", app.packageName, null)
        )
        startActivitySafe(intent)
    }

    override fun showAppMenu(app: AppEntry, view: View) = showAppMenu(view, app)

    private enum class Filter {
        ALL,
        USER,
        SYSTEM
    }
}
