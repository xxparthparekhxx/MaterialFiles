/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.ftp.client

import java.lang.reflect.Field
import java.net.Socket
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import org.apache.commons.net.ftp.FTPSClient

/**
 * vsftpd can require the data connection to resume the control connection's TLS session.
 * Conscrypt caches that session under the control host and port, so a data port never finds it
 * and the server answers the handshake with plaintext.
 */
class SessionReusingFtpsClient(isImplicit: Boolean) : FTPSClient(isImplicit) {
    override fun _prepareDataSocket_(socket: Socket) {
        if (socket !is SSLSocket) {
            return
        }
        val control = _socket_ as? SSLSocket ?: return
        val session = control.session
        if (!session.isValid) {
            return
        }
        try {
            copySession(session, socket)
        } catch (e: Exception) {
            if (!loggedReuseFailure) {
                loggedReuseFailure = true
                e.printStackTrace()
            }
        }
    }

    private fun copySession(session: SSLSession, dataSocket: SSLSocket) {
        val context = session.sessionContext ?: return
        val mapField = findField(context.javaClass, "sessionsByHostAndPort") ?: return
        mapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val sessions = mapField.get(context) as? MutableMap<Any, Any> ?: return
        val keyClass = Class.forName("${context.javaClass.name}\$HostAndPort")
        val constructor = keyClass.getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        synchronized(sessions) {
            val source = sessions.entries.firstOrNull { entry ->
                matches(entry.key, session.peerHost, session.peerPort)
            } ?: return
            val hosts = linkedSetOf<String>()
            dataSocket.inetAddress?.hostAddress?.let { hosts += it }
            dataSocket.inetAddress?.hostName?.let { hosts += it }
            passiveHost?.let { hosts += it }
            for (host in hosts) {
                val key = constructor.newInstance(host, dataSocket.port)
                if (key !in sessions) {
                    sessions[key] = source.value
                }
            }
        }
    }

    private fun matches(key: Any, host: String?, port: Int): Boolean {
        if (host == null) {
            return false
        }
        val keyClass = key.javaClass
        val keyHost = findField(keyClass, "host")?.apply { isAccessible = true }?.get(key) as? String
        val keyPort = findField(keyClass, "port")?.apply { isAccessible = true }?.get(key) as? Int
        return keyHost == host && keyPort == port
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    companion object {
        @Volatile
        private var loggedReuseFailure = false
    }
}
