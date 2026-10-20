/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.image

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.databinding.ExifAttributeItemBinding
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.layoutInflater

class ExifAttributesAdapter(
    attributes: Map<String, String>
) : RecyclerView.Adapter<ExifAttributesAdapter.ViewHolder>() {
    private val items = attributes.toList()

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ExifAttributeItemBinding.inflate(parent.context.layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, value) = items[position]
        holder.binding.nameText.text = name
        holder.binding.valueText.text = value
        holder.itemView.setOnClickListener {
            clipboardManager.copyText("$name: $value", holder.itemView.context)
        }
    }

    class ViewHolder(val binding: ExifAttributeItemBinding) : RecyclerView.ViewHolder(binding.root)
}
