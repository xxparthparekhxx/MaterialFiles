/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import me.zhanghai.android.files.provider.sftp.client.Authentication
import me.zhanghai.android.files.provider.sftp.client.Authenticator
import me.zhanghai.android.files.provider.sftp.client.Authority
import me.zhanghai.android.files.provider.sftp.client.PasswordAuthentication
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

object SftpServerAuthenticator : Authenticator {
    private val transientServers = mutableSetOf<SftpServer>()
    private val transientPasswords = mutableMapOf<Authority, PasswordAuthentication>()

    override fun getAuthentication(authority: Authority): Authentication? {
        synchronized(transientPasswords) {
            transientPasswords[authority]?.let { return it }
        }
        val server = synchronized(transientServers) {
            transientServers.find { it.authority == authority }
        } ?: Settings.STORAGES.valueCompat.find {
            it is SftpServer && it.authority == authority
        } as SftpServer?
        return server?.authentication
    }

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
}
