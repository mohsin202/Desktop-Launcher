package com.android.desktoplauncher.ui

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

import android.Manifest
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.desktoplauncher.Bus
import com.android.desktoplauncher.data.AppItem
import com.android.desktoplauncher.data.DesktopFiles
import com.android.desktoplauncher.data.FileClip
import com.android.desktoplauncher.data.Prefs
import com.android.desktoplauncher.data.appInfo
import com.android.desktoplauncher.data.launchApp
import com.android.desktoplauncher.data.openPath
import com.android.desktoplauncher.data.openWallpaper
import com.android.desktoplauncher.data.shareApp
import com.android.desktoplauncher.data.shareFile
import com.android.desktoplauncher.data.themedIcon
import com.android.desktoplauncher.data.uninstall
import com.android.desktoplauncher.data.webSearch
import com.android.desktoplauncher.taskbar.NavAccessibilityService
import com.android.desktoplauncher.widget.LauncherWidgetHost
import java.io.File
import kotlin.math.roundToInt

private val Ink = Color(0xFF1C1C1C)
private val Panel = Color(0xFFE7EEF8)
private val Menu = Color(0xFFE8EEF6)
private val Taskbar = Color(0xFFD5E2F4)
private val Paper = Color(0xFFF7F9FC)
private val Muted = Color(0xFF5C6570)
private val Hover = Color(0xFFD5DBE3)

private val SettingPages = listOf(
    "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
    "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
    "Display" to Settings.ACTION_DISPLAY_SETTINGS,
    "Sound" to Settings.ACTION_SOUND_SETTINGS,
    "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
    "Battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
    "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
    "Security" to Settings.ACTION_SECURITY_SETTINGS,
    "Accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
    "About phone" to Settings.ACTION_DEVICE_INFO_SETTINGS,
    "Wallpaper & style" to "wallpaper",
)

@Composable
fun HomeScreen(host: LauncherWidgetHost) {
    val model: HomeModel = viewModel()
    val context = LocalContext.current
    val life = LocalLifecycleOwner.current
    val drawerReq = Bus.drawerRequest
    LaunchedEffect(drawerReq) { if (drawerReq > 0) model.drawer = true }
    DisposableEffect(life) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refreshPerms()
        }
        life.lifecycle.addObserver(observer)
        onDispose { life.lifecycle.removeObserver(observer) }
    }
    val bindWidget = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (result.resultCode == android.app.Activity.RESULT_OK && id != -1) model.addHostWidget(id)
        else if (id != -1) host.deleteAppWidgetId(id)
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3D5A80), onPrimary = Paper, surface = Panel, onSurface = Ink)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                model.menu = null
                                model.flyout = null
                                model.drawer = false
                                model.recentsOpen = false
                            },
                            onLongPress = { model.menu = MenuAt(it.x, it.y, "desktop", "") },
                        )
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val down = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                                if (event.buttons.isSecondaryPressed) {
                                    model.menu = MenuAt(down.position.x, down.position.y, "desktop", "")
                                    model.flyout = null
                                    down.consume()
                                }
                            }
                        }
                    },
            ) {
                ColumnWrap(
                    Modifier
                        .statusBarsPadding()
                        .padding(start = 8.dp, top = 8.dp, bottom = 72.dp)
                        .fillMaxSize(),
                ) {
                    model.shortcuts.forEach { flat ->
                        val app = model.apps.firstOrNull { it.component.flattenToString() == flat }
                        if (app != null) {
                            DeskApp(app, model.iconSize, false, model.showDots && Bus.dots.contains(app.packageName)) {
                                launchApp(context, app)
                            }
                        }
                    }
                    model.files.forEach { file ->
                        FileOrb(file, model.iconSize, FileClip.op == "cut" && file.absolutePath in FileClip.paths, model, context)
                    }
                }
                model.spots.forEach { spot ->
                    SpotCard(spot, model, host, widthPx, heightPx)
                }
                if (!model.filesReady) {
                    Text(
                        "Allow all files access so the Desktop folder can store files.",
                        color = Paper,
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
                    )
                }
            }
            if (model.drawer) Drawer(model, context)
            if (model.panel != null) Panel(model, context, host, bindWidget, widthPx, heightPx)
            Taskbar(model, context)
            model.menu?.let { menu -> ContextMenu(menu, model, context) }
            if (model.prompt != null) NameDialog(model)
            if (model.showSetup) Setup(model, context)
            model.toast?.let {
                Surface(
                    color = Ink,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
                ) { Text(it, color = Paper, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
            }
            if (model.ghost != null) {
                Surface(
                    modifier = Modifier.offset { IntOffset(model.ghostX.roundToInt(), model.ghostY.roundToInt()) },
                    shape = RoundedCornerShape(16.dp),
                    color = Paper,
                ) { Text(model.ghost ?: "", modifier = Modifier.padding(12.dp), color = Ink) }
            }
        }
    }
}

