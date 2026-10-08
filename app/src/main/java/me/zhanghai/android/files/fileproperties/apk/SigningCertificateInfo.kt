/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.apk

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class SigningCertificateInfo(
    val subject: String,
    val signatureAlgorithm: String,
    val md5Digest: String,
    val sha1Digest: String,
    val sha256Digest: String
) {
    companion object {
        fun of(certificateBytes: ByteArray): SigningCertificateInfo {
            var subject = ""
            var signatureAlgorithm = ""
            try {
                val certificate = CertificateFactory.getInstance("X509")
                    .generateCertificate(ByteArrayInputStream(certificateBytes))
                    as? X509Certificate
                if (certificate != null) {
                    subject = certificate.subjectDN.name
                    signatureAlgorithm = certificate.sigAlgName
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return SigningCertificateInfo(
                subject, signatureAlgorithm,
                MessageDigest.getInstance("MD5").digest(certificateBytes).toFingerprintString(),
                MessageDigest.getInstance("SHA-1").digest(certificateBytes)
                    .toFingerprintString(),
                MessageDigest.getInstance("SHA-256").digest(certificateBytes)
                    .toFingerprintString()
            )
        }

        private fun ByteArray.toFingerprintString(): String =
            joinToString(":") { byte ->
                (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
            }
    }
}
