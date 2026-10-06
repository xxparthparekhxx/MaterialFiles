/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.root

import android.annotation.SuppressLint
import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.compat.UserHandleCompat
import me.zhanghai.android.files.provider.FileSystemProviders
import me.zhanghai.android.files.provider.remote.RemoteFileService
import me.zhanghai.android.files.provider.remote.RemoteInterface
import me.zhanghai.android.files.util.lazyReflectedMethod

// We are expanding our root file service to shell UID, but let's keep the original name since it's
// a bit awkward to express root-or-shell-UID in one or two words.
val isRunningAsRoot =
    when (UserHandleCompat.getAppId(Process.myUid())) {
        Process.ROOT_UID, Process.SHELL_UID -> true
        else -> false
    }

@SuppressLint("StaticFieldLeak")
lateinit var rootContext: Context private set

object RootFileService : RemoteFileService(
    RemoteInterface {
        if (ShizukuFileServiceLauncher.isAvailable()) {
            ShizukuFileServiceLauncher.launchService()
        } else {
            LibSuFileServiceLauncher.launchService()
        }
    }
) {
    const val TIMEOUT_MILLIS = 15 * 1000L

    private val LOG_TAG = RootFileService::class.java.simpleName

    // Not actually restricted because there's no restriction when running as root.
    //@RestrictedHiddenApi
    private val activityThreadCurrentActivityThreadMethod by lazyReflectedMethod(
        "android.app.ActivityThread", "currentActivityThread"
    )
    //@RestrictedHiddenApi
    private val activityThreadGetSystemContextMethod by lazyReflectedMethod(
        "android.app.ActivityThread", "getSystemContext"
    )

    fun main() {
        Log.i(LOG_TAG, "Creating package context")
        rootContext = createPackageContext(BuildConfig.APPLICATION_ID)
        ensureNativeLibrariesLoaded()
        Log.i(LOG_TAG, "Installing file system providers")
        FileSystemProviders.install()
        FileSystemProviders.overflowWatchEvents = true
    }

    private fun ensureNativeLibrariesLoaded() {
        val libraries = listOf("syscall", "selinux-jni")
        for (lib in libraries) {
            try {
                System.loadLibrary(lib)
            } catch (e: UnsatisfiedLinkError) {
                Log.w(LOG_TAG, "Failed to load $lib with System.loadLibrary(), trying fallback", e)
                loadNativeLibraryFallback(lib, e)
            }
        }
    }

    fun loadNativeLibraryFallback(name: String, error: UnsatisfiedLinkError) {
        val fileName = System.mapLibraryName(name)
        val fallbackCandidates = listOf(
            File("/data/dalvik-cache/materialfiles_lib", fileName),
            File("/data/local/tmp/materialfiles_lib", fileName)
        )
        for (candidate in fallbackCandidates) {
            if (candidate.exists()) {
                try {
                    System.load(candidate.absolutePath)
                    Log.i(LOG_TAG, "Loaded fallback library: ${candidate.absolutePath}")
                    return
                } catch (t: Throwable) {
                    Log.w(LOG_TAG, "Failed to load candidate: ${candidate.absolutePath}", t)
                }
            }
        }
        try {
            val nativeLibDir = rootContext.applicationInfo.nativeLibraryDir
            val sourceFile = File(nativeLibDir, fileName)
            if (sourceFile.exists()) {
                val targetDir = File("/data/dalvik-cache/materialfiles_lib").apply { mkdirs() }
                val targetFile = File(targetDir, fileName)
                sourceFile.copyTo(targetFile, overwrite = true)
                targetFile.setReadable(true, false)
                targetFile.setExecutable(true, false)
                System.load(targetFile.absolutePath)
                Log.i(LOG_TAG, "Loaded copied library: ${targetFile.absolutePath}")
                return
            }
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Failed to copy and load fallback library for $name", t)
        }
        throw error
    }

    private fun createPackageContext(packageName: String): Context {
        val activityThread = activityThreadCurrentActivityThreadMethod.invoke(null)
        val systemContext = activityThreadGetSystemContextMethod.invoke(activityThread) as Context
        return systemContext.createPackageContext(
            packageName, Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
        )
    }
}
