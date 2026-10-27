package com.android.desktoplauncher.data

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import java.io.File

object DesktopFiles {
    fun dir(): File = File(Environment.getExternalStorageDirectory(), "Desktop")

    fun ready(): Boolean = Environment.isExternalStorageManager()

    fun ensure(): File {
        val folder = dir()
        if (ready()) folder.mkdirs()
        return folder
    }

    fun list(): List<File> {
        if (!ready()) return emptyList()
        return ensure().listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }

    fun createFile(name: String): File? {
        if (!ready()) return null
        val clean = sanitize(if (name.contains('.')) name else "$name.txt")
        val file = unique(ensure(), clean)
        file.writeText("")
        return file
    }

    fun createFolder(name: String): File? {
        if (!ready()) return null
        val file = unique(ensure(), sanitize(name))
        file.mkdirs()
        return file
    }

    fun delete(path: String): Boolean {
        val file = File(path)
        if (!file.absolutePath.startsWith(dir().absolutePath)) return false
        return file.deleteRecursively()
    }

    fun removeToDocuments(path: String): Boolean {
        val file = File(path)
        val docs = File(Environment.getExternalStorageDirectory(), "Documents")
        if (!ready()) return false
        docs.mkdirs()
        val dest = unique(docs, file.name)
        if (file.renameTo(dest)) return true
        return runCatching {
            copyTree(file, docs)
            file.deleteRecursively()
        }.isSuccess
    }

    fun paste(context: Context, clipOp: String?, paths: List<String>): Int {
        if (!ready()) return 0
        val dest = ensure()
        if (clipOp != null && paths.isNotEmpty()) {
            var count = 0
            paths.forEach { raw ->
                val src = File(raw)
                if (!src.exists()) return@forEach
                if (clipOp == "cut") {
                    val target = unique(dest, src.name)
                    if (!src.renameTo(target)) {
                        copyTree(src, dest)
                        src.deleteRecursively()
                    }
                } else {
                    copyTree(src, dest)
                }
                count++
            }
            return count
        }
        return pasteClipboard(context, dest)
    }

    fun import(context: Context, uri: Uri) {
        if (!ready()) return
        val name = displayName(context, uri)
        val dest = unique(ensure(), sanitize(name))
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
    }

    private fun pasteClipboard(context: Context, dest: File): Int {
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip ?: return 0
        var count = 0
        for (i in 0 until clip.itemCount) {
            val uri = clip.getItemAt(i).uri ?: continue
            runCatching {
                val name = displayName(context, uri)
                val file = unique(dest, sanitize(name))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                count++
            }
        }
        return count
    }

    private fun copyTree(src: File, destDir: File) {
        if (src.isDirectory) {
            val dir = unique(destDir, src.name)
            dir.mkdirs()
            src.listFiles()?.forEach { child -> copyInto(child, dir) }
        } else {
            src.copyTo(unique(destDir, src.name), overwrite = false)
        }
    }

    private fun copyInto(src: File, destDir: File) {
        if (src.isDirectory) {
            val dir = File(destDir, src.name)
            dir.mkdirs()
            src.listFiles()?.forEach { copyInto(it, dir) }
        } else {
            src.copyTo(File(destDir, src.name), overwrite = true)
        }
    }

    private fun unique(dir: File, name: String): File {
        val clean = sanitize(name).ifBlank { "untitled" }
        var file = File(dir, clean)
        if (!file.exists()) return file
        val dot = clean.lastIndexOf('.')
        val stem = if (dot > 0) clean.substring(0, dot) else clean
        val ext = if (dot > 0) clean.substring(dot) else ""
        var i = 2
        while (File(dir, "$stem ($i)$ext").also { file = it }.exists()) i++
        return file
    }

    private fun sanitize(name: String) = name.replace(Regex("[\\\\/]+"), "_").trim().ifBlank { "untitled" }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val value = cursor.getString(0)
                if (!value.isNullOrBlank()) return value
            }
        }
        return uri.lastPathSegment ?: "file"
    }
}

object FileClip {
    var op: String? = null
    var paths: List<String> = emptyList()

    fun clear() {
        op = null
        paths = emptyList()
    }

    val active: Boolean get() = op != null && paths.isNotEmpty()
}
