/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp.client

import java8.nio.channels.SeekableByteChannel
import me.zhanghai.android.files.provider.common.LocalWatchService
import me.zhanghai.android.files.provider.common.NotifyEntryModifiedSeekableByteChannel
import me.zhanghai.android.files.util.closeSafe
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException
import java.io.IOException
import java.util.Collections
import java.util.WeakHashMap
import java8.nio.file.Path as Java8Path

object Client {
    @Volatile
    lateinit var authenticator: Authenticator

    private val clients = mutableMapOf<Authority, SFTPClient>()

    private val directoryFileAttributesCache =
        Collections.synchronizedMap(WeakHashMap<Path, FileAttributes>())

    @Throws(ClientException::class)
    fun access(path: Path, flags: Set<OpenMode>) {
        withClient(path.authority) { client ->
            val file = try {
                client.open(path.remotePath, flags, FileAttributes.EMPTY)
            } catch (e: IOException) {
                throw ClientException(e)
            }
            try {
                file.close()
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
    }

    @Throws(ClientException::class)
    fun lstat(path: Path): FileAttributes {
        synchronized(directoryFileAttributesCache) {
            directoryFileAttributesCache[path]?.let {
                return it.also { directoryFileAttributesCache -= path }
            }
        }
        return withClient(path.authority) { sftpClient ->
            try {
                sftpClient.lstat(path.remotePath)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
    }

    @Throws(ClientException::class)
    fun mkdir(path: Path, attributes: FileAttributes) {
        withClient(path.authority) { client ->
            try {
                client.sftpEngine.makeDir(path.remotePath, attributes)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
        LocalWatchService.onEntryCreated(path as Java8Path)
    }

    @Throws(ClientException::class)
    fun openByteChannel(
        path: Path,
        flags: Set<OpenMode>,
        attributes: FileAttributes
    ): SeekableByteChannel {
        val client = getClient(path.authority)
        val file = synchronized(client) {
            try {
                client.open(path.remotePath, flags, attributes)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
        return NotifyEntryModifiedSeekableByteChannel(
            FileByteChannel(file, flags.contains(OpenMode.APPEND), client) {
                dropClient(path.authority, client)
            },
            path as Java8Path
        )
    }

    @Throws(ClientException::class)
    fun readlink(path: Path): String =
        withClient(path.authority) { client ->
            try {
                client.readlink(path.remotePath)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }

    @Throws(ClientException::class)
    fun realpath(path: Path): Path {
        val realPath = withClient(path.authority) { client ->
            try {
                client.canonicalize(path.remotePath)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
        return path.resolve(realPath)
    }

    @Throws(ClientException::class)
    fun remove(path: Path) {
        val attributes = lstat(path)
        val isDirectory = attributes.type == FileMode.Type.DIRECTORY
        if (isDirectory) {
            rmdir(path)
        } else {
            unlink(path)
        }
    }

    // Note that unlike POSIX rename(), this won't overwrite an existing file.
    @Throws(ClientException::class)
    fun rename(path: Path, newPath: Path) {
        if (newPath.authority != path.authority) {
            throw ClientException(
                SFTPException(Response.StatusCode.FAILURE, "Paths aren't on the same authority")
            )
        }
        withClient(path.authority) { client ->
            try {
                client.rename(path.remotePath, newPath.remotePath)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
        directoryFileAttributesCache -= path
        directoryFileAttributesCache -= newPath
        LocalWatchService.onEntryDeleted(path as Java8Path)
        LocalWatchService.onEntryCreated(newPath as Java8Path)
    }

    @Throws(ClientException::class)
    fun rmdir(path: Path) {
        withClient(path.authority) { client ->
            try {
                client.rmdir(path.remotePath)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
        directoryFileAttributesCache -= path
        LocalWatchService.onEntryDeleted(path as Java8Path)
    }

    @Throws(ClientException::class)
    fun scandir(path: Path): List<Path> {
        val files = withClient(path.authority) { client ->
            try {
                client.ls(path.remotePath)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
        return files.map { file ->
            // The attributes here are from lstat().
            // https://github.com/openssh/openssh-portable/blob/71241fc05db4bbb11bb29340b44b92e2575373d8/sftp-server.c#L1110
            path.resolve(file.name).also { directoryFileAttributesCache[it] = file.attributes }
        }
    }

    @Throws(ClientException::class)
    fun setstat(path: Path, attributes: FileAttributes) {
        withClient(path.authority) { client ->
            try {
                client.setattr(path.remotePath, attributes)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
        directoryFileAttributesCache -= path
        LocalWatchService.onEntryModified(path as Java8Path)
    }

    @Throws(ClientException::class)
    fun stat(path: Path): FileAttributes {
        synchronized(directoryFileAttributesCache) {
            directoryFileAttributesCache[path]?.let {
                if (it.type != FileMode.Type.SYMLINK) {
                    return it.also { directoryFileAttributesCache -= path }
                }
            }
        }
        return withClient(path.authority) { sftpClient ->
            try {
                sftpClient.stat(path.remotePath)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
    }

    @Throws(ClientException::class)
    fun symlink(link: Path, target: String) {
        withClient(link.authority) { client ->
            try {
                client.symlink(link.remotePath, target)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
        LocalWatchService.onEntryCreated(link as Java8Path)
    }

    @Throws(ClientException::class)
    fun unlink(path: Path) {
        withClient(path.authority) { client ->
            try {
                client.rm(path.remotePath)
            } catch (e: IOException) {
                throw ClientException(e)
            }
        }
        directoryFileAttributesCache -= path
        LocalWatchService.onEntryDeleted(path as Java8Path)
    }

    @Throws(ClientException::class)
    private fun getClient(authority: Authority): SFTPClient {
        synchronized(clients) {
            var client = clients[authority]
            if (client != null) {
                if (client.sftpEngine.subsystem.isOpen) {
                    return client
                } else {
                    client.closeSafe()
                    clients -= authority
                }
            }
            val authentication = authenticator.getAuthentication(authority)
                ?: throw ClientException("No authentication found for $authority")
            if (authentication is PasswordAuthentication && authentication.password.isEmpty()) {
                // The server was saved without a password; ask for it instead of failing.
                throw SshPasswordRequiredException(authority)
            }
            SecurityProviderHelper.init()
            val hostKeyVerifier = SftpKnownHosts.Verifier()
            val sshClient = SSHClient().apply { addHostKeyVerifier(hostKeyVerifier) }
            try {
                sshClient.connect(authority.host, authority.port)
            } catch (e: IOException) {
                sshClient.closeSafe()
                val changedFingerprint = hostKeyVerifier.changedFingerprint
                if (changedFingerprint != null) {
                    throw ClientException(
                        "The host key of ${authority.host}:${authority.port} has changed" +
                            " (now $changedFingerprint). If you expected this, edit and save" +
                            " the server again to trust the new key.",
                        e
                    )
                }
                throw ClientException(e)
            }
            try {
                sshClient.auth(authority.username, authentication.toAuthMethod())
            } catch (e: UserAuthException) {
                sshClient.closeSafe()
                throw ClientException(e)
            } catch (e: TransportException) {
                sshClient.closeSafe()
                throw ClientException(e)
            }
            client = sshClient.newSFTPClient()
            clients[authority] = client
            return client
        }
    }

    private inline fun <T> withClient(authority: Authority, block: (SFTPClient) -> T): T {
        val client = getClient(authority)
        return synchronized(client) { block(client) }
    }

    private fun dropClient(authority: Authority, client: SFTPClient) {
        synchronized(clients) {
            if (clients[authority] === client) {
                clients -= authority
            }
        }
        client.closeSafe()
    }

    interface Path {
        val authority: Authority
        val remotePath: String
        fun resolve(other: String): Path
    }
}
