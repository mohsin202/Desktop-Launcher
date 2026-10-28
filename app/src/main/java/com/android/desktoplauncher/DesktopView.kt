package com.android.desktoplauncher

import android.app.AlertDialog
import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

sealed class Entry {
    abstract val key: String
    class AppE(val info: AppInfo) : Entry() { override val key = "app:" + info.comp.flattenToString() }
    class FileE(val file: File) : Entry() { override val key = "file:" + file.absolutePath }
}

/** The "Desktop": shows the contents of the Desktop folder, app shortcuts and widgets. */
class DesktopView(private val act: LauncherActivity) : FrameLayout(act) {
    private val items = ArrayList<Pair<Entry, View>>()
    private val widgets = ArrayList<WidgetBox>()
    private var selectedKey: String? = null
    private var lastX = 0
    private var lastY = 0

    init {
        UiText.applyDirection(this, act)
        isClickable = true
        isLongClickable = true
        setOnClickListener { clearSelection(); act.closeDrawer() }
        setOnLongClickListener { desktopMenu(lastX, lastY); true }
        setSecondaryClick { x, y -> desktopMenu(x, y) }
        setOnDragListener { _, event ->
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> event.clipDescription != null
                android.view.DragEvent.ACTION_DROP -> {
                    val clipData = event.clipData
                    var copied = 0
                    val permissions = try { act.requestDragAndDropPermissions(event) } catch (_: Exception) { null }
                    if (clipData != null) Thread {
                        try {
                            for (i in 0 until clipData.itemCount) {
                                val item = clipData.getItemAt(i)
                                val uri = item.uri ?: item.intent?.data ?: continue
                                try {
                                    var name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Dropped file"
                                    try {
                                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                                            if (it.moveToFirst()) name = it.getString(0)?.takeIf { n -> n.isNotBlank() } ?: name
                                        }
                                    } catch (_: Exception) {}
                                    val out = Files.unique(Files.root(context), name)
                                    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                                    if (out.exists()) copied++
                                } catch (_: Exception) {}
                            }
                        } finally { try { permissions?.release() } catch (_: Exception) {} }
                        post { reload(); context.toast(if (copied > 0) "Dropped ${copied} item(s)" else "Cannot drop these items here") }
                    }.start() else try { permissions?.release() } catch (_: Exception) {}
                    true
                }
                else -> true
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) { lastX = ev.rawX.toInt(); lastY = ev.rawY.toInt() }
        return super.dispatchTouchEvent(ev)
    }

    private fun d(v: Int) = context.dp(v)

    fun clearSelection() { selectedKey = null; refreshSelection() }

    private fun refreshSelection() {
        for ((e, v) in items) {
            v.background = if (e.key == selectedKey) Colors.rounded(0x55FFFFFF, d(8).toFloat()) else null
        }
    }

    private fun selectedFile(): File? =
        items.firstOrNull { it.first.key == selectedKey }?.first.let { (it as? Entry.FileE)?.file }

    fun reload() {
        val c = context
        removeAllViews(); items.clear(); widgets.clear()
        val hidden = Prefs.list(c, Prefs.K_HIDDEN).toSet()
        val list = ArrayList<Entry>()
        for (s in Prefs.list(c, Prefs.K_DESKAPPS)) {
            val cn = ComponentName.unflattenFromString(s) ?: continue
            AppRepo.find(c, cn)?.let { list += Entry.AppE(it) }
        }
        try {
            Files.root(c).listFiles()
                ?.filter { !it.name.startsWith(".") && it.absolutePath !in hidden }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.forEach { list += Entry.FileE(it) }
        } catch (_: Exception) {}
        for (e in list) {
            val v = makeItem(e)
            addView(v)
            items += e to v
        }
        for (s in Prefs.list(c, Prefs.K_WIDGETS)) {
            val p = s.split(",")
            if (p.size < 5) continue
            val id = p[0].toIntOrNull() ?: continue
            val info = act.awm.getAppWidgetInfo(id)
            if (info == null) { act.host.deleteAppWidgetId(id); continue }
            addWidgetView(id, info, p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt())
        }
        refreshSelection()
        requestLayout()
    }

    private fun addWidgetView(id: Int, info: android.appwidget.AppWidgetProviderInfo, x: Int, y: Int, w: Int, h: Int) {
        val hv: AppWidgetHostView = act.host.createView(act, id, info)
        val box = WidgetBox(act, id, hv, x, y, w, h, this)
        addView(box)
        widgets += box
        box.pushSize()
    }

    fun addWidget(id: Int, info: android.appwidget.AppWidgetProviderInfo, screenX: Int, screenY: Int) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val w = maxOf(info.minWidth, d(160))
        val h = maxOf(info.minHeight, d(100))
        val x = (screenX - loc[0]).coerceIn(0, maxOf(0, width - w))
        val y = (screenY - loc[1]).coerceIn(0, maxOf(0, height - h))
        addWidgetView(id, info, x, y, w, h)
        saveWidgets()
        requestLayout()
    }

    fun saveWidgets() {
        Prefs.setList(context, Prefs.K_WIDGETS, widgets.map { "${it.wid},${it.px},${it.py},${it.w},${it.h}" }, fire = false)
    }

    fun removeWidget(box: WidgetBox) {
        act.host.deleteAppWidgetId(box.wid)
        removeView(box)
        widgets.remove(box)
        saveWidgets()
    }

    private fun cell(): Pair<Int, Int> {
        val s = d(Prefs.iconDp(context))
        return Pair(s + d(36), s + d(50))
    }

    private var gCols = 1
    private var gRows = 1
    private var gCw = 1
    private var gCh = 1
    private val cells = HashMap<String, IntArray>()

    private fun computeGrid(w: Int, h: Int) {
        val (cw0, ch) = cell()
        val availW = w - paddingLeft - paddingRight - d(8)
        val avail = h - paddingTop - paddingBottom
        gRows = maxOf(1, avail / ch)
        gCols = maxOf(1, availW / cw0)
        // Phone: columns share the width evenly. Desktop: fixed-width columns on the left.
        gCw = if (Modes.tablet(context)) cw0 else maxOf(cw0, availW / gCols)
        gCh = ch
    }

    private fun posTag() = if (Modes.tablet(context)) "t" else "p"

    private fun storedPos(): HashMap<String, IntArray> {
        val m = HashMap<String, IntArray>()
        val tag = posTag()
        for (s in Prefs.list(context, Prefs.K_POS)) {
            if (s.length < 4 || s.substring(0, 2) != "$tag@") continue
            val i2 = s.lastIndexOf('@')
            val i1 = s.lastIndexOf('@', i2 - 1)
            if (i1 < 2) continue
            val col = s.substring(i1 + 1, i2).toIntOrNull() ?: continue
            val row = s.substring(i2 + 1).toIntOrNull() ?: continue
            m[s.substring(2, i1)] = intArrayOf(col, row)
        }
        return m
    }

    private fun savePos(updates: Map<String, IntArray>) {
        val tag = posTag()
        val keep = Prefs.list(context, Prefs.K_POS).filter { s ->
            if (s.length < 4 || s.substring(0, 2) != "$tag@") true else {
                val i2 = s.lastIndexOf('@'); val i1 = s.lastIndexOf('@', i2 - 1)
                !(i1 >= 2 && updates.containsKey(s.substring(2, i1)))
            }
        }
        Prefs.setList(context, Prefs.K_POS, keep + updates.map { "$tag@${it.key}@${it.value[0]}@${it.value[1]}" }, fire = false)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        computeGrid(w, h)
        for ((_, v) in items) v.measure(MeasureSpec.makeMeasureSpec(gCw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(gCh, MeasureSpec.EXACTLY))
        for (b in widgets) b.measure(MeasureSpec.makeMeasureSpec(b.w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(b.h, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        computeGrid(r - l, b - t)
        val tablet = Modes.tablet(context)
        cells.clear()
        val occ = HashSet<Int>()
        val stored = storedPos()
        // 1) icons the user placed freely
        for ((e, _) in items) {
            val p = stored[e.key] ?: continue
            if (p[0] in 0 until gCols && p[1] in 0 until gRows && !blockedByWidget(p[0], p[1]) && occ.add(p[0] * 10000 + p[1])) cells[e.key] = p
        }
        // 2) everything else flows automatically: desktop = columns (top to bottom), phone = rows (left to right)
        var i = 0
        for ((e, _) in items) {
            if (cells.containsKey(e.key)) continue
            var cell: IntArray? = null
            while (i < gCols * gRows && cell == null) {
                val k = i++
                val col = if (tablet) k / gRows else k % gCols
                val row = if (tablet) k % gRows else k / gCols
                if (!blockedByWidget(col, row) && occ.add(col * 10000 + row)) cell = intArrayOf(col, row)
            }
            cells[e.key] = cell ?: intArrayOf(0, 0)
        }
        for ((e, v) in items) {
            val c = cells[e.key] ?: continue
            val left = paddingLeft + d(8) + c[0] * gCw
            val top = paddingTop + c[1] * gCh
            v.layout(left, top, left + gCw, top + gCh)
        }
        for (box in widgets) box.layout(box.px, box.py, box.px + box.w, box.py + box.h)
    }

    /** Called when an icon is dropped after dragging. */
    private fun dropItem(e: Entry, v: View, dx: Float, dy: Float) {
        v.translationX = 0f; v.translationY = 0f; v.elevation = 0f
        val cx = v.left + v.width / 2f + dx
        val cy = v.top + v.height / 2f + dy
        val col = ((cx - paddingLeft - d(8)) / gCw).toInt().coerceIn(0, gCols - 1)
        val row = ((cy - paddingTop) / gCh).toInt().coerceIn(0, gRows - 1)
        val old = cells[e.key] ?: return
        val updates = HashMap<String, IntArray>()
        updates[e.key] = intArrayOf(col, row)
        // If another icon sits there it takes the old cell (swap).
        cells.entries.firstOrNull { it.key != e.key && it.value[0] == col && it.value[1] == row }?.let {
            updates[it.key] = intArrayOf(old[0], old[1])
        }
        savePos(updates)
        requestLayout()
    }

    private inner class ItemTouch(val e: Entry, val v: View) : View.OnTouchListener {
        private var state = 0 // 0 idle, 1 pressed, 2 dragging
        private var x0 = 0f
        private var y0 = 0f
        private var moved = false
        private var secondary = false
        private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        private val longPress = Runnable {
            if (state == 1) {
                state = 2
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                v.elevation = d(8).toFloat()
                v.bringToFront()
            }
        }

        override fun onTouch(view: View, ev: MotionEvent): Boolean {
            val mouse = ev.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    secondary = (ev.buttonState and MotionEvent.BUTTON_SECONDARY) != 0
                    if (secondary) { itemMenu(e, ev.rawX.toInt(), ev.rawY.toInt()); return true }
                    x0 = ev.rawX; y0 = ev.rawY; moved = false; state = 1
                    lastX = ev.rawX.toInt(); lastY = ev.rawY.toInt()
                    if (!mouse) v.postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (secondary) return true
                    val dx = ev.rawX - x0
                    val dy = ev.rawY - y0
                    val far = Math.hypot(dx.toDouble(), dy.toDouble()) > slop
                    if (state == 1 && far) {
                        if (mouse) { state = 2; v.elevation = d(8).toFloat(); v.bringToFront() }
                        else { v.removeCallbacks(longPress); state = 0 }
                    }
                    if (state == 2) {
                        if (far) moved = true
                        v.translationX = dx; v.translationY = dy
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    if (secondary) { secondary = false; return true }
                    val dx = ev.rawX - x0
                    val dy = ev.rawY - y0
                    val st = state
                    state = 0
                    when (st) {
                        1 -> { clearSelection(); open(e) }
                        2 -> if (moved) dropItem(e, v, dx, dy) else {
                            v.translationX = 0f; v.translationY = 0f; v.elevation = 0f
                            itemMenu(e, ev.rawX.toInt(), ev.rawY.toInt())
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPress)
                    state = 0; secondary = false
                    v.translationX = 0f; v.translationY = 0f; v.elevation = 0f
                    return true
                }
            }
            return true
        }
    }

    private fun makeItem(e: Entry): View {
        val c = context
        val v = LinearLayout(c)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER_HORIZONTAL
        v.setPadding(d(4), d(6), d(4), d(4))
        val sz = d(Prefs.iconDp(c))
        val ic = BadgedIcon(c)
        val label: String
        when (e) {
            is Entry.AppE -> {
                ic.setImageDrawable(Icons.app(c, e.info.comp, false))
                ic.dot = Prefs.bool(c, Prefs.K_DOTS, true) && NotifState.has(e.info.pkg)
                label = e.info.label
            }
            is Entry.FileE -> {
                ic.setImageDrawable(Icons.file(e.file))
                label = e.file.name
                if (Clip.cut && Clip.files.contains(e.file)) ic.alpha = 0.45f
            }
        }
        v.addView(ic, LinearLayout.LayoutParams(sz, sz))
        val t = TextView(c)
        t.text = label
        t.textSize = 12f
        t.setTextColor(Color.WHITE)
        t.setShadowLayer(4f, 0f, 1f, Color.BLACK)
        t.maxLines = 2
        t.gravity = Gravity.CENTER
        t.ellipsize = android.text.TextUtils.TruncateAt.END
        v.addView(t, LinearLayout.LayoutParams(MATCH, WRAP))
        v.setOnTouchListener(ItemTouch(e, v))
        return v
    }

    private fun open(e: Entry) {
        when (e) {
            is Entry.AppE -> Launch.app(context, e.info.comp)
            is Entry.FileE -> openFile(e.file)
        }
    }

    fun openFile(f: File) {
        if (f.extension == "desktoplink") {
            try {
                val targetPath = f.readLines().getOrNull(1)
                val target = targetPath?.let { File(it) }
                if (target != null && target.exists()) Files.open(context, target) else context.toast("Shortcut target not found")
            } catch (_: Exception) { context.toast("Invalid shortcut") }
        } else Files.open(context, f)
    }

    private fun blockedByWidget(col: Int, row: Int): Boolean {
        val left = paddingLeft + d(8) + col * gCw
        val top = paddingTop + row * gCh
        val right = left + gCw
        val bottom = top + gCh
        return widgets.any { b -> left < b.px + b.w && right > b.px && top < b.py + b.h && bottom > b.py }
    }

    // ---------------------------------------------------------------- menus

    private fun desktopMenu(x: Int, y: Int) {
        val c = context
        val sel = selectedFile()
        val hasClip = Clip.hasAny(c)
        val mode = Prefs.iconMode(c)
        val m = PopupMenuX(c, this)
        m.show(x, y, listOf(
            MenuItem("View", children = listOf(
                MenuItem("Small", checked = mode == 0) { Prefs.setIconMode(c, 0) },
                MenuItem("Normal", checked = mode == 1) { Prefs.setIconMode(c, 1) },
                MenuItem("Large", checked = mode == 2) { Prefs.setIconMode(c, 2) }
            )),
            MenuItem("Cut", enabled = sel != null, bold = true) { sel?.let { Clip.set(c, listOf(it), true); reload() } },
            MenuItem("Copy", enabled = sel != null, bold = true) { sel?.let { Clip.set(c, listOf(it), false); c.toast("Copied") } },
            MenuItem("Paste", enabled = hasClip, bold = true) { paste() },
            MenuItem("New", children = listOf(
                MenuItem("File") { newItem(false) },
                MenuItem("Folder") { newItem(true) }
            )),
            MenuItem("Wallpapers") { Launch.wallpapers(c) },
            MenuItem("Widgets") { act.pickWidget(x, y) },
            MenuItem("Settings") { Launch.start(c, android.content.Intent(c, SettingsActivity::class.java)) }
        ))
    }

    private fun itemMenu(e: Entry, x: Int, y: Int) {
        val c = context
        selectedKey = e.key
        refreshSelection()
        val m = PopupMenuX(c, this)
        when (e) {
            is Entry.AppE -> m.show(x, y, listOf(
                MenuItem("Open") { open(e) },
                MenuItem("App Info") { Launch.info(c, e.info.pkg) },
                MenuItem("Share") { Launch.shareApp(c, e.info.pkg) },
                MenuItem("Remove") {
                    Prefs.setList(c, Prefs.K_DESKAPPS, Prefs.list(c, Prefs.K_DESKAPPS).filter { it != e.info.comp.flattenToString() })
                },
                MenuItem("Uninstall") { Launch.uninstall(c, e.info.pkg) }
            ))
            is Entry.FileE -> m.show(x, y, listOf(
                MenuItem("Open") { open(e) },
                MenuItem("Rename") { renameItem(e.file) },
                MenuItem("Cut", bold = true) { Clip.set(c, listOf(e.file), true); reload() },
                MenuItem("Copy", bold = true) { Clip.set(c, listOf(e.file), false); c.toast("Copied") },
                MenuItem("Share", enabled = e.file.isFile) { Launch.shareFile(c, e.file) },
                MenuItem("Remove") {
                    Prefs.setList(c, Prefs.K_HIDDEN, Prefs.list(c, Prefs.K_HIDDEN) + e.file.absolutePath)
                    c.toast("Removed from desktop (file kept)")
                },
                MenuItem("Delete") { confirmDelete(e.file) }
            ))
        }
    }

    private fun renameItem(f: File) {
        val et = EditText(act)
        et.setText(f.name)
        et.selectAll()
        AlertDialog.Builder(act).setTitle("Rename").setView(et)
            .setPositiveButton("Rename") { _, _ ->
                val name = et.text.toString().trim().replace('/', '_')
                if (name.isNotBlank() && name != f.name) {
                    val target = File(f.parentFile, name)
                    if (target.exists()) context.toast("A file with that name already exists")
                    else if (!f.renameTo(target)) context.toast("Rename failed")
                    reload()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(act).setTitle("Delete").setMessage("Delete “${f.name}” permanently?")
            .setPositiveButton("Delete") { _, _ ->
                Thread { f.deleteRecursively(); post { reload() } }.start()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun paste() {
        val c = context
        Thread {
            val n = try { Clip.paste(c, Files.root(c)) } catch (_: Exception) { 0 }
            post { reload(); c.toast(if (n > 0) "Pasted" else "Nothing to paste") }
        }.start()
    }

    private fun newItem(folder: Boolean) {
        val c = context
        val et = EditText(act)
        et.setText(if (folder) "New Folder" else "New File.txt")
        et.selectAll()
        AlertDialog.Builder(act).setTitle(if (folder) "New folder" else "New file").setView(et)
            .setPositiveButton("Create") { _, _ ->
                val name = et.text.toString().trim().replace('/', '_')
                if (name.isNotEmpty()) {
                    try {
                        val f = Files.unique(Files.root(c), name)
                        if (folder) f.mkdirs() else f.createNewFile()
                    } catch (e: Exception) { c.toast("Cannot create: " + e.message) }
                    reload()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    fun widgetMenu(box: WidgetBox, x: Int, y: Int) {
        val m = PopupMenuX(context, this)
        m.show(x, y, listOf(MenuItem("Remove") { removeWidget(box) }))
    }
}

class WidgetBox(
    ctx: Context, val wid: Int, private val hv: AppWidgetHostView,
    var px: Int, var py: Int, var w: Int, var h: Int, private val owner: DesktopView
) : FrameLayout(ctx) {
    private var moving = false
    private var moved = false
    private var resizeMode = false
    private var justActivatedResize = false
    private var resizing = false
    private var resizeCorner = 3
    private var startRawX = 0f
    private var startRawY = 0f
    private var startPx = 0
    private var startPy = 0
    private var startW = 0
    private var startH = 0
    private val edgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Colors.accent(ctx); style = android.graphics.Paint.Style.STROKE; strokeWidth = ctx.dp(2).toFloat()
    }
    private val handlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Colors.accent(ctx); style = android.graphics.Paint.Style.FILL }

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            moving = true; moved = false
            startPx = px; startPy = py; startW = w; startH = h
            if (!resizeMode) { resizeMode = true; justActivatedResize = true; invalidate() }
            else { moving = false; owner.widgetMenu(this@WidgetBox, e.rawX.toInt(), e.rawY.toInt()) }
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    })

    init { addView(hv, LayoutParams(MATCH, MATCH)); setWillNotDraw(false) }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        super.dispatchDraw(canvas)
        if (resizeMode) {
            val inset = context.dp(2).toFloat()
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, context.dp(8).toFloat(), context.dp(8).toFloat(), edgePaint)
            val r = context.dp(5).toFloat()
            val pts = arrayOf(floatArrayOf(inset, inset), floatArrayOf(width - inset, inset), floatArrayOf(inset, height - inset), floatArrayOf(width - inset, height - inset))
            for (pt in pts) canvas.drawCircle(pt[0], pt[1], r, handlePaint)
        }
    }

    fun pushSize() {
        val dm = context.resources.displayMetrics
        val dw = (w / dm.density).toInt()
        val dh = (h / dm.density).toInt()
        try { hv.updateAppWidgetSize(Bundle(), dw, dh, dw, dh) } catch (_: Exception) {}
    }

    fun scale(f: Float) {
        w = (w * f).toInt().coerceAtLeast(context.dp(80))
        h = (h * f).toInt().coerceAtLeast(context.dp(60))
        pushSize(); owner.requestLayout(); owner.saveWidgets()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            startRawX = ev.rawX; startRawY = ev.rawY
            startPx = px; startPy = py; startW = w; startH = h
            if ((ev.buttonState and MotionEvent.BUTTON_SECONDARY) != 0) {
                owner.widgetMenu(this, ev.rawX.toInt(), ev.rawY.toInt()); return true
            }
            val handle = context.dp(24)
            val left = ev.x <= handle
            val right = ev.x >= width - handle
            val top = ev.y <= handle
            val bottom = ev.y >= height - handle
            resizing = resizeMode && (left || right) && (top || bottom)
            if (resizing) {
                resizeCorner = when {
                    left && top -> 0
                    right && top -> 1
                    left && bottom -> 2
                    else -> 3
                }
                moving = true; moved = false; return true
            }
        }
        gd.onTouchEvent(ev)
        return moving
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!moving) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startRawX
                val dy = ev.rawY - startRawY
                if (Math.abs(dx) > context.dp(4) || Math.abs(dy) > context.dp(4)) moved = true
                if (resizing) {
                    val minW = context.dp(100); val minH = context.dp(70)
                    val rawW = when (resizeCorner) { 0, 2 -> startW - dx; else -> startW + dx }
                    val rawH = when (resizeCorner) { 0, 1 -> startH - dy; else -> startH + dy }
                    w = rawW.toInt().coerceAtLeast(minW)
                    h = rawH.toInt().coerceAtLeast(minH)
                    if (resizeCorner == 0 || resizeCorner == 2) px = (startPx + startW - w).toInt().coerceAtLeast(0)
                    if (resizeCorner == 0 || resizeCorner == 1) py = (startPy + startH - h).toInt().coerceAtLeast(0)
                    w = minOf(w, maxOf(minW, owner.width - px))
                    h = minOf(h, maxOf(minH, owner.height - py))
                    pushSize(); owner.requestLayout(); invalidate()
                } else {
                    px = (startPx + dx).toInt().coerceIn(0, maxOf(0, owner.width - w))
                    py = (startPy + dy).toInt().coerceIn(0, maxOf(0, owner.height - h))
                    owner.requestLayout()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moving = false
                if (moved) owner.saveWidgets()
                else if (justActivatedResize) { justActivatedResize = false }
                else if (resizeMode) owner.widgetMenu(this, ev.rawX.toInt(), ev.rawY.toInt())
                justActivatedResize = false
                resizing = false
            }
        }
        return true
    }
}
