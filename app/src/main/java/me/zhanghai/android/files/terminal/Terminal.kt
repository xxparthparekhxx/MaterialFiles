/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import java.io.File
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.util.startActivitySafe

object Terminal {
    fun open(path: String, context: Context) {
        // Prefer terminals exposing a TermHere entry point (e.g. TermOne Plus).
        packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("*/*"), 0)
            .firstOrNull { it.activityInfo.name.endsWith(".TermHere") }?.activityInfo
            ?.let { ComponentName(it.packageName, it.name) }
            ?.let { componentName ->
                val intent = Intent()
                    .setComponent(componentName)
                    .setAction(Intent.ACTION_SEND)
                    .putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
                context.startActivitySafe(intent)
                return
            }
        // Termux doesn't expose TermHere. Ask it to start a shell in this directory.
        if (openTermux(path, context)) {
            return
        }
        // Preserve the previous fallback; the safe launcher surfaces a clear message if it
        // is missing as well.
        val intent = Intent()
            .setComponent(ComponentName("jackpal.androidterm", "jackpal.androidterm.TermHere"))
            .setAction(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
        context.startActivitySafe(intent)
    }

    private fun openTermux(path: String, context: Context): Boolean {
        val launchIntent = try {
            packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE_NAME)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return false
        val bash = try {
            val info = packageManager.getApplicationInfo(TERMUX_PACKAGE_NAME, PackageManager.GET_META_DATA)
            File(info.dataDir, "files/usr/bin/bash").path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        if (bash != null) {
            val intent = Intent(TERMUX_RUN_COMMAND)
                .setClassName(TERMUX_PACKAGE_NAME, TERMUX_RUN_COMMAND_SERVICE)
                .putExtra(TERMUX_RUN_COMMAND_PATH, bash)
                .putExtra(
                    TERMUX_RUN_COMMAND_ARGUMENTS,
                    arrayOf("-c", "cd ${shellSingleQuote(path)} && exec bash -i")
                )
                .putExtra(TERMUX_RUN_COMMAND_WORKDIR, path)
                .putExtra(TERMUX_RUN_COMMAND_BACKGROUND, false)
            try {
                ContextCompat.startForegroundService(context, intent)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        context.startActivitySafe(launchIntent)
        return true
    }

    private fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private const val TERMUX_PACKAGE_NAME = "com.termux"
    private const val TERMUX_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val TERMUX_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val TERMUX_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val TERMUX_RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val TERMUX_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
}
