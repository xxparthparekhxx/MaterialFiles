/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.app.AlertDialog
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.ContextThemeWrapper
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.doWithStartForegroundServiceAllowed
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

@RequiresApi(Build.VERSION_CODES.N)
class FtpServerTileService : TileService() {
    private val observer = Observer<FtpServerService.State> { onFtpServerStateChanged(it) }

    override fun onStartListening() {
        super.onStartListening()

        FtpServerService.stateLiveData.observeForever(observer)
    }

    override fun onStopListening() {
        super.onStopListening()

        FtpServerService.stateLiveData.removeObserver(observer)
    }

    private fun onFtpServerStateChanged(state: FtpServerService.State) {
        val tile = qsTile
        when (state) {
            FtpServerService.State.STARTING,
            FtpServerService.State.RUNNING -> tile.state = Tile.STATE_ACTIVE
            FtpServerService.State.STOPPING -> tile.state = Tile.STATE_UNAVAILABLE
            FtpServerService.State.STOPPED -> tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (isLocked) {
            unlockAndRun { onToggleRequested() }
        } else {
            onToggleRequested()
        }
    }

    private fun onToggleRequested() {
        val starting = FtpServerService.stateLiveData.valueCompat == FtpServerService.State.STOPPED
        if (starting && Settings.FTP_SERVER_TILE_CONFIRM_START.valueCompat) {
            val context = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            showDialog(
                AlertDialog.Builder(context)
                    .setTitle(R.string.ftp_server_tile_confirm_start_title)
                    .setMessage(R.string.ftp_server_tile_confirm_start_message)
                    .setPositiveButton(R.string.ftp_server_tile_confirm_start_positive) { _, _ ->
                        toggle()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
            )
        } else {
            toggle()
        }
    }

    private fun toggle() {
        doWithStartForegroundServiceAllowed { FtpServerService.toggle(this) }
    }
}
