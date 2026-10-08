/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import me.zhanghai.android.files.navigation.NavigationStorageRefreshLiveData
import me.zhanghai.android.files.util.findCauseByClass
import me.zhanghai.android.files.util.showToast
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicInteger

abstract class FileJob {
    val id = nextId.getAndIncrement()

    @Volatile
    var isCanceled = false
        private set

    internal lateinit var service: FileJobService
        private set

    internal val deletedLinuxPaths = mutableListOf<String>()

    fun cancel() {
        isCanceled = true
    }

    fun runOn(service: FileJobService) {
        this.service = service
        try {
            if (!isCanceled) {
                run()
            }
            // TODO: Toast
        } catch (e: InterruptedIOException) {
            // TODO
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
            // An SMB listing interrupted by a reload, or a canceled job, is not a failed transfer.
            if (!isCanceled && e.findCauseByClass<InterruptedException>() == null) {
                service.showToast(e)
            }
        } finally {
            flushDeletedLinuxPaths()
            service.notificationManager.cancel(id)
            FileJobProgresses.remove(id)
            NavigationStorageRefreshLiveData.notifyChanged()
        }
    }

    @Throws(IOException::class)
    protected abstract fun run()

    companion object {
        // 1 is reserved for the FTP server notification.
        private val nextId = AtomicInteger(2)
    }
}
