/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp.client

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.SocketFactory

// The no-arg socket stays unconnected so sshj's later Socket.connect() performs the SOCKS handshake.
class SocksSocketFactory(private val proxy: SocksProxy) : SocketFactory() {
    override fun createSocket(): Socket = newSocket()

    override fun createSocket(host: String, port: Int): Socket =
        newSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        newSocket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress(host, port))
        }

    override fun createSocket(host: InetAddress, port: Int): Socket =
        newSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket =
        newSocket().apply {
            bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port))
        }

    private fun newSocket(): Socket =
        Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxy.host, proxy.port)))
}
