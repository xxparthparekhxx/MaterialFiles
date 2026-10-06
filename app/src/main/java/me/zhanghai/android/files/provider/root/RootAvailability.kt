package me.zhanghai.android.files.provider.root

import kotlin.concurrent.Volatile

object RootAvailability {
    // The check starts a process, and the navigation list asks for this on every refresh, so a
    // negative answer is reused for a while.
    private const val UNAVAILABLE_CACHE_MILLIS = 60_000L

    @Volatile
    private var wasAvailable: Boolean = false

    @Volatile
    private var lastUnavailableMillis: Long = 0

    val isAvailable: Boolean
        get() {
            if (wasAvailable) {
                return true
            }
            val now = System.currentTimeMillis()
            if (lastUnavailableMillis != 0L && now - lastUnavailableMillis < UNAVAILABLE_CACHE_MILLIS) {
                return false
            }
            return checkAvailable().also {
                if (it) {
                    wasAvailable = true
                } else {
                    lastUnavailableMillis = now
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
