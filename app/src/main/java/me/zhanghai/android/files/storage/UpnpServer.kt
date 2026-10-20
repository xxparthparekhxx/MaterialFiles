/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.upnp.UpnpFileSystemProvider
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import kotlin.random.Random

/**
 * A UPnP media server, which is browsed read only.
 */
@Parcelize
class UpnpServer(
    override val id: Long,
    override val customName: String?,
    /** The location of the description document of the device. */
    val location: String,
    /** The friendly name of the device. */
    val friendlyName: String
) : Storage() {
    constructor(
        id: Long?,
        customName: String?,
        location: String,
        friendlyName: String
    ) : this(id ?: Random.nextLong(), customName, location, friendlyName)

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.computer_icon_white_24dp

    override fun getDefaultName(context: Context): String = friendlyName

    override val description: String
        get() = friendlyName

    override val path: Path
        get() = UpnpFileSystemProvider.getOrNewFileSystem(location, friendlyName).rootDirectory

    override fun createEditIntent(): Intent =
        EditUpnpServerActivity::class.createIntent().putArgs(EditUpnpServerFragment.Args(this))
}
