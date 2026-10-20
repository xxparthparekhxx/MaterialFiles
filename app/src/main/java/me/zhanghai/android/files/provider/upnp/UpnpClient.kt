/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.upnp

import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * A client of the UPnP ContentDirectory service, which is used by DLNA media servers.
 *
 * Devices are found with SSDP, their description document tells where the ContentDirectory service
 * lives, and the service is then browsed with SOAP.
 */
object UpnpClient {
    private const val SSDP_ADDRESS = "239.255.255.250"

    private const val SSDP_PORT = 1900

    private const val MEDIA_SERVER_DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaServer:1"

    private const val CONTENT_DIRECTORY_SERVICE_TYPE =
        "urn:schemas-upnp-org:service:ContentDirectory:1"

    private const val ROOT_OBJECT_ID = "0"

    private const val DISCOVERY_TIMEOUT_MILLIS = 3000

    private const val HTTP_TIMEOUT_MILLIS = 15 * 1000

    private const val USER_AGENT = "MaterialFiles/1.0 UPnP/1.0"

    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd"
    )

    /**
     * Finds the media servers that respond to an SSDP search.
     */
    @Throws(IOException::class)
    fun discoverDevices(timeoutMillis: Int = DISCOVERY_TIMEOUT_MILLIS): List<UpnpDevice> {
        val request = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 1\r\n")
            append("ST: $MEDIA_SERVER_DEVICE_TYPE\r\n")
            append("\r\n")
        }
        val locations = mutableSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMillis
            socket.broadcast = true
            val bytes = request.toByteArray(Charsets.UTF_8)
            socket.send(
                DatagramPacket(bytes, bytes.size, InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT)
            )
            val buffer = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    break
                }
                socket.soTimeout = remaining.toInt().coerceAtLeast(1)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: IOException) {
                    // The socket timed out or the packet was malformed.
                    break
                }
                val response = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                val location = response.findLocationHeader()
                if (location != null) {
                    locations.add(location)
                }
            }
        }
        return locations.mapNotNull { location ->
            try {
                getDevice(location)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Reads the description document of the device at the given location.
     */
    @Throws(IOException::class)
    fun getDevice(location: String): UpnpDevice {
        val url = URL(location)
        val document = url.openHttpConnection().let { connection ->
            try {
                connection.readXml()
            } finally {
                connection.disconnect()
            }
        }
        val device = document.documentElement.findFirstElement("device")
            ?: throw IOException("The device description document of $location has no device")
        val service = device.findFirstElement("serviceList")
            ?.findAllElements("service")
            ?.firstOrNull {
                it.findFirstElement("serviceType")?.textContent?.trim()
                    ?.startsWith(CONTENT_DIRECTORY_SERVICE_TYPE) == true
            }
        val controlUrl = service?.findFirstElement("controlURL")?.textContent?.trim()
        if (controlUrl == null) {
            throw IOException("The device at $location has no ContentDirectory service")
        }
        return UpnpDevice(
            location,
            device.findFirstElement("friendlyName")?.textContent?.trim() ?: location,
            device.findFirstElement("manufacturer")?.textContent?.trim(),
            device.findFirstElement("UDN")?.textContent?.trim(),
            URL(url, controlUrl).toString(),
            ROOT_OBJECT_ID
        )
    }

    /**
     * Lists the direct children of the given object.
     */
    @Throws(IOException::class)
    fun browse(device: UpnpDevice, objectId: String): List<UpnpItem> {
        val envelope = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
            append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
            append("<s:Body>")
            append("<u:Browse xmlns:u=\"$CONTENT_DIRECTORY_SERVICE_TYPE\">")
            append("<ObjectID>").append(escapeXml(objectId)).append("</ObjectID>")
            append("<BrowseFlag>DirectChildren</BrowseFlag>")
            append("<Filter>*</Filter>")
            append("<StartingIndex>0</StartingIndex>")
            append("<RequestedCount>0</RequestedCount>")
            append("<SortCriteria></SortCriteria>")
            append("</u:Browse>")
            append("</s:Body>")
            append("</s:Envelope>")
        }
        val response = URL(device.contentDirectoryControlUrl).openHttpConnection().let { connection ->
            try {
                connection.doOutput = true
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                connection.setRequestProperty("SOAPAction", "\"$CONTENT_DIRECTORY_SERVICE_TYPE#Browse\"")
                connection.outputStream.use { outputStream ->
                    outputStream.write(envelope.toByteArray(Charsets.UTF_8))
                }
                connection.readXml()
            } finally {
                connection.disconnect()
            }
        }
        val result = response.documentElement.findFirstElement("Body")
            ?.findFirstElement("BrowseResponse")
            ?.findFirstElement("Result")
            ?: throw IOException("The ContentDirectory service did not return a result")
        // Some servers embed the document as elements, others escape it as text.
        if (result.hasChildElements()) {
            return parseDidlItems(result)
        }
        val didl = result.textContent?.trim().orEmpty()
        if (didl.isEmpty()) {
            return emptyList()
        }
        return parseDidl(didl)
    }

    /**
     * Parses a DIDL-Lite document into items.
     */
    @Throws(IOException::class)
    fun parseDidl(didl: String): List<UpnpItem> {
        // Some servers escape the document instead of embedding it.
        val document = if (didl.startsWith("<")) {
            parseXml(didl)
        } else {
            parseXml(unescapeXml(didl))
        }
        return parseDidlItems(document.documentElement)
    }

    private fun parseDidlItems(root: Element): List<UpnpItem> {
        val items = mutableListOf<UpnpItem>()
        // The children are visited in the order they appear in the document.
        val children = root.childNodes
        for (i in 0 until children.length) {
            val element = children.item(i) as? Element ?: continue
            val localName = element.tagName.substringAfter(':')
            if (localName == "DIDL-Lite") {
                // The document is embedded in a wrapper element, so the items are in it.
                items += parseDidlItems(element)
                continue
            }
            if (localName != "item" && localName != "container") {
                continue
            }
            val id = element.getAttribute("id")
            if (id.isEmpty()) {
                continue
            }
            val title = element.findFirstElement("title")?.textContent?.trim() ?: continue
            val isContainer = localName == "container"
            val res = element.findFirstElement("res")
            val size = res?.getAttribute("size")?.toLongOrNull() ?: 0L
            // The protocol info is in the form of
            // protocol:networkMediaType:contentFormat:additionalInfo.
            val mimeType = res?.getAttribute("protocolInfo")?.split(':')?.getOrNull(2)
                ?.takeIf { it.isNotEmpty() && it != "*" }
            val lastModified = element.findFirstElement("date")?.textContent?.trim()
                ?.let { parseDate(it) }
            items.add(
                UpnpItem(
                    id, title, isContainer, res?.textContent?.trim(), size, mimeType, lastModified
                )
            )
        }
        return items
    }

    private fun parseXml(xml: String): Document {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            // The documents come from the network, so they must not be resolved against external
            // entities.
            setFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl")
            setFeatureQuietly(
                factory, "http://xml.org/sax/features/external-general-entities"
            )
            setFeatureQuietly(
                factory, "http://xml.org/sax/features/external-parameter-entities"
            )
            factory.isXIncludeAware = false
            factory.isExpandEntityReferences = false
            return factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            throw IOException("Failed to parse the XML document", e)
        }
    }

    private fun setFeatureQuietly(factory: DocumentBuilderFactory, name: String) {
        try {
            factory.setFeature(name, true)
        } catch (e: Exception) {
            // The feature is not supported.
        }
    }

    private fun HttpURLConnection.readXml(): Document {
        val statusCode = responseCode
        val stream: InputStream? = if (statusCode in 200..299) {
            inputStream
        } else {
            errorStream
        }
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (statusCode !in 200..299) {
            throw IOException("The server returned the status code $statusCode: $text")
        }
        return parseXml(text)
    }

    private fun URL.openHttpConnection(): HttpURLConnection {
        val connection = openConnection() as HttpURLConnection
        connection.connectTimeout = HTTP_TIMEOUT_MILLIS
        connection.readTimeout = HTTP_TIMEOUT_MILLIS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }

    private fun String.findLocationHeader(): String? =
        lineSequence().map { it.trim() }.firstOrNull {
            it.startsWith("LOCATION:", ignoreCase = true)
        }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

    private fun Element.hasChildElements(): Boolean {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            if (nodes.item(i).nodeType == Node.ELEMENT_NODE) {
                return true
            }
        }
        return false
    }

    private fun Element.findFirstElement(name: String): Element? =
        findAllElements(name).firstOrNull()

    private fun Element.findAllElements(name: String): List<Element> {
        val elements = mutableListOf<Element>()
        // The elements are in different namespaces, so they are matched by their local name.
        val nodes = getElementsByTagNameNS("*", name)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                elements.add(node)
            }
        }
        return elements
    }

    private fun escapeXml(string: String): String =
        string.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun unescapeXml(string: String): String =
        string.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&amp;", "&")

    private fun parseDate(string: String): Long? {
        for (format in DATE_FORMATS) {
            try {
                val date = java.text.SimpleDateFormat(format, Locale.US)
                date.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return date.parse(string)?.time
            } catch (e: Exception) {
                // Try the next format.
            }
        }
        return null
    }
}
