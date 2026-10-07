/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import me.zhanghai.android.files.provider.sftp.client.Authentication
import me.zhanghai.android.files.provider.sftp.client.Authenticator
import me.zhanghai.android.files.provider.sftp.client.Authority
import me.zhanghai.android.files.provider.sftp.client.PasswordAuthentication
import me.zhanghai.android.files.provider.sftp.client.PublicKeyAuthentication
import me.zhanghai.android.files.provider.sftp.client.SocksProxy
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

object SftpServerAuthenticator : Authenticator {
    private val transientServers = mutableSetOf<SftpServer>()
    private val transientPasswords = mutableMapOf<Authority, PasswordAuthentication>()

    // A missing key falls through to the saved proxy. An explicit null means the proxy is off.
    private val pendingSocksProxies = mutableMapOf<Authority, SocksProxy?>()

    override fun getAuthentication(authority: Authority): Authentication? {
        val server = findServer(authority)
        val transientPassword = synchronized(transientPasswords) {
            transientPasswords[authority]
        }
        if (server != null && transientPassword != null) {
            return when (val auth = server.authentication) {
                is PasswordAuthentication -> transientPassword
                is PublicKeyAuthentication -> PublicKeyAuthentication(auth.privateKey, transientPassword.password)
            }
        }
        if (transientPassword != null) {
            return transientPassword
        }
        return server?.authentication
    }

    override fun getSocksProxy(authority: Authority): SocksProxy? {
        synchronized(pendingSocksProxies) {
            if (authority in pendingSocksProxies) {
                return pendingSocksProxies[authority]
            }
        }
        val server = findServer(authority) ?: return null
        val stored = Settings.SFTP_SOCKS_PROXIES.valueCompat.find { it.serverId == server.id }
            ?: return null
        return SocksProxy(stored.host, stored.port)
    }

    private fun findServer(authority: Authority): SftpServer? =
        synchronized(transientServers) {
            transientServers.find { it.authority == authority }
        } ?: Settings.STORAGES.valueCompat.find {
            it is SftpServer && it.authority == authority
        } as SftpServer?

    fun addTransientServer(server: SftpServer) {
        synchronized(transientServers) { transientServers += server }
    }

    fun removeTransientServer(server: SftpServer) {
        synchronized(transientServers) { transientServers -= server }
    }

    fun putTransientPassword(authority: Authority, password: String) {
        synchronized(transientPasswords) {
            transientPasswords[authority] = PasswordAuthentication(password)
        }
    }

    fun removeTransientPassword(authority: Authority) {
        synchronized(transientPasswords) { transientPasswords -= authority }
    }

    fun setPendingSocksProxy(authority: Authority, proxy: SocksProxy?) {
        synchronized(pendingSocksProxies) { pendingSocksProxies[authority] = proxy }
    }

    fun hasPendingSocksProxy(authority: Authority): Boolean =
        synchronized(pendingSocksProxies) { authority in pendingSocksProxies }

    fun takePendingSocksProxy(authority: Authority): SocksProxy? {
        synchronized(pendingSocksProxies) {
            if (authority !in pendingSocksProxies) {
                return null
            }
            return pendingSocksProxies.remove(authority)
        }
    }

    fun clearPendingSocksProxy(authority: Authority) {
        synchronized(pendingSocksProxies) { pendingSocksProxies -= authority }
    }

    fun saveSocksProxy(serverId: Long, proxy: SocksProxy?) {
        val withoutServer = Settings.SFTP_SOCKS_PROXIES.valueCompat.filter { it.serverId != serverId }
        Settings.SFTP_SOCKS_PROXIES.putValue(
            if (proxy == null) {
                withoutServer
            } else {
                withoutServer + SftpSocksProxy(serverId, proxy.host, proxy.port)
            }
        )
    }

    fun removeSocksProxy(serverId: Long) {
        saveSocksProxy(serverId, null)
    }
}
