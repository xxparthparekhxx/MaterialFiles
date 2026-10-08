/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.apk

import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import java8.nio.file.Path
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.fileproperties.PathObserverLiveData
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.getPackageArchiveInfoCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.util.valueCompat
import java.io.IOException

class ApkInfoLiveData(path: Path) : PathObserverLiveData<Stateful<ApkInfo>>(path) {
    init {
        loadValue()
        observe()
    }

    override fun loadValue() {
        value = Loading(value?.value)
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val value = try {
                // We must always pass in PackageManager.GET_SIGNATURES for
                // PackageManager.getPackageArchiveInfo() to call
                // PackageParser.collectCertificates().
                @Suppress("DEPRECATION")
                var packageInfoFlags = (PackageManager.GET_PERMISSIONS
                    or PackageManager.GET_SIGNATURES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfoFlags = packageInfoFlags or PackageManager.GET_SIGNING_CERTIFICATES
                }
                val (packageInfo, closeable) =
                    packageManager.getPackageArchiveInfoCompat(path, packageInfoFlags)
                val apkInfo = closeable.use {
                    val applicationInfo = packageInfo?.applicationInfo
                        ?: throw IOException(
                            application.getString(R.string.file_properties_apk_error_unreadable)
                        )
                    val label = applicationInfo.loadLabel(packageManager).toString()
                    val signingCertificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // PackageInfo.signatures returns only the oldest certificate if there are
                        // past certificates on P and above for compatibility.
                        packageInfo.signingInfo?.apkContentsSigners
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.signatures
                    } ?: emptyArray()
                    val signingCertificateInfos = signingCertificates
                        .map { SigningCertificateInfo.of(it.toByteArray()) }
                    val pastSigningCertificates =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val signingInfo = packageInfo.signingInfo
                            // SigningInfo.getSigningCertificateHistory() may return the current
                            // certificate if there are no past certificates.
                            if (signingInfo?.hasPastSigningCertificates() == true) {
                                // SigningInfo.getSigningCertificateHistory() also returns the
                                // current certificate.
                                signingInfo.signingCertificateHistory?.toMutableList()
                                    ?.apply { removeAll(signingCertificates) }
                            } else {
                                null
                            }
                        } else {
                            null
                        } ?: emptyList()
                    val pastSigningCertificateInfos = pastSigningCertificates
                        .map { SigningCertificateInfo.of(it.toByteArray()) }
                    ApkInfo(
                        packageInfo, label, signingCertificateInfos, pastSigningCertificateInfos
                    )
                }
                Success(apkInfo)
            } catch (e: Exception) {
                Failure(valueCompat.value, e)
            }
            postValue(value)
        }
    }
}
