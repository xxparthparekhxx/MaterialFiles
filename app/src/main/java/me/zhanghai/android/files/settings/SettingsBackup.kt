/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object SettingsBackup {
    private const val VERSION = 1

    fun export(preferences: SharedPreferences): String {
        val entries = JSONObject()
        for ((key, value) in preferences.all) {
            exportValue(value)?.let { entries.put(key, it) }
        }
        return JSONObject()
            .put("version", VERSION)
            .put("preferences", entries)
            .toString(2)
    }

    private fun exportValue(value: Any?): JSONObject? =
        when (value) {
            is String -> JSONObject().put("type", "string").put("value", value)
            is Set<*> -> JSONObject().put("type", "stringSet").put(
                "value",
                JSONArray().apply { value.forEach { put(it.toString()) } }
            )
            is Boolean -> JSONObject().put("type", "boolean").put("value", value)
            is Int -> JSONObject().put("type", "int").put("value", value)
            is Long -> JSONObject().put("type", "long").put("value", value)
            is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
            else -> null
        }

    @Throws(IOException::class)
    fun import(json: String, preferences: SharedPreferences) {
        val root = JSONObject(json)
        if (root.optInt("version") != VERSION || !root.has("preferences")) {
            throw IOException("Unsupported settings backup")
        }
        val entries = root.getJSONObject("preferences")
        val editor = preferences.edit().clear()
        val keys = entries.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = entries.getJSONObject(key)
            when (item.getString("type")) {
                "string" -> editor.putString(key, item.getString("value"))
                "stringSet" -> {
                    val array = item.getJSONArray("value")
                    val set = mutableSetOf<String>()
                    for (index in 0 until array.length()) {
                        set.add(array.getString(index))
                    }
                    editor.putStringSet(key, set)
                }
                "boolean" -> editor.putBoolean(key, item.getBoolean("value"))
                "int" -> editor.putInt(key, item.getInt("value"))
                "long" -> editor.putLong(key, item.getLong("value"))
                "float" -> editor.putFloat(key, item.getDouble("value").toFloat())
                else -> throw IOException("Unsupported settings backup")
            }
        }
        editor.apply()
    }
}
