/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.RuntimeBroadcastReceiver
import me.zhanghai.android.files.util.getLocalAddress
import me.zhanghai.android.files.util.valueCompat
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

object FtpServerUrl {
    class Entry(val interfaceName: String, val url: String, val isHotspot: Boolean = false)

    fun getUrl(): String? {
        // Prefer a hotspot address so sharing over the phone's hotspot just works even when
        // the Wi-Fi client reports a public network address.
        val localAddress =
            getHotspotAddress() ?: InetAddress::class.getLocalAddress() ?: return null
        return createUrl(localAddress)
    }

    private fun getHotspotAddress(): InetAddress? {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .forEach { networkInterface ->
                    networkInterface.inetAddresses.toList()
                        .filter {
                            it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress
                        }
                        .forEach {
                            if (isHotspotInterface(networkInterface.name, it)) {
                                return it
                            }
                        }
                }
        } catch (e: SocketException) {
            e.printStackTrace()
        }
        return null
    }

    // There is no public API for detecting the hotspot interface, so match common interface
    // names and gateway-like addresses. Only ordering depends on this, so a miss just keeps
    // the previous behavior.
    private fun isHotspotInterface(interfaceName: String, address: InetAddress): Boolean {
        val name = interfaceName.lowercase()
        return name.startsWith("ap") || "hotspot" in name || name.startsWith("softap") ||
            name.startsWith("swlan") || name == "wlan1" ||
            address.hostAddress?.endsWith(".1") == true
    }

    private fun createUrl(address: InetAddress): String {
        val username = if (!Settings.FTP_SERVER_ANONYMOUS_LOGIN.valueCompat) {
            Settings.FTP_SERVER_USERNAME.valueCompat
        } else {
            null
        }
        val host = address.hostAddress
        val port = Settings.FTP_SERVER_PORT.valueCompat
        return "ftp://${if (username != null) "$username@" else ""}$host:$port/"
    }

    // The URL for every network interface that has an IPv4 address, such as Wi-Fi and Wi-Fi
    // Direct, with hotspot addresses and then the default URL first.
    fun getEntries(): List<Entry> {
        val entries = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses.toList()
                        .filter {
                            it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress
                        }
                        .map {
                            Entry(
                                networkInterface.name, createUrl(it),
                                isHotspotInterface(networkInterface.name, it)
                            )
                        }
                }
        } catch (e: SocketException) {
            e.printStackTrace()
            emptyList()
        }
        val defaultUrl = getUrl()
        return entries.sortedWith(
            compareByDescending<Entry> { it.isHotspot }.thenByDescending { it.url == defaultUrl }
        )
    }

    fun createChangeReceiver(context: Context, onChange: () -> Unit): RuntimeBroadcastReceiver =
        RuntimeBroadcastReceiver(
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION), object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    onChange()
                }
            }, context
        )
}
