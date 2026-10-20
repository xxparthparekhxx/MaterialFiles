/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.compare

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.FileCompareItemBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.file.guessFromPath
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.util.layoutInflater

class FileCompareAdapter(
    private val leftName: String,
    private val rightName: String,
    private val listener: Listener
) : RecyclerView.Adapter<FileCompareAdapter.ViewHolder>() {
    private var entries: List<FolderCompareEntry> = emptyList()

    fun replace(entries: List<FolderCompareEntry>) {
        this.entries = entries
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(FileCompareItemBinding.inflate(parent.context.layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val binding = holder.binding
        val file = entry.left ?: entry.right ?: return
        val mimeType = if (file.isDirectory) {
            MimeType.DIRECTORY
        } else {
            MimeType.guessFromPath(file.path.toString())
        }
        binding.iconImage.setImageResource(mimeType.iconRes)
        binding.titleText.text = entry.relativePath
        binding.descriptionText.text = getDescription(binding.root.context, entry)
        binding.root.setOnClickListener {
            listener.onEntryMenu(entry, binding.menuButton)
        }
        binding.menuButton.setOnClickListener {
            listener.onEntryMenu(entry, binding.menuButton)
        }
    }

    private fun getDescription(context: Context, entry: FolderCompareEntry): String {
        val baseDescription = when (entry.status) {
            FolderCompareStatus.ONLY_LEFT ->
                context.getString(R.string.file_compare_only_in_format, leftName)
            FolderCompareStatus.ONLY_RIGHT ->
                context.getString(R.string.file_compare_only_in_format, rightName)
            FolderCompareStatus.DIFFERENT -> getDifferentDescription(context, entry)
        }
        return when (entry.contentState) {
            ContentCompareState.UNKNOWN -> baseDescription
            ContentCompareState.COMPARING -> context.getString(R.string.file_compare_content_comparing)
            ContentCompareState.SAME -> context.getString(R.string.file_compare_content_same)
            ContentCompareState.DIFFERENT -> context.getString(R.string.file_compare_content_different)
            ContentCompareState.ERROR -> context.getString(R.string.file_compare_content_error)
        }
    }

    private fun getDifferentDescription(
        context: Context,
        entry: FolderCompareEntry
    ): String {
        val left = entry.left ?: return ""
        val right = entry.right ?: return ""
        return if (left.isDirectory != right.isDirectory) {
            context.getString(R.string.file_compare_different_type)
        } else if (left.size != right.size) {
            context.getString(
                R.string.file_compare_different_size_format,
                left.size.asFileSize().formatHumanReadable(context),
                right.size.asFileSize().formatHumanReadable(context)
            )
        } else {
            context.getString(R.string.file_compare_different_time)
        }
    }

    class ViewHolder(val binding: FileCompareItemBinding) : RecyclerView.ViewHolder(binding.root)

    interface Listener {
        fun onEntryMenu(entry: FolderCompareEntry, anchor: View)
    }
}