@Composable
private fun FileOrb(file: File, sizeName: String, cut: Boolean, model: HomeModel, context: Context) {
    val size = iconSizeDp(sizeName)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(size + 16.dp)
            .combinedClickable(
                onClick = { openPath(context, file) },
                onLongClick = { model.menu = MenuAt(0f, 120f, "file", file.absolutePath) },
            ),
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (cut) Paper.copy(alpha = 0.45f) else Paper),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (file.isDirectory) "Dir" else file.extension.ifBlank { "File" }.take(4), color = Ink, fontSize = 11.sp)
        }
        Text(file.name, color = Paper, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DeskApp(app: AppItem, sizeName: String, themed: Boolean, dot: Boolean, onOpen: () -> Unit) {
    val model: HomeModel = viewModel()
    val size = iconSizeDp(sizeName)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(size + 16.dp)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { model.menu = MenuAt(0f, 80f, "shortcut", app.component.flattenToString()) },
            ),
    ) {
        Box {
            AppDisc(app, size, themed)
            if (dot) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(Color(0xFFD64545)))
        }
        Text(app.label, color = Paper, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppDisc(app: AppItem, size: Dp, themed: Boolean) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.size(size).clip(CircleShape).background(Paper),
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            val raw = runCatching { context.packageManager.getActivityIcon(app.component) }.getOrNull()
            view.setImageDrawable(if (raw != null && themed) themedIcon(raw) else raw)
            view.setPadding((size.value * 0.2f).toInt(), (size.value * 0.2f).toInt(), (size.value * 0.2f).toInt(), (size.value * 0.2f).toInt())
        },
    )
}

