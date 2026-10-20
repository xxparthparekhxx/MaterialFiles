/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.content.Context
import androidx.core.content.edit
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.util.toHexString
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import org.apache.ftpserver.ssl.SslConfiguration
import org.apache.ftpserver.ssl.SslConfigurationFactory
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * The encryption of the built-in FTP server.
 */
enum class FtpServerTls {
    /** The connection is not encrypted. */
    NONE,
    /** The client encrypts the connection with the AUTH command. */
    EXPLICIT,
    /** The connection is encrypted from the start. */
    IMPLICIT;

    val isEnabled: Boolean
        get() = this != NONE
}

/**
 * The self-signed certificate of the built-in FTP server, which is generated on first use and
 * then kept in the app's private files directory.
 */
object FtpServerTlsConfiguration {
    private const val KEYSTORE_TYPE = "PKCS12"
    private const val KEY_ALIAS = "ftp-server"
    private const val KEYSTORE_FILE_NAME = "ftp-server-keystore.p12"
    private const val PREFERENCES_NAME = "ftp_server_tls"
    private const val PREFERENCES_KEY_KEYSTORE_PASSWORD = "keystore_password"
    private const val CERTIFICATE_VALIDITY_DAYS = 365L * 10

    fun createSslConfiguration(): SslConfiguration {
        val keystoreFile = File(application.filesDir, KEYSTORE_FILE_NAME)
        val keystorePassword = getOrCreateKeystorePassword()
        if (!keystoreFile.exists()) {
            createKeystore(keystoreFile, keystorePassword)
        }
        return SslConfigurationFactory().apply {
            this.keystoreFile = keystoreFile
            this.keystorePassword = keystorePassword
            this.keystoreType = KEYSTORE_TYPE
        }.createSslConfiguration()
    }

    private fun getOrCreateKeystorePassword(): String {
        val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val password = preferences.getString(PREFERENCES_KEY_KEYSTORE_PASSWORD, null)
        if (password != null) {
            return password
        }
        val newPassword = ByteArray(32).also { SecureRandom().nextBytes(it) }.toHexString()
        preferences.edit { putString(PREFERENCES_KEY_KEYSTORE_PASSWORD, newPassword) }
        return newPassword
    }

    private fun createKeystore(keystoreFile: File, keystorePassword: String) {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            KEY_ALIAS, keyPair.private, keystorePassword.toCharArray(),
            arrayOf(createSelfSignedCertificate(keyPair))
        )
        FileOutputStream(keystoreFile).use {
            keyStore.store(it, keystorePassword.toCharArray())
        }
    }

    private fun createSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=Material Files FTP Server")
        val builder = JcaX509v3CertificateBuilder(
            subject, BigInteger.valueOf(now), Date(now - TimeUnit.DAYS.toMillis(1)),
            Date(now + TimeUnit.DAYS.toMillis(CERTIFICATE_VALIDITY_DAYS)), subject, keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }
}
