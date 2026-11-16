package de.dna.tvui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast

/**
 * Browser für die Fernbedienung:
 *  Pfeiltasten  = Mauszeiger bewegen (gedrückt halten = schneller, am Rand wird gescrollt)
 *  OK           = Klick (gedrückt halten = langer Druck)
 *  Zurück       = vorige Seite / Vollbild verlassen / schließen
 *  Menü         = Seite neu laden
 */
class BrowserActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var cursor: CursorView
    private lateinit var progress: ProgressBar

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var touchDownTime = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        web = WebView(this)
        cursor = CursorView(this)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(progress, FrameLayout.LayoutParams(MATCH, dp(4f).toInt()))
        root.addView(cursor, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("http")) return false
                // z. B. intent:// oder App-Links: an Android weitergeben
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            // Video-Vollbild der Seite
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView = view
                fullscreenCallback = callback
                root.addView(view, root.indexOfChild(cursor), FrameLayout.LayoutParams(MATCH, MATCH))
                web.visibility = View.INVISIBLE
                cursor.hideSoon()
            }

            override fun onHideCustomView() = leaveFullscreen()
        }

        cursor.post { cursor.moveTo(root.width / 2f, root.height / 2f) }
        load(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent?) {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return
        leaveFullscreen()
        web.loadUrl(url)
    }

    private fun leaveFullscreen() {
        val v = fullscreenView ?: return
        root.removeView(v)
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        web.visibility = View.VISIBLE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) move(-1, 0, event.repeatCount); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) move(1, 0, event.repeatCount); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { if (down) move(0, -1, event.repeatCount); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (down) move(0, 1, event.repeatCount); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (down && event.repeatCount == 0) touch(MotionEvent.ACTION_DOWN)
                else if (!down) touch(MotionEvent.ACTION_UP)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!down) when {
                    fullscreenView != null -> leaveFullscreen()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (!down) {
                    web.reload()
                    Toast.makeText(this, "Neu geladen", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Zeiger bewegen; gedrückt halten beschleunigt. Am Rand wird die Seite gescrollt. */
    private fun move(dx: Int, dy: Int, repeat: Int) {
        val step = dp(9f) * (1f + repeat.coerceAtMost(14) * 0.45f)
        val margin = dp(6f)
        var x = cursor.cx + dx * step
        var y = cursor.cy + dy * step
        val maxX = root.width - margin
        val maxY = root.height - margin
        if (fullscreenView == null) {
            if (y < margin && dy < 0) web.scrollBy(0, -(step * 2.5f).toInt())
            if (y > maxY && dy > 0) web.scrollBy(0, (step * 2.5f).toInt())
            if (x < margin && dx < 0) web.scrollBy(-(step * 2.5f).toInt(), 0)
            if (x > maxX && dx > 0) web.scrollBy((step * 2.5f).toInt(), 0)
        }
        x = x.coerceIn(margin, maxX)
        y = y.coerceIn(margin, maxY)
        cursor.moveTo(x, y)
    }

    /** Simuliert einen Finger-Tipp an der Zeiger-Position. */
    private fun touch(action: Int) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) touchDownTime = now
        val ev = MotionEvent.obtain(touchDownTime, now, action, cursor.cx, cursor.cy, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        (fullscreenView ?: web).dispatchTouchEvent(ev)
        ev.recycle()
        cursor.pressed(action == MotionEvent.ACTION_DOWN)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
    }
}

/** Runder Mauszeiger im Glas-Look; blendet sich nach ein paar Sekunden Ruhe aus. */
class CursorView(context: Context) : View(context) {
    var cx = 0f
        private set
    var cy = 0f
        private set
    private var down = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2f)
        color = 0x99000000.toInt()
    }
    private val fade = Runnable { animate().alpha(0f).setDuration(400).start() }

    init {
        isFocusable = false
        isClickable = false
    }

    fun moveTo(x: Float, y: Float) {
        cx = x
        cy = y
        animate().cancel()
        alpha = 1f
        invalidate()
        hideSoon()
    }

    fun pressed(p: Boolean) {
        down = p
        alpha = 1f
        invalidate()
        hideSoon()
    }

    fun hideSoon() {
        removeCallbacks(fade)
        postDelayed(fade, 3500)
    }

    override fun onTouchEvent(event: MotionEvent?) = false

    override fun onDraw(canvas: Canvas) {
        val r = context.dp(if (down) 9f else 12f)
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, ring)
    }
}
