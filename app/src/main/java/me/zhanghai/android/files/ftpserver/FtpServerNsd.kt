/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import me.zhanghai.android.files.app.application

object FtpServerNsd {
    private const val SERVICE_TYPE = "_ftp._tcp."

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun register(port: Int) {
        unregister()
        val nsdManager = application.getSystemService(NsdManager::class.java) ?: return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Material Files"
            serviceType = SERVICE_TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        acquireMulticastLock()
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            e.printStackTrace()
            registrationListener = null
            releaseMulticastLock()
        }
    }

    fun unregister() {
        val listener = registrationListener
        registrationListener = null
        if (listener != null) {
            val nsdManager = application.getSystemService(NsdManager::class.java)
            try {
                nsdManager?.unregisterService(listener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        if (multicastLock != null) {
            return
        }
        val wifiManager = application.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return
        multicastLock = wifiManager.createMulticastLock(FtpServerNsd::class.java.simpleName).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock
        multicastLock = null
        if (lock != null && lock.isHeld) {
            try {
                lock.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
