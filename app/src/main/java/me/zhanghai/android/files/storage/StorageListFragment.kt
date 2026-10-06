/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.graphics.drawable.NinePatchDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.StorageListFragmentBinding
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.ScrollingViewOnApplyWindowInsetsListener
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.fadeToVisibilityUnsafe
import me.zhanghai.android.files.util.getDrawable
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.valueCompat

class StorageListFragment : Fragment(), StorageListAdapter.Listener {
    private lateinit var binding: StorageListFragmentBinding

    private lateinit var adapter: StorageListAdapter
    private lateinit var dragDropManager: RecyclerViewDragDropManager
    private lateinit var wrappedAdapter: RecyclerView.Adapter<*>

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportStorages(uri)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importStorages(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        StorageListFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        Storages.ensureExternalVolumes()
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(
            activity, RecyclerView.VERTICAL, false
        )
        adapter = StorageListAdapter(this)
        dragDropManager = RecyclerViewDragDropManager().apply {
            setDraggingItemShadowDrawable(
                getDrawable(
                    com.h6ah4i.android.materialshadowninepatch.R.drawable.ms9_composite_shadow_z2
                ) as NinePatchDrawable
            )
        }
        wrappedAdapter = dragDropManager.createWrappedAdapter(adapter)
        binding.recyclerView.adapter = wrappedAdapter
        binding.recyclerView.itemAnimator = DraggableItemAnimator()
        dragDropManager.attachRecyclerView(binding.recyclerView)
        binding.recyclerView.setOnApplyWindowInsetsListener(
            ScrollingViewOnApplyWindowInsetsListener(binding.recyclerView)
        )
        binding.fab.setOnClickListener { onAddStorage() }

        Settings.STORAGES.observe(viewLifecycleOwner) { onStorageListChanged(it) }
    }

    override fun onPause() {
        super.onPause()

        dragDropManager.cancelDrag()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        dragDropManager.release()
        WrapperAdapterUtils.releaseAll(wrappedAdapter)
    }

    private fun onStorageListChanged(storages: List<Storage>) {
        binding.emptyView.fadeToVisibilityUnsafe(storages.isEmpty())
        adapter.replace(storages)
    }

    private fun onAddStorage() {
        startActivitySafe(AddStorageDialogActivity::class.createIntent())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.storage_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_import -> {
                importLauncher.launch(arrayOf("application/json"))
                true
            }
            R.id.action_export -> {
                exportLauncher.launch(EXPORT_FILE_NAME)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun exportStorages(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val json = StorageImportExport.exportStorages(
                        Settings.STORAGES.valueCompat
                    )
                    requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray())
                    } ?: throw IOException("Cannot open $uri for writing")
                    null
                } catch (e: Exception) {
                    e.printStackTrace()
                    e
                }
            }
            if (!isAdded) {
                return@launch
            }
            if (error != null) {
                showToast(error.toString())
            } else {
                showToast(R.string.storage_list_export_success)
            }
        }
    }

    private fun importStorages(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val json = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IOException("Cannot open $uri for reading")
                    Result.success(StorageImportExport.importStorages(json))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Result.failure(e)
                }
            }
            if (!isAdded) {
                return@launch
            }
            val importResult = result.getOrElse {
                showToast(it.toString())
                return@launch
            }
            for (storage in importResult.storages) {
                Storages.addOrReplace(storage)
            }
            showToast(
                getString(
                    R.string.storage_list_import_success_format, importResult.storages.size,
                    importResult.skippedCount
                )
            )
        }
    }

    override fun editStorage(storage: Storage) {
        startActivitySafe(storage.createEditIntent())
    }

    override fun moveStorage(fromPosition: Int, toPosition: Int) {
        Storages.move(fromPosition, toPosition)
    }

    companion object {
        private const val EXPORT_FILE_NAME = "material-files-servers.json"
    }
}
