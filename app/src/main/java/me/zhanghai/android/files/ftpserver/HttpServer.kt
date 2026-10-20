/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.webkit.MimeTypeMap
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import me.zhanghai.android.files.compat.getMimeTypeFromExtensionCompat
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.FtpFile

/**
 * A minimal read-only HTTP server that serves the same files as the built-in FTP server, so that
 * they can also be opened in a browser.
 */
class HttpServer(private val port: Int, private val fileSystemView: FileSystemView) {
    private var serverSocket: ServerSocket? = null
    private var executorService: ExecutorService? = null

    fun start() {
        val serverSocket = ServerSocket(port)
        this.serverSocket = serverSocket
        val executorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "HTTP server").apply { isDaemon = true }
        }
        this.executorService = executorService
        executorService.execute {
            while (true) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: IOException) {
                    if (serverSocket.isClosed) {
                        break
                    }
                    e.printStackTrace()
                    continue
                }
                executorService.execute { handle(socket) }
            }
        }
    }

    fun stop() {
        serverSocket?.close()
        serverSocket = null
        executorService?.shutdownNow()
        executorService = null
    }

    private fun handle(socket: Socket) {
        socket.use {
            try {
                val inputStream = socket.getInputStream()
                val outputStream = BufferedOutputStream(socket.getOutputStream())
                val requestLine = readLine(inputStream) ?: return
                val requestParts = requestLine.split(' ')
                if (requestParts.size < 2) {
                    sendError(outputStream, 400, "Bad Request")
                    return
                }
                val method = requestParts[0]
                val target = requestParts[1]
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(inputStream) ?: break
                    if (line.isEmpty()) {
                        break
                    }
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        headers[line.substring(0, colonIndex).trim().lowercase()] =
                            line.substring(colonIndex + 1).trim()
                    }
                }
                if (method != "GET" && method != "HEAD") {
                    sendError(outputStream, 405, "Method Not Allowed")
                    return
                }
                serve(outputStream, target, headers["range"], method == "HEAD")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun serve(
        outputStream: OutputStream,
        target: String,
        rangeHeader: String?,
        headOnly: Boolean
    ) {
        val path = target.substringBefore('?')
        val decodedPath = try {
            URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            sendError(outputStream, 400, "Bad Request")
            return
        }
        val file = fileSystemView.getFile(decodedPath)
        if (!file.doesExist() || !file.isReadable) {
            sendError(outputStream, 404, "Not Found")
            return
        }
        if (file.isDirectory) {
            sendDirectory(outputStream, file, headOnly)
        } else {
            sendFile(outputStream, file, rangeHeader, headOnly)
        }
    }

    private fun sendDirectory(outputStream: OutputStream, directory: FtpFile, headOnly: Boolean) {
        val children = directory.listFiles() ?: emptyList()
        val builder = StringBuilder()
        builder.append("<!DOCTYPE html>\n<html>\n<head>\n")
        builder.append("<meta charset=\"utf-8\">\n")
        builder.append("<title>").append(escapeHtml(directory.name)).append("</title>\n")
        builder.append("</head>\n<body>\n<h1>").append(escapeHtml(directory.name)).append("</h1>\n<ul>\n")
        for (child in children) {
            val name = child.name + if (child.isDirectory) "/" else ""
            builder.append("<li><a href=\"")
                .append(encodePath(child.absolutePath))
                .append("\">")
                .append(escapeHtml(name))
                .append("</a></li>\n")
        }
        builder.append("</ul>\n</body>\n</html>\n")
        val content = builder.toString().toByteArray(StandardCharsets.UTF_8)
        writeHeaders(outputStream, "200 OK", "text/html; charset=utf-8", content.size.toLong(), null)
        if (!headOnly) {
            outputStream.write(content)
        }
        outputStream.flush()
    }

    private fun sendFile(
        outputStream: OutputStream,
        file: FtpFile,
        rangeHeader: String?,
        headOnly: Boolean
    ) {
        val size = file.size
        val range = parseRange(rangeHeader, size)
        val start = range?.first ?: 0L
        val end = range?.last ?: (size - 1)
        val length = if (size == 0L) 0L else end - start + 1
        val status = if (range != null) "206 Partial Content" else "200 OK"
        val contentRange =
            if (range != null) "bytes $start-$end/$size" else null
        val extension = file.name.substringAfterLast('.', "")
        val contentType =
            MimeTypeMap.getSingleton().getMimeTypeFromExtensionCompat(extension)
                ?: "application/octet-stream"
        writeHeaders(outputStream, status, contentType, length, contentRange, file.lastModified)
        if (!headOnly && length > 0) {
            file.createInputStream(start).use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var remaining = length
                while (remaining > 0) {
                    val read = inputStream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read == -1) {
                        break
                    }
                    outputStream.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
        outputStream.flush()
    }

    private fun writeHeaders(
        outputStream: OutputStream,
        status: String,
        contentType: String,
        length: Long,
        contentRange: String?,
        lastModified: Long = 0
    ) {
        val builder = StringBuilder()
        builder.append("HTTP/1.1 ").append(status).append("\r\n")
        builder.append("Content-Type: ").append(contentType).append("\r\n")
        builder.append("Content-Length: ").append(length).append("\r\n")
        builder.append("Accept-Ranges: bytes\r\n")
        if (lastModified > 0) {
            builder.append("Last-Modified: ")
                .append(HTTP_DATE_FORMATTER.format(Instant.ofEpochMilli(lastModified)))
                .append("\r\n")
        }
        if (contentRange != null) {
            builder.append("Content-Range: ").append(contentRange).append("\r\n")
        }
        builder.append("Connection: close\r\n")
        builder.append("\r\n")
        outputStream.write(builder.toString().toByteArray(StandardCharsets.ISO_8859_1))
    }

    private fun sendError(outputStream: OutputStream, code: Int, reason: String) {
        val content = "$code $reason\n".toByteArray(StandardCharsets.UTF_8)
        writeHeaders(outputStream, "$code $reason", "text/plain; charset=utf-8", content.size.toLong(), null)
        outputStream.write(content)
        outputStream.flush()
    }

    // Only a single range is supported, and an unsatisfiable one is served as the whole file.
    private fun parseRange(rangeHeader: String?, size: Long): LongRange? {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return null
        }
        val spec = rangeHeader.removePrefix("bytes=").trim()
        val separatorIndex = spec.indexOf('-')
        if (separatorIndex < 0) {
            return null
        }
        val startString = spec.substring(0, separatorIndex).trim()
        val endString = spec.substring(separatorIndex + 1).trim()
        if (startString.isEmpty()) {
            val suffixLength = endString.toLongOrNull() ?: return null
            if (suffixLength <= 0) {
                return null
            }
            return (size - suffixLength).coerceAtLeast(0)..(size - 1)
        }
        val start = startString.toLongOrNull() ?: return null
        if (start >= size) {
            return null
        }
        val end = if (endString.isEmpty()) size - 1 else endString.toLongOrNull() ?: return null
        return start..minOf(end, size - 1)
    }

    private fun readLine(inputStream: InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val byte = inputStream.read()
            if (byte == -1) {
                return if (builder.isEmpty()) null else builder.toString()
            }
            if (byte == '\n'.code) {
                if (builder.isNotEmpty() && builder.last() == '\r') {
                    builder.setLength(builder.length - 1)
                }
                return builder.toString()
            }
            builder.append(byte.toChar())
        }
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun encodePath(path: String): String {
        val builder = StringBuilder()
        for (byte in path.toByteArray(StandardCharsets.UTF_8)) {
            val char = byte.toInt().toChar()
            if (char.isLetterOrDigit() || char in "-_.~!$&'()*+,;=:@/") {
                builder.append(char)
            } else {
                builder.append('%')
                    .append("%02X".format(byte.toInt() and 0xFF))
            }
        }
        return builder.toString()
    }

    companion object {
        private const val BUFFER_SIZE = 8192

        private val HTTP_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                .withZone(ZoneOffset.UTC)
    }
}
