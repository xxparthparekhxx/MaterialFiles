/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.linux

import android.os.SystemClock
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.linux.syscall.StructMntent
import me.zhanghai.android.files.provider.linux.syscall.Syscall
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

object MountPoints {
    @Volatile
    private var cachedMountDirs: Set<ByteString>? = null
    @Volatile
    private var cachedMountEntries: List<StructMntent>? = null
    @Volatile
    private var lastFetchTime = 0L
    private const val CACHE_TTL_MILLIS = 2000L

    @Volatile
    private var cachedParentPath: ByteString? = null
    @Volatile
    private var cachedParentDeviceId: Long? = null

    internal fun refreshMountsIfNeeded(): List<StructMntent> {
        val now = try {
            SystemClock.uptimeMillis()
        } catch (e: Throwable) {
            System.currentTimeMillis()
        }
        val entries = cachedMountEntries
        if (entries != null && now - lastFetchTime < CACHE_TTL_MILLIS) {
            return entries
        }
        val newEntries = fetchMountEntries()
        cachedMountEntries = newEntries
        cachedMountDirs = newEntries.mapTo(mutableSetOf()) { it.mnt_dir }
        lastFetchTime = now
        return newEntries
    }

    private fun fetchMountEntries(): List<StructMntent> {
        try {
            return LocalLinuxFileStore.getMountEntries()
        } catch (e: Throwable) {
            // SyscallException or UnsatisfiedLinkError in non-JNI environments
        }
        for (procPath in listOf("/proc/self/mounts", "/proc/mounts")) {
            val file = File(procPath)
            if (file.canRead()) {
                try {
                    val entries = mutableListOf<StructMntent>()
                    BufferedReader(FileReader(file)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val parts = line!!.split(" ")
                            if (parts.size >= 4) {
                                val fsname = parts[0].toByteString()
                                val dir = parts[1].toByteString()
                                val type = parts[2].toByteString()
                                val opts = parts[3].toByteString()
                                val freq = parts.getOrNull(4)?.toIntOrNull() ?: 0
                                val passno = parts.getOrNull(5)?.toIntOrNull() ?: 0
                                entries += StructMntent(fsname, dir, type, opts, freq, passno)
                            }
                        }
                    }
                    if (entries.isNotEmpty()) {
                        return entries
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        return emptyList()
    }

    fun isMountPoint(path: Path, attributes: BasicFileAttributes? = null): Boolean {
        if (!path.isLinuxPath) {
            return false
        }
        val linuxPath = path as LinuxPath
        val parent = linuxPath.parent
        if (parent == null) {
            // Root directory / is always a mount point.
            return true
        }

        val pathBytes = linuxPath.toByteString()
        refreshMountsIfNeeded()
        if (cachedMountDirs?.contains(pathBytes) == true) {
            return true
        }

        return try {
            val devId = (attributes as? LinuxFileAttributes)?.deviceId
                ?: Syscall.lstat(pathBytes).st_dev

            val parentBytes = parent.toByteString()
            val parentDevId = if (cachedParentPath == parentBytes && cachedParentDeviceId != null) {
                cachedParentDeviceId!!
            } else {
                val stat = Syscall.lstat(parentBytes)
                cachedParentPath = parentBytes
                cachedParentDeviceId = stat.st_dev
                stat.st_dev
            }

            devId != parentDevId
        } catch (e: Throwable) {
            false
        }
    }

    fun getMountEntry(path: Path): StructMntent? {
        if (!path.isLinuxPath) {
            return null
        }
        val entries = refreshMountsIfNeeded()
        val pathBytes = (path as LinuxPath).toByteString()
        return entries.find { it.mnt_dir == pathBytes }
    }

    fun findMountEntry(path: Path): StructMntent? {
        if (!path.isLinuxPath) {
            return null
        }
        val entries = refreshMountsIfNeeded()
        val entryMap = entries.associateBy { it.mnt_dir }
        var curr: LinuxPath? = path as LinuxPath
        while (curr != null) {
            val mntent = entryMap[curr.toByteString()]
            if (mntent != null) {
                return mntent
            }
            curr = curr.parent
        }
        return null
    }
}

val Path.isMountPoint: Boolean
    get() = MountPoints.isMountPoint(this)

fun Path.isMountPoint(attributes: BasicFileAttributes? = null): Boolean =
    MountPoints.isMountPoint(this, attributes)
