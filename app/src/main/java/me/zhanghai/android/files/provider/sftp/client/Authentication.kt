/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp.client

import android.os.Parcelable
import com.hierynomus.sshj.common.KeyDecryptionFailedException
import kotlinx.parcelize.Parcelize
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.userauth.password.PasswordUtils
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import org.bouncycastle.pkcs.PKCSException
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder
import java.io.IOException
import java.io.StringReader

sealed class Authentication : Parcelable {
    abstract fun toAuthMethod(): AuthMethod
}

@Parcelize
data class PasswordAuthentication(
    val password: String
) : Authentication() {
    override fun toAuthMethod(): AuthMethod =
        AuthPassword(PasswordUtils.createOneOff(password.toCharArray()))
}

@Parcelize
data class PublicKeyAuthentication(
    val privateKey: String,
    val privateKeyPassword: String?
) : Authentication() {
    override fun toAuthMethod(): AuthMethod =
        AuthPublickey(createKeyProvider(privateKey, privateKeyPassword))

    companion object {
        private val KEY_PROVIDER_FACTORIES by lazy {
            SecurityProviderHelper.init()
            DefaultConfig().fileKeyProviderFactories
        }

        fun validate(privateKey: String, privateKeyPassword: String?): Exception? =
            try {
                createKeyProvider(privateKey, privateKeyPassword).private
                null
            } catch (e: Exception) {
                e
            }

        /**
         * @see net.schmizz.sshj.SSHClient.loadKeys
         */
        @Throws(IOException::class)
        private fun createKeyProvider(
            privateKey: String,
            privateKeyPassword: String?
        ): KeyProvider {
            SecurityProviderHelper.init()
            return try {
                createStandardKeyProvider(privateKey, privateKeyPassword)
            } catch (e: Exception) {
                try {
                    createBouncyCastleKeyProvider(privateKey, privateKeyPassword)
                } catch (bcException: Exception) {
                    when {
                        bcException is KeyDecryptionFailedException -> throw bcException
                        e is KeyDecryptionFailedException -> throw e
                        e is IOException -> throw e
                        else -> throw IOException("Failed to load key: ${e.message}", e)
                    }
                }
            }
        }

        @Throws(IOException::class)
        private fun createStandardKeyProvider(
            privateKey: String,
            privateKeyPassword: String?
        ): KeyProvider {
            val format = KeyProviderUtil.detectKeyFileFormat(privateKey, false)
            val keyProvider = Factory.Named.Util.create(KEY_PROVIDER_FACTORIES, format.toString())
                ?: throw IOException("No key provider factory found for $format")
            keyProvider.init(
                privateKey, null,
                privateKeyPassword?.let { PasswordUtils.createOneOff(it.toCharArray()) }
            )
            return keyProvider
        }

        @Throws(Exception::class)
        private fun createBouncyCastleKeyProvider(
            privateKey: String,
            privateKeyPassword: String?
        ): KeyProvider {
            val pemParser = PEMParser(StringReader(privateKey))
            val pemObject = pemParser.readObject() ?: throw IOException("Empty PEM object")
            val pki: PrivateKeyInfo = when (pemObject) {
                is PKCS8EncryptedPrivateKeyInfo -> {
                    if (privateKeyPassword == null) {
                        throw KeyDecryptionFailedException("Private key is encrypted but no password provided")
                    }
                    val decryptor = JcePKCSPBEInputDecryptorProviderBuilder()
                        .setProvider("BC")
                        .build(privateKeyPassword.toCharArray())
                    try {
                        pemObject.decryptPrivateKeyInfo(decryptor)
                    } catch (e: PKCSException) {
                        throw KeyDecryptionFailedException(e.message, e)
                    }
                }
                is PrivateKeyInfo -> pemObject
                else -> throw IOException("Unsupported key object: ${pemObject.javaClass.name}")
            }
            val converter = JcaPEMKeyConverter().setProvider("BC")
            val privKey = converter.getPrivateKey(pki)
            val privKeyParameters = PrivateKeyFactory.createKey(pki)
            val pubKey = if (privKeyParameters is Ed25519PrivateKeyParameters) {
                val pubKeyParameters = privKeyParameters.generatePublicKey()
                converter.getPublicKey(SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubKeyParameters))
            } else {
                throw IOException("Unsupported key parameter type: ${privKeyParameters.javaClass.name}")
            }
            return KeyPairWrapper(pubKey, privKey)
        }
    }
}
