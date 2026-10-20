/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.Context
import androidx.core.content.edit
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import org.json.JSONObject
import java8.nio.file.Path

/**
 * Per-file color tags stored by path string. The map is keyed by the path's string form so it
 * works for all file systems (local, content, SFTP, SMB, archive, ...).
 */
object FileColorStore {
    val COLORS: List<Pair<String, Int>> = listOf(
        "red" to R.color.file_color_red,
        "orange" to R.color.file_color_orange,
        "yellow" to R.color.file_color_yellow,
        "green" to R.color.file_color_green,
        "cyan" to R.color.file_color_cyan,
        "blue" to R.color.file_color_blue,
        "purple" to R.color.file_color_purple,
        "pink" to R.color.file_color_pink,
    )

    private const val PREFERENCES_NAME = "file_colors"
    private const val KEY_COLORS = "colors"

    private val map = HashMap<String, String>()
    private var cachedJson: String? = null

    private fun loadIfNeeded() {
        val json = application.getSharedPreferences(
            PREFERENCES_NAME, Context.MODE_PRIVATE
        ).getString(KEY_COLORS, null)
        if (json == cachedJson) {
            return
        }
        cachedJson = json
        map.clear()
        if (json != null) {
            val colors = JSONObject(json)
            for (key in colors.keys()) {
                map[key] = colors.getString(key)
            }
        }
    }

    fun colorOf(path: Path): String? {
        loadIfNeeded()
        return map[path.toString()]
    }

    fun setColor(path: Path, color: String?) {
        loadIfNeeded()
        if (color == null) {
            map.remove(path.toString())
        } else {
            map[path.toString()] = color
        }
        val colors = JSONObject()
        for ((key, value) in map) {
            colors.put(key, value)
        }
        application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_COLORS, colors.toString()) }
    }

    fun colorResOf(key: String): Int =
        COLORS.first { it.first == key }.second
}
