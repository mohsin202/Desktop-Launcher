package com.android.desktoplauncher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var col: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setDecorFitsSystemWindows(false)
        val sv = ScrollView(this)
        sv.setBackgroundColor(Colors.surfaceSolid(this))
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(20), dp(20), dp(30))
        sv.addView(col)
        setContentView(sv)
        sv.setOnApplyWindowInsetsListener { v, ins ->
            val bars = ins.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            ins
        }
        build()
    }

    override fun onResume() { super.onResume(); build() }

    private fun head(t: String) {
        val v = TextView(this)
        v.text = t
        v.textSize = 13f
        v.setTextColor(Colors.accent(this))
        v.setPadding(0, dp(22), 0, dp(6))
        col.addView(v)
    }

    private fun note(t: String) {
        val v = TextView(this)
        v.text = t
        v.textSize = 12f
        v.alpha = 0.7f
        v.setTextColor(Colors.text(this))
        col.addView(v)
    }

    private fun sw(title: String, key: String, def: Boolean) {
        val s = Switch(this)
        s.text = title
        s.textSize = 16f
        s.setTextColor(Colors.text(this))
        s.isChecked = Prefs.bool(this, key, def)
        s.setPadding(0, dp(10), 0, dp(10))
        s.setOnCheckedChangeListener { _, on -> Prefs.setBool(this, key, on) }
        col.addView(s)
    }

    private fun btn(t: String, a: () -> Unit) {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { a() }
        col.addView(b)
    }

    private fun go(i: Intent) { try { startActivity(i) } catch (_: Exception) { toast("Cannot open") } }

    private fun build() {
        col.removeAllViews()
        val title = TextView(this)
        title.text = "Desktop Launcher"
        title.textSize = 24f
        title.setTextColor(Colors.text(this))
        col.addView(title)

        head("ICONS")
        val rg = RadioGroup(this)
        val names = listOf("Small", "Normal", "Large")
        names.forEachIndexed { i, n ->
            val r = RadioButton(this)
            r.text = n
            r.id = 100 + i
            r.setTextColor(Colors.text(this))
            rg.addView(r)
        }
        rg.check(100 + Prefs.iconMode(this))
        rg.setOnCheckedChangeListener { _, id -> Prefs.setIconMode(this, id - 100) }
        col.addView(rg)
        sw("App drawer themed icons", Prefs.K_THEMED, false)
        note("Uses the wallpaper colours for apps that provide a monochrome icon (Android 13+).")
        sw("Show notification dots", Prefs.K_DOTS, true)
        val notifOn = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        if (!notifOn) btn("Allow notification access (needed for dots)") { go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        head("LAYOUT")
        val lg = RadioGroup(this)
        listOf("Automatic (600 dp or more = desktop taskbar)", "Always phone (dock)", "Always desktop (taskbar)").forEachIndexed { i, n ->
            val r = RadioButton(this)
            r.text = n
            r.id = 200 + i
            r.setTextColor(Colors.text(this))
            lg.addView(r)
        }
        lg.check(200 + Prefs.layoutMode(this))
        lg.setOnCheckedChangeListener { _, id -> Prefs.setLayoutMode(this, id - 200) }
        col.addView(lg)

        head("APPEARANCE")
        sw("Enable UI blur", Prefs.K_BLUR, false)
        note("Blurs the taskbar, app drawer and menus. Needs Android 12+ with window blur enabled in the ROM. When off, solid Material You colours are used.")

        head("TASKBAR / DOCK")
        val sw600 = resources.configuration.smallestScreenWidthDp
        note("Smallest width: $sw600 dp \u2014 " + (if (sw600 >= 600) "tablet mode: the dock is merged into the taskbar (over all apps)."
            else "phone mode: normal dock on the home screen. At 600 dp or more the dock becomes the taskbar."))
        sw("Show taskbar / dock", Prefs.K_TASKBAR, true)
        sw("Show Back / Home / Recents buttons", Prefs.K_NAV, true)
        sw("Show language switch icon while typing", Prefs.K_IME, true)
        sw("Windows key opens the app drawer", Prefs.K_WINKEY, true)
        val h = TextView(this)
        h.setTextColor(Colors.text(this))
        h.text = "Taskbar height: ${Prefs.tbDp(this)} dp"
        col.addView(h)
        val sb = SeekBar(this)
        sb.max = 44
        sb.progress = Prefs.tbDp(this) - 36
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { h.text = "Taskbar height: ${p + 36} dp" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { Prefs.setTbDp(this@SettingsActivity, (s?.progress ?: 12) + 36) }
        })
        col.addView(sb)
        note("Match this to the system navigation bar height so the taskbar covers it exactly.")
        btn("Turn on taskbar (Accessibility settings)") { go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        head("PERMISSIONS")
        btn(if (Environment.isExternalStorageManager()) "All files access: allowed" else "Allow all files access (Desktop folder)") {
            try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
            catch (_: Exception) { go(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
        val c = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        btn(if (c) "Contacts search: allowed" else "Allow contacts (for search)") {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1)
        }
        btn("Set as default home app") { go(Intent(Settings.ACTION_HOME_SETTINGS)) }

        head("DESKTOP")
        btn("Restore removed desktop items") {
            Prefs.setList(this, Prefs.K_HIDDEN, emptyList()); toast("Restored")
        }
        btn("Wallpaper & style") { Launch.wallpapers(this) }
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g); build()
    }
}
