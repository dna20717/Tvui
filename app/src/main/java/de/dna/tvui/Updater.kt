package de.dna.tvui

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Selbst-Update: holt den neuesten GitHub-Build (Release "latest"),
 * lädt die APK und übergibt sie an den Android-Installer.
 */
class Updater(private val activity: Activity) {

    class Release(val build: Int, val apkUrl: String)

    @Suppress("DEPRECATION")
    fun currentBuild(): Int =
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode

    /** Hintergrund-Thread. */
    fun fetchLatest(): Release {
        val conn = open("https://api.github.com/repos/$REPO/releases/tags/latest")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val build = Regex("(\\d+)\\s*$").find(json.optString("name"))
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return Release(build, a.getString("browser_download_url"))
                }
            }
            throw IOException("Keine APK im Release")
        } finally {
            conn.disconnect()
        }
    }

    /** Hintergrund-Thread. Meldet den Fortschritt in Prozent. */
    fun download(url: String, progress: (Int) -> Unit): File {
        val file = File(activity.cacheDir, "update.apk")
        val conn = open(url)
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLength
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; progress(pct) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return file
    }

    /** Übergibt die APK an den System-Installer. Der fragt dann nach Bestätigung. */
    fun install(file: File) {
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("tvui.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getBroadcast(
                activity, id,
                Intent(ACTION_INSTALL_STATUS).setPackage(activity.packageName),
                flags,
            )
            session.commit(pi.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TVUI")
        }

    companion object {
        const val REPO = "dna20717/Tvui"
        const val ACTION_INSTALL_STATUS = "de.dna.tvui.INSTALL_STATUS"
    }
}
