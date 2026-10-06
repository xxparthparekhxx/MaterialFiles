/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.image

import java8.nio.file.Path

/**
 * The image list is too large to put in an [android.content.Intent] or saved state. Either one is
 * sent across processes, and a folder of a few thousand images overflows that limit.
 */
object ImageViewerPaths {
    const val EXTRA_ID = "me.zhanghai.android.files.viewer.image.extra.PATH_LIST_ID"

    private var nextId = 1
    private val lists = LinkedHashMap<Int, MutableList<Path>>()

    fun put(paths: List<Path>): Int {
        val id = nextId++
        lists[id] = paths.toMutableList()
        while (lists.size > MAX_LISTS) {
            lists.remove(lists.keys.first())
        }
        return id
    }

    fun get(id: Int): MutableList<Path>? = lists[id]

    fun remove(id: Int) {
        lists.remove(id)
    }

    private const val MAX_LISTS = 8
}
