/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import me.zhanghai.android.files.provider.ftp.client.Authority as FtpAuthority
import me.zhanghai.android.files.provider.ftp.client.Mode as FtpMode
import me.zhanghai.android.files.provider.ftp.client.Protocol as FtpProtocol
import me.zhanghai.android.files.provider.sftp.client.Authentication as SftpAuthentication
import me.zhanghai.android.files.provider.sftp.client.Authority as SftpAuthority
import me.zhanghai.android.files.provider.sftp.client.PasswordAuthentication as SftpPasswordAuthentication
import me.zhanghai.android.files.provider.sftp.client.PublicKeyAuthentication as SftpPublicKeyAuthentication
import me.zhanghai.android.files.provider.smb.client.Authority as SmbAuthority
import me.zhanghai.android.files.provider.webdav.client.AccessTokenAuthentication as WebDavAccessTokenAuthentication
import me.zhanghai.android.files.provider.webdav.client.Authentication as WebDavAuthentication
import me.zhanghai.android.files.provider.webdav.client.Authority as WebDavAuthority
import me.zhanghai.android.files.provider.webdav.client.NoneAuthentication as WebDavNoneAuthentication
import me.zhanghai.android.files.provider.webdav.client.PasswordAuthentication as WebDavPasswordAuthentication
import me.zhanghai.android.files.provider.webdav.client.Protocol as WebDavProtocol
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

object StorageImportExport {
    private const val VERSION = 1

    fun exportStorages(storages: List<Storage>): String {
        val array = JSONArray()
        for (storage in storages) {
            exportStorage(storage)?.let { array.put(it) }
        }
        return JSONObject()
            .put("version", VERSION)
            .put("storages", array)
            .toString(2)
    }

    private fun exportStorage(storage: Storage): JSONObject? =
        when (storage) {
            is SftpServer -> JSONObject()
                .put("type", "sftp")
                .put("customName", storage.customName)
                .put("host", storage.authority.host)
                .put("port", storage.authority.port)
                .put("username", storage.authority.username)
                .put("authentication", exportSftpAuthentication(storage.authentication))
                .put("relativePath", storage.relativePath)
            is SmbServer -> JSONObject()
                .put("type", "smb")
                .put("customName", storage.customName)
                .put("host", storage.authority.host)
                .put("port", storage.authority.port)
                .put("username", storage.authority.username)
                .put("domain", storage.authority.domain)
                .put("password", storage.password)
                .put("relativePath", storage.relativePath)
            is FtpServer -> JSONObject()
                .put("type", "ftp")
                .put("customName", storage.customName)
                .put("protocol", storage.authority.protocol.name)
                .put("host", storage.authority.host)
                .put("port", storage.authority.port)
                .put("username", storage.authority.username)
                .put("mode", storage.authority.mode.name)
                .put("encoding", storage.authority.encoding)
                .put("password", storage.password)
                .put("relativePath", storage.relativePath)
            is WebDavServer -> JSONObject()
                .put("type", "webdav")
                .put("customName", storage.customName)
                .put("protocol", storage.authority.protocol.name)
                .put("host", storage.authority.host)
                .put("port", storage.authority.port)
                .put("username", storage.authority.username)
                .put("authentication", exportWebDavAuthentication(storage.authentication))
                .put("relativePath", storage.relativePath)
            else -> null
        }

    private fun exportSftpAuthentication(authentication: SftpAuthentication): JSONObject =
        when (authentication) {
            is SftpPasswordAuthentication -> JSONObject()
                .put("type", "password")
                .put("password", authentication.password)
            is SftpPublicKeyAuthentication -> JSONObject()
                .put("type", "publicKey")
                .put("privateKey", authentication.privateKey)
                .put("privateKeyPassword", authentication.privateKeyPassword)
        }

    private fun exportWebDavAuthentication(authentication: WebDavAuthentication): JSONObject =
        when (authentication) {
            is WebDavNoneAuthentication -> JSONObject().put("type", "none")
            is WebDavPasswordAuthentication -> JSONObject()
                .put("type", "password")
                .put("password", authentication.password)
            is WebDavAccessTokenAuthentication -> JSONObject()
                .put("type", "accessToken")
                .put("accessToken", authentication.accessToken)
        }

    data class ImportResult(val storages: List<Storage>, val skippedCount: Int)

    fun importStorages(json: String): ImportResult {
        val storages = mutableListOf<Storage>()
        var skippedCount = 0
        val root = JSONObject(json)
        val array = root.optJSONArray("storages") ?: return ImportResult(emptyList(), 0)
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item == null) {
                ++skippedCount
                continue
            }
            try {
                importStorage(item)?.let { storages += it } ?: ++skippedCount
            } catch (e: Exception) {
                e.printStackTrace()
                ++skippedCount
            }
        }
        return ImportResult(storages, skippedCount)
    }

    private fun importStorage(item: JSONObject): Storage? {
        // Assign fresh IDs so imports never overwrite existing storages.
        val id = Random.nextLong()
        val customName = item.optString("customName").takeIf { item.has("customName") }
        val relativePath = item.optString("relativePath", "")
        return when (item.optString("type")) {
            "sftp" -> {
                val authority = SftpAuthority(
                    item.getString("host"), item.getInt("port"), item.getString("username")
                )
                SftpServer(id, customName, authority, importSftpAuthentication(item), relativePath)
            }
            "smb" -> {
                val authority = SmbAuthority(
                    item.getString("host"), item.getInt("port"), item.getString("username"),
                    item.optString("domain").takeIf { item.has("domain") }
                )
                SmbServer(
                    id, customName, authority, item.optString("password", ""), relativePath
                )
            }
            "ftp" -> {
                val authority = FtpAuthority(
                    enumValueOf<FtpProtocol>(item.getString("protocol")),
                    item.getString("host"), item.getInt("port"), item.getString("username"),
                    enumValueOf<FtpMode>(item.getString("mode")), item.getString("encoding")
                )
                FtpServer(
                    id, customName, authority, item.optString("password", ""), relativePath
                )
            }
            "webdav" -> {
                val authority = WebDavAuthority(
                    enumValueOf<WebDavProtocol>(item.getString("protocol")),
                    item.getString("host"), item.getInt("port"), item.getString("username")
                )
                WebDavServer(
                    id, customName, authority, importWebDavAuthentication(item), relativePath
                )
            }
            else -> null
        }
    }

    private fun importSftpAuthentication(item: JSONObject): SftpAuthentication {
        val authentication = item.getJSONObject("authentication")
        return when (authentication.optString("type")) {
            "publicKey" -> SftpPublicKeyAuthentication(
                authentication.getString("privateKey"),
                authentication.optString("privateKeyPassword")
                    .takeIf { authentication.has("privateKeyPassword") }
            )
            else -> SftpPasswordAuthentication(authentication.optString("password", ""))
        }
    }

    private fun importWebDavAuthentication(item: JSONObject): WebDavAuthentication {
        val authentication = item.getJSONObject("authentication")
        return when (authentication.optString("type")) {
            "password" -> WebDavPasswordAuthentication(authentication.optString("password", ""))
            "accessToken" -> WebDavAccessTokenAuthentication(
                authentication.optString("accessToken", "")
            )
            else -> WebDavNoneAuthentication
        }
    }
}
