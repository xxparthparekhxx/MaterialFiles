/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import java8.nio.file.Path
import me.zhanghai.android.fastscroll.PopupTextProvider
import me.zhanghai.android.files.R
import me.zhanghai.android.files.coil.AppIconPackageName
import me.zhanghai.android.files.compat.foregroundCompat
import me.zhanghai.android.files.compat.getDrawableCompat
import me.zhanghai.android.files.compat.isSingleLineCompat
import me.zhanghai.android.files.databinding.FileItemCompactListBinding
import me.zhanghai.android.files.databinding.FileItemGridBinding
import me.zhanghai.android.files.databinding.FileItemListBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatShort
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.isEncrypted
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.provider.common.toModeString
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.provider.common.isHidden
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.AnimatedListAdapter
import me.zhanghai.android.files.ui.CheckableForegroundLinearLayout
import me.zhanghai.android.files.ui.CheckableItemBackground
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.isMaterial3Theme
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.valueCompat
import java.io.IOException
import java.util.Locale

private fun FileItem.sameDisplayedContent(other: FileItem): Boolean {
    if (mimeType != other.mimeType || isHidden != other.isHidden ||
        symbolicLinkTarget != other.symbolicLinkTarget ||
        attributesNoFollowLinks.isSymbolicLink != other.attributesNoFollowLinks.isSymbolicLink
    ) {
        return false
    }
    val left = attributes
    val right = other.attributes
    return left.size() == right.size() &&
        left.lastModifiedTime() == right.lastModifiedTime() &&
        left.isDirectory == right.isDirectory &&
        left.isEncrypted() == right.isEncrypted()
}

