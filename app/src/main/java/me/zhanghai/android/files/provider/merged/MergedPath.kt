/*
 * Copyright (c) 2025 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.merged

import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringListPath
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.root.RootablePath
import me.zhanghai.android.files.util.readParcelable
import java.io.File
import java.io.IOException

/**
 * The single path of a [MergedFileSystem]. It has no parent, so the merged directory is the top
 * crumb of the path trail, named after the bookmark.
 */
internal class MergedPath : ByteStringListPath<MergedPath>, RootablePath {
    private val fileSystem: MergedFileSystem

    constructor(fileSystem: MergedFileSystem, path: ByteString) : super(
        MergedFileSystem.SEPARATOR, path
    ) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: MergedFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(MergedFileSystem.SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        !path.isEmpty() && path[0] == MergedFileSystem.SEPARATOR

    override fun createPath(path: ByteString): MergedPath = MergedPath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): MergedPath =
        MergedPath(fileSystem, absolute, segments)

    override fun getParent(): MergedPath? = null

    override val uriPath: ByteString
        // Prepend a slash character to make it a valid URI path, since we always have an (empty)
        // authority. The path contains the source URIs joined by a unit separator character.
        get() = ("/" + fileSystem.sources.joinToString(
            0x1F.toChar().toString()
        ) { it.toUri().toString() }).toByteString()

    override val uriQuery: ByteString?
        get() = super.uriPath

    override val defaultDirectory: MergedPath
        get() = fileSystem.defaultDirectory

    override fun getFileSystem(): MergedFileSystem = fileSystem

    override fun getRoot(): MergedPath? = if (isAbsolute) fileSystem.rootDirectory else null

    @Throws(IOException::class)
    override fun toRealPath(vararg options: LinkOption): MergedPath {
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

    override fun isRootRequired(isAttributeAccess: Boolean): Boolean = false

    private constructor(source: Parcel) : super(source) {
        fileSystem = source.readParcelable()!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.writeParcelable(fileSystem, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<MergedPath> {
            override fun createFromParcel(source: Parcel): MergedPath = MergedPath(source)

            override fun newArray(size: Int): Array<MergedPath?> = arrayOfNulls(size)
        }
    }
}

val Path.isMergedPath: Boolean
    get() = this is MergedPath
