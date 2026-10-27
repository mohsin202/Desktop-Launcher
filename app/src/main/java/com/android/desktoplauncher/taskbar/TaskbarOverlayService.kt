package com.android.desktoplauncher.taskbar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.android.desktoplauncher.DesktopActivity
import com.android.desktoplauncher.R
import com.android.desktoplauncher.data.AppRepository
import com.android.desktoplauncher.data.DesktopFiles
import com.android.desktoplauncher.data.Prefs
import com.android.desktoplauncher.data.launchApp
import com.android.desktoplauncher.data.openFolder
import com.android.desktoplauncher.data.openHome

class TaskbarOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var root: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!becomeForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val show = intent?.action != ACTION_HIDE &&
            !DesktopActivity.visible &&
            Prefs.taskbar(this) &&
            Settings.canDrawOverlays(this)
        if (show) showBar() else hideBar()
        return START_STICKY
    }

    override fun onDestroy() {
        hideBar()
        super.onDestroy()
    }

    private fun becomeForeground(): Boolean = try {
        val channelId = "taskbar"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Taskbar", NotificationManager.IMPORTANCE_MIN),
            )
        }
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Desktop taskbar")
            .setContentText("Stays on screen while you use other apps")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(7, notification)
        }
        true
    } catch (_: Throwable) {
        false
    }

    private fun showBar() {
        if (root != null) return
        val apps = AppRepository.load(this)
        val pins = Prefs.pins(this).ifEmpty { apps.take(4).map { it.packageName } }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val recentsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E8EEF6"))
            visibility = View.GONE
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#D5E2F4"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        bar.addView(label("Apps") { openHome(this, drawer = true) })
        pins.forEach { pkg ->
            val item = apps.firstOrNull { it.packageName == pkg } ?: return@forEach
            val icon = ImageView(this)
            icon.setImageDrawable(runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull())
            icon.setPadding(dp(6), dp(6), dp(6), dp(6))
            icon.setOnClickListener { launchApp(this, item) }
            bar.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        bar.addView(label("Files") {
            if (DesktopFiles.ready()) openFolder(this, DesktopFiles.ensure())
            else Toast.makeText(this, "Allow all files access", Toast.LENGTH_SHORT).show()
        })
        val spacer = View(this)
        bar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(label("‹") {
            if (!NavAccessibilityService.back()) {
                Toast.makeText(this, "Turn on accessibility for Back", Toast.LENGTH_SHORT).show()
            }
        })
        bar.addView(label("●") {
            if (!NavAccessibilityService.home()) openHome(this, false)
        })
        bar.addView(label("□") {
            if (recentsBox.visibility == View.VISIBLE) {
                recentsBox.visibility = View.GONE
            } else {
                recentsBox.removeAllViews()
                val recentPkgs = Prefs.recents(this).ifEmpty { pins }
                if (recentPkgs.isEmpty()) recentsBox.addView(label("Nothing recent") {})
                recentPkgs.forEach { pkg ->
                    val item = apps.firstOrNull { it.packageName == pkg } ?: return@forEach
                    recentsBox.addView(label(item.label) {
                        recentsBox.visibility = View.GONE
                        launchApp(this, item)
                    })
                }
                recentsBox.addView(label("System recents") {
                    recentsBox.visibility = View.GONE
                    if (!NavAccessibilityService.recents()) {
                        Toast.makeText(this, "Turn on accessibility for Recents", Toast.LENGTH_SHORT).show()
                    }
                })
                recentsBox.visibility = View.VISIBLE
            }
        })
        column.addView(recentsBox)
        column.addView(bar)
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.BOTTOM
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.addView(column, params)
        windowManager = wm
        root = column
    }

    private fun label(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#1C1C1C"))
            textSize = 14f
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { onClick() }
        }
    }

    private fun hideBar() {
        val view = root ?: return
        runCatching { windowManager?.removeView(view) }
        root = null
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_SHOW = "show"
        const val ACTION_HIDE = "hide"

        fun show(context: android.content.Context) {
            if (!Prefs.taskbar(context) || !Settings.canDrawOverlays(context)) return
            val intent = Intent(context, TaskbarOverlayService::class.java).setAction(ACTION_SHOW)
            context.startForegroundService(intent)
        }

        fun hide(context: android.content.Context) {
            val intent = Intent(context, TaskbarOverlayService::class.java).setAction(ACTION_HIDE)
            runCatching { context.startService(intent) }
        }
    }
}
