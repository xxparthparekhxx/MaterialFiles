/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.google.android.material.snackbar.Snackbar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.leinardi.android.speeddial.SpeedDialView
import java8.nio.file.AccessDeniedException
import java8.nio.file.FileVisitResult
import java8.nio.file.Files
import java8.nio.file.NoSuchFileException
import java8.nio.file.NotDirectoryException
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.SimpleFileVisitor
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset
import kotlin.math.roundToInt
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.provider.document.DocumentListingMessage
import me.zhanghai.android.files.coil.ThumbnailGeneration
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.checkSelfPermissionCompat
import me.zhanghai.android.files.compat.setGroupDividerEnabledCompat
import me.zhanghai.android.files.databinding.FileJobProgressCardBinding
import me.zhanghai.android.files.databinding.FileListFragmentAppBarIncludeBinding
import me.zhanghai.android.files.databinding.FileListFragmentBinding
import me.zhanghai.android.files.databinding.FileListFragmentBottomBarIncludeBinding
import me.zhanghai.android.files.databinding.FileListFragmentContentIncludeBinding
import me.zhanghai.android.files.databinding.FileListFragmentIncludeBinding
import me.zhanghai.android.files.databinding.FileListFragmentSpeedDialIncludeBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.provider.common.AndroidFileTypeDetector
import me.zhanghai.android.files.file.extension
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.filejob.FileJobProgress
import me.zhanghai.android.files.filejob.FileJobProgresses
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.FileSortOptions.By
import me.zhanghai.android.files.filelist.FileSortOptions.Order
import me.zhanghai.android.files.fileproperties.FilePropertiesDialogFragment
import me.zhanghai.android.files.ftpserver.FtpServerService
import me.zhanghai.android.files.navigation.BookmarkDirectories
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.navigation.NavigationFragment
import me.zhanghai.android.files.navigation.NavigationRootMapLiveData
import me.zhanghai.android.files.navigation.NavigationStorageRefreshLiveData
import me.zhanghai.android.files.provider.archive.createArchiveRootPath
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.isDirectory
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.terminal.Terminal
import me.zhanghai.android.files.ui.AppBarLayoutExpandHackListener
import me.zhanghai.android.files.ui.CoordinatorAppBarLayout
import me.zhanghai.android.files.ui.DrawerLayoutOnBackPressedCallback
import me.zhanghai.android.files.ui.FixQueryChangeSearchView
import me.zhanghai.android.files.ui.OverlayToolbarActionMode
import me.zhanghai.android.files.ui.PersistentBarLayout
import me.zhanghai.android.files.ui.PersistentBarLayoutToolbarActionMode
import me.zhanghai.android.files.ui.PersistentDrawerLayout
import me.zhanghai.android.files.ui.ScrollingViewOnApplyWindowInsetsListener
import me.zhanghai.android.files.ui.SpeedDialViewOnBackPressedCallback
import me.zhanghai.android.files.ui.ThemedFastScroller
import me.zhanghai.android.files.ui.ToolbarActionMode
import me.zhanghai.android.files.util.DebouncedRunnable
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import me.zhanghai.android.files.provider.common.UserActionRequiredException
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.findCauseByClass
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.addOnBackPressedCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.asFileName
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.checkSelfPermission
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.primaryText
import me.zhanghai.android.files.util.create
import me.zhanghai.android.files.util.createInstallPackageIntent
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent
import me.zhanghai.android.files.util.createSendStreamIntent
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.externalStorageRootPath
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.fadeToVisibilityUnsafe
import me.zhanghai.android.files.util.getDimensionDp
import me.zhanghai.android.files.util.getQuantityString
import me.zhanghai.android.files.util.hasSw600Dp
import me.zhanghai.android.files.util.hideSoftInput
import me.zhanghai.android.files.util.isOrientationLandscape
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.setOnEditorConfirmActionListener
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.supportsExternalStorageManager
import me.zhanghai.android.files.util.takeIfNotEmpty
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.viewModels
import me.zhanghai.android.files.util.withChooser
import me.zhanghai.android.files.viewer.image.ImageViewerActivity
import java.io.File

