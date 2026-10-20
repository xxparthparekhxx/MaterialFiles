/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import com.google.android.material.tabs.TabLayout
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.databinding.ActivityFileListBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.util.ACTION_PICK_DIRECTORY_OI
import me.zhanghai.android.files.navigation.NavigationRootMapLiveData
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.storage.createOrLog
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.valueCompat
import kotlinx.coroutines.launch
import java.net.URI
import java.util.ArrayList

class FileListActivity : AppActivity() {
    private lateinit var fragment: FileListFragment

    // Tab mode: the open tabs. Only used when Settings.FILE_LIST_TABS is enabled.
    private val tabIds = mutableListOf<Int>()
    private val tabLabels = mutableListOf<String>()
    private val tabPathUris = mutableListOf<String>()
    private val tabFragments = mutableMapOf<Int, FileListFragment>()
    private var currentTabId: Int? = null

    private lateinit var tabLayout: TabLayout

    // Tab mode: the fragment that currently owns the action bar.
    internal var activeTabFragment: FileListFragment? = null
        set(value) {
            field = value
            value?.let { updateSupportActionBar(it) }
        }

    private var currentPathObserver: Pair<FileListFragment, Observer<Path>>? = null

    // Prevents the tab bar updates from being mistaken for a user selection.
    private var isUpdatingTabBar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            tabIds.addAll((savedInstanceState.getIntArray(STATE_TAB_IDS) ?: intArrayOf()).toList())
            savedInstanceState.getStringArrayList(STATE_TAB_LABELS)?.let { tabLabels.addAll(it) }
            savedInstanceState.getStringArrayList(STATE_TAB_PATH_URIS)?.let { tabPathUris.addAll(it) }
            currentTabId = savedInstanceState.getInt(STATE_CURRENT_TAB_ID, -1)
                .takeIf { it >= 0 }
        }

        if (Settings.FILE_LIST_TABS.valueCompat) {
            setupTabs(savedInstanceState)
        } else {
            setupSinglePane(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_TAB_IDS, tabIds.toIntArray())
        outState.putStringArrayList(STATE_TAB_LABELS, ArrayList(tabLabels))
        outState.putStringArrayList(STATE_TAB_PATH_URIS, ArrayList(tabPathUris))
        outState.putInt(STATE_CURRENT_TAB_ID, currentTabId ?: -1)
    }

    private fun setupSinglePane(savedInstanceState: Bundle?) {
        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            fragment = FileListFragment().putArgs(FileListFragment.Args(intent))
            supportFragmentManager.commit { add(android.R.id.content, fragment) }
        } else {
            fragment = supportFragmentManager.findFragmentById(android.R.id.content)
                as FileListFragment
        }
    }

    private fun setupTabs(savedInstanceState: Bundle?) {
        val binding = ActivityFileListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tabLayout = binding.tabLayout
        // The tab bar is above the file list fragment, which applies the window insets to itself.
        // The top inset is applied to the tab bar here instead, so that it is not covered by the
        // status bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            tabLayout.updatePadding(top = systemBars.top)
            WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    Insets.of(0, 0, 0, systemBars.bottom)
                )
                .build()
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (!isUpdatingTabBar) {
                    showTabIndex(tab.position)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        restoreTabFragments()
        refreshTabBar()
        if (tabIds.isEmpty()) {
            val path = intent.extraPath ?: getStartPath()
            addTab(path, getTabLabel(path))
        } else {
            showTabIndex(currentTabIndex())
        }
    }

    /**
     * After the process is recreated, the fragments are restored by the fragment manager, but the
     * mapping from tab to fragment is lost. Match them by the tag they were added with.
     */
    private fun restoreTabFragments() {
        for (id in tabIds) {
            val fragment = supportFragmentManager.findFragmentByTag(tabFragmentTag(id))
            if (fragment is FileListFragment) {
                tabFragments[id] = fragment
            }
        }
    }

    private fun tabFragmentTag(id: Int): String = "tab_$id"

    private fun refreshTabBar() {
        isUpdatingTabBar = true
        tabLayout.removeAllTabs()
        for (i in tabIds.indices) {
            tabLayout.addTab(tabLayout.newTab().setText(tabLabels[i]), i)
        }
        val index = currentTabIndex()
        if (index >= 0) {
            tabLayout.getTabAt(index)?.let { tabLayout.selectTab(it) }
        }
        isUpdatingTabBar = false
    }

    private fun currentTabIndex(): Int {
        currentTabId?.let { id ->
            val index = tabIds.indexOf(id)
            if (index >= 0) {
                return index
            }
        }
        return tabIds.lastIndex
    }

    private fun showTabIndex(index: Int) {
        if (index < 0 || index >= tabIds.size) {
            return
        }
        val id = tabIds[index]
        val path = URI::class.createOrLog(tabPathUris[index])?.let { Paths.get(it) } ?: return
        val f = tabFragments.getOrPut(id) {
            FileListFragment().putArgs(FileListFragment.Args(Intent().apply { extraPath = path }))
        }
        supportFragmentManager.commit {
            if (!f.isAdded) {
                add(R.id.tabContainer, f, tabFragmentTag(id))
            }
        }
        for ((tabId, frag) in tabFragments) {
            val show = tabId == id
            // A fragment is hidden when it is not the visible one.
            if (show == frag.isHidden) {
                supportFragmentManager.commit {
                    if (show) {
                        frag.setHasOptionsMenu(true)
                        show(frag)
                    } else {
                        frag.setHasOptionsMenu(false)
                        hide(frag)
                    }
                }
            }
            if (show) {
                setSupportActionBarToolbar(f)
                currentTabId = id
                activeTabFragment = f
                this@FileListActivity.fragment = f
                observeCurrentPath(f, index)
            }
        }
    }

    /**
     * Keeps the tab label in sync with the folder the tab is showing. The fragment's view model
     * is only available once it is attached to a context.
     */
    private fun observeCurrentPath(f: FileListFragment, index: Int) {
        if (!f.isAdded) {
            f.lifecycleScope.launch {
                f.lifecycle.whenStarted {
                    observeCurrentPath(f, index)
                }
            }
            return
        }
        currentPathObserver?.let { (observed, observer) ->
            observed.fileListViewModel.currentPathLiveData.removeObserver(observer)
        }
        val observer = Observer<Path> { path ->
            if (index >= tabLabels.size) {
                return@Observer
            }
            // The path of the tab is the path that is shown, so that a new tab starts there and
            // the tab is restored at the right place.
            if (index < tabPathUris.size) {
                tabPathUris[index] = path.toUri().toString()
            }
            val label = getTabLabel(path)
            if (tabLabels[index] != label) {
                tabLabels[index] = label
                refreshTabBar()
            }
        }
        currentPathObserver = f to observer
        f.fileListViewModel.currentPathLiveData.observe(this, observer)
    }

    private fun setSupportActionBarToolbar(frag: FileListFragment) {
        frag.view?.let { view ->
            val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
            setSupportActionBar(toolbar)
        }
    }

    /**
     * Only the visible tab may take over the action bar, otherwise the toolbar of a background tab
     * would show up.
     */
    internal fun updateSupportActionBar(f: FileListFragment) {
        if (activeTabFragment == null || activeTabFragment === f) {
            f.toolbar?.let { setSupportActionBar(it) }
        }
    }

    /**
     * The name shown on a tab. A storage root is named after the storage volume instead of its
     * path, like the breadcrumb does.
     */
    private fun getTabLabel(path: Path): String {
        val navigationRoot = NavigationRootMapLiveData.valueCompat[path]
        if (navigationRoot != null) {
            return navigationRoot.getName(this)
        }
        return path.fileName.toString()
    }

    private fun addTab(path: Path, label: String) {
        val id = (tabIds.maxOrNull() ?: 0) + 1
        tabIds.add(id)
        tabLabels.add(label)
        tabPathUris.add(path.toUri().toString())
        currentTabId = id
        refreshTabBar()
        showTabIndex(tabIds.lastIndex)
    }

    private fun closeTab() {
        val index = currentTabIndex()
        if (index < 0 || index >= tabIds.size) {
            return
        }
        val id = tabIds.removeAt(index)
        tabLabels.removeAt(index)
        tabPathUris.removeAt(index)
        val removed = tabFragments.remove(id)
        removed?.takeIf { it.isAdded }?.let {
            supportFragmentManager.commit { remove(it) }
        }
        if (tabIds.isEmpty()) {
            val path = getStartPath()
            addTab(path, getTabLabel(path))
        } else {
            currentTabId = tabIds[index.coerceAtMost(tabIds.lastIndex)]
            refreshTabBar()
            showTabIndex(currentTabIndex())
        }
    }

    private fun getStartPath(): Path {
        if (Settings.FILE_LIST_REMEMBER_LAST_DIRECTORY.valueCompat) {
            val lastPath = Settings.FILE_LIST_LAST_DIRECTORY.valueCompat
            if (lastPath.isLinuxPath && java.io.File(lastPath.toString()).isDirectory) {
                return lastPath
            }
        }
        return Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
    }

    private fun newTab() {
        val index = currentTabIndex()
        val path = if (index in tabPathUris.indices) {
            URI::class.createOrLog(tabPathUris[index])?.let { Paths.get(it) }
        } else {
            null
        } ?: intent.extraPath ?: getStartPath()
        addTab(path, getTabLabel(path))
    }

    /**
     * Opens a folder in a new tab and switches to it.
     */
    fun openInNewTab(path: Path) {
        if (!Settings.FILE_LIST_TABS.valueCompat) {
            // The tabs are turned off, so there is no tab to open the path in.
            return
        }
        addTab(path, getTabLabel(path))
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (fragment.onKeyShortcut(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        if (Settings.FILE_LIST_TABS.valueCompat) {
            menuInflater.inflate(R.menu.file_list_tabs, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_tab -> {
                newTab()
                true
            }
            R.id.action_close_tab -> {
                closeTab()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val STATE_TAB_IDS = "tabIds"
        private const val STATE_TAB_LABELS = "tabLabels"
        private const val STATE_TAB_PATH_URIS = "tabPathUris"
        private const val STATE_CURRENT_TAB_ID = "currentTabId"

        fun createViewIntent(path: Path): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_VIEW)
                .apply { extraPath = path }

        fun createPickDirectoryIntent(path: Path? = null): Intent =
            FileListActivity::class.createIntent()
                .setAction(ACTION_PICK_DIRECTORY_OI)
                .apply { path?.let { extraPath = it } }
    }

    class OpenFileContract : ActivityResultContract<List<MimeType>, Path?>() {
        override fun createIntent(context: Context, input: List<MimeType>): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT)
                .setType(MimeType.ANY.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, input.map { it.value }.toTypedArray())

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class CreateFileContract : ActivityResultContract<Triple<MimeType, String?, Path?>, Path?>() {
        override fun createIntent(
            context: Context,
            input: Triple<MimeType, String?, Path?>
        ): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_CREATE_DOCUMENT)
                .setType(input.first.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .apply {
                    input.second?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    input.third?.let { extraPath = it }
                }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class OpenDirectoryContract : ActivityResultContract<Path?, Path?>() {
        override fun createIntent(context: Context, input: Path?): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .apply { input?.let { extraPath = it } }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }
}
