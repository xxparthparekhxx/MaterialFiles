/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.upnp

import java.io.Serializable

/**
 * An item of a UPnP ContentDirectory, either a container (a folder) or an item (a file).
 */
class UpnpItem(
    /** The object ID of the item, which is unique within the device. */
    val id: String,
    /** The title of the item, which is used as its file name. */
    val title: String,
    /** Whether the item is a container. */
    val isContainer: Boolean,
    /** The URL of the resource, only present for items. */
    val resourceUrl: String?,
    /** The size of the resource in bytes, or 0 if unknown. */
    val size: Long,
    /** The MIME type of the resource, if known. */
    val mimeType: String?,
    /** The last modification time of the resource in milliseconds since the epoch, or null. */
    val lastModified: Long?
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
