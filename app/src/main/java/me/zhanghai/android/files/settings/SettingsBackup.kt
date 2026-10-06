/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

object SettingsBackup {
    private const val VERSION = 1

    const val MAX_JSON_BYTES = 2 * 1024 * 1024
    private const val MAX_ENTRIES = 200
    private const val MAX_STRING_LEN = 200_000
    private const val MAX_SET_SIZE = 1000

    // Never exported: server credentials and FTP-server password are stored here.
    // key_storages holds SFTP/SMB/FTP/WebDAV passwords (Base64 parcel, trivially decodable),
    // key_ftp_server_password is plaintext. Use StorageImportExport for servers instead.
    private val SENSITIVE_KEYS = setOf("key_storages", "key_ftp_server_password")

    fun export(preferences: SharedPreferences): String {
        val entries = JSONObject()
        for ((key, value) in preferences.all) {
            if (key in SENSITIVE_KEYS) {
                continue
            }
            exportValue(value)?.let { entries.put(key, it) }
        }
        return JSONObject()
            .put("version", VERSION)
            .put("preferences", entries)
            .toString(2)
    }

    private fun exportValue(value: Any?): JSONObject? =
        when (value) {
            is String -> {
                if (value.length > MAX_STRING_LEN) {
                    null
                } else {
                    JSONObject().put("type", "string").put("value", value)
                }
            }
            is Set<*> -> {
                if (value.size > MAX_SET_SIZE) {
                    null
                } else {
                    JSONObject().put("type", "stringSet").put(
                        "value",
                        JSONArray().apply {
                            value.forEach {
                                val s = it.toString()
                                if (s.length <= MAX_STRING_LEN) {
                                    put(s)
                                }
                            }
                        }
                    )
                }
            }
            is Boolean -> JSONObject().put("type", "boolean").put("value", value)
            is Int -> JSONObject().put("type", "int").put("value", value)
            is Long -> JSONObject().put("type", "long").put("value", value)
            is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
            else -> null
        }

    @Throws(IOException::class)
    fun import(json: String, preferences: SharedPreferences) {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) {
            throw IOException("Settings backup too large")
        }
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw IOException("Invalid settings backup", e)
        }
        if (!root.has("version") || root.optInt("version", -1) != VERSION ||
            !root.has("preferences")) {
            throw IOException("Unsupported settings backup")
        }
        val entries = try {
            root.getJSONObject("preferences")
        } catch (e: JSONException) {
            throw IOException("Invalid settings backup", e)
        }
        if (entries.length() > MAX_ENTRIES) {
            throw IOException("Settings backup has too many entries")
        }
        // Validate everything before touching prefs.
        data class Pending(val type: String, val string: String? = null,
            val stringSet: Set<String>? = null, val boolean: Boolean? = null,
            val int: Int? = null, val long: Long? = null, val float: Float? = null)
        val pending = mutableMapOf<String, Pending>()
        val keys = entries.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in SENSITIVE_KEYS) {
                throw IOException("Backup contains server credentials which are not supported")
            }
            if (key.length > 256) {
                throw IOException("Invalid settings backup")
            }
            val item = try {
                entries.getJSONObject(key)
            } catch (e: JSONException) {
                throw IOException("Invalid settings backup", e)
            }
            if (!item.has("type") || !item.has("value")) {
                throw IOException("Invalid settings backup")
            }
            val type = try {
                item.getString("type")
            } catch (e: JSONException) {
                throw IOException("Invalid settings backup", e)
            }
            try {
                when (type) {
                    "string" -> {
                        val v = item.getString("value")
                        if (v.length > MAX_STRING_LEN) {
                            throw IOException("Settings backup entry too large")
                        }
                        pending[key] = Pending(type, string = v)
                    }
                    "stringSet" -> {
                        val array = item.getJSONArray("value")
                        if (array.length() > MAX_SET_SIZE) {
                            throw IOException("Settings backup entry too large")
                        }
                        val set = mutableSetOf<String>()
                        for (index in 0 until array.length()) {
                            val s = array.getString(index)
                            if (s.length > MAX_STRING_LEN) {
                                throw IOException("Settings backup entry too large")
                            }
                            set.add(s)
                        }
                        pending[key] = Pending(type, stringSet = set)
                    }
                    "boolean" -> pending[key] = Pending(type, boolean = item.getBoolean("value"))
                    "int" -> pending[key] = Pending(type, int = item.getInt("value"))
                    "long" -> pending[key] = Pending(type, long = item.getLong("value"))
                    "float" -> {
                        val d = item.getDouble("value")
                        if (!d.isFinite()) {
                            throw IOException("Invalid settings backup")
                        }
                        pending[key] = Pending(type, float = d.toFloat())
                    }
                    else -> throw IOException("Unsupported settings backup")
                }
            } catch (e: IOException) {
                throw e
            } catch (e: JSONException) {
                throw IOException("Invalid settings backup", e)
            }
        }
        val editor = preferences.edit().clear()
        for ((key, value) in pending) {
            when (value.type) {
                "string" -> editor.putString(key, value.string)
                "stringSet" -> editor.putStringSet(key, value.stringSet)
                "boolean" -> editor.putBoolean(key, value.boolean ?: false)
                "int" -> editor.putInt(key, value.int ?: 0)
                "long" -> editor.putLong(key, value.long ?: 0L)
                "float" -> editor.putFloat(key, value.float ?: 0f)
            }
        }
        if (!editor.commit()) {
            throw IOException("Failed to restore settings")
        }
    }
}
