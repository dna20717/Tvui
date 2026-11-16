package de.dna.tvui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.IOException

/**
 * Läuft, solange TVUI lebt:
 *  - der kleine Server im Heimnetz (Fotos + „Auf TV abspielen“ vom iPad)
 *  - das Zurückholen von TVUI nach dem Standby
 */
class TvuiApp : Application() {

    private val handler = Handler(Looper.getMainLooper())
    private val bringToFront = Runnable { showHome(this) }
    private lateinit var store: WallpaperStore
    private var server: WallpaperServer? = null

    /** Die Startseite hört hier zu, um einen neuen Hintergrund sofort zu zeigen. */
    var onWallpaperChanged: (() -> Unit)? = null

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(bringToFront)
            handler.postDelayed(bringToFront, 1500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = WallpaperStore(this)
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
        startServer()
    }

    /** Startet den Server, falls er noch nicht läuft. */
    fun startServer(): Boolean {
        if (server?.isRunning == true) return true
        val s = WallpaperServer(
            assets,
            onUpload = { bytes ->
                val ok = store.saveCustom(bytes)
                if (ok) handler.post { onWallpaperChanged?.invoke() }
                ok
            },
            onPlay = { q ->
                val page = q["page"]?.takeIf { it.startsWith("http") }
                val video = q["video"]?.takeIf { it.startsWith("http") }
                val ref = q["ref"]?.takeIf { it.startsWith("http") }
                if (page == null && video == null) false
                else {
                    handler.post { MediaLauncher.open(this, page, video, ref) }
                    true
                }
            },
        )
        return try {
            s.start()
            server = s
            true
        } catch (e: IOException) {
            false
        }
    }

    companion object {
        fun showHome(context: Context) {
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            } catch (_: Exception) {}
        }
    }
}

/** Entscheidet, womit ein vom iPad geschickter Link geöffnet wird. */
object MediaLauncher {
    fun open(context: Context, page: String?, video: String?, ref: String?) {
        // 1. Echte Videodatei gefunden → eigener Player
        if (video != null) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_VIDEO, video)
                    .putExtra(PlayerActivity.EXTRA_PAGE, page)
                    .putExtra(PlayerActivity.EXTRA_REF, ref ?: page)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            return
        }
        val url = page ?: return
        // 2. YouTube → YouTube-App
        val host = Uri.parse(url).host.orEmpty()
        if (host.endsWith("youtube.com") || host == "youtu.be") {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .setPackage("com.google.android.youtube.tv")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (_: Exception) {}
        }
        // 3. Alles andere → TV-Browser mit Mauszeiger
        openBrowser(context, url)
    }

    fun openBrowser(context: Context, url: String) {
        context.startActivity(
            Intent(context, BrowserActivity::class.java)
                .putExtra(BrowserActivity.EXTRA_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }
}

/** Nach einem kompletten Neustart: TVUI öffnen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Handler(Looper.getMainLooper()).postDelayed({ TvuiApp.showHome(context.applicationContext) }, 3000)
    }
}
