package de.dna.tvui

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Environment
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/**
 * Aufräumen: listet alle selbst installierten Apps (keine System-Apps)
 * und öffnet für die gewählte App den Deinstallieren-Dialog von Android.
 * Dazu: alte APK-Dateien (z. B. aus Downloader) löschen.
 */
class CleanerPanel(
    private val act: Activity,
    private val root: View,
    private val list: LinearLayout,
    private val dockPackages: () -> Set<String>,
    private val onCleanFiles: () -> Unit,
) {
    val isOpen get() = root.visibility == View.VISIBLE

    fun open() {
        root.alpha = 0f
        root.visibility = View.VISIBLE
        root.animate().alpha(1f).setDuration(180).start()
        rebuild(focusFirst = true)
    }

    fun close() {
        root.visibility = View.GONE
    }

    fun rebuild(focusFirst: Boolean = false) {
        val focusedTag = list.findFocus()?.tag
        list.removeAllViews()

        val files = leftoverFiles()
        val hint = when {
            !hasStorageAccess() -> "OK drücken, Zugriff erlauben"
            files.isEmpty() -> "Alles sauber ✓"
            else -> "${files.size} Datei(en) · ${mb(files.sumOf { it.length() })}"
        }
        list.addView(row(null, "🗑️  Alte APK-Dateien löschen", hint, TAG_FILES) { onCleanFiles() })

        val pm = act.packageManager
        val dock = dockPackages()
        val apps = pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != act.packageName }
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }

        if (apps.isEmpty()) {
            list.addView(TextView(act).apply {
                text = "Keine zusätzlichen Apps installiert 🎉"
                setTextColor(0xB3FFFFFF.toInt())
                textSize = 16f
                setPadding(act.dp(16f).toInt(), act.dp(14f).toInt(), 0, 0)
            })
        }
        for ((ai, label) in apps) {
            val icon = try { ai.loadIcon(pm) } catch (e: Exception) { null }
            val note = if (ai.packageName in dock) "im Dock" else ""
            list.addView(row(icon, label, note, ai.packageName) { uninstall(ai.packageName) })
        }

        val target = focusedTag?.let { list.findViewWithTag<View>(it) }
            ?: if (focusFirst || focusedTag != null) list.getChildAt(0) else null
        target?.requestFocus()
    }

    private fun uninstall(pkg: String) {
        try {
            act.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        } catch (e: Exception) {
            android.widget.Toast.makeText(act, "Deinstallieren nicht möglich", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun hasStorageAccess() =
        act.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** APK-Dateien im Download-Ordner (Downloader legt sie dort ab) + eigene Update-Reste. */
    fun leftoverFiles(): List<File> {
        val result = mutableListOf<File>()
        act.cacheDir.listFiles()?.filter { it.name.endsWith(".apk") }?.let { result += it }
        if (!hasStorageAccess()) return result
        fun scan(dir: File?, depth: Int) {
            if (dir == null || depth < 0) return
            for (f in dir.listFiles() ?: return) {
                if (f.isDirectory) scan(f, depth - 1)
                else if (f.name.endsWith(".apk", ignoreCase = true)) result += f
            }
        }
        scan(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), 3)
        Environment.getExternalStorageDirectory().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
            ?.let { result += it }
        return result.distinctBy { it.absolutePath }
    }

    private fun row(icon: Drawable?, title: String, note: String, tag: String, onClick: () -> Unit): View {
        val d = act.resources.displayMetrics.density
        return LinearLayout(act).apply {
            this.tag = tag
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            minimumHeight = (52 * d).toInt()
            setPadding((14 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = (4 * d).toInt() }
            fun style(focused: Boolean) {
                background = GradientDrawable().apply {
                    cornerRadius = 14 * d
                    setColor(if (focused) 0x33FFFFFF else 0x00000000)
                    if (focused) setStroke((2 * d).toInt(), 0xE6FFFFFF.toInt())
                }
            }
            style(false)
            setOnFocusChangeListener { _, f -> style(f) }
            setOnClickListener { onClick() }

            if (icon != null) addView(ImageView(act).apply { setImageDrawable(icon) },
                LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt()).apply { marginEnd = (14 * d).toInt() })
            addView(TextView(act).apply {
                text = title
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 17f
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(act).apply {
                text = note
                setTextColor(0xB3FFFFFF.toInt())
                textSize = 13f
                setSingleLine()
            })
        }
    }

    companion object {
        const val TAG_FILES = "__files__"
        fun mb(bytes: Long) = String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1_048_576.0)
    }
}
