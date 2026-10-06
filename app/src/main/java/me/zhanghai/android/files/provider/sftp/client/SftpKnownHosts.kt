package me.zhanghai.android.files.provider.sftp.client

import android.content.Context
import me.zhanghai.android.files.app.application
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey

/**
 * Trust-on-first-use storage of SFTP host key fingerprints: the first key seen for a host and port
 * is remembered, and later connections are refused if the server presents a different key.
 */
object SftpKnownHosts {
    private const val PREFERENCES_NAME = "sftp_known_hosts"

    private val preferences
        get() = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun key(host: String, port: Int) = "$host:$port"

    /** Forget the remembered host key, so that the next connection trusts whatever it sees. */
    fun forget(host: String, port: Int) {
        preferences.edit().remove(key(host, port)).apply()
    }

    fun fingerprint(publicKey: PublicKey): String {
        val encoded = Buffer.PlainBuffer().putPublicKey(publicKey).compactData
        return MessageDigest.getInstance("SHA-256").digest(encoded)
            .joinToString(":") { "%02x".format(it) }
    }

    class Verifier : HostKeyVerifier {
        var changedFingerprint: String? = null
            private set

        @Synchronized
        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val fingerprint = fingerprint(key)
            val preferences = preferences
            val known = preferences.getString(key(hostname, port), null)
            return when (known) {
                null -> {
                    preferences.edit().putString(key(hostname, port), fingerprint).apply()
                    true
                }
                fingerprint -> true
                else -> {
                    changedFingerprint = fingerprint
                    false
                }
            }
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
            emptyList()
    }
}
