package com.android.desktoplauncher

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.android.desktoplauncher.data.DesktopFiles
import com.android.desktoplauncher.taskbar.TaskbarOverlayService
import com.android.desktoplauncher.ui.HomeScreen
import com.android.desktoplauncher.widget.LauncherWidgetHost

class DesktopActivity : ComponentActivity() {
    lateinit var widgetHost: LauncherWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetHost = LauncherWidgetHost(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        importShare(intent)
        setContent { HomeScreen(widgetHost) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importShare(intent)
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        TaskbarOverlayService.hide(this)
    }

    override fun onPause() {
        visible = false
        TaskbarOverlayService.show(this)
        super.onPause()
    }

    override fun onStop() {
        widgetHost.stopListening()
        super.onStop()
    }

    private fun importShare(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return
        val uris = mutableListOf<Uri>()
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
        }
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris += it }
        }
        uris.distinct().forEach { runCatching { DesktopFiles.import(this, it) } }
        setIntent(Intent(this, DesktopActivity::class.java))
    }

    companion object {
        @Volatile
        var visible: Boolean = false
    }
}