@Composable
private fun BoxScope.Drawer(model: HomeModel, context: Context) {
    val hits = remember(model.query, model.apps, model.files) {
        searchHits(context, model.query, model.apps, model.files)
    }
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = 12.dp, bottom = 68.dp)
            .width(320.dp)
            .height(460.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Panel)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(12.dp),
    ) {
        BasicTextField(
            value = model.query,
            onValueChange = { model.query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Ink),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Paper)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (model.query.isEmpty()) Text("Search", color = Ink)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        if (model.query.isBlank()) {
            LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), modifier = Modifier.weight(1f)) {
                items(model.apps, key = { it.component.flattenToString() }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .combinedClickable(
                                onClick = { launchApp(context, app); model.drawer = false },
                                onLongClick = {
                                    model.menu = MenuAt(40f, 200f, "drawer", app.component.flattenToString())
                                },
                            ),
                    ) {
                        Box {
                            AppDisc(app, 52.dp, model.themed)
                            if (model.showDots && Bus.dots.contains(app.packageName)) {
                                Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(Color(0xFFD64545)))
                            }
                        }
                        Text(app.label, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                hits.forEach { hit ->
                    Text(
                        hit.title,
                        color = Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openHit(context, model, hit) }
                            .padding(vertical = 8.dp),
                    )
                    Text(hit.subtitle, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun Modifier.alignInParent(): Modifier = this

@Composable
private fun BoxScope.Taskbar(model: HomeModel, context: Context) {
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(56.dp)
            .background(Taskbar)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton("All") { model.drawer = !model.drawer }
        model.pins.take(6).forEach { pkg ->
            val app = model.apps.firstOrNull { it.packageName == pkg } ?: return@forEach
            Box(Modifier.size(44.dp).clickable { launchApp(context, app) }, contentAlignment = Alignment.Center) {
                AppDisc(app, 28.dp, false)
            }
        }
        BarButton("Files") {
            if (model.filesReady) openPath(context, DesktopFiles.ensure())
            else model.message("Allow all files access")
        }
        Spacer(Modifier.weight(1f))
        BarButton("Back") { goBack(model) }
        BarButton("Home") {
            model.drawer = false
            model.menu = null
            model.panel = null
            model.recentsOpen = false
            NavAccessibilityService.home()
        }
        BarButton("Recent") { model.recentsOpen = !model.recentsOpen }
    }
    if (model.recentsOpen) {
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 60.dp)
                .width(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Menu)
                .padding(6.dp),
        ) {
            val recents = Prefs.recents(context)
            if (recents.isEmpty()) Text("Nothing recent yet", color = Ink, modifier = Modifier.padding(8.dp))
            recents.forEach { pkg ->
                val app = model.apps.firstOrNull { it.packageName == pkg } ?: return@forEach
                MenuRow(app.label, true) {
                    model.recentsOpen = false
                    launchApp(context, app)
                }
            }
            MenuRow("System recents", true) {
                model.recentsOpen = false
                if (!NavAccessibilityService.recents()) {
                    Toast.makeText(context, "Turn on accessibility for Recents", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

private fun goBack(model: HomeModel) {
    Text(label, color = Ink, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp))
}

@Composable
private fun ContextMenu(menu: MenuAt, model: HomeModel, context: Context) {
    Column(
        Modifier
            .offset { IntOffset(menu.x.roundToInt().coerceAtLeast(8), menu.y.roundToInt().coerceAtLeast(8)) }
            .width(196.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Menu)
            .padding(4.dp),
    ) {
        when (menu.kind) {
            "desktop" -> DesktopMenu(model, context)
            "file" -> FileMenu(menu.arg, model, context)
            "shortcut" -> ShortcutMenu(menu.arg, model, context)
            "drawer" -> DrawerMenu(menu.arg, model, context)
            "widget" -> MenuRow("Remove", true) { model.removeSpot(menu.arg) }
        }
    }
}

@Composable
private fun DesktopMenu(model: HomeModel, context: Context) {
    MenuRow("View", true) { model.flyout = if (model.flyout == "view") null else "view" }
    if (model.flyout == "view") {
        listOf("small" to "Small", "normal" to "Normal", "large" to "Large").forEach { (id, label) ->
            MenuRow(label, true) { model.setSize(id) }
        }
    }
    val path = model.selected
    val fileOk = path != null && File(path).exists()
    MenuRow("Cut", fileOk) { if (path != null) model.cutPath(path) }
    MenuRow("Copy", fileOk) { if (path != null) model.copyPath(path) }
    MenuRow("Paste", FileClip.active || clipboardHasUri(context)) { model.paste() }
    MenuRow("New", true) { model.flyout = if (model.flyout == "new") null else "new" }
    if (model.flyout == "new") {
        MenuRow("File", true) { model.prompt = "file"; model.menu = null }
        MenuRow("Folder", true) { model.prompt = "folder"; model.menu = null }
    }
    MenuRow("Wallpapers", true) { model.menu = null; openWallpaper(context) }
    MenuRow("Widgets", true) { model.menu = null; model.panel = "widgets" }
    MenuRow("Settings", true) { model.menu = null; model.panel = "settings" }
}

@Composable
private fun FileMenu(path: String, model: HomeModel, context: Context) {
    val file = File(path)
    MenuRow("Open", true) { model.menu = null; openPath(context, file) }
    MenuRow("Share", file.isFile) { model.menu = null; shareFile(context, file) }
    MenuRow("Remove", true) { model.removePath(path) }
    MenuRow("Delete", true) { model.deletePath(path) }
}

@Composable
private fun ShortcutMenu(flat: String, model: HomeModel, context: Context) {
    val app = model.apps.firstOrNull { it.component.flattenToString() == flat } ?: return
    MenuRow("Open", true) { model.menu = null; launchApp(context, app) }
    MenuRow("App Info", true) { model.menu = null; appInfo(context, app.packageName) }
    MenuRow("Share", true) { model.menu = null; shareApp(context, app.packageName) }
    MenuRow("Remove", true) { model.removeShortcut(flat) }
    MenuRow("Uninstall", true) { model.menu = null; uninstall(context, app.packageName) }
}

@Composable
private fun DrawerMenu(flat: String, model: HomeModel, context: Context) {
    val app = model.apps.firstOrNull { it.component.flattenToString() == flat } ?: return
    val pinned = app.packageName in model.pins
    MenuRow("Open", true) { model.menu = null; model.drawer = false; launchApp(context, app) }
    MenuRow("App Info", true) { model.menu = null; appInfo(context, app.packageName) }
    MenuRow("Share", true) { model.menu = null; shareApp(context, app.packageName) }
    MenuRow(if (pinned) "Unpin from Taskbar" else "Pin to Taskbar", true) { model.togglePin(app.packageName) }
    MenuRow("Add to Desktop", true) { model.addShortcut(flat) }
    MenuRow("Uninstall", true) { model.menu = null; uninstall(context, app.packageName) }
}

@Composable
private fun MenuRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) Ink else Ink.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun NameDialog(model: HomeModel) {
    var name by remember(model.prompt) { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0x66202838)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(280.dp).clip(RoundedCornerShape(16.dp)).background(Menu).padding(16.dp),
        ) {
            Text(if (model.prompt == "folder") "New folder" else "New file", color = Ink)
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Paper).padding(10.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { model.prompt = null }) { Text("Cancel") }
                TextButton(onClick = { model.create(model.prompt ?: "file", name); model.prompt = null }) { Text("Create") }
            }
        }
    }
}

