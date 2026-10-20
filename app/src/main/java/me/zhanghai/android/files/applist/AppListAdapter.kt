/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.applist

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.AppListItemBinding
import me.zhanghai.android.files.file.asFileSize

class AppEntry(
    val applicationInfo: ApplicationInfo,
    val label: String,
    val isSystem: Boolean,
    val size: Long
) {
    val packageName: String
        get() = applicationInfo.packageName
}

class AppListAdapter(
    private val packageManager: PackageManager,
    private val listener: Listener
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {
    private var apps: List<AppEntry> = emptyList()

    fun replace(apps: List<AppEntry>) {
        this.apps = apps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AppListItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    inner class ViewHolder(
        private val binding: AppListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppEntry) {
            val context = binding.root.context
            binding.iconImage.setImageDrawable(app.applicationInfo.loadIcon(packageManager))
            binding.nameText.text = app.label
            binding.detailsText.text = context.getString(
                R.string.app_list_details_format, app.packageName,
                app.size.asFileSize().formatHumanReadable(context)
            )
            binding.menuButton.setOnClickListener { listener.showAppMenu(app, binding.menuButton) }
            binding.root.setOnClickListener { listener.openApp(app) }
        }
    }

    interface Listener {
        fun openApp(app: AppEntry)
        fun backupApp(app: AppEntry)
        fun uninstallApp(app: AppEntry)
        fun showAppMenu(app: AppEntry, view: View)
    }
}
