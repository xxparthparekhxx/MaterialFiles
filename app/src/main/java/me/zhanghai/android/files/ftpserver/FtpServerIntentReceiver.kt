package me.zhanghai.android.files.ftpserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.zhanghai.android.files.R
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.valueCompat

/**
 * Lets automation apps and `adb` start and stop the FTP server with a broadcast, but only when the
 * user has explicitly allowed it in the FTP server settings: otherwise any app on the device
 * could change the FTP server state.
 */
class FtpServerIntentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Settings.FTP_SERVER_ALLOW_EXTERNAL_CONTROL.valueCompat) {
            context.showToast(R.string.ftp_server_external_control_disabled)
            return
        }
        try {
            when (intent.action) {
                ACTION_START -> FtpServerService.start(context)
                ACTION_STOP -> FtpServerService.stop(context)
            }
        } catch (e: Exception) {
            // Android may refuse to start a foreground service from the background.
            e.printStackTrace()
            context.showToast(e.toString())
        }
    }

    companion object {
        const val ACTION_START = "me.zhanghai.android.files.intent.action.START_FTP_SERVER"
        const val ACTION_STOP = "me.zhanghai.android.files.intent.action.STOP_FTP_SERVER"
    }
}