@Composable
private fun Panel(
    model: HomeModel,
    context: Context,
    host: LauncherWidgetHost,
    bindWidget: androidx.activity.result.ActivityResultLauncher<Intent>,
    widthPx: Float,
    heightPx: Float,
) {
    Column(
        Modifier
            .padding(24.dp)
            .statusBarsPadding()
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (model.panel == "widgets") "Widgets" else "Settings", color = Ink, fontSize = 18.sp)
            TextButton(onClick = { model.panel = null }) { Text("Close") }
        }
        if (model.panel == "settings") SettingsBlock(model, context) else WidgetBlock(model, context, host, bindWidget, widthPx, heightPx)
    }
}

@Composable
private fun SettingsBlock(model: HomeModel, context: Context) {
    ToggleRow("App drawer themed icons", "Wallpaper & style only themes the home screen.", model.themed) { model.toggleThemed() }
    ToggleRow("Show notification dots", "Dot on apps that have a notification.", model.showDots) { model.toggleDots() }
    ToggleRow("Window mode", "Ask the system to open apps as freeform windows. The taskbar stays either way.", model.windowed) { model.toggleWindowed() }
    ToggleRow("Taskbar over other apps", "Keeps the taskbar on screen after you leave home.", model.taskbarOn) { model.toggleTaskbar() }
    Text("Icon size", color = Ink, modifier = Modifier.padding(top = 8.dp))
    Row {
        listOf("small" to "Small", "normal" to "Normal", "large" to "Large").forEach { (id, label) ->
            TextButton(onClick = { model.setSize(id) }) { Text(if (model.iconSize == id) "• $label" else label) }
        }
    }
    Text(
        "The taskbar uses the navigation area. Back, Home and Recents need the accessibility service. A normal app cannot replace the system bar the way the built-in Launcher3 does.",
        color = Muted,
        modifier = Modifier.padding(top = 8.dp),
    )
    TextButton(onClick = { model.showSetup = true }) { Text("Permissions") }
    TextButton(onClick = { openWallpaper(context) }) { Text("Wallpaper & style") }
}