class FileListAdapter(
    private val listener: Listener
) : AnimatedListAdapter<FileItem, FileListAdapter.ViewHolder>(CALLBACK), PopupTextProvider {
    private var isSearching = false

    private lateinit var _viewType: FileViewType
    var viewType: FileViewType
        get() = _viewType
        set(value) {
            _viewType = value
            if (!isSearching) {
                super.replace(list, true)
            }
        }

    private lateinit var _sortOptions: FileSortOptions
    var sortOptions: FileSortOptions
        get() = _sortOptions
        set(value) {
            _sortOptions = value
            val sortedList = list.sortedWith(createComparator())
            super.replace(sortedList, true)
            rebuildFilePositionMap()
        }

    var pinnedPaths: Set<String> = emptySet()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (!::_sortOptions.isInitialized) {
                return
            }
            super.replace(list.sortedWith(createComparator()), false)
            rebuildFilePositionMap()
        }

    // Pinned files are listed first, except in search results where they are not pinned.
    private fun createComparator(): Comparator<FileItem> {
        val comparator = sortOptions.createComparator(isHiddenFirst, folderSortBy)
        return if (isSearching || pinnedPaths.isEmpty()) {
            comparator
        } else {
            compareBy<FileItem> { it.path.toString() !in pinnedPaths }.then(comparator)
        }
    }

    var isHiddenFirst: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (!::_sortOptions.isInitialized) {
                return
            }
            super.replace(list.sortedWith(createComparator()), false)
            rebuildFilePositionMap()
        }

    var fitThumbnails: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            notifyDataSetChanged()
        }

    var folderSortBy: FileSortOptions.By? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (!::_sortOptions.isInitialized) {
                return
            }
            super.replace(list.sortedWith(createComparator()), false)
            rebuildFilePositionMap()
        }

    var pickOptions: PickOptions? = null
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    var hasPaste: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    var listDensity: FileListDensity = FileListDensity.COMFORTABLE
        set(value) {
            if (field == value) {
                return
            }
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    private val selectedFiles = fileItemSetOf()

    private val filePositionMap = mutableMapOf<Path, Int>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val directoryItemCounts = mutableMapOf<Path, Int>()
    private val directoryItemCountFailures = mutableSetOf<Path>()
    private val directoryItemCountJobs = mutableSetOf<Path>()
    private var directoryItemCountGeneration = 0

    fun invalidateDirectoryItemCounts() {
        directoryItemCountGeneration++
        directoryItemCounts.clear()
        directoryItemCountFailures.clear()
        directoryItemCountJobs.clear()
        notifyItemRangeChanged(0, itemCount, PAYLOAD_DIRECTORY_ITEM_COUNT)
    }

    private var activePopupMenu: PopupMenu? = null

    fun dismissActivePopupMenu() {
        activePopupMenu?.dismiss()
        activePopupMenu = null
    }

    private lateinit var _nameEllipsize: TextUtils.TruncateAt
    var nameEllipsize: TextUtils.TruncateAt
        get() = _nameEllipsize
        set(value) {
            _nameEllipsize = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    var fontScale: Float = 1f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    fun replaceSelectedFiles(files: FileItemSet) {
        val changedFiles = fileItemSetOf()
        val iterator = selectedFiles.iterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file !in files) {
                iterator.remove()
                changedFiles.add(file)
            }
        }
        for (file in files) {
            if (file !in selectedFiles) {
                selectedFiles.add(file)
                changedFiles.add(file)
            }
        }
        for (file in changedFiles) {
            val position = filePositionMap[file.path]
            position?.let { notifyItemChanged(it, PAYLOAD_STATE_CHANGED) }
        }
    }

    private fun selectFile(file: FileItem) {
        if (!isFileSelectable(file)) {
            return
        }
        val selected = file in selectedFiles
        val pickOptions = pickOptions
        if (!selected && pickOptions != null && !pickOptions.allowMultiple) {
            listener.clearSelectedFiles()
        }
        listener.selectFile(file, !selected)
    }

    fun selectAllFiles() {
        val files = fileItemSetOf()
        for (index in 0..<itemCount) {
            val file = getItem(index)
            if (isFileSelectable(file)) {
                files.add(file)
            }
        }
        listener.selectFiles(files, true)
    }

    // Selects every file between the first and the last selected file in the list.
    fun selectRange() {
        var first = -1
        var last = -1
        for (index in 0..<itemCount) {
            if (getItem(index) in selectedFiles) {
                if (first == -1) {
                    first = index
                }
                last = index
            }
        }
        if (first == -1 || first == last) {
            return
        }
        val files = fileItemSetOf()
        for (index in first..last) {
            val file = getItem(index)
            if (isFileSelectable(file)) {
                files.add(file)
            }
        }
        listener.selectFiles(files, true)
    }

    private fun isFileSelectable(file: FileItem): Boolean {
        val pickOptions = pickOptions ?: return true
        return when (pickOptions.mode) {
            PickOptions.Mode.OPEN_FILE, PickOptions.Mode.CREATE_FILE ->
                !file.attributes.isDirectory &&
                    pickOptions.mimeTypes.any { it.match(file.mimeType) }
            PickOptions.Mode.OPEN_DIRECTORY -> file.attributes.isDirectory
        }
    }

    override fun clear() {
        super.clear()

        rebuildFilePositionMap()
    }

    @Deprecated("", ReplaceWith("replaceListAndSearching(list, searching)"))
    override fun replace(list: List<FileItem>, clear: Boolean) {
        throw UnsupportedOperationException()
    }

    fun replaceListAndIsSearching(list: List<FileItem>, isSearching: Boolean) {
        val clear = this.isSearching != isSearching
        this.isSearching = isSearching
        val sortedList = list.sortedWith(createComparator())
        super.replace(sortedList, clear)
        rebuildFilePositionMap()
    }

    fun getFilePosition(path: Path): Int = filePositionMap[path] ?: RecyclerView.NO_POSITION

    private fun rebuildFilePositionMap() {
        filePositionMap.clear()
        for (index in 0..<itemCount) {
            val file = getItem(index)
            filePositionMap[file.path] = index
        }
    }

    override fun getItemViewType(position: Int): Int = viewType.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewType = FileViewType.entries[viewType]
        val inflater = parent.context.layoutInflater
        val holder = when (viewType) {
            FileViewType.LIST -> ViewHolder(FileItemListBinding.inflate(inflater, parent, false))
            FileViewType.GRID -> ViewHolder(FileItemGridBinding.inflate(inflater, parent, false))
            FileViewType.COMPACT_LIST ->
                ViewHolder(FileItemCompactListBinding.inflate(inflater, parent, false))
        }
        return holder.apply {
            itemLayout.apply {
                val context = context
                val isMaterial3Theme = context.isMaterial3Theme
                if (viewType == FileViewType.GRID && isMaterial3Theme) {
                    foregroundCompat =
                        context.getDrawableCompat(R.drawable.file_item_grid_foreground_material3)
                }
                background = if (viewType == FileViewType.GRID && isMaterial3Theme) {
                    CheckableItemBackground.create(4f, 12f, context)
                } else {
                    CheckableItemBackground.create(0f, 0f, context)
                }
            }
            thumbnailOutlineView?.apply {
                val context = context
                if (context.isMaterial3Theme) {
                    background = context.getDrawableCompat(
                        R.drawable.file_item_grid_thumbnail_outline_material3
                    )
                }
            }
            popupMenu = PopupMenu(menuButton.context, menuButton)
                .apply {
                    inflate(R.menu.file_item)
                    setOnDismissListener {
                        if (activePopupMenu === this) {
                            activePopupMenu = null
                        }
                    }
                }
            menuButton.setOnClickListener {
                activePopupMenu?.dismiss()
                activePopupMenu = popupMenu
                popupMenu.show()
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)

        if (activePopupMenu === holder.popupMenu) {
            holder.popupMenu.dismiss()
            activePopupMenu = null
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        throw UnsupportedOperationException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        val file = getItem(position)
        val isDirectory = file.attributes.isDirectory
        val isEnabled = isFileSelectable(file) || isDirectory
        holder.itemLayout.isEnabled = isEnabled
        holder.menuButton.isEnabled = isEnabled
        val menu = holder.popupMenu.menu
        val path = file.path
        val hasPickOptions = pickOptions != null
        val isReadOnly = path.fileSystem.isReadOnly
        menu.findItem(R.id.action_cut).isVisible = !hasPickOptions && !isReadOnly
        menu.findItem(R.id.action_copy).isVisible = !hasPickOptions
        menu.findItem(R.id.action_paste_into).isVisible =
            isDirectory && !hasPickOptions && !isReadOnly && hasPaste
        val checked = file in selectedFiles
        holder.itemLayout.isChecked = checked
        holder.nameText.setTextSize(
            TypedValue.COMPLEX_UNIT_PX, holder.nameTextBaseSize * fontScale
        )
        holder.descriptionText?.setTextSize(
            TypedValue.COMPLEX_UNIT_PX, holder.descriptionTextBaseSize * fontScale
        )
        holder.nameText.apply {
            if (isSingleLineCompat) {
                val nameEllipsize = nameEllipsize
                ellipsize = nameEllipsize
                isSelected = nameEllipsize == TextUtils.TruncateAt.MARQUEE
            } else {
                // End ellipsis hides the extension on a wrapped name.
                ellipsize = TextUtils.TruncateAt.MIDDLE
                isSelected = false
            }
        }
        applyListDensity(holder)
        bindDescription(holder, file, isDirectory)
        if (payloads.isNotEmpty()) {
            return
        }
        bindViewHolderAnimation(holder)
        holder.itemLayout.apply {
            setOnClickListener {
                if (selectedFiles.isEmpty()) {
                    listener.openFile(file)
                } else {
                    selectFile(file)
                }
            }
            setOnLongClickListener {
                if (selectedFiles.isEmpty()) {
                    selectFile(file)
                } else {
                    listener.openFile(file)
                }
                true
            }
            // A right click with a mouse or touchpad opens the item menu.
            setOnContextClickListener {
                holder.menuButton.performClick()
                true
            }
        }
        holder.iconLayout.setOnClickListener { selectFile(file) }
        val iconRes = file.mimeType.iconRes
        holder.iconImage.apply {
            isVisible = true
            setImageResource(iconRes)
        }
        holder.directoryThumbnailImage?.isVisible = isDirectory
        holder.thumbnailOutlineView?.isVisible = !isDirectory
        val supportsThumbnail = file.supportsThumbnail
        val shouldLoadThumbnailIcon = supportsThumbnail && holder.thumbnailIconImage != null &&
            file.mimeType.isApk
        val attributes = file.attributes
        holder.thumbnailIconImage?.apply {
            dispose()
            isVisible = !isDirectory
            setImageResource(iconRes)
            if (shouldLoadThumbnailIcon) {
                load(path to attributes)
            }
        }
        holder.thumbnailImage.apply {
            dispose()
            setImageDrawable(null)
            scaleType = if (fitThumbnails) {
                ImageView.ScaleType.FIT_CENTER
            } else {
                ImageView.ScaleType.CENTER_CROP
            }
            val shouldLoadThumbnail = supportsThumbnail && !shouldLoadThumbnailIcon
            isVisible = shouldLoadThumbnail
            if (shouldLoadThumbnail) {
                load(path to attributes) {
                    listener { _, _ ->
                        val iconImage = holder.thumbnailIconImage ?: holder.iconImage
                        iconImage.isVisible = false
                    }
                }
            }
        }
        holder.appIconBadgeImage.apply {
            dispose()
            setImageDrawable(null)
            val appDirectoryPackageName = file.appDirectoryPackageName
            val hasAppIconBadge = appDirectoryPackageName != null
            isVisible = hasAppIconBadge
            if (hasAppIconBadge) {
                load(AppIconPackageName(appDirectoryPackageName!!))
            }
        }
        holder.badgeImage.apply {
            val badgeIconRes = if (file.attributesNoFollowLinks.isSymbolicLink) {
                if (file.isSymbolicLinkBroken) {
                    R.drawable.error_badge_icon_18dp
                } else {
                    R.drawable.symbolic_link_badge_icon_18dp
                }
            } else if (file.attributesNoFollowLinks.isEncrypted()) {
                R.drawable.encrypted_badge_icon_18dp
            } else {
                null
            }
            val hasBadge = badgeIconRes != null
            isVisible = hasBadge
            if (hasBadge) {
                setImageResource(badgeIconRes!!)
            } else {
                setImageDrawable(null)
            }
        }
        holder.nameText.text = file.name
        val isArchivePath = path.isArchivePath
        menu.findItem(R.id.action_copy)
            .setTitle(if (isArchivePath) R.string.file_item_action_extract else R.string.copy)
        menu.findItem(R.id.action_delete).apply {
            isVisible = !isReadOnly
            val title = holder.menuButton.context.getString(R.string.delete)
            this.title = if (Settings.HIGHLIGHT_DELETE.valueCompat) {
                SpannableString(title).apply {
                    setSpan(
                        ForegroundColorSpan(
                            holder.menuButton.context.getColorByAttr(
                                androidx.appcompat.R.attr.colorError
                            )
                        ),
                        0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                title
            }
        }
        menu.findItem(R.id.action_rename).isVisible = !isReadOnly
        menu.findItem(R.id.action_extract).isVisible = file.isArchiveFile
        menu.findItem(R.id.action_test_archive).isVisible = file.isArchiveFile
        menu.findItem(R.id.action_open_as_archive).isVisible =
            !file.attributes.isDirectory && !file.isArchiveFile && !isArchivePath
        menu.findItem(R.id.action_create_link).isVisible = path.isLinuxPath && !isReadOnly
        menu.findItem(R.id.action_archive).isVisible = !isArchivePath
        menu.findItem(R.id.action_add_bookmark).isVisible = isDirectory
        menu.findItem(R.id.action_show_in_folder).isVisible = isSearching
        holder.popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_open_with -> {
                    listener.openFileWith(file)
                    true
                }
                R.id.action_cut -> {
                    listener.cutFile(file)
                    true
                }
                R.id.action_copy -> {
                    listener.copyFile(file)
                    true
                }
                R.id.action_paste_into -> {
                    listener.pasteInto(file.path)
                    true
                }
                R.id.action_delete -> {
                    listener.confirmDeleteFile(file)
                    true
                }
                R.id.action_rename -> {
                    listener.showRenameFileDialog(file)
                    true
                }
                R.id.action_extract -> {
                    listener.extractFile(file)
                    true
                }
                R.id.action_test_archive -> {
                    listener.testArchive(file)
                    true
                }
                R.id.action_open_as_archive -> {
                    listener.openAsArchive(file)
                    true
                }
                R.id.action_create_link -> {
                    listener.showCreateLinkDialog(file)
                    true
                }
                R.id.action_archive -> {
                    listener.showCreateArchiveDialog(file)
                    true
                }
                R.id.action_share -> {
                    listener.shareFile(file)
                    true
                }
                R.id.action_show_in_folder -> {
                    listener.showInFolder(file)
                    true
                }
                R.id.action_copy_path -> {
                    listener.copyPath(file)
                    true
                }
                R.id.action_add_bookmark -> {
                    listener.addBookmark(file)
                    true
                }
                R.id.action_create_shortcut -> {
                    listener.createShortcut(file)
                    true
                }
                R.id.action_hide -> {
                    listener.hideFile(file)
                    true
                }
                R.id.action_properties -> {
                    listener.showPropertiesDialog(file)
                    true
                }
                else -> false
            }
        }
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val file = getItem(position)
        return when (sortOptions.by) {
            FileSortOptions.By.NAME -> file.name.take(1).uppercase(Locale.getDefault())
            FileSortOptions.By.TYPE -> file.extension.uppercase(Locale.getDefault())
            FileSortOptions.By.SIZE -> file.attributes.fileSize.formatHumanReadable(view.context)
            FileSortOptions.By.LAST_MODIFIED ->
                file.attributes.lastModifiedTime().toInstant().formatShort(view.context)
        }
    }

    override val isAnimationEnabled: Boolean
        get() = Settings.FILE_LIST_ANIMATION.valueCompat

    private fun bindDescription(holder: ViewHolder, file: FileItem, isDirectory: Boolean) {
        val descriptionText = holder.descriptionText ?: return
        val context = descriptionText.context
        val attributes = file.attributes
        val descriptionParts = mutableListOf<String>()
        descriptionParts += attributes.lastModifiedTime().toInstant().formatShort(context)
        if (!isDirectory) {
            descriptionParts += attributes.fileSize.formatHumanReadable(context)
        } else if (Settings.FILE_LIST_SHOW_DIRECTORY_ITEM_COUNT.valueCompat) {
            val count = directoryItemCounts[file.path]
            if (count != null) {
                descriptionParts += descriptionText.resources.getQuantityString(
                    R.plurals.file_list_directory_item_count_format, count, count
                )
            } else {
                requestDirectoryItemCount(file.path)
            }
        }
        if (Settings.FILE_LIST_SHOW_PERMISSIONS.valueCompat) {
            (attributes as? PosixFileAttributes)?.mode()?.let {
                descriptionParts += it.toModeString()
            }
        }
        if (!isDirectory) {
            file.extension.takeIf { it.isNotEmpty() }?.let {
                descriptionParts += it.uppercase(Locale.getDefault())
            }
        }
        val descriptionSeparator = context.getString(R.string.file_item_description_separator)
        descriptionText.text = descriptionParts.joinToString(descriptionSeparator)
    }

    private fun requestDirectoryItemCount(path: Path) {
        if (path in directoryItemCountJobs || path in directoryItemCounts ||
            path in directoryItemCountFailures
        ) {
            return
        }
        directoryItemCountJobs.add(path)
        val generation = directoryItemCountGeneration
        val showHidden = Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val count = countDirectoryItems(path, showHidden)
            mainHandler.post {
                if (generation != directoryItemCountGeneration) {
                    return@post
                }
                directoryItemCountJobs.remove(path)
                if (count == null) {
                    directoryItemCountFailures.add(path)
                    return@post
                }
                directoryItemCounts[path] = count
                filePositionMap[path]?.let {
                    notifyItemChanged(it, PAYLOAD_DIRECTORY_ITEM_COUNT)
                }
            }
        }
    }

    companion object {
        private val PAYLOAD_STATE_CHANGED = Any()
        private val PAYLOAD_DIRECTORY_ITEM_COUNT = Any()

        private val CALLBACK = object : DiffUtil.ItemCallback<FileItem>() {
            override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
                oldItem.path == newItem.path

            override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
                oldItem.sameDisplayedContent(newItem)
        }
    }

    class ViewHolder private constructor(
        root: View,
        val itemLayout: CheckableForegroundLinearLayout,
        val iconLayout: View,
        val iconImage: ImageView,
        val directoryThumbnailImage: ImageView?,
        val thumbnailOutlineView: View?,
        val thumbnailIconImage: ImageView?,
        val thumbnailImage: ImageView,
        val appIconBadgeImage: ImageView,
        val badgeImage: ImageView,
        val nameText: TextView,
        val descriptionText: TextView?,
        val menuButton: ImageButton
    ) : RecyclerView.ViewHolder(root) {
        // The sizes from the layout, so that the font scale is always applied to the original.
        val nameTextBaseSize = nameText.textSize
        val descriptionTextBaseSize = descriptionText?.textSize ?: 0f

        constructor(binding: FileItemListBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            null,
            null,
            null,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            binding.descriptionText,
            binding.menuButton
        )

        constructor(binding: FileItemCompactListBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            null,
            null,
            null,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            binding.descriptionText,
            binding.menuButton
        )

        constructor(binding: FileItemGridBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            binding.directoryThumbnailImage,
            binding.thumbnailOutlineView,
            binding.thumbnailIconImage,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            null,
            binding.menuButton
        )

        lateinit var popupMenu: PopupMenu
    }

    private fun applyListDensity(holder: ViewHolder) {
        if (_viewType == FileViewType.GRID) {
            return
        }
        val heightDp = when (_viewType) {
            FileViewType.COMPACT_LIST -> when (listDensity) {
                FileListDensity.COMFORTABLE -> 40
                FileListDensity.COMPACT -> 36
                FileListDensity.TIGHT -> 32
            }
            else -> when (listDensity) {
                FileListDensity.COMFORTABLE -> 72
                FileListDensity.COMPACT -> 56
                FileListDensity.TIGHT -> 48
            }
        }
        val heightPx = (heightDp * holder.itemLayout.resources.displayMetrics.density).toInt()
        val layoutParams = holder.itemLayout.layoutParams
        if (layoutParams.height != heightPx) {
            layoutParams.height = heightPx
            holder.itemLayout.requestLayout()
        }
        val nameSp = when (listDensity) {
            FileListDensity.COMFORTABLE -> 16f
            FileListDensity.COMPACT -> 14f
            FileListDensity.TIGHT -> 13f
        }
        holder.nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, nameSp)
        holder.descriptionText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, (nameSp - 2f).coerceAtLeast(11f))
    }

    interface Listener {
        fun clearSelectedFiles()
        fun selectFile(file: FileItem, selected: Boolean)
        fun selectFiles(files: FileItemSet, selected: Boolean)
        fun openFile(file: FileItem)
        fun openFileWith(file: FileItem)
        fun cutFile(file: FileItem)
        fun copyFile(file: FileItem)
        fun pasteInto(directory: Path)
        fun confirmDeleteFile(file: FileItem)
        fun showRenameFileDialog(file: FileItem)
        fun extractFile(file: FileItem)
        fun testArchive(file: FileItem)
        fun openAsArchive(file: FileItem)
        fun showCreateLinkDialog(file: FileItem)
        fun showCreateArchiveDialog(file: FileItem)
        fun shareFile(file: FileItem)
        fun copyPath(file: FileItem)
        fun addBookmark(file: FileItem)

        fun showInFolder(file: FileItem)
        fun createShortcut(file: FileItem)
        fun hideFile(file: FileItem)
        fun showPropertiesDialog(file: FileItem)
    }
}

private fun countDirectoryItems(path: Path, showHidden: Boolean): Int? =
    try {
        var count = 0
        path.newDirectoryStream().use { stream ->
            for (child in stream) {
                if (!showHidden) {
                    val hidden = try {
                        child.isHidden
                    } catch (e: IOException) {
                        false
                    }
                    if (hidden) {
                        continue
                    }
                }
                count++
            }
        }
        count
    } catch (e: Exception) {
        null
    }
