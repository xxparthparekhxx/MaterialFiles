package me.zhanghai.android.files.provider.common

import java.net.URI
import java.net.URISyntaxException

data class UriAuthority(
    val userInfo: String?,
    val host: String,
    val port: Int?
) {
    fun encode(): String {
        try {
            // HACK: An empty host/authority requires a path, so use "/" as path here.
            val uri = URI(null, userInfo, host, port ?: -1, "/", null, null)
            // URI.getRawAuthority() returns null when authority is empty.
            return uri.rawAuthority.orEmpty()
        } catch (e: URISyntaxException) {
            // java.net.URI rejects host names with '_', which real servers use.
            return toString()
        }
    }

    // toString() is called by UI when the URI may not be valid, so build the string manually.
    override fun toString(): String = buildString {
        if (userInfo != null) {
            append(userInfo)
            append('@')
        }
        append(host)
        if (port != null) {
            append(':')
            append(port.toString())
        }
    }

    companion object {
        val EMPTY = UriAuthority(null, "", null)
    }
}
