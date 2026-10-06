/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.preference.SwitchPreferenceCompat
import me.zhanghai.android.files.R

class FtpServerStatePreference : SwitchPreferenceCompat {
    private val observer = Observer<FtpServerService.State> { onStateChanged(it) }

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
    }

    override fun onAttached() {
        super.onAttached()

        FtpServerService.stateLiveData.observeForever(observer)
    }

    override fun onDetached() {
        super.onDetached()

        FtpServerService.stateLiveData.removeObserver(observer)
    }

    private fun onStateChanged(state: FtpServerService.State) {
        val summaryRes = when (state) {
            FtpServerService.State.STARTING -> R.string.ftp_server_state_summary_starting
            FtpServerService.State.RUNNING -> R.string.ftp_server_state_summary_running
            FtpServerService.State.STOPPING -> R.string.ftp_server_state_summary_stopping
            FtpServerService.State.STOPPED -> R.string.ftp_server_state_summary_stopped
        }
        summary = context.getString(summaryRes)
        isChecked = state == FtpServerService.State.STARTING
            || state == FtpServerService.State.RUNNING
        isEnabled = !(state == FtpServerService.State.STARTING
            || state == FtpServerService.State.STOPPING)
    }

    override fun onClick() {
        val state = FtpServerService.stateLiveData.value
        val isRunning = state == FtpServerService.State.RUNNING
            || state == FtpServerService.State.STARTING
        if (!isRunning && requestNearbyWifiPermission()) {
            return
        }
        FtpServerService.toggle(context)
    }

    private fun requestNearbyWifiPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val activity = context.findActivity() ?: return false
        activity.requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 0)
        return true
    }

    private fun Context.findActivity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) {
                return current
            }
            current = current.baseContext
        }
        return null
    }
}
