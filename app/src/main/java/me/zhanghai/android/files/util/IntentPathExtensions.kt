/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.app.storageManager
import me.zhanghai.android.files.compat.DocumentsContractCompat
import me.zhanghai.android.files.compat.directoryCompat
import me.zhanghai.android.files.compat.isPrimaryCompat
import me.zhanghai.android.files.compat.storageVolumesCompat
import me.zhanghai.android.files.compat.uuidCompat
import me.zhanghai.android.files.storage.createOrLog
import java.io.Serializable
import java.net.URI

const val ACTION_PICK_DIRECTORY_OI = "org.openintents.action.PICK_DIRECTORY"
const val ACTION_PICK_DIRECTORY_ESTRONGS = "com.estrongs.action.PICK_DIRECTORY"
const val EXTRA_TITLE_OI = "org.openintents.extra.TITLE"
const val EXTRA_WRITEABLE_OI = "org.openintents.extra.WRITE_ABLE"
const val EXTRA_DIR_PATH_OI = "org.openintents.extra.DIR_PATH"
const val EXTRA_ABSOLUTE_PATH_OI = "org.openintents.extra.ABSOLUTE_PATH"

private const val EXTRA_PATH_URI = "${BuildConfig.APPLICATION_ID}.extra.PATH_URI"

var Intent.extraPath: Path?
    get() {
        val extraPathUri = getStringExtra(EXTRA_PATH_URI)
        extraPathUri?.let { URI::class.createOrLog(it) }?.let { return Paths.get(it) }
        data?.toPathOrNull()?.let { return it }
        val extraInitialUri = getParcelableExtraSafe<Uri>(DocumentsContractCompat.EXTRA_INITIAL_URI)
        extraInitialUri?.toPathOrNull()?.let { return it }
        val extraAbsolutePath = getStringExtra(EXTRA_ABSOLUTE_PATH_OI)
            ?.takeIfNotEmpty()
        extraAbsolutePath?.let { return Paths.get(it) }
        val extraDirPath = getStringExtra(EXTRA_DIR_PATH_OI)
            ?.takeIfNotEmpty()
        extraDirPath?.let { return Paths.get(it) }
        return null
    }
    set(value) {
        // We cannot put Path into intent here, otherwise we will crash other apps unmarshalling it.
        // We cannot put URI into intent here either, because ShortcutInfo uses PersistableBundle
        // which doesn't support Serializable.
        putExtra(EXTRA_PATH_URI, value?.toUri()?.toString())
    }

internal val DIRECTORY_MIME_TYPES = setOf(
    "inode/directory",
    "inode/mount-point",
    "resource/folder",
    "vnd.android.cursor.dir/*",
    "vnd.android.document/directory",
    "vnd.android.document/root"
)

fun Intent.isDirectoryPick(): Boolean {
    val mimeType = type
    return mimeType in DIRECTORY_MIME_TYPES ||
        (mimeType != null && mimeType.startsWith("vnd.android.cursor.dir/"))
}

fun Intent.isDirectoryView(): Boolean =
    externalStorageRootPath() != null || isDirectoryPick()

/**
 * USB storage notifications open `content://…/root/<uuid>`. That is the volume, not a file to save.
 */
fun Intent.externalStorageRootPath(): Path? {
    val uri = data ?: return null
    if (uri.authority != DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY) {
        return null
    }
    val segments = uri.pathSegments
    if (segments.size != 2 || segments[0] != "root") {
        return null
    }
    val rootId = segments[1]
    val volumes = storageManager.storageVolumesCompat
    val volume = if (rootId == DocumentsContractCompat.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID) {
        volumes.firstOrNull { it.isPrimaryCompat }
    } else {
        volumes.firstOrNull { it.uuidCompat?.equals(rootId, ignoreCase = true) == true }
    } ?: return null
    val directory = volume.directoryCompat ?: return null
    return Paths.get(directory.path)
}

val Intent.saveAsPath: Path?
    get() {
        val uri =
            when (action) {
                Intent.ACTION_VIEW -> data
                Intent.ACTION_SEND -> getParcelableExtraSafe(Intent.EXTRA_STREAM) as? Uri
                else -> null
            }
        return uri?.toPathOrNull()
    }

val Intent.saveAsPaths: List<Path>
    get() =
        when (action) {
            Intent.ACTION_SEND_MULTIPLE ->
                getParcelableArrayListExtraSafe<Uri>(Intent.EXTRA_STREAM).orEmpty()
                    .mapNotNull { it.toPathOrNull() }
            else -> listOfNotNull(saveAsPath)
        }

private fun Uri.toPathOrNull(): Path? =
    when (scheme) {
        ContentResolver.SCHEME_FILE, null -> path?.takeIfNotEmpty()?.let { Paths.get(it) }
        "materialfiles" -> {
            if (host != null && host != "view") {
                null
            } else {
                path?.takeIfNotEmpty()?.let { Paths.get(it) }
            }
        }
        ContentResolver.SCHEME_CONTENT -> {
            val uri = URI::class.createOrLog(toString())
                // Some people use Uri.parse() without encoding their path. Let's try saving
                // them by calling the other URI constructor that encodes everything.
                ?: URI::class.createOrLog(scheme, userInfo, host, port, path, query, fragment)
            uri?.let { Paths.get(it) }
        }
        else -> null
    }

private const val EXTRA_PATH_URI_LIST = "${BuildConfig.APPLICATION_ID}.extra.PATH_URI_LIST"

var Intent.extraPathList: List<Path>
    get() {
        @Suppress("UNCHECKED_CAST")
        val extraPathUris = (getSerializableExtra(EXTRA_PATH_URI_LIST) as List<URI>?)
            ?.takeIfNotEmpty()
        extraPathUris?.let { return it.map { uri -> Paths.get(uri) } }
        return listOfNotNull(extraPath)
    }
    set(value) {
        // We cannot put Path into intent here, otherwise we will crash other apps unmarshalling it.
        val pathUris = value.map { it.toUri() }
        putExtra(EXTRA_PATH_URI_LIST, pathUris as Serializable)
    }
