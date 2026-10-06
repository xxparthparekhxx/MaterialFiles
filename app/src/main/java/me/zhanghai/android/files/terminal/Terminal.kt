/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        // Termux doesn't expose TermHere, so open it directly when installed instead of
        // failing with "No application found to handle this action".
        val termuxIntent = try {
            packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE_NAME)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        if (termuxIntent != null) {
            context.startActivitySafe(termuxIntent)
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

    private const val TERMUX_PACKAGE_NAME = "com.termux"
}
