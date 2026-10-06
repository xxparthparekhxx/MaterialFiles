/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.os.Environment
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.getDescriptionCompat
import me.zhanghai.android.files.compat.isPrimaryCompat
import me.zhanghai.android.files.compat.pathCompat
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.storage.StorageVolumeListLiveData
import me.zhanghai.android.files.util.isMounted
import me.zhanghai.android.files.util.supportsExternalStorageManager
import me.zhanghai.android.files.util.valueCompat
import org.apache.ftpserver.ftplet.User

data class FtpServerRoot(
    val name: String,
    val path: Path
)

object FtpServerRoots {
    fun isMultiRootEnabled(user: User? = null): Boolean {
        if (!Settings.FTP_SERVER_EXPOSE_ALL_STORAGES.valueCompat) {
            return false
        }
        // Never expose every storage to anonymous sessions, even if the switch is on.
        if (Settings.FTP_SERVER_ANONYMOUS_LOGIN.valueCompat) {
            return false
        }
        if (user != null && user.name == FtpServerService.USERNAME_ANONYMOUS) {
            return false
        }
        return true
    }

    fun get(user: User? = null): List<FtpServerRoot> {
        val home = Settings.FTP_SERVER_HOME_DIRECTORY.valueCompat.normalize()
        if (!isMultiRootEnabled(user)) {
            val name = home.fileName?.toString()?.takeIf { it.isNotEmpty() } ?: "Home"
            return listOf(FtpServerRoot(sanitize(name), home))
        }
        val roots = mutableListOf<FtpServerRoot>()
        val paths = mutableSetOf<Path>()
        for (storage in Settings.STORAGES.valueCompat) {
            if (!storage.isVisible) {
                continue
            }
            val path = storage.path?.normalize() ?: continue
            if (!paths.add(path)) {
                continue
            }
            roots += FtpServerRoot(uniqueName(sanitize(storage.getName(application)), roots), path)
        }
        if (Environment::class.supportsExternalStorageManager()) {
            for (volume in StorageVolumeListLiveData.valueCompat) {
                if (volume.isPrimaryCompat || !volume.isMounted) {
                    continue
                }
                val path = Paths.get(volume.pathCompat).normalize()
                if (!paths.add(path)) {
                    continue
                }
                val name = volume.getDescriptionCompat(application)
                roots += FtpServerRoot(uniqueName(sanitize(name), roots), path)
            }
        }
        if (paths.add(home)) {
            val name = home.fileName?.toString()?.takeIf { it.isNotEmpty() } ?: "Home"
            roots += FtpServerRoot(uniqueName(sanitize(name), roots), home)
        }
        return roots
    }

    private fun sanitize(name: String): String {
        val cleaned = name.map { char ->
            if (char == '/' || char == '\\' || char == '\u0000' || char == '\r' || char == '\n') {
                '_'
            } else {
                char
            }
        }.joinToString("").trim().trimEnd('.')
        return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") "Storage" else cleaned
    }

    private fun uniqueName(base: String, roots: List<FtpServerRoot>): String {
        if (roots.none { it.name == base }) {
            return base
        }
        var index = 2
        while (roots.any { it.name == "$base $index" }) {
            index += 1
        }
        return "$base $index"
    }
}
