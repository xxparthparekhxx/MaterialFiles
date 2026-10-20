/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import org.apache.ftpserver.ftplet.DefaultFtpReply
import org.apache.ftpserver.ftplet.FtpReply
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.Ftplet
import org.apache.ftpserver.ftplet.FtpletContext
import org.apache.ftpserver.ftplet.FtpletResult
import org.apache.ftpserver.impl.DefaultFtpSession

/**
 * Rejects a login that is not encrypted.
 *
 * The "Explicit" mode lets the client ask for the encryption with the `AUTH TLS` command, but
 * nothing stops it from logging in without it, which also lets an attacker in the middle strip
 * the encryption.
 */
class RequireTlsFtplet : Ftplet {
    override fun init(context: FtpletContext) {}

    override fun destroy() {}

    override fun beforeCommand(session: FtpSession, request: FtpRequest): FtpletResult {
        if (request.command == USER_COMMAND && !session.isSecure()) {
            session.write(
                DefaultFtpReply(534, "The server requires an encrypted connection.")
            )
            return FtpletResult.SKIP
        }
        return FtpletResult.DEFAULT
    }

    override fun afterCommand(
        session: FtpSession,
        request: FtpRequest,
        reply: FtpReply
    ): FtpletResult = FtpletResult.DEFAULT

    override fun onConnect(session: FtpSession): FtpletResult = FtpletResult.DEFAULT

    override fun onDisconnect(session: FtpSession): FtpletResult = FtpletResult.DEFAULT

    companion object {
        private const val USER_COMMAND = "USER"
    }
}

// The session is encrypted when the TLS filter is in its filter chain.
private fun FtpSession.isSecure(): Boolean = (this as? DefaultFtpSession)?.isSecure == true
