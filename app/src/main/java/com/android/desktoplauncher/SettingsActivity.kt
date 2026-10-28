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
        UiText.applyDirection(sv, this)
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

    private fun go(i: Intent) { try { startActivity(i) } catch (_: Exception) { toast(UiText.text(this, "Cannot open")) } }

    private fun build() {
        col.removeAllViews()
        UiText.applyDirection(col, this)
        val title = TextView(this)
        title.text = UiText.text(this, "Desktop Launcher")
        title.textSize = 24f
        title.setTextColor(Colors.text(this))
        col.addView(title)

        head(UiText.text(this, "LANGUAGE & DIRECTION"))
        note(UiText.text(this, "UI language"))
        val languageGroup = RadioGroup(this)
        listOf(UiText.text(this, "English"), UiText.text(this, "Urdu")).forEachIndexed { i, label ->
            val r = RadioButton(this); r.id = 700 + i; r.text = label; r.setTextColor(Colors.text(this)); languageGroup.addView(r)
        }
        languageGroup.check(700 + Prefs.language(this))
        languageGroup.setOnCheckedChangeListener { _, id -> if (id >= 700) Prefs.setLanguage(this, id - 700); build() }
        col.addView(languageGroup)
        note(UiText.text(this, "Layout direction"))
        val directionGroup = RadioGroup(this)
        listOf(UiText.text(this, "System default"), UiText.text(this, "Left to right (LTR)"), UiText.text(this, "Right to left (RTL)")).forEachIndexed { i, label ->
            val r = RadioButton(this); r.id = 710 + i; r.text = label; r.setTextColor(Colors.text(this)); directionGroup.addView(r)
        }
        directionGroup.check(710 + Prefs.direction(this))
        directionGroup.setOnCheckedChangeListener { _, id -> if (id >= 710) Prefs.setDirection(this, id - 710); UiText.applyDirection(col, this) }
        col.addView(directionGroup)
        note(UiText.text(this, "Changes apply immediately where possible; reopen the launcher if an old screen does not update."))

        head(UiText.text(this, "ICONS"))
        val rg = RadioGroup(this)
        val names = listOf(UiText.text(this, "Small"), UiText.text(this, "Normal"), UiText.text(this, "Large"))
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
        sw(UiText.text(this, "App drawer themed icons"), Prefs.K_THEMED, false)
        note(UiText.text(this, "Uses the wallpaper colours for apps that provide a monochrome icon (Android 13+)."))
        sw(UiText.text(this, "Show notification dots"), Prefs.K_DOTS, true)
        val notifOn = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        if (!notifOn) btn(UiText.text(this, "Allow notification access (needed for dots)")) { go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        head(UiText.text(this, "LAYOUT"))
        val lg = RadioGroup(this)
        listOf(UiText.text(this, "Automatic (600 dp or more = desktop taskbar)"), UiText.text(this, "Always phone (dock)"), UiText.text(this, "Always desktop (taskbar)")).forEachIndexed { i, n ->
            val r = RadioButton(this)
            r.text = n
            r.id = 200 + i
            r.setTextColor(Colors.text(this))
            lg.addView(r)
        }
        lg.check(200 + Prefs.layoutMode(this))
        lg.setOnCheckedChangeListener { _, id -> Prefs.setLayoutMode(this, id - 200) }
        col.addView(lg)

        head(UiText.text(this, "APPEARANCE"))
        sw(UiText.text(this, "Enable UI blur"), Prefs.K_BLUR, false)
        note(UiText.text(this, "Blurs only overlay windows such as the taskbar and menus. When off, solid Material You colours are used."))
        sw(UiText.text(this, "Show keyboard when app drawer opens"), Prefs.K_DRAWER_IME, false)
        note(UiText.text(this, "When off, the app drawer opens without automatically showing the keyboard. Back should hide the keyboard before closing the drawer."))

        head(UiText.text(this, "TASKBAR / DOCK"))
        val sw600 = resources.configuration.smallestScreenWidthDp
        note(UiText.text(this, "Smallest width") + ": $sw600 dp — " + UiText.text(this, if (sw600 >= 600) "tablet mode: the dock is merged into the taskbar (over all apps)." else "phone mode: normal dock on the home screen. At 600 dp or more the dock becomes the taskbar."))
        sw(UiText.text(this, "Show taskbar / dock"), Prefs.K_TASKBAR, true)
        sw(UiText.text(this, "Show Back / Home / Recents buttons"), Prefs.K_NAV, true)
        sw(UiText.text(this, "Show language switch icon while typing"), Prefs.K_IME, true)
        sw(UiText.text(this, "Windows key opens the app drawer"), Prefs.K_WINKEY, true)
        val h = TextView(this)
        h.setTextColor(Colors.text(this))
        h.text = UiText.text(this, "Taskbar height") + ": ${Prefs.tbDp(this)} dp"
        col.addView(h)
        val sb = SeekBar(this)
        sb.max = 44
        sb.progress = Prefs.tbDp(this) - 36
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { h.text = UiText.text(this, "Taskbar height") + ": ${p + 36} dp" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { Prefs.setTbDp(this@SettingsActivity, (s?.progress ?: 12) + 36) }
        })
        col.addView(sb)
        note(UiText.text(this, "Match this to the system navigation bar height so the taskbar covers it exactly."))
        btn(UiText.text(this, "Turn on taskbar (Accessibility settings)")) { go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        head(UiText.text(this, "PERMISSIONS"))
        btn(UiText.text(this, if (Environment.isExternalStorageManager()) "All files access: allowed" else "Allow all files access (Desktop folder)")) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
            catch (_: Exception) { go(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
        val c = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        btn(UiText.text(this, if (c) "Contacts search: allowed" else "Allow contacts (for search)")) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1)
        }
        btn(UiText.text(this, "Set as default home app")) { go(Intent(Settings.ACTION_HOME_SETTINGS)) }

        head(UiText.text(this, "DESKTOP"))
        btn(UiText.text(this, "Restore removed desktop items")) {
            Prefs.setList(this, Prefs.K_HIDDEN, emptyList()); toast(UiText.text(this, "Restored"))
        }
        btn(UiText.text(this, "Wallpaper & style")) { Launch.wallpapers(this) }
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g); build()
    }
}
