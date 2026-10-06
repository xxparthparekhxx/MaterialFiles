/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.compat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import me.zhanghai.android.files.util.lazyReflectedMethod
import java.io.File

// Work around @SuppressLint not applicable to top level property with delegate.
@SuppressLint("NewApi")
private val storageVolumeClass = StorageVolume::class.java

private val getPathMethod by lazyReflectedMethod(storageVolumeClass, "getPath")

val StorageVolume.pathCompat: String
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            directory?.path ?: (getPathMethod.invoke(this) as String)
        } else {
            getPathMethod.invoke(this) as String
        }

private val getPathFileMethod by lazyReflectedMethod(storageVolumeClass, "getPathFile")

val StorageVolume.pathFileCompat: File
    get() = File(pathCompat)

val StorageVolume.directoryCompat: File?
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            directory ?: pathFileCompat.takeIf {
                when (stateCompat) {
                    Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY -> true
                    else -> false
                }
            }
        } else {
            when (stateCompat) {
                Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY -> pathFileCompat
                else -> null
            }
        }

@SuppressLint("NewApi")
fun StorageVolume.getDescriptionCompat(context: Context): String = getDescription(context)

val StorageVolume.isPrimaryCompat: Boolean
    @SuppressLint("NewApi")
    get() = isPrimary

val StorageVolume.isRemovableCompat: Boolean
    @SuppressLint("NewApi")
    get() = isRemovable

val StorageVolume.isEmulatedCompat: Boolean
    @SuppressLint("NewApi")
    get() = isEmulated

val StorageVolume.uuidCompat: String?
    @SuppressLint("NewApi")
    get() = uuid

val StorageVolume.stateCompat: String
    @SuppressLint("NewApi")
    get() = state

fun StorageVolume.createOpenDocumentTreeIntentCompat(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        createOpenDocumentTreeIntent()
    } else {
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            val rootId = if (isEmulatedCompat) {
                DocumentsContractCompat.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID
            } else {
                uuidCompat
            }
            if (!rootId.isNullOrEmpty()) {
                val rootUri = DocumentsContract.buildRootUri(
                    DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY, rootId
                )
                putExtra(DocumentsContractCompat.EXTRA_INITIAL_URI, rootUri)
            }
            putExtra(DocumentsContractCompat.EXTRA_SHOW_ADVANCED, true)
        }
    }
