package me.zhanghai.android.files.provider.root

import kotlin.concurrent.Volatile

object RootAvailability {
    @Volatile
    private var wasAvailable: Boolean = false

    val isAvailable: Boolean
        get() {
            if (wasAvailable) {
                return true
            }
            return checkAvailable().also {
                if (it) {
                    wasAvailable = true
                }
            }
        }

    private fun checkAvailable(): Boolean {
        try {
            if (isRunningAsRoot) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (ShizukuFileServiceLauncher.isAvailable()) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (LibSuFileServiceLauncher.isSuAvailable()) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
