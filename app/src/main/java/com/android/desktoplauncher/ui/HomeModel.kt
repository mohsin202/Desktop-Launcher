package com.android.desktoplauncher.ui

import android.app.Application
import android.os.FileObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.desktoplauncher.data.AppItem
import com.android.desktoplauncher.data.AppRepository
import com.android.desktoplauncher.data.DesktopFiles
import com.android.desktoplauncher.data.FileClip
import com.android.desktoplauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Spot(
    val key: String,
    val kind: String,
    val hostId: Int,
    val x: Float,
    val y: Float,
)

data class MenuAt(val x: Float, val y: Float, val kind: String, val arg: String)

class HomeModel(app: Application) : AndroidViewModel(app) {
    var apps by mutableStateOf<List<AppItem>>(emptyList())
    var files by mutableStateOf<List<File>>(emptyList())
    var shortcuts by mutableStateOf<List<String>>(emptyList())
    var spots by mutableStateOf<List<Spot>>(emptyList())
    var pins by mutableStateOf<List<String>>(emptyList())
    var iconSize by mutableStateOf(Prefs.iconSize(app))
    var themed by mutableStateOf(Prefs.themed(app))
    var showDots by mutableStateOf(Prefs.dots(app))
    var windowed by mutableStateOf(Prefs.windowed(app))
    var taskbarOn by mutableStateOf(Prefs.taskbar(app))
    var query by mutableStateOf("")
    var drawer by mutableStateOf(true)
    var menu by mutableStateOf<MenuAt?>(null)
    var flyout by mutableStateOf<String?>(null)
    var prompt by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    var recentsOpen by mutableStateOf(false)
    var selected by mutableStateOf<String?>(null)
    var panel by mutableStateOf<String?>(null)
    var showSetup by mutableStateOf(!Prefs.setupDone(app))
    var filesReady by mutableStateOf(DesktopFiles.ready())
    var ghost by mutableStateOf<String?>(null)
    var ghostX by mutableStateOf(0f)
    var ghostY by mutableStateOf(0f)

    private var observer: FileObserver? = null

    init {
        reload()
    }

