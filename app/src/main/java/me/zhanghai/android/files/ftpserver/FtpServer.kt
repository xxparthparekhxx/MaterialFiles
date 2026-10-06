/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import java8.nio.file.Path
import org.apache.ftpserver.ConnectionConfigFactory
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.impl.DefaultFtpServer
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.listener.nio.FtpServerProtocolCodecFactory
import org.apache.ftpserver.listener.nio.NioListener
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.IoSession
import org.apache.mina.filter.codec.ProtocolCodecFactory
import org.apache.mina.filter.codec.ProtocolCodecFilter
import org.apache.mina.filter.codec.ProtocolDecoder
import org.apache.mina.filter.codec.ProtocolEncoderAdapter
import org.apache.mina.filter.codec.ProtocolEncoderOutput
import org.apache.mina.transport.socket.SocketAcceptor
import java.nio.charset.StandardCharsets

class FtpServer(
    private val username: String,
    private val password: String?,
    private val port: Int,
    private val homeDirectory: Path,
    private val writable: Boolean
) {
    private lateinit var server: FtpServer

    @Throws(FtpException::class, RuntimeException::class)
    fun start() {
        server = FtpServerFactory()
            .apply {
                val listener = ListenerFactory()
                    .apply { port = this@FtpServer.port }
                    .createListener()
                addListener("default", listener)
                val user = BaseUser().apply {
                    name = username
                    password = this@FtpServer.password
                    authorities = if (writable) listOf(WritePermission()) else emptyList()
                    homeDirectory = this@FtpServer.homeDirectory.toUri().toString()
                }
                userManager.save(user)
                fileSystem = ProviderFileSystemFactory()
                connectionConfig = ConnectionConfigFactory()
                    .apply { isAnonymousLoginEnabled = true }
                    .createConnectionConfig()
            }
            .createServer()
        server.start()
        // The stock reply encoder sizes its buffer by character count. A UTF-8 character takes
        // more than one byte, so a short reply such as a name with one CJK character overflows
        // and kills the server thread.
        installExpandingReplyEncoder()
    }

    fun stop() {
        server.stop()
    }

    private fun installExpandingReplyEncoder() {
        try {
            val listener = (server as DefaultFtpServer).getListener("default") as NioListener
            val acceptorField = NioListener::class.java.getDeclaredField("acceptor")
            acceptorField.isAccessible = true
            val acceptor = acceptorField.get(listener) as SocketAcceptor
            acceptor.filterChain.replace("codec", ProtocolCodecFilter(ExpandingFtpCodecFactory()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private class ExpandingFtpCodecFactory : ProtocolCodecFactory {
    private val delegate = FtpServerProtocolCodecFactory()
    private val encoder = ExpandingFtpResponseEncoder()

    override fun getEncoder(session: IoSession) = encoder

    override fun getDecoder(session: IoSession): ProtocolDecoder = delegate.getDecoder(session)
}

private class ExpandingFtpResponseEncoder : ProtocolEncoderAdapter() {
    override fun encode(session: IoSession, message: Any, out: ProtocolEncoderOutput) {
        val value = message.toString()
        val charsetEncoder = StandardCharsets.UTF_8.newEncoder()
        val capacity = (value.length * charsetEncoder.maxBytesPerChar()).toInt() + 8
        val buffer = IoBuffer.allocate(capacity.coerceAtLeast(16)).setAutoExpand(true)
        buffer.putString(value, charsetEncoder)
        buffer.flip()
        out.write(buffer)
    }
}
