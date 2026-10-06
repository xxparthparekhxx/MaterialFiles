package me.zhanghai.android.files.provider.ftp.client

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.FTPFile
import java.io.File
import java.io.IOException
import java.util.Calendar

private val DUMMY_ROOT_FTP_FILE = FTPFile().apply {
    rawListing = "Type=dir;Size=4096;Modify=19700101000000;Perm=cdeflmp; /"
    type = FTPFile.DIRECTORY_TYPE
    size = 4096
    timestamp = Calendar.getInstance().apply { timeInMillis = 0 }
    setPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION, true)
    setPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION, true)
    setPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION, true)
    name = "/"
}

private val SPECIAL_FTP_CHARACTERS = charArrayOf('[', ']', '*', '?', '{', '}', '~')

private fun String.hasSpecialFtpCharacters(): Boolean =
    any { it in SPECIAL_FTP_CHARACTERS }

@Throws(IOException::class)
fun FTPClient.mlistFileCompat(pathname: String): FTPFile? {
    val path = File(pathname)
    val parent = path.parent ?: if (pathname == "/" || pathname.isEmpty()) return DUMMY_ROOT_FTP_FILE else "/"
    if (hasFeature(FTPCmd.MLST) && !pathname.hasSpecialFtpCharacters()) {
        val file = mlistFile(pathname)
        if (file != null && (File(file.name).name == path.name || path.name.isEmpty())) {
            return file
        }
    }
    return mlistDirCompat(parent)?.firstOrNull { it.name == path.name }
}

@Throws(IOException::class)
fun FTPClient.mlistDirCompat(pathname: String): Array<FTPFile>? {
    // Note that there is no distinct FEAT output for MLSD. The presence of the MLST feature
    // indicates that both MLST and MLSD are supported.
    // @see https://datatracker.ietf.org/doc/html/rfc3659#section-7.8
    // FTPClient silently returns an empty array even when server returns an error for unknown
    // command, so we have to rely on checking the feature.
    val canUseMlsd = hasFeature(FTPCmd.MLST)
    // Some servers ignore the pathname on MLSD and list the working directory instead, so enter
    // the directory first and list it with no path. Changing directory also avoids globbing of
    // special characters (such as '[') in the pathname.
    val files = listAfterChangingDirectory(pathname, allowMlsd = canUseMlsd)
    if (files != null) {
        return files
    }
    if (canUseMlsd && !pathname.hasSpecialFtpCharacters()) {
        return mlistDir(pathname)
    }
    return null
}

private fun FTPClient.mlistDirOrNull(pathname: String?): Array<FTPFile>? =
    try {
        if (pathname == null) mlistDir() else mlistDir(pathname)
    } catch (e: IOException) {
        null
    }

private fun FTPClient.listingFailed(files: Array<FTPFile>): Boolean {
    if (files.isNotEmpty()) {
        return false
    }
    if (replyCode >= 400) {
        return true
    }
    val reply = replyString ?: return false
    return reply.contains("Out of memory", ignoreCase = true) ||
        reply.contains("globbing", ignoreCase = true)
}

@Throws(IOException::class)
private fun FTPClient.listAfterChangingDirectory(
    pathname: String,
    allowMlsd: Boolean
): Array<FTPFile>? {
    // Changing working directory avoids globbing and syntax issues with special characters
    // (such as '[') in pathname on MLSD and LIST.
    val previousWorkingDirectory = printWorkingDirectory()
    if (!changeWorkingDirectory(pathname)) {
        return null
    }
    try {
        if (allowMlsd) {
            val files = mlistDirOrNull(null)
            if (files != null && !listingFailed(files)) {
                return files
            }
        }
        return listFiles()
    } finally {
        if (previousWorkingDirectory != null) {
            if (!changeWorkingDirectory(previousWorkingDirectory)) {
                disconnect()
            }
        }
    }
}

@Throws(IOException::class)
fun FTPClient.setModificationTimeCompat(pathname: String, timeval: String): Boolean =
    // @see https://www.ietf.org/archive/id/draft-somers-ftp-mfxx-04.txt
    // This is frequently called during file operations, so in order to avoid wasting network
    // requests, we check the feature first which is cached locally.
    if (hasFeature(FTPCmd.MFMT)) {
        setModificationTime(pathname, timeval)
    } else {
        throw IOException("Missing feature ${FTPCmd.MFMT.command}")
    }
