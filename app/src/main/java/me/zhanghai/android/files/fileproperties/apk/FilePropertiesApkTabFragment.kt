/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.apk

import android.os.Build
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.longVersionCodeCompat
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.fileproperties.FilePropertiesTabFragment
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.getQuantityString
import me.zhanghai.android.files.util.getStringArray
import me.zhanghai.android.files.util.isGetPackageArchiveInfoCompatible
import me.zhanghai.android.files.util.viewModels

class FilePropertiesApkTabFragment : FilePropertiesTabFragment() {
    private val args by args<Args>()

    private val viewModel by viewModels { { FilePropertiesApkTabViewModel(args.path) } }

    override fun onResume() {
        super.onResume()

        viewModel.apkInfoLiveData.observe(viewLifecycleOwner) { onApkInfoChanged(it) }
    }

    override fun refresh() {
        viewModel.reload()
    }

    private fun onApkInfoChanged(stateful: Stateful<ApkInfo>) {
        bindView(stateful) { apkInfo ->
            addItemView(R.string.file_properties_apk_label, apkInfo.label)
            val packageInfo = apkInfo.packageInfo
            addItemView(R.string.file_properties_apk_package_name, packageInfo.packageName)
            addItemView(
                R.string.file_properties_apk_version, getString(
                    R.string.file_properties_apk_version_format, packageInfo.versionName,
                    packageInfo.longVersionCodeCompat
                )
            )
            val applicationInfo = packageInfo.applicationInfo!!
            // PackageParser didn't return minSdkVersion before N, so it's hard to implement a
            // compat version.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addItemView(
                    R.string.file_properties_apk_min_sdk_version,
                    getSdkVersionText(applicationInfo.minSdkVersion)
                )
            }
            addItemView(
                R.string.file_properties_apk_target_sdk_version,
                getSdkVersionText(applicationInfo.targetSdkVersion)
            )
            val requestedPermissionsSize = packageInfo.requestedPermissions?.size ?: 0
            addItemView(
                R.string.file_properties_apk_requested_permissions,
                if (requestedPermissionsSize == 0) {
                    getString(R.string.file_properties_apk_requested_permissions_zero)
                } else {
                    getQuantityString(
                        R.plurals.file_properties_apk_requested_permissions_positive_format,
                        requestedPermissionsSize, requestedPermissionsSize
                    )
                }, if (requestedPermissionsSize == 0) {
                    null
                } else {
                    {
                        PermissionListDialogFragment.show(
                            packageInfo.requestedPermissions!!, this@FilePropertiesApkTabFragment
                        )
                    }
                }
            )
            addItemView(
                R.string.file_properties_apk_signature_digests,
                if (apkInfo.signingCertificates.isNotEmpty()) {
                    apkInfo.signingCertificates.joinToString("\n\n") { formatCertificate(it) }
                } else {
                    getString(R.string.file_properties_apk_signature_digests_empty)
                }
            )
            if (apkInfo.pastSigningCertificates.isNotEmpty()) {
                addItemView(
                    R.string.file_properties_apk_past_signature_digests,
                    apkInfo.pastSigningCertificates.joinToString("\n\n") { formatCertificate(it) }
                )
            }
        }
    }

    private fun getSdkVersionText(sdkVersion: Int): String {
        val names = getStringArray(R.array.file_properites_apk_sdk_version_names)
        val codeNames = getStringArray(R.array.file_properites_apk_sdk_version_codenames)
        return getString(
            R.string.file_properites_apk_sdk_version_format,
            names[sdkVersion.coerceIn(names.indices)],
            codeNames[sdkVersion.coerceIn(codeNames.indices)], sdkVersion
        )
    }

    private fun formatCertificate(info: SigningCertificateInfo): String {
        val lines = mutableListOf<String>()
        if (info.subject.isNotEmpty()) {
            lines += getString(R.string.file_properties_apk_signature_subject_format, info.subject)
        }
        if (info.signatureAlgorithm.isNotEmpty()) {
            lines += getString(
                R.string.file_properties_apk_signature_algorithm_format, info.signatureAlgorithm
            )
        }
        lines += getString(R.string.file_properties_apk_signature_md5_format, info.md5Digest)
        lines += getString(R.string.file_properties_apk_signature_sha1_format, info.sha1Digest)
        lines += getString(R.string.file_properties_apk_signature_sha256_format, info.sha256Digest)
        return lines.joinToString("\n")
    }

    companion object {
        fun isAvailable(file: FileItem): Boolean =
            file.mimeType.isApk && file.path.isGetPackageArchiveInfoCompatible
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}
