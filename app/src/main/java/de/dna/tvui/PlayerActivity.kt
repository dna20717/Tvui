package de.dna.tvui

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Spielt eine vom iPad geschickte Videodatei (MP4, HLS …) direkt ab.
 * OK = Pause/Weiter, Pfeiltasten = Steuerleiste, Zurück = beenden.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerView(this)
        setContentView(view)
        start()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        release()
        start()
    }

    private fun start() {
        val video = intent.getStringExtra(EXTRA_VIDEO) ?: return finish()
        val page = intent.getStringExtra(EXTRA_PAGE)
        val ref = intent.getStringExtra(EXTRA_REF)

        // Viele Seiten liefern ihre Videos nur aus, wenn der Aufruf "von ihrer Seite" kommt
        val headers = mutableMapOf<String, String>()
        if (ref != null) {
            headers["Referer"] = ref
            android.net.Uri.parse(ref).let { u -> if (u.scheme != null && u.host != null) headers["Origin"] = "${u.scheme}://${u.host}" }
        }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        player = p
        view.player = p
        view.keepScreenOn = true

        val item = MediaItem.Builder().setUri(video).apply {
            if (video.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
            if (video.contains(".mpd", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_MPD)
        }.build()

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (page != null) {
                    Toast.makeText(this@PlayerActivity, "Video lässt sich nicht direkt abspielen, ich öffne die Seite", Toast.LENGTH_LONG).show()
                    MediaLauncher.openBrowser(this@PlayerActivity, page)
                } else {
                    Toast.makeText(this@PlayerActivity, "Video lässt sich nicht abspielen 😕", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        })
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
        view.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // OK pausiert direkt, ohne erst die Steuerleiste zu öffnen
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!view.isControllerFullyVisible) {
                if (event.action == KeyEvent.ACTION_UP) {
                    player?.let { it.playWhenReady = !it.playWhenReady }
                    view.showController()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_VIDEO = "video"
        const val EXTRA_PAGE = "page"
        const val EXTRA_REF = "ref"
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
    }
}
