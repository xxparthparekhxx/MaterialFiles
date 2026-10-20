/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Intent
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.compat.getDrawableCompat
import me.zhanghai.android.files.compat.setTintCompat
import me.zhanghai.android.files.databinding.HomeItemBinding
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.layoutInflater
import java8.nio.file.Path

data class HomeItem(
    @DrawableRes val iconRes: Int,
    val title: String,
    val subtitle: String?,
    val path: Path?,
    val intent: Intent?,
)

class HomeAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<HomeAdapter.ViewHolder>() {
    private var items: List<HomeItem> = emptyList()

    fun replace(items: List<HomeItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(HomeItemBinding.inflate(parent.context.layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val binding = holder.binding
        val context = binding.root.context
        val secondaryTextColor = context.getColorByAttr(android.R.attr.textColorSecondary)
        binding.icon.setImageDrawable(
            context.getDrawableCompat(item.iconRes).apply {
                mutate()
                setTintCompat(secondaryTextColor)
            }
        )
        binding.title.text = item.title
        binding.subtitle.text = item.subtitle
        binding.subtitle.isVisible = item.subtitle != null
        binding.root.setOnClickListener { listener.onItemClick(item) }
    }

    class ViewHolder(val binding: HomeItemBinding) : RecyclerView.ViewHolder(binding.root)

    interface Listener {
        fun onItemClick(item: HomeItem)
    }
}