    fun reload() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = AppRepository.load(context)
            if (!Prefs.hasPins(context)) {
                val prefer = listOf(
                    "com.android.chrome",
                    "com.google.android.calendar",
                    "com.google.android.gm",
                    "com.google.android.apps.maps",
                    "com.android.camera2",
                    "com.google.android.apps.docs",
                )
                val picked = (prefer.mapNotNull { pkg -> loaded.firstOrNull { it.packageName == pkg }?.packageName } +
                    loaded.map { it.packageName }).distinct().take(5)
                Prefs.setPins(context, picked)
            }
            val nextFiles = DesktopFiles.list()
            val nextShortcuts = Prefs.shortcuts(context)
            val nextPins = Prefs.pins(context)
            val nextSpots = readSpots(context)
            withContext(Dispatchers.Main) {
                apps = loaded
                files = nextFiles
                shortcuts = nextShortcuts
                pins = nextPins
                spots = nextSpots
                filesReady = DesktopFiles.ready()
                watch()
            }
        }
    }

    fun refreshPerms() {
        filesReady = DesktopFiles.ready()
        if (filesReady) reload()
    }

    fun message(text: String) {
        toast = text
        viewModelScope.launch {
            delay(2000)
            if (toast == text) toast = null
        }
    }

    fun setSize(value: String) {
        iconSize = value
        Prefs.setIconSize(getApplication(), value)
        menu = null
        flyout = null
    }

    fun toggleThemed() {
        themed = !themed
        Prefs.setThemed(getApplication(), themed)
    }

    fun toggleDots() {
        showDots = !showDots
        Prefs.setDots(getApplication(), showDots)
    }

    fun toggleWindowed() {
        windowed = !windowed
        Prefs.setWindowed(getApplication(), windowed)
    }

    fun toggleTaskbar() {
        taskbarOn = !taskbarOn
        Prefs.setTaskbar(getApplication(), taskbarOn)
    }

    fun finishSetup() {
        showSetup = false
        Prefs.setSetupDone(getApplication(), true)
    }

    fun copyPath(path: String) {
        FileClip.op = "copy"
        FileClip.paths = listOf(path)
        selected = path
        menu = null
        message("Copied")
    }

    fun cutPath(path: String) {
        FileClip.op = "cut"
        FileClip.paths = listOf(path)
        selected = path
        menu = null
        message("Cut")
    }

    fun paste() {
        if (!filesReady) {
            message("Allow all files access")
            menu = null
            return
        }
        val count = DesktopFiles.paste(getApplication(), FileClip.op, FileClip.paths)
        if (FileClip.op == "cut") FileClip.clear()
        menu = null
        files = DesktopFiles.list()
        message(if (count > 0) "Pasted" else "Nothing to paste")
    }

    fun create(kind: String, raw: String) {
        val name = raw.trim()
        if (name.isEmpty()) return
        if (!filesReady) {
            message("Allow all files access")
            return
        }
        if (kind == "folder") DesktopFiles.createFolder(name) else DesktopFiles.createFile(name)
        files = DesktopFiles.list()
        message(if (kind == "folder") "Folder created" else "File created")
    }

    fun removePath(path: String) {
        if (DesktopFiles.removeToDocuments(path)) message("Removed from desktop")
        else message("Couldn’t remove")
        files = DesktopFiles.list()
        menu = null
    }

    fun deletePath(path: String) {
        DesktopFiles.delete(path)
        if (FileClip.paths.contains(path)) FileClip.clear()
        files = DesktopFiles.list()
        menu = null
        message("Deleted")
    }

    fun addShortcut(flat: String) {
        val context = getApplication<Application>()
        val next = (Prefs.shortcuts(context) + flat).distinct()
        Prefs.setShortcuts(context, next)
        shortcuts = next
        menu = null
        message("Added to desktop")
    }

    fun removeShortcut(flat: String) {
        val context = getApplication<Application>()
        val next = Prefs.shortcuts(context).filter { it != flat }
        Prefs.setShortcuts(context, next)
        shortcuts = next
        menu = null
        message("Removed")
    }

    fun togglePin(pkg: String) {
        val context = getApplication<Application>()
        val current = Prefs.pins(context)
        val next = if (pkg in current) current.filter { it != pkg } else current + pkg
        Prefs.setPins(context, next)
        pins = next
        menu = null
        message(if (pkg in next) "Pinned to taskbar" else "Unpinned")
    }

    fun addBuiltin(kind: String, x: Float, y: Float) {
        val spot = Spot("b-${System.nanoTime()}", kind, 0, x.coerceIn(0.04f, 0.75f), y.coerceIn(0.05f, 0.7f))
        writeSpots(spots + spot)
        panel = null
        message("Widget added")
    }

    fun addHostWidget(id: Int) {
        val spot = Spot("h-$id", "host", id, 0.2f, 0.28f)
        writeSpots(spots + spot)
        panel = null
        message("Widget added")
    }

    fun moveSpot(key: String, x: Float, y: Float) {
        writeSpots(spots.map { if (it.key == key) it.copy(x = x.coerceIn(0.02f, 0.8f), y = y.coerceIn(0.02f, 0.72f)) else it })
    }

    fun removeSpot(key: String) {
        writeSpots(spots.filter { it.key != key })
        menu = null
    }

    private fun writeSpots(next: List<Spot>) {
        spots = next
        val array = JSONArray()
        next.forEach {
            array.put(
                JSONObject()
                    .put("key", it.key)
                    .put("kind", it.kind)
                    .put("id", it.hostId)
                    .put("x", it.x.toDouble())
                    .put("y", it.y.toDouble()),
            )
        }
        Prefs.setWidgets(getApplication(), array)
    }

    private fun readSpots(context: Application): List<Spot> {
        val array = Prefs.widgets(context)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    Spot(
                        obj.optString("key"),
                        obj.optString("kind"),
                        obj.optInt("id"),
                        obj.optDouble("x", 0.3).toFloat(),
                        obj.optDouble("y", 0.2).toFloat(),
                    ),
                )
            }
        }
    }

    private fun watch() {
        if (!DesktopFiles.ready()) return
        observer?.stopWatching()
        val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE
        observer = object : FileObserver(DesktopFiles.ensure(), mask) {
            override fun onEvent(event: Int, path: String?) {
                viewModelScope.launch(Dispatchers.Main) { files = DesktopFiles.list() }
            }
        }
        observer?.startWatching()
    }

    override fun onCleared() {
        observer?.stopWatching()
        super.onCleared()
    }
}
