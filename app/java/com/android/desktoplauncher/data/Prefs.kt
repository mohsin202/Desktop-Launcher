package com.android.desktoplauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private fun sp(context: Context) =
        context.getSharedPreferences("desktop", Context.MODE_PRIVATE)

    fun iconSize(context: Context) = sp(context).getString("icon", "normal") ?: "normal"
    fun setIconSize(context: Context, value: String) = sp(context).edit().putString("icon", value).apply()

    fun themed(context: Context) = sp(context).getBoolean("themed", false)
    fun setThemed(context: Context, value: Boolean) = sp(context).edit().putBoolean("themed", value).apply()

    fun dots(context: Context) = sp(context).getBoolean("dots", true)
    fun setDots(context: Context, value: Boolean) = sp(context).edit().putBoolean("dots", value).apply()

    fun taskbar(context: Context) = sp(context).getBoolean("taskbar", true)
    fun setTaskbar(context: Context, value: Boolean) = sp(context).edit().putBoolean("taskbar", value).apply()

    fun windowed(context: Context) = sp(context).getBoolean("windowed", false)
    fun setWindowed(context: Context, value: Boolean) = sp(context).edit().putBoolean("windowed", value).apply()

    fun setupDone(context: Context) = sp(context).getBoolean("setup", false)
    fun setSetupDone(context: Context, value: Boolean) = sp(context).edit().putBoolean("setup", value).apply()

    fun shortcuts(context: Context): List<String> =
        sp(context).getString("shortcuts", "")!!.split('\n').filter { it.isNotBlank() }

    fun setShortcuts(context: Context, values: List<String>) =
        sp(context).edit().putString("shortcuts", values.distinct().joinToString("\n")).apply()

    fun pins(context: Context): List<String> =
        sp(context).getString("pins", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    fun hasPins(context: Context) = sp(context).contains("pins")

    fun setPins(context: Context, values: List<String>) =
        sp(context).edit().putString("pins", values.distinct().joinToString("\n")).apply()

    fun recents(context: Context): List<String> =
        sp(context).getString("recents", "")!!.split('\n').filter { it.isNotBlank() }

    fun pushRecent(context: Context, pkg: String) {
        val next = listOf(pkg) + recents(context).filter { it != pkg }
        sp(context).edit().putString("recents", next.take(8).joinToString("\n")).apply()
    }

    fun widgets(context: Context): JSONArray =
        runCatching { JSONArray(sp(context).getString("widgets", "[]")) }.getOrDefault(JSONArray())

    fun setWidgets(context: Context, array: JSONArray) =
        sp(context).edit().putString("widgets", array.toString()).apply()

    fun putWidget(context: Context, obj: JSONObject) {
        val array = widgets(context)
        array.put(obj)
        setWidgets(context, array)
    }
}