@Composable
private fun ToggleRow(title: String, body: String, on: Boolean, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink)
            Text(body, color = Muted, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = { toggle() })
    }
}

@Composable
private fun WidgetBlock(
    model: HomeModel,
    context: Context,
    host: LauncherWidgetHost,
    bindWidget: androidx.activity.result.ActivityResultLauncher<Intent>,
    widthPx: Float,
    heightPx: Float,
) {
    Text("Tap Add, or press and drag onto the desktop.", color = Muted)
    listOf("clock" to "Clock", "notes" to "Notes", "weather" to "Weather", "calendar" to "Calendar").forEach { (kind, label) ->
        var originX by remember { mutableStateOf(0f) }
        var originY by remember { mutableStateOf(0f) }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .pointerInput(kind) {
                    var x = 0f
                    var y = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            x = originX + start.x
                            y = originY + start.y
                            model.ghost = label
                            model.ghostX = x
                            model.ghostY = y
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            x += drag.x
                            y += drag.y
                            model.ghostX = x
                            model.ghostY = y
                        },
                        onDragEnd = {
                            model.ghost = null
                            model.addBuiltin(kind, x / widthPx, y / heightPx)
                        },
                        onDragCancel = { model.ghost = null },
                    )
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = { model.addBuiltin(kind, 0.42f, 0.18f) }) { Text("Add") }
        }
    }
    val providers = remember { AppWidgetManager.getInstance(context).installedProviders.take(24) }
    providers.forEach { info ->
        TextButton(onClick = {
            val id = host.allocateAppWidgetId()
            val manager = AppWidgetManager.getInstance(context)
            if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
                model.addHostWidget(id)
            } else {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                }
                bindWidget.launch(intent)
            }
        }) { Text(info.loadLabel(context.packageManager), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun SpotCard(spot: Spot, model: HomeModel, host: LauncherWidgetHost, widthPx: Float, heightPx: Float) {
    val context = LocalContext.current
    Box(
        Modifier
            .offset { IntOffset((spot.x * widthPx).roundToInt(), (spot.y * heightPx).roundToInt()) }
            .width(168.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Paper.copy(alpha = 0.92f))
            .pointerInput(spot.key) {
                var x = spot.x
                var y = spot.y
                detectDragGestures(
                    onDragStart = {
                        x = spot.x
                        y = spot.y
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        x = (x + drag.x / widthPx).coerceIn(0.02f, 0.72f)
                        y = (y + drag.y / heightPx).coerceIn(0.04f, 0.7f)
                        model.moveSpot(spot.key, x, y)
                    },
                )
            }
            .combinedClickable(onClick = {}, onLongClick = { model.menu = MenuAt(spot.x * widthPx, spot.y * heightPx, "widget", spot.key) })
            .padding(12.dp),
    ) {
        Column {
            when (spot.kind) {
                "clock" -> ClockText()
                "notes" -> Text("Notes", color = Ink)
                "weather" -> Text("24°  Clear", color = Ink, fontSize = 22.sp)
                "calendar" -> Text("Calendar", color = Ink)
                "host" -> HostWidget(host, spot.hostId)
                else -> Text(spot.kind, color = Ink)
            }
        }
    }
}

@Composable
private fun ClockText() {
    var now by remember { mutableStateOf(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
            kotlinx.coroutines.delay(1000)
        }
    }
    Text(now, color = Ink, fontSize = 28.sp)
}

@Composable
private fun HostWidget(host: LauncherWidgetHost, id: Int) {
    val context = LocalContext.current
    val info = remember(id) { AppWidgetManager.getInstance(context).getAppWidgetInfo(id) }
    if (info == null) {
        Text("Widget", color = Ink)
        return
    }
    AndroidView(
        modifier = Modifier.size(150.dp, 100.dp),
        factory = { ctx -> host.createView(ctx, id, info).apply { setAppWidget(id, info) } },
    )
}

@Composable
private fun Setup(model: HomeModel, context: Context) {
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(
        Modifier
            .padding(16.dp)
            .statusBarsPadding()
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Menu)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Desktop Launcher", color = Ink, fontSize = 20.sp)
        Text(
            "Taskbar یوں نیویگیشن والے حصے پر رہتی ہے اور دوسری ایپ کھولنے پر غائب نہیں ہوتی۔ Back، Home، Recents کے لیے accessibility آن کریں۔",
            color = Ink,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        TextButton(onClick = {
            val rm = context.getSystemService(RoleManager::class.java)
            if (!rm.isRoleHeld(RoleManager.ROLE_HOME)) role.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }) { Text("Set as home") }
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Display over other apps") }
        TextButton(onClick = {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }) { Text("All files access (Desktop folder)") }
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Accessibility") }
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Notification dots") }
        TextButton(onClick = { contacts.launch(Manifest.permission.READ_CONTACTS) }) { Text("Contacts search") }
        TextButton(onClick = { notes.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Taskbar notification") }
        TextButton(onClick = { model.finishSetup() }) { Text("Continue") }
    }
}

@Composable
private fun ColumnWrap(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val maxH = constraints.maxHeight
        var x = 0
        var y = 0
        var colW = 0
        val gap = 8
        val pos = Array(placeables.size) { IntOffset.Zero }
        placeables.forEachIndexed { i, placeable ->
            if (y > 0 && y + placeable.height > maxH) {
                x += colW + gap
                y = 0
                colW = 0
            }
            pos[i] = IntOffset(x, y)
            y += placeable.height + gap
            colW = maxOf(colW, placeable.width)
        }
        layout(constraints.maxWidth, maxH) {
            placeables.forEachIndexed { i, placeable -> placeable.place(pos[i].x, pos[i].y) }
        }
    }
}

private fun iconSizeDp(name: String): Dp = when (name) {
    "small" -> 44.dp
    "large" -> 72.dp
    else -> 56.dp
}

private fun goBack(model: HomeModel) {
    when {
        model.prompt != null -> model.prompt = null
        model.panel != null -> model.panel = null
        model.menu != null -> model.menu = null
        model.recentsOpen -> model.recentsOpen = false
        model.drawer -> model.drawer = false
        else -> NavAccessibilityService.back()
    }
}

private fun clipboardHasUri(context: Context): Boolean {
    val clip = context.getSystemService(android.content.ClipboardManager::class.java).primaryClip ?: return false
    return (0 until clip.itemCount).any { clip.getItemAt(it).uri != null }
}

private data class Hit(val title: String, val subtitle: String, val kind: String, val value: String)

private fun searchHits(context: Context, query: String, apps: List<AppItem>, files: List<File>): List<Hit> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    val hits = mutableListOf<Hit>()
    apps.filter { it.label.lowercase().contains(needle) }.take(8).forEach {
        hits += Hit(it.label, it.packageName, "app", it.component.flattenToString())
    }
    SettingPages.filter { it.first.lowercase().contains(needle) }.forEach {
        hits += Hit(it.first, "Settings", "setting", it.second)
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
                arrayOf("%$needle%"),
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                var count = 0
                while (cursor.moveToNext() && count < 6) {
                    hits += Hit(cursor.getString(nameCol) ?: "Contact", "Contact", "contact", cursor.getString(idCol) ?: "")
                    count++
                }
            }
        }
    }
    files.filter { it.name.lowercase().contains(needle) }.take(8).forEach {
        hits += Hit(it.name, "Desktop", "File", it.absolutePath)
    }
    hits += Hit("Search the web for “${query.trim()}”", "Browser", "web", query.trim())
    return hits
}

private fun openHit(context: Context, model: HomeModel, hit: Hit) {
    model.drawer = false
    model.query = ""
    when (hit.kind) {
        "app" -> model.apps.firstOrNull { it.component.flattenToString() == hit.value }?.let { launchApp(context, it) }
        "setting" -> if (hit.value == "wallpaper") openWallpaper(context) else context.startActivity(Intent(hit.value).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "contact" -> context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, hit.value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        "file" -> openPath(context, File(hit.value))
        "web" -> webSearch(context, hit.value)
    }
}

@Suppress("unused")
private val keepConstraints = Constraints
