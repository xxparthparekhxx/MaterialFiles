/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpClientTest {
    @Test
    fun parsesDidlLiteDocument() {
        val didl = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                <container id="1" parentID="0" restricted="1">
                    <dc:title>Videos</dc:title>
                    <upnp:class>object.container.storageFolder</upnp:class>
                </container>
                <item id="2" parentID="0" restricted="1">
                    <dc:title>movie.mp4</dc:title>
                    <upnp:class>object.item.videoItem</upnp:class>
                    <res protocolInfo="http-get:*:video/mp4:*" size="12345" duration="0:01:00">http://192.168.1.1/movie.mp4</res>
                    <dc:date>2024-01-02T03:04:05Z</dc:date>
                </item>
            </DIDL-Lite>
        """.trimIndent()
        val items = UpnpClient.parseDidl(didl)
        assertEquals(2, items.size)
        val container = items[0]
        assertEquals("1", container.id)
        assertEquals("Videos", container.title)
        assertTrue(container.isContainer)
        assertNull(container.resourceUrl)
        val item = items[1]
        assertEquals("2", item.id)
        assertEquals("movie.mp4", item.title)
        assertEquals(false, item.isContainer)
        assertEquals("http://192.168.1.1/movie.mp4", item.resourceUrl)
        assertEquals(12345L, item.size)
        assertEquals("video/mp4", item.mimeType)
        assertEquals(1704164645000L, item.lastModified)
    }

    @Test
    fun parsesEscapedDidlLiteDocument() {
        val didl = "&lt;DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"&gt;" +
            "&lt;item id=\"3\" parentID=\"0\"&gt;" +
            "&lt;dc:title&gt;song.mp3&lt;/dc:title&gt;" +
            "&lt;res protocolInfo=\"http-get:*:audio/mpeg:*\" size=\"42\"&gt;" +
            "http://192.168.1.2/song.mp3&lt;/res&gt;" +
            "&lt;/item&gt;&lt;/DIDL-Lite&gt;"
        val items = UpnpClient.parseDidl(didl)
        assertEquals(1, items.size)
        assertEquals("3", items[0].id)
        assertEquals("song.mp3", items[0].title)
        assertEquals("http://192.168.1.2/song.mp3", items[0].resourceUrl)
        assertEquals(42L, items[0].size)
    }

    @Test
    fun parsesEmptyDocument() {
        assertTrue(UpnpClient.parseDidl(
            "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"></DIDL-Lite>"
        ).isEmpty())
    }
}
