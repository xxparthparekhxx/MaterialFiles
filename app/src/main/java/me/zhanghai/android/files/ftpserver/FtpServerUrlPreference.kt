/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.content.Context
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Menu
import android.view.View
import android.view.View.OnCreateContextMenuListener
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.lifecycle.Observer
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.valueCompat

class FtpServerUrlPreference : Preference {
    private val observer = Observer<Any> { updateUrl() }
    private val receiver = FtpServerUrl.createChangeReceiver(context) { updateUrl() }

    private var url: String? = null
    private var entries = emptyList<FtpServerUrl.Entry>()
    private var entryIndex = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        isPersistent = false
        updateUrl()
    }

    override fun onAttached() {
        super.onAttached()

        Settings.FTP_SERVER_ANONYMOUS_LOGIN.observeForever(observer)
        Settings.FTP_SERVER_USERNAME.observeForever(observer)
        Settings.FTP_SERVER_PORT.observeForever(observer)
        receiver.register()
    }

    override fun onDetached() {
        super.onDetached()

        Settings.FTP_SERVER_ANONYMOUS_LOGIN.removeObserver(observer)
        Settings.FTP_SERVER_USERNAME.removeObserver(observer)
        Settings.FTP_SERVER_PORT.removeObserver(observer)
        receiver.unregister()
    }

    private fun updateUrl() {
        entries = FtpServerUrl.getEntries()
        if (entryIndex >= entries.size) {
            entryIndex = 0
        }
        val entry = entries.getOrNull(entryIndex)
        url = entry?.url ?: FtpServerUrl.getUrl()
        summary = when {
            url == null ->
                context.getString(R.string.ftp_server_url_summary_no_local_inet_address)
            entries.size > 1 -> {
                val interfaceName = entry!!.interfaceName
                val interfaceLabel =
                    if (entry.isHotspot) {
                        "$interfaceName, " +
                            context.getString(R.string.ftp_server_url_interface_hotspot)
                    } else {
                        interfaceName
                    }
                context.getString(
                    R.string.ftp_server_url_summary_interface_format, url, interfaceLabel
                )
            }
            else -> url
        }
    }

    // Tapping cycles through the URLs of all network interfaces.
    override fun onClick() {
        super.onClick()

        if (entries.size > 1) {
            entryIndex = (entryIndex + 1) % entries.size
            updateUrl()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.setOnCreateContextMenuListener(object : OnCreateContextMenuListener {
            override fun onCreateContextMenu(
                menu: ContextMenu,
                view: View,
                menuInfo: ContextMenuInfo?
            ) {
                val url = url ?: return
                menu.apply {
                    setHeaderTitle(url)
                    add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.ftp_server_url_menu_copy_url)
                        .setOnMenuItemClickListener {
                            clipboardManager.copyText(url, context)
                            true
                        }
                    if (!Settings.FTP_SERVER_ANONYMOUS_LOGIN.valueCompat) {
                        val password = Settings.FTP_SERVER_PASSWORD.valueCompat
                        if (password.isNotEmpty()) {
                            add(
                                Menu.NONE, Menu.NONE, Menu.NONE,
                                R.string.ftp_server_url_menu_copy_password
                            ).setOnMenuItemClickListener {
                                clipboardManager.copyText(password, context)
                                true
                            }
                        }
                    }
                }
            }
        })
    }
}
