/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.upnp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A UPnP device that provides a ContentDirectory service, for example a DLNA media server.
 */
@Parcelize
class UpnpDevice(
    /** The URL of the device description document. */
    val location: String,
    /** The friendly name of the device. */
    val friendlyName: String,
    /** The manufacturer of the device, if known. */
    val manufacturer: String?,
    /** The unique device name, if known. */
    val udn: String?,
    /** The control URL of the ContentDirectory service. */
    val contentDirectoryControlUrl: String,
    /** The object ID of the root container. */
    val rootObjectId: String
) : Parcelable