class FileListFragment : Fragment(), BreadcrumbLayout.Listener, FileListAdapter.Listener,
    ConfirmReplaceFileDialogFragment.Listener, OpenApkDialogFragment.Listener,
    ConfirmDeleteFilesDialogFragment.Listener, CreateArchiveDialogFragment.Listener,
    RenameFileDialogFragment.Listener, RenameFilesDialogFragment.Listener,
    CreateFileDialogFragment.Listener,
    CreateLinkDialogFragment.Listener,
    CreateDirectoryDialogFragment.Listener, NavigateToPathDialogFragment.Listener,
    NavigationFragment.Listener, ShowRequestAllFilesAccessRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionInSettingsRationaleDialogFragment.Listener {
    private val requestAllFilesAccessLauncher = registerForActivityResult(
        RequestAllFilesAccessContract(), this::onRequestAllFilesAccessResult
    )
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), this::onRequestStoragePermissionResult
    )
    private val requestStoragePermissionInSettingsLauncher = registerForActivityResult(
        RequestPermissionInSettingsContract(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
        this::onRequestStoragePermissionInSettingsResult
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), this::onRequestNotificationPermissionResult
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionInSettingsLauncher = registerForActivityResult(
        RequestPermissionInSettingsContract(android.Manifest.permission.POST_NOTIFICATIONS),
        this::onRequestNotificationPermissionInSettingsResult
    )

    private val args by args<Args>()
    private val argsPath by lazy { args.intent.extraPath }

    private val viewModel by viewModels { { FileListViewModel() } }

    internal val fileListViewModel: FileListViewModel
        get() = viewModel

    private lateinit var binding: Binding

    private lateinit var navigationFragment: NavigationFragment

    private lateinit var menuBinding: MenuBinding

    private lateinit var overlayActionMode: ToolbarActionMode

    private lateinit var bottomActionMode: ToolbarActionMode

    private lateinit var layoutManager: GridLayoutManager

    private var lastPath: Path? = null
    private var pendingHighlightPath: Path? = null

    private var errorDialog: AlertDialog? = null

    private var lastToastedFileListError: String? = null

    private var userRequestedRefresh = false

    private var hasPromptedUsbStorageAccess = false
    private var pendingScrollPath: Path? = null
    private var pendingScrollParent: Path? = null

    private var documentListingMessage: String? = null

    private val fileJobProgressCards = mutableMapOf<Int, FileJobProgressCardBinding>()

    private var fileListBasePaddingBottom = -1

    private var fabBaseBottomMargin = -1

    private lateinit var adapter: FileListAdapter

    private var appBarOffsetListener: AppBarLayout.OnOffsetChangedListener? = null

    private var drawerListener: DrawerLayout.DrawerListener? = null

    private var thumbnailEpoch = ThumbnailGeneration.epoch

    private val debouncedSearchRunnable = DebouncedRunnable(Handler(Looper.getMainLooper()), 1000) {
        if (!isResumed || !viewModel.isSearchViewExpanded) {
            return@DebouncedRunnable
        }
        val query = viewModel.searchViewQuery
        if (query.isEmpty()) {
            viewModel.stopSearching()
            return@DebouncedRunnable
        }
        viewModel.search(query)
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
        Binding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onDestroyView() {
        appBarOffsetListener?.let { binding.appBarLayout.removeOnOffsetChangedListener(it) }
        appBarOffsetListener = null
        drawerListener?.let { binding.drawerLayout?.removeDrawerListener(it) }
        drawerListener = null
        super.onDestroyView()

        adapter.dismissActivePopupMenu()
        errorDialog?.dismiss()
        errorDialog = null
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState == null) {
            navigationFragment = NavigationFragment()
            childFragmentManager.commit { add(R.id.navigationFragment, navigationFragment) }
        } else {
            navigationFragment = childFragmentManager.findFragmentById(R.id.navigationFragment)
                as NavigationFragment
        }
        navigationFragment.listener = this
        val activity = requireActivity() as AppCompatActivity
        activity.setTitle(R.string.file_list_title)
        activity.setSupportActionBar(binding.toolbar)
        overlayActionMode = OverlayToolbarActionMode(binding.overlayToolbar, binding.toolbar)
        bottomActionMode = PersistentBarLayoutToolbarActionMode(
            binding.persistentBarLayout, binding.bottomBarLayout, binding.bottomToolbar
        )
        val contentLayoutInitialPaddingBottom = binding.contentLayout.paddingBottom
        appBarOffsetListener = AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            binding.contentLayout.updatePaddingRelative(
                bottom = contentLayoutInitialPaddingBottom +
                    binding.appBarLayout.totalScrollRange + verticalOffset
            )
        }
        binding.appBarLayout.addOnOffsetChangedListener(appBarOffsetListener!!)
        binding.appBarLayout.syncBackgroundColorTo(binding.overlayToolbar)
        binding.breadcrumbLayout.setListener(this)
        if (!(activity.hasSw600Dp && activity.isOrientationLandscape)) {
            binding.swipeRefreshLayout.setProgressViewEndTarget(
                true, binding.swipeRefreshLayout.progressViewEndOffset
            )
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            userRequestedRefresh = true
            refresh()
        }
        layoutManager = GridLayoutManager(activity, 1)
        binding.recyclerView.layoutManager = layoutManager
        adapter = FileListAdapter(this)
        binding.recyclerView.adapter = adapter
        val fastScroller = ThemedFastScroller.create(binding.recyclerView)
        binding.recyclerView.setOnApplyWindowInsetsListener(
            ScrollingViewOnApplyWindowInsetsListener(binding.recyclerView, fastScroller)
        )
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && viewModel.isSearchViewExpanded) {
                    val searchView = if (this@FileListFragment::menuBinding.isInitialized) {
                        menuBinding.searchItem.actionView as? SearchView
                    } else {
                        null
                    }
                    searchView?.clearFocus()
                    recyclerView.hideSoftInput()
                }
            }
        })
        binding.speedDialView.inflate(R.menu.file_list_speed_dial)
        binding.speedDialView.setOnActionSelectedListener {
            when (it.id) {
                R.id.action_create_file -> showCreateFileDialog()
                R.id.action_create_directory -> showCreateDirectoryDialog()
                R.id.action_toggle_ftp_server -> toggleFtpServer()
            }
            // Returning false causes the speed dial to close without animation.
            //return false
            binding.speedDialView.close()
            true
        }
        // Long pressing the main button opens the current directory in a new window.
        binding.speedDialView.mainFab.setOnLongClickListener {
            binding.speedDialView.close()
            openInNewTask(currentPath)
            true
        }

        val viewLifecycleOwner = viewLifecycleOwner
        // Added first so that it has the lowest priority, and is only reached when nothing else
        // handles back.
        addOnBackPressedCallback(
            object : OnBackPressedCallback(Settings.FILE_LIST_DOUBLE_BACK_TO_EXIT.valueCompat) {
                private var lastBackPressedUptimeMillis = 0L

                override fun handleOnBackPressed() {
                    val uptimeMillis = SystemClock.uptimeMillis()
                    if (
                        uptimeMillis - lastBackPressedUptimeMillis <=
                        DOUBLE_BACK_TO_EXIT_TIMEOUT_MILLIS
                    ) {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                        return
                    }
                    lastBackPressedUptimeMillis = uptimeMillis
                    showToast(R.string.file_list_press_back_again_to_exit)
                }
            }
                .also { callback ->
                    Settings.FILE_LIST_DOUBLE_BACK_TO_EXIT.observe(viewLifecycleOwner) {
                        callback.isEnabled = it
                    }
                }
        )
        addOnBackPressedCallback(
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    if (Settings.FILE_LIST_BACK_EXITS.valueCompat) {
                        requireActivity().finish()
                    } else {
                        viewModel.navigateUp()
                    }
                }
            }
                .also { callback ->
                    viewModel.breadcrumbLiveData.observe(viewLifecycleOwner) {
                        callback.isEnabled = viewModel.canNavigateUpBreadcrumb
                    }
                }
        )
        addOnBackPressedCallback(
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    val searchView = if (this@FileListFragment::menuBinding.isInitialized) {
                        menuBinding.searchItem.actionView as? SearchView
                    } else {
                        null
                    }
                    val searchSrcText = searchView?.findViewById<View>(androidx.appcompat.R.id.search_src_text)
                    val isKeyboardVisible = searchSrcText?.let {
                        ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime())
                    } ?: false
                    if (searchView != null && (isKeyboardVisible || searchView.hasFocus())) {
                        searchView.clearFocus()
                        searchSrcText?.hideSoftInput()
                    } else {
                        collapseSearchView()
                    }
                }
            }
                .also { callback ->
                    viewModel.searchViewExpandedLiveData.observe(viewLifecycleOwner) {
                        callback.isEnabled = it
                    }
                }
        )
        addOnBackPressedCallback(overlayActionMode.onBackPressedCallback)
        addOnBackPressedCallback(SpeedDialViewOnBackPressedCallback(binding.speedDialView))
        binding.drawerLayout?.let {
            addOnBackPressedCallback(DrawerLayoutOnBackPressedCallback(it))
            // When swiping is disabled the drawer is locked closed, so that it can only be opened
            // with the navigation button. Unlock it while it is open so that it can still be
            // dismissed by swiping.
            fun updateDrawerLockMode(isOpen: Boolean) {
                it.setDrawerLockMode(
                    if (Settings.FILE_LIST_DRAWER_SWIPE.valueCompat || isOpen) {
                        DrawerLayout.LOCK_MODE_UNLOCKED
                    } else {
                        DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                    }
                )
            }
            drawerListener = object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) {
                    NavigationStorageRefreshLiveData.notifyChanged()
                    updateDrawerLockMode(true)
                }

                override fun onDrawerClosed(drawerView: View) {
                    updateDrawerLockMode(false)
                }
            }
            it.addDrawerListener(drawerListener!!)
            Settings.FILE_LIST_DRAWER_SWIPE.observe(viewLifecycleOwner) { _ ->
                updateDrawerLockMode(it.isDrawerOpen(GravityCompat.START))
            }
        }

        if (!viewModel.hasTrail) {
            var path = argsPath
            val intent = args.intent
            var pickOptions: PickOptions? = null
            when (val action = intent.action) {
                Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT,
                Intent.ACTION_CREATE_DOCUMENT -> {
                    val mode = if (action == Intent.ACTION_CREATE_DOCUMENT) {
                        PickOptions.Mode.CREATE_FILE
                    } else {
                        PickOptions.Mode.OPEN_FILE
                    }
                    val mimeType = intent.type?.asMimeTypeOrNull() ?: MimeType.ANY
                    val fileName = if (mode == PickOptions.Mode.CREATE_FILE) {
                        intent.getStringExtra(Intent.EXTRA_TITLE)?.asFileNameOrNull()?.value
                            ?: mimeType.extension?.let { "file.$it" } ?: "file"
                    } else {
                        null
                    }
                    val readOnly = action == Intent.ACTION_GET_CONTENT
                    val extraMimeTypes = if (mode == PickOptions.Mode.OPEN_FILE) {
                        intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
                            ?.mapNotNull { it.asMimeTypeOrNull() }?.takeIfNotEmpty()
                    } else {
                        null
                    }
                    val mimeTypes = extraMimeTypes ?: listOf(mimeType)
                    val localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)
                    val allowMultiple = mode != PickOptions.Mode.CREATE_FILE &&
                        intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                    pickOptions =
                        PickOptions(mode, fileName, readOnly, mimeTypes, localOnly, allowMultiple)
                }
                Intent.ACTION_OPEN_DOCUMENT_TREE -> {
                    val localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)
                    pickOptions = PickOptions(
                        PickOptions.Mode.OPEN_DIRECTORY, null, false, emptyList(), localOnly, false
                    )
                }
                ACTION_VIEW_DOWNLOADS ->
                    path = Paths.get(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        ).path
                    )
                else -> {
                    intent.externalStorageRootPath()?.let { path = it }
                    if (path != null) {
                        val mimeType = intent.type?.asMimeTypeOrNull()
                        path = when {
                            mimeType != null && path.isArchiveFile(mimeType) ->
                                path.createArchiveRootPath()
                            path.isDirectory(LinkOption.NOFOLLOW_LINKS) -> path
                            else -> path.parent ?: path
                        }
                    }
                }
            }
            if (path == null) {
                path = getStartPath(pickOptions)
            }
            viewModel.resetTo(path)
            if (pickOptions != null) {
                viewModel.pickOptions = pickOptions
            }
        }
        viewModel.currentPathLiveData.observe(viewLifecycleOwner) { onCurrentPathChanged(it) }
        viewModel.searchViewExpandedLiveData.observe(viewLifecycleOwner) {
            onSearchViewExpandedChanged(it)
        }
        viewModel.breadcrumbLiveData.observe(viewLifecycleOwner) {
            binding.breadcrumbLayout.setData(it)
        }
        viewModel.viewTypeLiveData.observe(viewLifecycleOwner) { onViewTypeChanged(it) }
        // Live data only calls observeForever() on its sources when it is active, so we have to
        // make view type live data active first (so that it can load its initial value) before we
        // register another observer that needs to get the view type.
        if (binding.persistentDrawerLayout != null) {
            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.observe(viewLifecycleOwner) {
                onPersistentDrawerOpenChanged(it)
            }
        }
        Settings.FILE_LIST_HIDDEN_FIRST.observe(viewLifecycleOwner) { adapter.isHiddenFirst = it }
        Settings.FILE_LIST_FOLDER_SORT_BY.observe(viewLifecycleOwner) {
            adapter.folderSortBy = when (it) {
                "1" -> By.NAME
                "2" -> By.LAST_MODIFIED
                else -> null
            }
        }
        viewModel.sortOptionsLiveData.observe(viewLifecycleOwner) { onSortOptionsChanged(it) }
        viewModel.viewSortPathSpecificLiveData.observe(viewLifecycleOwner) {
            onViewSortPathSpecificChanged(it)
        }
        viewModel.pickOptionsLiveData.observe(viewLifecycleOwner) { onPickOptionsChanged(it) }
        viewModel.selectedFilesLiveData.observe(viewLifecycleOwner) { onSelectedFilesChanged(it) }
        viewModel.pasteStateLiveData.observe(viewLifecycleOwner) { onPasteStateChanged(it) }
        Settings.FILE_NAME_ELLIPSIZE.observe(viewLifecycleOwner) { onFileNameEllipsizeChanged(it) }
        Settings.FILE_LIST_FONT_SIZE.observe(viewLifecycleOwner) {
            adapter.fontScale = (it.toIntOrNull() ?: 100) / 100f
        }
        Settings.FILE_LIST_PINNED_PATHS.observe(viewLifecycleOwner) {
            adapter.pinnedPaths = it.toSet()
        }
        Settings.FILE_LIST_FIT_THUMBNAILS.observe(viewLifecycleOwner) {
            adapter.fitThumbnails = it
        }
        Settings.FILE_LIST_HIDE_ADD_BUTTON.observe(viewLifecycleOwner) { updateAddButton() }
        Settings.FILE_LIST_DIVIDERS.observe(viewLifecycleOwner) { updateDividers() }
        Settings.FILE_LIST_DENSITY.observe(viewLifecycleOwner) { adapter.listDensity = it }
        Settings.FILE_LIST_GRID_SPAN_COUNT.observe(viewLifecycleOwner) { updateSpanCount() }
        viewModel.fileListLiveData.observe(viewLifecycleOwner) { onFileListChanged(it) }
        DocumentListingMessage.liveData.observe(viewLifecycleOwner) { message ->
            documentListingMessage = message
            if (!message.isNullOrBlank() && viewModel.fileListStateful is Loading &&
                !viewModel.searchState.isSearching
            ) {
                binding.toolbar.subtitle = message
            }
        }
        FileJobProgresses.liveData.observe(viewLifecycleOwner) { onFileJobProgressChanged(it) }
        binding.fileJobProgressLayout.addOnLayoutChangeListener { sheet, _, _, _, _, _, _, _, _ ->
            positionFabAboveFileJobs(sheet)
        }
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.observe(viewLifecycleOwner) {
            onShowHiddenFilesChanged(it)
        }
        Settings.FILE_LIST_HIDDEN_PATHS.observe(viewLifecycleOwner) {
            updateAdapterFileList()
        }
        Settings.FILE_LIST_SHOW_ITEM_COUNT.observe(viewLifecycleOwner) {
            val stateful = viewModel.fileListStateful
            val files = stateful.value
            if (files != null && stateful !is Failure) {
                binding.toolbar.subtitle = getSubtitle(files)
            }
        }
        Settings.FILE_LIST_SHOW_DIRECTORY_ITEM_COUNT.observe(viewLifecycleOwner) {
            adapter.invalidateDirectoryItemCounts()
        }
        Settings.FILE_LIST_STANDARD_DIRECTORY_ICONS.observe(viewLifecycleOwner) {
            updateAdapterFileList()
        }
        adapter.lastActivatedPath = viewModel.lastActivatedPath
        Settings.FILE_LIST_INDICATE_LAST_OPENED_ITEM.observe(viewLifecycleOwner) {
            adapter.indicateLastOpenedItem = it
        }
        Settings.FILE_LIST_LOCK_HEADER.observe(viewLifecycleOwner) {
            updateToolbarScrollFlags()
        }
        updateToolbarScrollFlags()
        Settings.FILE_LIST_LOADING_INDICATOR.observe(viewLifecycleOwner) {
            if (!it) {
                binding.progress.fadeToVisibilityUnsafe(false)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val epoch = ThumbnailGeneration.epoch
        if (epoch != thumbnailEpoch) {
            thumbnailEpoch = epoch
            adapter.reloadThumbnails()
        }
    }

    override fun onResume() {
        super.onResume()

        if (!viewModel.isNotificationPermissionRequested) {
            ensureStorageAccess()
        }
        if (!viewModel.isStorageAccessRequested) {
            ensureNotificationPermission()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        menuBinding = MenuBinding.inflate(menu, inflater)
        showLabelsOnTablet(menu, R.id.action_view_sort)
        setUpSearchView()
    }

    private fun setUpSearchView() {
        val searchView = menuBinding.searchItem.actionView as FixQueryChangeSearchView
        // MenuItem.OnActionExpandListener.onMenuItemActionExpand() is called before SearchView
        // resets the query.
        searchView.setOnSearchClickListener {
            viewModel.isSearchViewExpanded = true
            searchView.setQuery(viewModel.searchViewQuery, false)
            debouncedSearchRunnable()
        }
        // SearchView.OnCloseListener.onClose() is not always called.
        menuBinding.searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.isSearchViewExpanded = false
                viewModel.stopSearching()
                return true
            }
        })
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                debouncedSearchRunnable.cancel()
                if (query.isEmpty()) {
                    viewModel.stopSearching()
                } else {
                    viewModel.search(query)
                }
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                if (searchView.shouldIgnoreQueryChange) {
                    return false
                }
                viewModel.searchViewQuery = query
                if (query.isEmpty()) {
                    debouncedSearchRunnable.cancel()
                    viewModel.stopSearching()
                } else {
                    debouncedSearchRunnable()
                }
                return false
            }
        })
        if (viewModel.isSearchViewExpanded) {
            menuBinding.searchItem.expandActionView()
        }
    }

    private fun collapseSearchView() {
        if (this::menuBinding.isInitialized && menuBinding.searchItem.isActionViewExpanded) {
            menuBinding.searchItem.collapseActionView()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        updateViewSortMenuItems()
        updateSelectAllMenuItem()
        updateShowHiddenFilesMenuItem()
        menu.findItem(R.id.action_rescan_media)?.isVisible = currentPath.isLinuxPath
        updateAddButton()
        updateArchiveFileNameEncodingMenuItem()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout?.openDrawer(GravityCompat.START)
                if (binding.persistentDrawerLayout != null) {
                    Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.putValue(
                        !Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat
                    )
                }
                true
            }
            R.id.action_view_sort -> {
                ViewSortBottomSheetFragment.show(this)
                true
            }
            R.id.action_new_folder -> {
                showCreateDirectoryDialog()
                true
            }
            R.id.action_new_file -> {
                showCreateFileDialog()
                true
            }
            R.id.action_new_task -> {
                newTask()
                true
            }
            R.id.action_navigate_up -> {
                navigateUp()
                true
            }
            R.id.action_navigate_to -> {
                showNavigateToPathDialog()
                true
            }
            R.id.action_refresh -> {
                refresh()
                true
            }
            R.id.action_archive_file_name_encoding -> {
                showArchiveFileNameEncodingDialog()
                true
            }
            R.id.action_select_all -> {
                selectAllFiles()
                true
            }
            R.id.action_show_hidden_files -> {
                setShowHiddenFiles(!menuBinding.showHiddenFilesItem.isChecked)
                true
            }
            R.id.action_share -> {
                share()
                true
            }
            R.id.action_copy_path -> {
                copyPath()
                true
            }
            R.id.action_open_in_terminal -> {
                openInTerminal()
                true
            }
            R.id.action_rescan_media -> {
                rescanMedia()
                true
            }
            R.id.action_add_bookmark -> {
                addBookmark()
                true
            }
            R.id.action_create_shortcut -> {
                createShortcut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (bottomActionMode.isActive) {
            val menu = bottomActionMode.menu
            menu.setQwertyMode(
                KeyCharacterMap.load(event.deviceId).keyboardType != KeyCharacterMap.NUMERIC
            )
            if (menu.performShortcut(keyCode, event, 0)) {
                return true
            }
        }
        if (overlayActionMode.isActive) {
            val menu = overlayActionMode.menu
            menu.setQwertyMode(
                KeyCharacterMap.load(event.deviceId).keyboardType != KeyCharacterMap.NUMERIC
            )
            if (menu.performShortcut(keyCode, event, 0)) {
                return true
            }
        }
        return false
    }

    private fun onPersistentDrawerOpenChanged(open: Boolean) {
        binding.persistentDrawerLayout?.let {
            if (open) {
                it.openDrawer(GravityCompat.START)
            } else {
                it.closeDrawer(GravityCompat.START)
            }
        }
        updateSpanCount()
    }

    private fun getStartPath(pickOptions: PickOptions?): Path {
        if (pickOptions == null && Settings.FILE_LIST_REMEMBER_LAST_DIRECTORY.valueCompat) {
            val lastPath = Settings.FILE_LIST_LAST_DIRECTORY.valueCompat
            // Only restore local directories that still exist, so that we neither stall on a
            // remote server nor open a folder that was deleted since.
            if (lastPath.isLinuxPath && File(lastPath.toString()).isDirectory) {
                return lastPath
            }
        }
        return Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
    }

    /**
     * The add button, or its replacement entries in the toolbar menu, are only available when the
     * current directory is writable.
     */
    private fun updateAddButton() {
        val canCreate = !currentPath.fileSystem.isReadOnly
        val hideAddButton = Settings.FILE_LIST_HIDE_ADD_BUTTON.valueCompat
        binding.speedDialView.isVisible = canCreate && !hideAddButton
        if (this::menuBinding.isInitialized) {
            menuBinding.menu.findItem(R.id.action_new_folder)?.isVisible =
                canCreate && hideAddButton
            menuBinding.menu.findItem(R.id.action_new_file)?.isVisible =
                canCreate && hideAddButton
        }
    }

    private fun onCurrentPathChanged(path: Path) {
        // When going up to an ancestor, remember the child we came from so it can be highlighted.
        val previousPath = lastPath
        lastPath = path
        pendingHighlightPath = if (previousPath != null && previousPath != path &&
            previousPath.fileSystem == path.fileSystem && previousPath.startsWith(path)
        ) {
            var child: Path = previousPath
            while (child.parent != path) {
                child = child.parent ?: break
            }
            child
        } else {
            null
        }
        if (pendingHighlightPath != null) {
            viewModel.lastActivatedPath = pendingHighlightPath
            adapter.lastActivatedPath = pendingHighlightPath
        } else if (viewModel.lastActivatedPath?.parent != path && viewModel.lastActivatedPath != path) {
            viewModel.lastActivatedPath = null
            adapter.lastActivatedPath = null
        }
        if (viewModel.pickOptions == null && path.isLinuxPath &&
            Settings.FILE_LIST_REMEMBER_LAST_DIRECTORY.valueCompat) {
            Settings.FILE_LIST_LAST_DIRECTORY.putValue(path)
        }
        updateAddButton()
        updateOverlayToolbar()
        updateBottomToolbar()
        ensureUsbStorageAccess(path)
    }

    private fun ensureUsbStorageAccess(path: Path) {
        if (hasPromptedUsbStorageAccess || viewModel.isStorageAccessRequested) {
            return
        }
        if (!shouldRequestAllFilesAccessForPath(path)) {
            return
        }
        hasPromptedUsbStorageAccess = true
        ShowRequestAllFilesAccessRationaleDialogFragment.show(this)
        viewModel.isStorageAccessRequested = true
    }

    private fun onSearchViewExpandedChanged(expanded: Boolean) {
        updateViewSortMenuItems()
        if (!expanded || !this::menuBinding.isInitialized) {
            return
        }
        val searchItem = menuBinding.searchItem
        if (!searchItem.isActionViewExpanded) {
            searchItem.expandActionView()
        }
    }

    private fun onFileJobProgressChanged(progresses: List<FileJobProgress>) {
        val layout = binding.fileJobProgressCardsLayout
        val ids = progresses.mapTo(mutableSetOf()) { it.id }
        for (id in fileJobProgressCards.keys.toList()) {
            if (id !in ids) {
                layout.removeView(fileJobProgressCards.remove(id)!!.root)
            }
        }
        progresses.forEachIndexed { index, progress ->
            val card = fileJobProgressCards.getOrPut(progress.id) {
                FileJobProgressCardBinding.inflate(layoutInflater, layout, false).also { binding ->
                    binding.root.tag = progress.id
                    binding.fileJobProgressCancel.setOnClickListener {
                        FileJobService.cancelJob(binding.root.tag as Int)
                    }
                    layout.addView(binding.root)
                }
            }
            card.root.tag = progress.id
            bindFileJobProgressCard(card, progress)
            if (layout.indexOfChild(card.root) != index) {
                layout.removeView(card.root)
                layout.addView(card.root, index)
            }
        }
        binding.fileJobProgressLayout.isVisible = progresses.isNotEmpty()
    }

    private fun positionFabAboveFileJobs(sheet: View) {
        val sheetHeight = if (sheet.isVisible) sheet.height else 0
        val fab = binding.speedDialView
        fab.translationY = 0f
        val params = fab.layoutParams as ViewGroup.MarginLayoutParams
        if (fabBaseBottomMargin < 0) {
            fabBaseBottomMargin = params.bottomMargin
        }
        val gap = (8 * resources.displayMetrics.density).toInt()
        val desiredMargin = fabBaseBottomMargin + sheetHeight + if (sheetHeight > 0) gap else 0
        if (params.bottomMargin != desiredMargin) {
            params.bottomMargin = desiredMargin
            fab.layoutParams = params
        }
        val recycler = binding.recyclerView
        if (fileListBasePaddingBottom < 0) {
            fileListBasePaddingBottom = recycler.paddingBottom.coerceAtLeast(
                resources.getDimensionPixelSize(R.dimen.list_bottom_padding_with_fab)
            )
        }
        val desiredPadding = fileListBasePaddingBottom + sheetHeight
        if (recycler.paddingBottom != desiredPadding) {
            recycler.updatePadding(bottom = desiredPadding)
        }
    }

    private fun bindFileJobProgressCard(card: FileJobProgressCardBinding, progress: FileJobProgress) {
        card.fileJobProgressFile.text = progress.fileName ?: progress.title
        val speed = progress.speedText
        val speedLines = speed?.split('\n', limit = 2)
        if (speedLines != null && speedLines.size == 2) {
            card.fileJobProgressSpeed.text = speedLines[0]
            card.fileJobProgressSpeedUnit.isVisible = true
            card.fileJobProgressSpeedUnit.text = speedLines[1]
        } else {
            card.fileJobProgressSpeed.text = speed ?: progress.fileText ?: progress.text
            card.fileJobProgressSpeedUnit.isVisible = false
        }
        val primaryMax: Int
        val primaryProgress: Int
        val primaryIndeterminate: Boolean
        if (progress.showFileProgress) {
            primaryMax = progress.fileMax
            primaryProgress = progress.fileProgress
            primaryIndeterminate = false
        } else {
            primaryMax = progress.max
            primaryProgress = progress.progress
            primaryIndeterminate = progress.indeterminate
        }
        bindProgressBar(
            card.fileJobProgressCircle, primaryMax, primaryProgress, primaryIndeterminate
        )
        val overall = if (progress.showFileProgress) progress.text else null
        card.fileJobProgressText.isVisible = !overall.isNullOrEmpty()
        card.fileJobProgressText.text = overall
    }

    private fun bindProgressBar(
        bar: BaseProgressIndicator<*>,
        max: Int,
        progress: Int,
        indeterminate: Boolean
    ) {
        bar.isIndeterminate = indeterminate
        if (!indeterminate) {
            bar.max = max.coerceAtLeast(1)
            bar.setProgressCompat(progress.coerceIn(0, bar.max), true)
        }
    }

    private fun onFileListChanged(stateful: Stateful<List<FileItem>>) {
        val files = stateful.value
        val isSearching = viewModel.searchState.isSearching
        val isLoading = stateful is Loading
        val showUserRefresh = userRequestedRefresh && isLoading
        if (!isLoading) {
            userRequestedRefresh = false
        }
        val hasFiles = !files.isNullOrEmpty()
        when {
            stateful is Failure -> binding.toolbar.setSubtitle(R.string.error)
            isLoading && !isSearching && (!hasFiles || showUserRefresh) -> {
                val message = documentListingMessage
                if (message.isNullOrBlank()) {
                    binding.toolbar.setSubtitle(R.string.loading)
                } else {
                    binding.toolbar.subtitle = message
                }
            }
            else -> binding.toolbar.subtitle = getSubtitle(files!!)
        }
        binding.swipeRefreshLayout.isRefreshing = showUserRefresh && (hasFiles || isSearching)
        binding.progress.fadeToVisibilityUnsafe(
            Settings.FILE_LIST_LOADING_INDICATOR.valueCompat && stateful is Loading && !(hasFiles || isSearching)
        )
        binding.errorText.fadeToVisibilityUnsafe(stateful is Failure && !hasFiles)
        val throwable = (stateful as? Failure)?.throwable
        if (throwable != null && !isSearching && throwable.isMissingDirectory()) {
            if (viewModel.dropMissingCurrentPath()) {
                showToast(getString(R.string.file_list_error_directory_not_found))
                return
            }
            // Couldn't navigate away (e.g. already at trail root): fall through and show a
            // friendly message instead of the raw exception below.
        }
        if (throwable != null) {
            throwable.printStackTrace()
            val userAction = throwable.findCauseByClass<UserActionRequiredException>()
            if (userAction != null && userAction !== viewModel.promptedUserAction) {
                viewModel.promptedUserAction = userAction
                promptUserAction(userAction)
            }
            val error = throwable.toUserFriendlyMessage()
            if (hasFiles && userAction == null) {
                if (Settings.ERRORS_IN_DIALOG.valueCompat) {
                    errorDialog?.dismiss()
                    errorDialog = MaterialAlertDialogBuilder(requireContext())
                        .setMessage(error)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else if (error != lastToastedFileListError) {
                    lastToastedFileListError = error
                    showToast(error)
                }
            } else {
                lastToastedFileListError = null
                binding.errorText.text = error
            }
        } else if (stateful is Success) {
            lastToastedFileListError = null
        }
        binding.emptyView.fadeToVisibilityUnsafe(stateful is Success && !hasFiles)
        if (files != null) {
            updateAdapterFileList()
        } else {
            // This resets animation as well.
            adapter.clear()
        }
        if (stateful is Success) {
            viewModel.pendingState?.let { layoutManager.onRestoreInstanceState(it) }
            highlightPendingPath()
            scrollToPendingPath()
        }
    }

    private fun highlightPendingPath() {
        val path = pendingHighlightPath ?: return
        val position = adapter.getFilePosition(path)
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        pendingHighlightPath = null
        val recyclerView = binding.recyclerView
        val flash = {
            recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.let { view ->
                view.isPressed = true
                view.postDelayed({ view.isPressed = false }, 400)
            }
            Unit
        }
        if (recyclerView.findViewHolderForAdapterPosition(position) != null) {
            flash()
        } else {
            layoutManager.scrollToPosition(position)
            recyclerView.post { flash() }
        }
    }

    private fun getSubtitle(files: List<FileItem>): String {
        val directoryCount = files.count { it.attributes.isDirectory }
        val fileCount = files.size - directoryCount
        val directoryCountText = if (directoryCount > 0) {
            getQuantityString(
                R.plurals.file_list_subtitle_directory_count_format, directoryCount, directoryCount
            )
        } else {
            null
        }
        val fileCountText = if (fileCount > 0) {
            getQuantityString(
                R.plurals.file_list_subtitle_file_count_format, fileCount, fileCount
            )
        } else {
            null
        }
        val breakdownText = when {
            !directoryCountText.isNullOrEmpty() && !fileCountText.isNullOrEmpty() ->
                (directoryCountText + getString(R.string.file_list_subtitle_separator)
                    + fileCountText)
            !directoryCountText.isNullOrEmpty() -> directoryCountText
            !fileCountText.isNullOrEmpty() -> fileCountText
            else -> getString(R.string.empty)
        }
        if (Settings.FILE_LIST_SHOW_ITEM_COUNT.valueCompat) {
            val itemCountText = getQuantityString(
                R.plurals.file_list_subtitle_item_count_format, files.size, files.size
            )
            return if (files.isNotEmpty()) {
                itemCountText + getString(R.string.file_list_subtitle_separator) + breakdownText
            } else {
                breakdownText
            }
        }
        return breakdownText
    }

    private fun onViewTypeChanged(viewType: FileViewType) {
        updateSpanCount()
        adapter.viewType = viewType
        updateViewSortMenuItems()
        updateDividers()
    }

    private var dividerDecoration: DividerItemDecoration? = null

    private fun updateDividers() {
        val recyclerView = binding.recyclerView
        dividerDecoration?.let { recyclerView.removeItemDecoration(it) }
        dividerDecoration = null
        if (Settings.FILE_LIST_DIVIDERS.valueCompat && viewModel.viewType != FileViewType.GRID) {
            dividerDecoration = DividerItemDecoration(
                recyclerView.context, DividerItemDecoration.VERTICAL
            ).also { recyclerView.addItemDecoration(it) }
        }
    }

    private fun updateToolbarScrollFlags() {
        val layoutParams =
            (binding.toolbar.parent as View).layoutParams as? AppBarLayout.LayoutParams ?: return
        if (Settings.FILE_LIST_LOCK_HEADER.valueCompat) {
            layoutParams.scrollFlags = 0
            binding.appBarLayout.setExpanded(true)
        } else {
            layoutParams.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
        }
    }

    private fun updateSpanCount() {
        layoutManager.spanCount = when (viewModel.viewType) {
            FileViewType.LIST, FileViewType.COMPACT_LIST -> 1
            FileViewType.GRID -> {
                Settings.FILE_LIST_GRID_SPAN_COUNT.valueCompat.toIntOrNull()
                    ?.takeIf { it >= 2 }
                    ?: run {
                        var widthDp = resources.configuration.screenWidthDp
                        val persistentDrawerLayout = binding.persistentDrawerLayout
                        if (persistentDrawerLayout != null &&
                            persistentDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                            widthDp -= getDimensionDp(R.dimen.navigation_max_width).roundToInt()
                        }
                        (widthDp / 180).coerceAtLeast(2)
                    }
            }
        }
    }

    private fun onSortOptionsChanged(sortOptions: FileSortOptions) {
        adapter.sortOptions = sortOptions
        updateViewSortMenuItems()
    }

    private fun onViewSortPathSpecificChanged(pathSpecific: Boolean) {
        updateViewSortMenuItems()
    }

    private fun updateViewSortMenuItems() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.viewSortItem.isVisible = !viewModel.isSearchViewExpanded
    }

    private fun navigateUp() {
        collapseSearchView()
        viewModel.navigateUp()
    }

    private fun showNavigateToPathDialog() {
        NavigateToPathDialogFragment.show(currentPath, this)
    }

    private fun newTask() {
        openInNewTask(currentPath)
    }

    private fun refresh() {
        adapter.invalidateDirectoryItemCounts()
        viewModel.reload()
    }

    private fun promptUserAction(exception: UserActionRequiredException) {
        val userAction = exception.getUserAction(
            object : Continuation<Boolean> {
                override val context: CoroutineContext
                    get() = EmptyCoroutineContext

                override fun resumeWith(result: Result<Boolean>) {
                    val postedView = view ?: return
                    postedView.post {
                        if (result.getOrDefault(false)) {
                            viewModel.promptedUserAction = null
                            refresh()
                        }
                    }
                }
            },
            requireContext()
        )
        startActivity(userAction.intent)
    }

    private fun updateArchiveFileNameEncodingMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.archiveFileNameEncodingItem.isVisible = currentPath.isArchivePath
    }

    private fun showArchiveFileNameEncodingDialog() {
        val names = Charset.availableCharsets().keys.toTypedArray()
        val current = Settings.ARCHIVE_FILE_NAME_ENCODING.valueCompat
        val checked = names.indexOfFirst { it.equals(current, ignoreCase = true) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_archive_file_name_encoding_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                Settings.ARCHIVE_FILE_NAME_ENCODING.putValue(names[which])
                viewModel.reload()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setShowHiddenFiles(showHiddenFiles: Boolean) {
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.putValue(showHiddenFiles)
    }

    private fun onShowHiddenFilesChanged(showHiddenFiles: Boolean) {
        adapter.invalidateDirectoryItemCounts()
        updateAdapterFileList()
        updateShowHiddenFilesMenuItem()
    }

    private fun updateAdapterFileList() {
        var files = viewModel.fileListStateful.value ?: return
        if (!Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat) {
            files = files.filterNot { it.isHidden }
        }
        val hiddenPaths = Settings.FILE_LIST_HIDDEN_PATHS.valueCompat
        if (hiddenPaths.isNotEmpty()) {
            files = files.filterNot { it.path.toUri().toString() in hiddenPaths }
        }
        adapter.replaceListAndIsSearching(files, viewModel.searchState.isSearching)
    }

    private fun updateShowHiddenFilesMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val showHiddenFiles = Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat
        menuBinding.showHiddenFilesItem.isChecked = showHiddenFiles
    }

    private fun share() {
        shareFile(currentPath, MimeType.DIRECTORY)
    }

    private fun rescanMedia() {
        val context = requireContext().applicationContext
        val directory = currentPath.toFile()
        showToast(R.string.file_list_rescan_media_started)
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val paths = directory.walkTopDown().filter { it.isFile }.map { it.path }.toList()
            if (paths.isNotEmpty()) {
                MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
            }
        }
    }

    private fun copyPath() {
        copyPath(currentPath)
    }

    private fun openInTerminal() {
        val path = currentPath
        if (path.isLinuxPath) {
            Terminal.open(path.toFile().path, requireContext())
        } else {
            // TODO
        }
    }

    override fun navigateTo(path: Path) {
        val state = layoutManager.onSaveInstanceState()
        viewModel.rememberSearchForReturn(path)
        collapseSearchView()
        viewModel.navigateTo(state!!, path)
    }

    override fun copyPath(path: Path) {
        val text = path.toClipboardString()
        clipboardManager.primaryText = text
        val message = android.widget.TextView(requireContext()).apply {
            this.text = text
            setTextIsSelectable(true)
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.file_item_action_copy_path)
            .setView(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun openInNewTask(path: Path) {
        val intent = FileListActivity.createViewIntent(path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        startActivitySafe(intent)
    }

    private fun onPickOptionsChanged(pickOptions: PickOptions?) {
        val title = if (pickOptions == null) {
            getString(R.string.file_list_title)
        } else {
            val count = if (pickOptions.allowMultiple) Int.MAX_VALUE else 1
            when (pickOptions.mode) {
                PickOptions.Mode.OPEN_FILE ->
                    getQuantityString(R.plurals.file_list_title_open_file, count)
                PickOptions.Mode.CREATE_FILE -> getString(R.string.file_list_title_create_file)
                PickOptions.Mode.OPEN_DIRECTORY ->
                    getQuantityString(R.plurals.file_list_title_open_directory, count)
            }
        }
        requireActivity().title = title
        updateSelectAllMenuItem()
        updateOverlayToolbar()
        updateBottomToolbar()
        adapter.pickOptions = pickOptions
    }

    private fun updateSelectAllMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val pickOptions = viewModel.pickOptions
        menuBinding.selectAllItem.isVisible = pickOptions == null || pickOptions.allowMultiple
    }

    private fun pickFiles(files: FileItemSet) {
        pickPaths(files.mapTo(linkedSetOf()) { it.path })
    }

    private fun pickPaths(paths: LinkedHashSet<Path>) {
        val pickOptions = viewModel.pickOptions!!
        if (pickOptions.localOnly && paths.any { it.isRemotePath }) {
            showToast(R.string.file_list_pick_local_only_error)
            return
        }
        val intent = Intent().apply {
            if (paths.size == 1) {
                val path = paths.single()
                data = path.fileProviderUri
                extraPath = path
            } else {
                val mimeTypes = pickOptions.mimeTypes.map { it.value }
                val items = paths.map { ClipData.Item(it.fileProviderUri) }
                clipData = ClipData::class.create(null, mimeTypes, items)
                extraPathList = paths.toList()
            }
            var flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            if (!pickOptions.readOnly) {
                flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            if (pickOptions.mode == PickOptions.Mode.OPEN_DIRECTORY) {
                flags = flags or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            }
            addFlags(flags)
        }
        requireActivity().run {
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    private fun onSelectedFilesChanged(files: FileItemSet) {
        updateOverlayToolbar()
        adapter.replaceSelectedFiles(files)
    }

    /** Android's guidelines allow labels next to toolbar icons on tablets. */
    private fun showLabelsOnTablet(menu: Menu, vararg itemIds: Int) {
        if (resources.configuration.smallestScreenWidthDp < 600) {
            return
        }
        for (itemId in itemIds) {
            menu.findItem(itemId)?.setShowAsAction(
                MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT
            )
        }
    }

    private fun updateOverlayToolbar() {
        val files = viewModel.selectedFiles
        if (files.isEmpty()) {
            if (overlayActionMode.isActive) {
                overlayActionMode.finish()
            }
            return
        }
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            overlayActionMode.title = getString(R.string.file_list_select_title_format, files.size)
            overlayActionMode.setMenuResource(R.menu.file_list_pick)
            val menu = overlayActionMode.menu
            val isOpen = when (pickOptions.mode) {
                PickOptions.Mode.OPEN_FILE, PickOptions.Mode.OPEN_DIRECTORY -> true
                PickOptions.Mode.CREATE_FILE -> false
            }
            menu.findItem(R.id.action_open).isVisible = isOpen
            menu.findItem(R.id.action_create).isVisible = !isOpen
            menu.findItem(R.id.action_select_all).isVisible = pickOptions.allowMultiple
            menu.findItem(R.id.action_select_range).isVisible =
                pickOptions.allowMultiple && files.size >= 2
        } else {
            overlayActionMode.title = getSelectTitle(files)
            overlayActionMode.setMenuResource(R.menu.file_list_select)
            val menu = overlayActionMode.menu
            showLabelsOnTablet(menu, R.id.action_cut, R.id.action_copy, R.id.action_delete)
            val isAnyFileReadOnly = files.any { it.path.fileSystem.isReadOnly }
            menu.findItem(R.id.action_cut).isVisible = !isAnyFileReadOnly
            menu.findItem(R.id.action_select_range).isVisible = files.size >= 2
            val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
            menu.findItem(R.id.action_copy)
                .setIcon(
                    if (areAllFilesArchivePaths) {
                        R.drawable.extract_icon_control_normal_24dp
                    } else {
                        R.drawable.copy_icon_control_normal_24dp
                    }
                )
                .setTitle(
                    if (areAllFilesArchivePaths) {
                        R.string.file_list_select_action_extract
                    } else {
                        R.string.copy
                    }
                )
            menu.findItem(R.id.action_delete).isVisible = !isAnyFileReadOnly
            menu.findItem(R.id.action_rename).isVisible = !isAnyFileReadOnly && files.size >= 2
            val pinnedPaths = Settings.FILE_LIST_PINNED_PATHS.valueCompat.toSet()
            menu.findItem(R.id.action_pin).isVisible =
                files.any { it.path.toString() !in pinnedPaths }
            menu.findItem(R.id.action_unpin).isVisible =
                files.any { it.path.toString() in pinnedPaths }
            val areAllFilesArchiveFiles = files.all { it.isArchiveFile }
            menu.findItem(R.id.action_extract).isVisible = areAllFilesArchiveFiles
            val isCurrentPathReadOnly = viewModel.currentPath.fileSystem.isReadOnly
            menu.findItem(R.id.action_archive).isVisible = !isCurrentPathReadOnly
        }
        if (!overlayActionMode.isActive) {
            binding.appBarLayout.setExpanded(true)
            binding.appBarLayout.addOnOffsetChangedListener(
                AppBarLayoutExpandHackListener(binding.recyclerView)
            )
            binding.appBarLayout.isActionMode = true
            overlayActionMode.start(object : ToolbarActionMode.Callback {
                override fun onToolbarActionModeMenuItemClicked(
                    toolbarActionMode: ToolbarActionMode,
                    item: MenuItem
                ): Boolean = onOverlayActionModeMenuItemClicked(item)

                override fun onToolbarActionModeFinished(toolbarActionMode: ToolbarActionMode) {
                    binding.appBarLayout.isActionMode = false
                    onOverlayActionModeFinished()
                }
            })
        }
    }

    private fun getSelectTitle(files: FileItemSet): String {
        // Directory sizes are not meaningful here, so only show the total size when there are
        // none selected.
        if (files.any { it.attributesNoFollowLinks.isDirectory }) {
            return getString(R.string.file_list_select_title_format, files.size)
        }
        val totalSize = files.sumOf { it.attributes.size() }
        return getString(
            R.string.file_list_select_title_size_format, files.size,
            totalSize.asFileSize().formatHumanReadable(requireContext())
        )
    }

    private fun onOverlayActionModeMenuItemClicked(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_open -> {
                pickFiles(viewModel.selectedFiles)
                true
            }
            R.id.action_create -> {
                confirmReplaceFile(viewModel.selectedFiles.single())
                true
            }
            R.id.action_cut -> {
                cutFiles(viewModel.selectedFiles)
                true
            }
            R.id.action_copy -> {
                copyFiles(viewModel.selectedFiles)
                true
            }
            R.id.action_delete -> {
                confirmDeleteFiles(viewModel.selectedFiles)
                true
            }
            R.id.action_rename -> {
                RenameFilesDialogFragment.show(viewModel.selectedFiles, this)
                true
            }
            R.id.action_pin -> {
                pinFiles(viewModel.selectedFiles, true)
                true
            }
            R.id.action_unpin -> {
                pinFiles(viewModel.selectedFiles, false)
                true
            }
            R.id.action_extract -> {
                extractFiles(viewModel.selectedFiles)
                true
            }
            R.id.action_archive -> {
                showCreateArchiveDialog(viewModel.selectedFiles)
                true
            }
            R.id.action_share -> {
                shareFiles(viewModel.selectedFiles)
                true
            }
            R.id.action_copy_path -> {
                copyPaths(viewModel.selectedFiles)
                true
            }
            R.id.action_select_all -> {
                selectAllFiles()
                true
            }
            R.id.action_select_range -> {
                selectRangeOfFiles()
                true
            }
            else -> false
        }

    private fun onOverlayActionModeFinished() {
        viewModel.clearSelectedFiles()
    }

    private fun confirmReplaceFile(file: FileItem, setFileName: Boolean = true) {
        if (setFileName) {
            val fileName = file.name
            binding.bottomCreateFileNameEdit.setText(fileName)
            binding.bottomCreateFileNameEdit.setSelection(
                0, fileName.asFileName().baseName.length
            )
        }
        ConfirmReplaceFileDialogFragment.show(file, this)
    }

    override fun replaceFile(file: FileItem) {
        pickFiles(fileItemSetOf(file))
    }

    private fun pinFiles(files: FileItemSet, pin: Boolean) {
        val paths = files.map { it.path.toString() }
        val pinnedPaths = Settings.FILE_LIST_PINNED_PATHS.valueCompat
        Settings.FILE_LIST_PINNED_PATHS.putValue(
            if (pin) (pinnedPaths + paths).distinct() else pinnedPaths - paths.toSet()
        )
        viewModel.selectFiles(files, false)
    }

    private fun cutFiles(files: FileItemSet) {
        viewModel.addToPasteState(false, files)
        viewModel.selectFiles(files, false)
    }

    private fun copyFiles(files: FileItemSet) {
        viewModel.addToPasteState(true, files)
        viewModel.selectFiles(files, false)
    }

    private fun confirmDeleteFiles(files: FileItemSet) {
        ConfirmDeleteFilesDialogFragment.show(files, this)
    }

    override fun deleteFiles(files: FileItemSet) {
        FileJobService.delete(makePathListForJob(files), requireContext())
        viewModel.selectFiles(files, false)
    }

    private fun extractFiles(files: FileItemSet) {
        copyFiles(files.mapTo(fileItemSetOf()) { it.createDummyArchiveRoot() })
        viewModel.selectFiles(files, false)
    }

    private fun showCreateArchiveDialog(files: FileItemSet) {
        CreateArchiveDialogFragment.show(files, this)
    }

    override fun archive(
        files: FileItemSet,
        name: String,
        format: Int,
        filter: Int,
        password: String?,
        compressionLevel: Int
    ) {
        val archiveFile = viewModel.currentPath.resolve(name)
        FileJobService.archive(
            makePathListForJob(files), archiveFile, format, filter, password, compressionLevel,
            requireContext()
        )
        viewModel.selectFiles(files, false)
    }

    private fun shareFiles(files: FileItemSet) {
        shareFiles(files.map { it.path }, files.map { it.mimeType })
        viewModel.selectFiles(files, false)
    }

    private fun Path.toClipboardString(): String {
        val string = toUserFriendlyString()
        return if (Settings.COPY_PATH_SHELL_ESCAPE.valueCompat) string.escapeForShell() else string
    }

    private fun String.escapeForShell(): String =
        if (isNotEmpty() && all { it.isLetterOrDigit() && it.code < 128 || it in "_@%+=:,./-" }) {
            this
        } else {
            "'" + replace("'", "'\\''") + "'"
        }

    private fun copyPaths(files: FileItemSet) {
        val paths = files.map { it.path.toClipboardString() }
            .joinToString(if (Settings.COPY_PATH_SHELL_ESCAPE.valueCompat) " " else "\n")
        clipboardManager.copyText(paths, requireContext())
        viewModel.selectFiles(files, false)
    }

    private fun selectAllFiles() {
        adapter.selectAllFiles()
    }

    private fun selectRangeOfFiles() {
        adapter.selectRange()
    }

    private fun onPasteStateChanged(pasteState: PasteState) {
        adapter.hasPaste = pasteState.files.isNotEmpty()
        updateBottomToolbar()
    }

    private fun updateBottomToolbar() {
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            bottomActionMode.setMenuResource(R.menu.file_list_pick_bottom)
            val menu = bottomActionMode.menu
            when (pickOptions.mode) {
                PickOptions.Mode.CREATE_FILE -> {
                    bottomActionMode.title = null
                    binding.bottomCreateFileNameEdit.isVisible = true
                    val createMenuItem = menu.findItem(R.id.action_create)
                    binding.bottomCreateFileNameEdit.setOnEditorConfirmActionListener {
                        onBottomActionModeMenuItemClicked(createMenuItem)
                    }
                    if (!viewModel.isCreateFileNameEditInitialized) {
                        val fileName = pickOptions.fileName!!
                        binding.bottomCreateFileNameEdit.setText(fileName)
                        binding.bottomCreateFileNameEdit.setSelection(
                            0, fileName.asFileName().baseName.length
                        )
                        binding.bottomCreateFileNameEdit.requestFocus()
                        viewModel.isCreateFileNameEditInitialized = true
                    }
                    menu.findItem(R.id.action_open).isVisible = false
                    createMenuItem.isVisible = true
                }
                PickOptions.Mode.OPEN_DIRECTORY -> {
                    val path = viewModel.currentPath
                    val navigationRoot = NavigationRootMapLiveData.valueCompat[path]
                    val name = navigationRoot?.getName(requireContext()) ?: path.name
                    bottomActionMode.title =
                        getString(R.string.file_list_open_current_directory_format, name)
                    binding.bottomCreateFileNameEdit.isVisible = false
                    menu.findItem(R.id.action_open).isVisible = true
                    menu.findItem(R.id.action_create).isVisible = false
                }
                else -> {
                    if (bottomActionMode.isActive) {
                        bottomActionMode.finish()
                    }
                    return
                }
            }
        } else {
            val pasteState = viewModel.pasteState
            val files = pasteState.files
            if (files.isEmpty()) {
                if (bottomActionMode.isActive) {
                    bottomActionMode.finish()
                }
                return
            }
            val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
            bottomActionMode.title = getString(
                if (pasteState.copy) {
                    if (areAllFilesArchivePaths) {
                        R.string.file_list_paste_extract_title_format
                    } else {
                        R.string.file_list_paste_copy_title_format
                    }
                } else {
                    R.string.file_list_paste_move_title_format
                }, files.size
            )
            binding.bottomCreateFileNameEdit.isVisible = false
            bottomActionMode.setMenuResource(R.menu.file_list_paste)
            val isCurrentPathReadOnly = viewModel.currentPath.fileSystem.isReadOnly
            val pasteItem = bottomActionMode.menu.findItem(R.id.action_paste)
            pasteItem.setTitle(
                if (areAllFilesArchivePaths) {
                    R.string.file_list_paste_action_extract_here
                } else {
                    R.string.paste
                }
            )
            pasteItem.isEnabled = !isCurrentPathReadOnly
            val deleteArchiveItem =
                bottomActionMode.menu.findItem(R.id.action_delete_archive_after_extract)
            deleteArchiveItem.isVisible = areAllFilesArchivePaths
            deleteArchiveItem.isChecked = Settings.DELETE_ARCHIVE_AFTER_EXTRACT.valueCompat
        }
        if (!bottomActionMode.isActive) {
            bottomActionMode.start(object : ToolbarActionMode.Callback {
                override fun onToolbarNavigationIconClicked(toolbarActionMode: ToolbarActionMode) {
                    onBottomToolbarNavigationIconClicked()
                }

                override fun onToolbarActionModeMenuItemClicked(
                    toolbarActionMode: ToolbarActionMode,
                    item: MenuItem
                ): Boolean = onBottomActionModeMenuItemClicked(item)

                override fun onToolbarActionModeFinished(toolbarActionMode: ToolbarActionMode) {
                    onBottomActionModeFinished()
                }
            })
        }
    }

    private fun onBottomToolbarNavigationIconClicked() {
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            requireActivity().finish()
        } else {
            bottomActionMode.finish()
        }
    }

    private fun onBottomActionModeMenuItemClicked(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_open -> {
                pickPaths(linkedSetOf(viewModel.currentPath))
                true
            }
            R.id.action_create -> {
                val fileName = binding.bottomCreateFileNameEdit.text.toString()
                if (fileName.isEmpty()) {
                    showToast(R.string.file_list_create_file_name_error_empty)
                } else if (fileName.asFileNameOrNull() == null) {
                    showToast(R.string.file_list_create_file_name_error_invalid)
                } else {
                    val file = getFileWithName(fileName)
                    if (file != null) {
                        confirmReplaceFile(file, false)
                    } else {
                        val path = viewModel.currentPath.resolve(fileName)
                        pickPaths(linkedSetOf(path))
                    }
                }
                true
            }
            R.id.action_delete_archive_after_extract -> {
                val deleteArchive = !item.isChecked
                item.isChecked = deleteArchive
                Settings.DELETE_ARCHIVE_AFTER_EXTRACT.putValue(deleteArchive)
                true
            }
            R.id.action_paste -> {
                pasteFiles(currentPath)
                true
            }
            else -> false
        }

    private fun onBottomActionModeFinished() {
        val pickOptions = viewModel.pickOptions
        if (pickOptions == null) {
            viewModel.clearPasteState()
        }
    }

    private fun pasteFiles(targetDirectory: Path) {
        val pasteState = viewModel.pasteState
        val sourceDirectory = pasteState.files.firstOrNull()?.path?.parent
        if (viewModel.pasteState.copy) {
            FileJobService.copy(
                makePathListForJob(pasteState.files), targetDirectory, requireContext()
            )
        } else {
            FileJobService.move(
                makePathListForJob(pasteState.files), targetDirectory, requireContext()
            )
        }
        viewModel.clearPasteState()
        offerGoBackToSource(sourceDirectory, targetDirectory)
    }

    private fun offerGoBackToSource(sourceDirectory: Path?, targetDirectory: Path) {
        if (sourceDirectory == null || sourceDirectory == targetDirectory) {
            return
        }
        Snackbar.make(binding.root, R.string.file_list_paste_started, Snackbar.LENGTH_LONG)
            .setAction(
                getString(R.string.file_list_paste_go_back_format, sourceDirectory.toUserFriendlyString())
            ) { navigateTo(sourceDirectory) }
            .show()
    }

    private fun makePathListForJob(files: FileItemSet): List<Path> =
        files.map { it.path }.sortedBy { it.toUri() }

    private fun onFileNameEllipsizeChanged(fileNameEllipsize: FileNameEllipsize) {
        adapter.nameEllipsize = fileNameEllipsize
    }

    override fun clearSelectedFiles() {
        viewModel.clearSelectedFiles()
    }

    override fun selectFile(file: FileItem, selected: Boolean) {
        if (!isAdded) {
            return
        }
        viewModel.selectFile(file, selected)
    }

    override fun selectFiles(files: FileItemSet, selected: Boolean) {
        if (!isAdded) {
            return
        }
        viewModel.selectFiles(files, selected)
    }

    override fun openFile(file: FileItem) {
        if (!isAdded) {
            return
        }
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            if (file.attributes.isDirectory) {
                navigateTo(file.path)
            } else {
                when (pickOptions.mode) {
                    PickOptions.Mode.OPEN_FILE -> pickFiles(fileItemSetOf(file))
                    PickOptions.Mode.CREATE_FILE -> confirmReplaceFile(file)
                    PickOptions.Mode.OPEN_DIRECTORY -> {}
                }
            }
            return
        }
        if (adapter.indicateLastOpenedItem && !file.isListable) {
            viewModel.lastActivatedPath = file.path
            adapter.lastActivatedPath = file.path
        }
        if (file.mimeType.isApk) {
            RecentFiles.add(file)
            openApk(file)
            return
        }
        if (file.isListable) {
            navigateTo(file.listablePath)
            return
        }
        openFileWithIntent(file, false)
    }

    private fun openApk(file: FileItem) {
        if (!file.isListable) {
            installApk(file)
            return
        }
        when (Settings.OPEN_APK_DEFAULT_ACTION.valueCompat) {
            OpenApkDefaultAction.INSTALL -> installApk(file)
            OpenApkDefaultAction.VIEW -> viewApk(file)
            OpenApkDefaultAction.ASK -> OpenApkDialogFragment.show(file, this)
        }
    }

    override fun installApk(file: FileItem) {
        if (!isAdded) {
            return
        }
        val path = file.path
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!path.isArchivePath) path.fileProviderUri else null
        } else {
            // PackageInstaller only supports file URI before N.
            if (path.isLinuxPath) Uri.fromFile(path.toFile()) else null
        }
        if (uri != null) {
            startActivitySafe(uri.createInstallPackageIntent())
        } else {
            FileJobService.installApk(path, requireContext())
        }
    }

    override fun viewApk(file: FileItem) {
        if (!isAdded) {
            return
        }
        navigateTo(file.listablePath)
    }

    override fun openFileWith(file: FileItem) {
        if (!isAdded) {
            return
        }
        openFileWithIntent(file, true)
    }

    private fun openFileWithIntent(file: FileItem, withChooser: Boolean) {
        if (adapter.indicateLastOpenedItem) {
            viewModel.lastActivatedPath = file.path
            adapter.lastActivatedPath = file.path
        }
        RecentFiles.add(file)
        val path = file.path
        val mimeType = file.mimeType
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, withChooser, requireContext())
        } else if (withChooser) {
            showOpenWithDialog(file)
        } else {
            val override = FileOpenDefaults.componentFor(mimeType)
            val intent = path.fileProviderUri.createViewIntent(mimeType)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .apply {
                    extraPath = path
                    if (override == null || override.packageName == requireContext().packageName) {
                        maybeAddImageViewerActivityExtras(this, path, mimeType)
                    }
                    if (Settings.OPEN_FILES_IN_NEW_TASK.valueCompat) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (override == null && Settings.OPEN_WITH_BUILT_IN_VIEWERS.valueCompat &&
                        (mimeType.isImage || mimeType.value.startsWith("text/"))
                    ) {
                        // Restrict resolution to our own image viewer and text editor.
                        setPackage(requireContext().packageName)
                    }
                    override?.let { component = it }
                }
            startActivitySafe(intent)
        }
    }

    private fun showOpenWithDialog(file: FileItem) {
        val path = file.path
        val mimeType = file.mimeType
        val viewIntent = path.fileProviderUri.createViewIntent(mimeType)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .apply { extraPath = path }
        val packageManager = requireContext().packageManager
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                viewIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(viewIntent, PackageManager.MATCH_ALL)
        }
        data class Choice(
            val label: String,
            val component: ComponentName?,
            val launch: () -> Unit
        )
        val choices = mutableListOf<Choice>()
        choices.add(
            Choice(getString(R.string.file_edit_title), null) {
                startActivitySafe(
                    EditFileActivity::class.createIntent()
                        .putArgs(EditFileActivity.Args(path, mimeType))
                )
            }
        )
        choices.add(
            Choice(getString(R.string.file_open_as_title), null) {
                startActivitySafe(
                    OpenFileAsDialogActivity::class.createIntent()
                        .putArgs(OpenFileAsDialogFragment.Args(path))
                )
            }
        )
        for (info in activities) {
            val activityInfo = info.activityInfo ?: continue
            val component = ComponentName(activityInfo.packageName, activityInfo.name)
            choices.add(
                Choice(info.loadLabel(packageManager).toString(), component) {
                    startActivitySafe(
                        Intent(viewIntent).apply {
                            this.component = component
                            if (component.packageName == requireContext().packageName) {
                                maybeAddImageViewerActivityExtras(this, path, mimeType)
                            }
                        }
                    )
                }
            )
        }
        choices.sortBy { it.label }
        var selected = 0
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.file_item_action_open_with)
            .setSingleChoiceItems(choices.map { it.label }.toTypedArray(), 0) { _, which ->
                selected = which
            }
            .setPositiveButton(R.string.file_open_with_once) { _, _ ->
                choices[selected].launch()
            }
            .setNeutralButton(R.string.file_open_with_always) { _, _ ->
                val choice = choices[selected]
                choice.component?.let { FileOpenDefaults.remember(mimeType, it) }
                choice.launch()
            }
            .show()
    }

    private fun maybeAddImageViewerActivityExtras(intent: Intent, path: Path, mimeType: MimeType) {
        if (!mimeType.isImage) {
            return
        }
        val paths = mutableListOf<Path>()
        // We need the ordered list from our adapter instead of the list from FileListLiveData.
        for (index in 0..<adapter.itemCount) {
            val file = adapter.getItem(index)
            val filePath = file.path
            if (file.mimeType.isImage || filePath == path) {
                paths.add(filePath)
            }
        }
        val position = paths.indexOf(path)
        if (position == -1) {
            return
        }
        ImageViewerActivity.putExtras(intent, paths, position)
    }

    override fun cutFile(file: FileItem) {
        if (!isAdded) {
            return
        }
        cutFiles(fileItemSetOf(file))
    }

    override fun copyFile(file: FileItem) {
        if (!isAdded) {
            return
        }
        copyFiles(fileItemSetOf(file))
    }

    override fun pasteInto(directory: Path) {
        if (!isAdded) {
            return
        }
        pasteFiles(directory)
    }

    override fun confirmDeleteFile(file: FileItem) {
        if (!isAdded) {
            return
        }
        confirmDeleteFiles(fileItemSetOf(file))
    }

    override fun showRenameFileDialog(file: FileItem) {
        if (!isAdded) {
            return
        }
        RenameFileDialogFragment.show(file, this)
    }

    override fun hasFileWithName(name: String): Boolean = getFileWithName(name) != null

    private fun getFileWithName(name: String): FileItem? {
        val fileListData = viewModel.fileListStateful
        if (fileListData !is Success) {
            return null
        }
        return fileListData.value.find { it.name == name }
    }

    override fun renameFile(file: FileItem, newName: String) {
        if (!isAdded) {
            return
        }
        FileJobService.rename(file.path, newName, requireContext())
        viewModel.selectFile(file, false)
    }

    override fun renameFiles(newNames: List<Pair<FileItem, String>>) {
        if (!isAdded) {
            return
        }
        for ((file, newName) in newNames) {
            if (newName != file.name) {
                FileJobService.rename(file.path, newName, requireContext())
            }
        }
        viewModel.selectFiles(newNames.mapTo(fileItemSetOf()) { it.first }, false)
    }

    override fun openAsArchive(file: FileItem) {
        if (!isAdded) {
            return
        }
        navigateTo(file.path.createArchiveRootPath())
    }

    override fun showCreateLinkDialog(file: FileItem) {
        if (!isAdded) {
            return
        }
        CreateLinkDialogFragment.show(file, this)
    }

    override fun createLink(target: FileItem, name: String) {
        if (currentPath.fileSystem.isReadOnly) {
            showToast(getString(R.string.file_list_create_error_read_only))
            return
        }
        FileJobService.createSymbolicLink(currentPath.resolve(name), target.path, requireContext())
    }

    override fun extractFile(file: FileItem) {
        if (!isAdded) {
            return
        }
        copyFile(file.createDummyArchiveRoot())
    }

    override fun testArchive(file: FileItem) {
        if (!isAdded) {
            return
        }
        FileJobService.testArchive(file.createDummyArchiveRoot().path, requireContext())
    }

    override fun showCreateArchiveDialog(file: FileItem) {
        if (!isAdded) {
            return
        }
        showCreateArchiveDialog(fileItemSetOf(file))
    }

    override fun shareFile(file: FileItem) {
        if (!isAdded) {
            return
        }
        shareFile(file.path, file.mimeType)
    }

    private fun shareFile(path: Path, mimeType: MimeType) {
        shareFiles(listOf(path), listOf(mimeType))
    }

    private fun shareFiles(paths: List<Path>, mimeTypes: List<MimeType>) {
        val pairs = paths.zip(mimeTypes)
        if (pairs.none { it.second == MimeType.DIRECTORY }) {
            val uris = paths.map { it.fileProviderUri }
            val intent = uris.createSendStreamIntent(mimeTypes)
                .withChooser()
            startActivitySafe(intent)
            return
        }
        lifecycleScope.launch {
            var reachedLimit = false
            val resolvedFiles = withContext(Dispatchers.IO) {
                val result = mutableListOf<Pair<Path, MimeType>>()
                for ((path, mimeType) in pairs) {
                    val isDirectory = mimeType == MimeType.DIRECTORY || try {
                        path.isDirectory()
                    } catch (e: Exception) {
                        false
                    }
                    if (isDirectory) {
                        try {
                            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                                override fun visitFile(
                                    file: Path,
                                    attributes: BasicFileAttributes
                                ): FileVisitResult {
                                    if (result.size >= MAX_SHARE_FILE_COUNT) {
                                        reachedLimit = true
                                        return FileVisitResult.TERMINATE
                                    }
                                    if (attributes.isRegularFile) {
                                        val fileMimeType = AndroidFileTypeDetector.getMimeType(
                                            file, attributes
                                        ).asMimeType()
                                        result.add(file to fileMimeType)
                                    }
                                    return FileVisitResult.CONTINUE
                                }

                                override fun visitFileFailed(
                                    file: Path,
                                    exception: java.io.IOException
                                ): FileVisitResult {
                                    exception.printStackTrace()
                                    return FileVisitResult.CONTINUE
                                }
                            })
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        if (result.size < MAX_SHARE_FILE_COUNT) {
                            result.add(path to mimeType)
                        } else {
                            reachedLimit = true
                        }
                    }
                }
                result
            }
            if (!isAdded) {
                return@launch
            }
            if (resolvedFiles.isEmpty()) {
                showToast(R.string.file_list_share_empty_directory_error)
                return@launch
            }
            if (reachedLimit) {
                showToast(getString(R.string.file_list_share_too_many_files_toast, MAX_SHARE_FILE_COUNT))
            }
            val uris = resolvedFiles.map { it.first.fileProviderUri }
            val intent = uris.createSendStreamIntent(resolvedFiles.map { it.second })
                .withChooser()
            startActivitySafe(intent)
        }
    }

    override fun copyPath(file: FileItem) {
        if (!isAdded) {
            return
        }
        copyPath(file.path)
    }

    override fun showInFolder(file: FileItem) {
        if (!isAdded) {
            return
        }
        val parent = file.path.parent ?: return
        pendingScrollPath = file.path
        pendingScrollParent = parent
        navigateTo(parent)
    }

    private fun scrollToPendingPath() {
        val path = pendingScrollPath ?: return
        // Ignore the list of the search that we are leaving, and only act on the target folder.
        if (viewModel.searchState.isSearching || viewModel.currentPath != pendingScrollParent) {
            return
        }
        pendingScrollPath = null
        pendingScrollParent = null
        val position = adapter.getFilePosition(path)
        if (position != RecyclerView.NO_POSITION) {
            binding.recyclerView.scrollToPosition(position)
        }
    }

    override fun addBookmark(file: FileItem) {
        if (!isAdded) {
            return
        }
        addBookmark(file.path)
    }

    private fun addBookmark() {
        addBookmark(currentPath)
    }

    private fun addBookmark(path: Path) {
        BookmarkDirectories.add(BookmarkDirectory(null, path))
        showToast(R.string.file_add_bookmark_success)
    }

    override fun createShortcut(file: FileItem) {
        if (!isAdded) {
            return
        }
        createShortcut(file.path, file.mimeType)
    }

    override fun hideFile(file: FileItem) {
        if (!isAdded) {
            return
        }
        val hiddenPaths = Settings.FILE_LIST_HIDDEN_PATHS.valueCompat.toMutableSet()
        hiddenPaths += file.path.toUri().toString()
        Settings.FILE_LIST_HIDDEN_PATHS.putValue(hiddenPaths)
        showToast(R.string.file_hide_success)
    }

    private fun createShortcut() {
        createShortcut(currentPath, MimeType.DIRECTORY)
    }

    private fun createShortcut(path: Path, mimeType: MimeType) {
        val context = context ?: return
        if (mimeType.isImage && path.isLinuxPath) {
            // Use a small thumbnail of the image as the icon, decoded off the main thread.
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                val thumbnail = path.decodeShortcutThumbnail()
                view?.post {
                    if (isAdded) {
                        createShortcut(path, mimeType, thumbnail)
                    }
                } ?: Unit
            }
            return
        }
        createShortcut(path, mimeType, null)
    }

    private fun Path.decodeShortcutThumbnail(): Bitmap? =
        try {
            val file = toFile()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            val size = SHORTCUT_THUMBNAIL_SIZE
            var sampleSize = 1
            while (options.outWidth / (sampleSize * 2) >= size &&
                options.outHeight / (sampleSize * 2) >= size
            ) {
                sampleSize *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
            decoded?.let { ThumbnailUtils.extractThumbnail(it, size, size) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    private fun createShortcut(path: Path, mimeType: MimeType, thumbnail: Bitmap?) {
        val context = context ?: return
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            showToast(R.string.shortcut_not_supported)
            return
        }
        try {
            val isDirectory = mimeType == MimeType.DIRECTORY
            val label = path.name.ifEmpty { path.toString() }
            val shortcutInfo = ShortcutInfoCompat.Builder(context, path.toString())
                .setShortLabel(label)
                .setLongLabel(label)
                .setIntent(
                    if (isDirectory) {
                        // The launcher resolves this again. Without a type and the default category
                        // it does not match the folder activity, so the shortcut has no name and
                        // does not open.
                        FileListActivity.createViewIntent(path)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .setType("vnd.android.document/directory")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    } else {
                        OpenFileActivity.createIntent(path, mimeType)
                    }
                )
                .setIcon(
                    if (thumbnail != null) {
                        IconCompat.createWithBitmap(thumbnail)
                    } else if (isDirectory) {
                        IconCompat.createWithResource(context, R.mipmap.directory_shortcut_icon)
                    } else {
                        createFileShortcutIcon(context, mimeType.iconRes)
                    }
                )
                .build()
            val successful = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && successful) {
                showToast(R.string.shortcut_created)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(e.toString())
        }
    }

    private fun createFileShortcutIcon(context: Context, iconRes: Int): IconCompat {
        val density = context.resources.displayMetrics.density
        val size = (108 * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ContextCompat.getColor(context, R.color.shortcut_icon_background))
        val drawable = AppCompatResources.getDrawable(context, iconRes)?.mutate()
            ?: return IconCompat.createWithResource(context, R.mipmap.file_shortcut_icon)
        val iconSize = (48 * density).toInt().coerceAtLeast(1)
        val inset = (size - iconSize) / 2
        drawable.setBounds(inset, inset, inset + iconSize, inset + iconSize)
        drawable.draw(canvas)
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    override fun showPropertiesDialog(file: FileItem) {
        if (!isAdded) {
            return
        }
        FilePropertiesDialogFragment.show(file, this)
    }

    override fun onFileActivated(file: FileItem) {
        if (!isAdded) {
            return
        }
        viewModel.lastActivatedPath = file.path
    }

    private fun toggleFtpServer() {
        when (FtpServerService.stateLiveData.valueCompat) {
            FtpServerService.State.RUNNING -> showToast(R.string.file_list_ftp_server_stopping)
            FtpServerService.State.STOPPED -> showToast(R.string.file_list_ftp_server_starting)
            else -> {}
        }
        FtpServerService.toggle(requireContext())
    }

    private fun showCreateFileDialog() {
        CreateFileDialogFragment.show(this)
    }

    override fun createFile(name: String) {
        if (currentPath.fileSystem.isReadOnly) {
            showToast(getString(R.string.file_list_create_error_read_only))
            return
        }
        val path = currentPath.resolve(name)
        FileJobService.create(path, false, requireContext())
    }

    private fun showCreateDirectoryDialog() {
        CreateDirectoryDialogFragment.show(this)
    }

    override fun createDirectory(name: String) {
        if (currentPath.fileSystem.isReadOnly) {
            showToast(getString(R.string.file_list_create_error_read_only))
            return
        }
        val path = currentPath.resolve(name)
        FileJobService.create(path, true, requireContext())
    }

    override val currentPath: Path
        get() = viewModel.currentPath

    override fun navigateToRoot(path: Path) {
        collapseSearchView()
        viewModel.resetTo(path)
    }

    override fun navigateToDefaultRoot() {
        navigateToRoot(Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat)
    }

    override fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit) {
        viewModel.currentPathLiveData.observe(owner, observer)
    }

    override fun closeNavigationDrawer() {
        binding.drawerLayout?.closeDrawer(GravityCompat.START)
    }

    private fun ensureStorageAccess() {
        if (viewModel.isStorageAccessRequested) {
            return
        }
        if (Environment::class.supportsExternalStorageManager()) {
            if (!Environment.isExternalStorageManager()) {
                ShowRequestAllFilesAccessRationaleDialogFragment.show(this)
                viewModel.isStorageAccessRequested = true
            }
        } else if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (shouldShowRequestPermissionRationale(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )) {
                ShowRequestStoragePermissionRationaleDialogFragment.show(this)
            } else {
                requestStoragePermission()
            }
            viewModel.isStorageAccessRequested = true
        }
    }

    override fun onShowRequestAllFilesAccessRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestAllFilesAccess()
        } else {
            viewModel.isStorageAccessRequested = false
            // This isn't an onActivityResult() callback so it's not delivered before calling
            // onResume(), and we need to do this manually.
            ensureNotificationPermission()
        }
    }

    private fun requestAllFilesAccess() {
        requestAllFilesAccessLauncher.launch(Unit)
    }

    private fun onRequestAllFilesAccessResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            refresh()
        }
    }

    override fun onShowRequestStoragePermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermission()
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun requestStoragePermission() {
        requestStoragePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun onRequestStoragePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isStorageAccessRequested = false
            refresh()
        } else if (shouldShowRequestPermissionRationale(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )) {
            ShowRequestStoragePermissionRationaleDialogFragment.show(this)
        } else {
            ShowRequestStoragePermissionInSettingsRationaleDialogFragment.show(this)
        }
    }

    override fun onShowRequestStoragePermissionInSettingsRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermissionInSettings()
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun requestStoragePermissionInSettings() {
        requestStoragePermissionInSettingsLauncher.launch(Unit)
    }

    private fun onRequestStoragePermissionInSettingsResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            refresh()
        }
    }

    private fun ensureNotificationPermission() {
        if (viewModel.isNotificationPermissionRequested || Settings.NOTIFICATION_PERMISSION_DISMISSED.valueCompat) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )) {
                    ShowRequestNotificationPermissionRationaleDialogFragment.show(this)
                } else {
                    requestNotificationPermission()
                }
                viewModel.isNotificationPermissionRequested = true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onShowRequestNotificationPermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestNotificationPermission()
        } else {
            Settings.NOTIFICATION_PERMISSION_DISMISSED.putValue(true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
            Settings.NOTIFICATION_PERMISSION_DISMISSED.putValue(false)
        } else if (shouldShowRequestPermissionRationale(
            android.Manifest.permission.POST_NOTIFICATIONS
        )) {
            ShowRequestNotificationPermissionRationaleDialogFragment.show(this)
        } else {
            ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.show(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onShowRequestNotificationPermissionInSettingsRationaleResult(
        shouldRequest: Boolean
    ) {
        if (shouldRequest) {
            requestNotificationPermissionInSettings()
        } else {
            Settings.NOTIFICATION_PERMISSION_DISMISSED.putValue(true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermissionInSettings() {
        requestNotificationPermissionInSettingsLauncher.launch(Unit)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionInSettingsResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
            Settings.NOTIFICATION_PERMISSION_DISMISSED.putValue(false)
        } else {
            Settings.NOTIFICATION_PERMISSION_DISMISSED.putValue(true)
        }
    }

    companion object {
        private const val ACTION_VIEW_DOWNLOADS =
            "me.zhanghai.android.files.intent.action.VIEW_DOWNLOADS"

        private const val DOUBLE_BACK_TO_EXIT_TIMEOUT_MILLIS = 2000L
        private const val SHORTCUT_THUMBNAIL_SIZE = 192
    }

    private class RequestAllFilesAccessContract : ActivityResultContract<Unit, Boolean>() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun createIntent(context: Context, input: Unit): Intent =
            Environment::class.createManageAppAllFilesAccessPermissionIntent(context.packageName)

        @RequiresApi(Build.VERSION_CODES.R)
        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            Environment.isExternalStorageManager()
    }

    private class RequestPermissionInSettingsContract(private val permissionName: String)
        : ActivityResultContract<Unit, Boolean>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            application.checkSelfPermissionCompat(permissionName) ==
                PackageManager.PERMISSION_GRANTED
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    private class Binding private constructor(
        val root: View,
        val drawerLayout: DrawerLayout? = null,
        val persistentDrawerLayout: PersistentDrawerLayout? = null,
        val persistentBarLayout: PersistentBarLayout,
        val appBarLayout: CoordinatorAppBarLayout,
        val toolbar: Toolbar,
        val overlayToolbar: Toolbar,
        val breadcrumbLayout: BreadcrumbLayout,
        val contentLayout: ViewGroup,
        val progress: ProgressBar,
        val errorText: TextView,
        val emptyView: View,
        val swipeRefreshLayout: SwipeRefreshLayout,
        val recyclerView: RecyclerView,
        val fileJobProgressLayout: LinearLayout,
        val fileJobProgressCardsLayout: LinearLayout,
        val bottomBarLayout: ViewGroup,
        val bottomToolbar: Toolbar,
        val bottomCreateFileNameEdit: EditText,
        val speedDialView: SpeedDialView
    ) {
        companion object {
            fun inflate(
                inflater: LayoutInflater,
                root: ViewGroup?,
                attachToRoot: Boolean
            ): Binding {
                val binding = FileListFragmentBinding.inflate(inflater, root, attachToRoot)
                val bindingRoot = binding.root
                val includeBinding = FileListFragmentIncludeBinding.bind(bindingRoot)
                val appBarBinding = FileListFragmentAppBarIncludeBinding.bind(bindingRoot)
                val contentBinding = FileListFragmentContentIncludeBinding.bind(bindingRoot)
                val bottomBarBinding = FileListFragmentBottomBarIncludeBinding.bind(bindingRoot)
                val speedDialBinding = FileListFragmentSpeedDialIncludeBinding.bind(bindingRoot)
                return Binding(
                    bindingRoot, includeBinding.drawerLayout, includeBinding.persistentDrawerLayout,
                    includeBinding.persistentBarLayout, appBarBinding.appBarLayout,
                    appBarBinding.toolbar, appBarBinding.overlayToolbar,
                    appBarBinding.breadcrumbLayout, contentBinding.contentLayout,
                    contentBinding.progress, contentBinding.errorText, contentBinding.emptyView,
                    contentBinding.swipeRefreshLayout, contentBinding.recyclerView,
                    contentBinding.fileJobProgressLayout, contentBinding.fileJobProgressCards,
                    bottomBarBinding.bottomBarLayout, bottomBarBinding.bottomToolbar,
                    bottomBarBinding.bottomCreateFileNameEdit, speedDialBinding.speedDialView
                )
            }
        }
    }

    private fun Throwable.isMissingDirectory(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is NoSuchFileException || current is NotDirectoryException) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun Throwable.toUserFriendlyMessage(): String =
        when {
            isMissingDirectory() -> getString(R.string.file_list_error_directory_not_found)
            hasCauseMessage("Root isn't available") ->
                getString(R.string.file_list_error_root_unavailable)
            hasCauseMessage("Shizuku isn't available") ->
                getString(R.string.file_list_error_shizuku_unavailable)
            hasCause<AccessDeniedException>() -> getString(R.string.file_list_error_access_denied)
            hasCause<UnknownHostException>() -> getString(R.string.file_list_error_unknown_host)
            hasCause<ConnectException>() || hasCause<SocketTimeoutException>() ->
                getString(R.string.file_list_error_connection_failed)
            else -> localizedMessage?.takeIfNotEmpty() ?: toString()
        }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is T }

    private fun Throwable.hasCauseMessage(message: String): Boolean =
        generateSequence(this) { it.cause }.any { it.message == message }

    private class MenuBinding private constructor(
        val menu: Menu,
        val searchItem: MenuItem,
        val viewSortItem: MenuItem,
        val selectAllItem: MenuItem,
        val showHiddenFilesItem: MenuItem,
        val archiveFileNameEncodingItem: MenuItem
    ) {
        companion object {
            fun inflate(menu: Menu, inflater: MenuInflater): MenuBinding {
                inflater.inflate(R.menu.file_list, menu)
                return MenuBinding(
                    menu, menu.findItem(R.id.action_search), menu.findItem(R.id.action_view_sort),
                    menu.findItem(R.id.action_select_all),
                    menu.findItem(R.id.action_show_hidden_files),
                    menu.findItem(R.id.action_archive_file_name_encoding)
                )
            }
        }
    }
}

private const val MAX_SHARE_FILE_COUNT = 500
