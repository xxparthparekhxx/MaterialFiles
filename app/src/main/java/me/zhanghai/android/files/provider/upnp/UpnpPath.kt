/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.upnp

import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringListPath
import me.zhanghai.android.files.util.readParcelable
import java.io.File
import java.io.IOException

internal class UpnpPath : ByteStringListPath<UpnpPath> {
    private val fileSystem: UpnpFileSystem

    constructor(fileSystem: UpnpFileSystem, path: ByteString) : super(
        UpnpFileSystem.SEPARATOR, path
    ) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: UpnpFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(UpnpFileSystem.SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        path.isNotEmpty() && path[0] == UpnpFileSystem.SEPARATOR

    override fun createPath(path: ByteString): UpnpPath = UpnpPath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): UpnpPath =
        UpnpPath(fileSystem, absolute, segments)

    override val defaultDirectory: UpnpPath
        get() = fileSystem.defaultDirectory

    override fun getFileSystem(): FileSystem = fileSystem

    override fun getRoot(): UpnpPath? = if (isAbsolute) fileSystem.rootDirectory else null

    @Throws(IOException::class)
    override fun toRealPath(vararg options: LinkOption): UpnpPath {
        throw UnsupportedOperationException()
    }

    override fun toFile(): File {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey {
        throw UnsupportedOperationException()
    }

    val device: UpnpDevice
        get() = fileSystem.device

    private constructor(source: Parcel) : super(source) {
        fileSystem = source.readParcelable()!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.writeParcelable(fileSystem, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<UpnpPath> {
            override fun createFromParcel(source: Parcel): UpnpPath = UpnpPath(source)

            override fun newArray(size: Int): Array<UpnpPath?> = arrayOfNulls(size)
        }
    }
}
