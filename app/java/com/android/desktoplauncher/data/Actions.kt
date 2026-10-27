package com.android.desktoplauncher.data

import android.app.ActivityOptions
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.android.desktoplauncher.DesktopActivity
import java.io.File

data class AppItem(
    val label: String,
    val packageName: String,
    val component: ComponentName,
)

object AppRepository {
    fun load(context: Context): List<AppItem> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map {
                AppItem(
                    it.loadLabel(pm).toString(),
                    it.activityInfo.packageName,
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                )
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.component.flattenToString() }
            .sortedBy { it.label.lowercase() }
    }

    fun find(context: Context, flat: String): AppItem? =
        load(context).firstOrNull { it.component.flattenToString() == flat }

    fun byPackage(context: Context, pkg: String): AppItem? =
        load(context).firstOrNull { it.packageName == pkg }
}

fun themedIcon(icon: Drawable): Drawable {
    if (Build.VERSION.SDK_INT >= 33 && icon is AdaptiveIconDrawable) {
        val mono = icon.monochrome
        if (mono != null) return mono.mutate().apply { setTint(0xFF3D5A80.toInt()) }
    }
    return icon.mutate().apply { setTint(0xFF3D5A80.toInt()) }
}

fun launchApp(context: Context, item: AppItem) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = item.component
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    Prefs.pushRecent(context, item.packageName)
    startFlexible(context, intent)
}

fun startFlexible(context: Context, intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Prefs.windowed(context) && Build.VERSION.SDK_INT >= 24) {
        try {
            // Freeform launch is a system/OEM capability. Avoid referencing hidden
            // android.app.WindowConfiguration APIs at compile time; on builds that
            // expose the method, request windowing mode 5 (FREEFORM) reflectively.
            val options = ActivityOptions.makeBasic()
            val method = options.javaClass.methods.firstOrNull {
                it.name == "setLaunchWindowingMode" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
            }
            if (method != null) {
                method.invoke(options, 5)
                context.startActivity(intent, options.toBundle())
                return
            }
        } catch (_: Throwable) {
        }
    }
    try {
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "Can’t open that", Toast.LENGTH_SHORT).show()
    }
}

fun openWallpaper(context: Context) {
    val tries = listOf(
        Intent().setClassName(
            "com.android.wallpaper",
            "com.android.wallpaper.picker.CustomizationPickerActivity",
        ),
        Intent().setClassName(
            "com.google.android.apps.wallpaper",
            "com.google.android.apps.wallpaper.picker.CustomizationPickerActivity",
        ),
        Intent(Intent.ACTION_SET_WALLPAPER),
    )
    for (intent in tries) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) continue
        try {
            context.startActivity(intent)
            return
        } catch (_: Throwable) {
        }
    }
    Toast.makeText(context, "Wallpaper app not found", Toast.LENGTH_SHORT).show()
}

fun openPath(context: Context, file: File) {
    if (file.isDirectory) {
        openFolder(context, file)
    } else {
        openFile(context, file)
    }
}

fun openFolder(context: Context, dir: File) {
    val root = Environment.getExternalStorageDirectory()
    val relative = runCatching { dir.relativeTo(root).invariantSeparatorsPath }.getOrDefault(dir.name)
    val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$relative")
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No file manager found", Toast.LENGTH_SHORT).show()
    }
}

fun openFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeOf(file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
        Toast.makeText(context, "No app for this file", Toast.LENGTH_SHORT).show()
    }
}

fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeOf(file)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareApp(context: Context, pkg: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=$pkg")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun appInfo(context: Context, pkg: String) {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$pkg")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun uninstall(context: Context, pkg: String) {
    val intent = Intent(Intent.ACTION_DELETE).apply {
        data = Uri.parse("package:$pkg")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun webSearch(context: Context, query: String) {
    val search = Intent(Intent.ACTION_WEB_SEARCH).apply {
        putExtra(SearchManager.QUERY, query)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(search)
    } catch (_: Throwable) {
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun openHome(context: Context, drawer: Boolean) {
    if (drawer) com.android.desktoplauncher.Bus.drawerRequest++
    context.startActivity(
        Intent(context, DesktopActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ),
    )
}

private fun mimeOf(file: File): String {
    val ext = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}
