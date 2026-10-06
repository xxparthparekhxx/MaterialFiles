/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.RecentFileItemBinding
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.util.layoutInflater

class RecentFilesAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<RecentFilesAdapter.ViewHolder>() {
    private var files: List<RecentFile> = emptyList()

    fun replace(files: List<RecentFile>) {
        this.files = files
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = files.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(RecentFileItemBinding.inflate(parent.context.layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        val binding = holder.binding
        val mimeType = file.mimeType.asMimeType()
        binding.iconImage.setImageResource(mimeType.iconRes)
        binding.nameText.text = file.name
        val parentPath = RecentFiles.pathOf(file)?.parent?.toString()
        val time = DateUtils.getRelativeTimeSpanString(file.openedAt).toString()
        binding.descriptionText.text = if (parentPath.isNullOrEmpty()) time else "$parentPath · $time"
        binding.root.setOnClickListener { listener.openRecentFile(file) }
        binding.root.setOnLongClickListener {
            val popup = PopupMenu(binding.root.context, binding.root)
            popup.inflate(R.menu.recent_file_item)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_show_in_folder -> {
                        listener.showRecentFileInFolder(file)
                        true
                    }
                    R.id.action_remove -> {
                        listener.removeRecentFile(file)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
            true
        }
    }

    class ViewHolder(val binding: RecentFileItemBinding) : RecyclerView.ViewHolder(binding.root)

    interface Listener {
        fun openRecentFile(file: RecentFile)
        fun showRecentFileInFolder(file: RecentFile)
        fun removeRecentFile(file: RecentFile)
    }
}
