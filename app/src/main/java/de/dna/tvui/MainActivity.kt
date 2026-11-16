package de.dna.tvui

import android.animation.TimeAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Matrix
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : Activity() {

    private lateinit var store: WallpaperStore
    private lateinit var wallpaperView: ImageView
    private lateinit var content: LinearLayout
    private lateinit var appScroll: HorizontalScrollView
    private lateinit var appRow: LinearLayout
    private lateinit var appName: TextView
    private lateinit var settingsButton: GlassLayout
    private lateinit var overlay: View
    private lateinit var presetRow: LinearLayout
    private lateinit var presetName: TextView
    private lateinit var qrView: ImageView
    private lateinit var uploadUrl: TextView
    private lateinit var updateText: TextView
    private lateinit var motionText: TextView
    private lateinit var guardText: TextView

    // Sanfte Hintergrund-Bewegung
    private var wallpaperBitmap: Bitmap? = null
    private val motionMatrix = Matrix()
    private val imageMatrix = Matrix()
    private var motionMs = 0L
    private var sinceFrameMs = 0L
    private val motionAnimator = TimeAnimator().apply {
        setTimeListener { _, _, delta ->
            motionMs += delta
            sinceFrameMs += delta
            if (sinceFrameMs >= 33) {   // ~30 Bilder/s reichen für langsame Bewegung
                sinceFrameMs = 0
                updateMotion()
            }
        }
    }

    private lateinit var updater: Updater
    private var latest: Updater.Release? = null
    @Volatile private var updating = false

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm != null) {
                        try {
                            startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            updateText.text = "Installer nicht gefunden 😕"
                            updating = false
                        }
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    updateText.text = "Update installiert ✓"
                    updating = false
                }
                else -> {
                    updateText.text = "Update abgebrochen"
                    updating = false
                }
            }
        }
    }

    private val screen = Point()
    private var currentPackages: List<String> = emptyList()
    private var server: WallpaperServer? = null

    private val settingsOpen get() = overlay.visibility == View.VISIBLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(screen)

        store = WallpaperStore(this)
        wallpaperView = findViewById(R.id.wallpaper)
        content = findViewById(R.id.content)
        appScroll = findViewById(R.id.appScroll)
        appRow = findViewById(R.id.appRow)
        appName = findViewById(R.id.appName)
        settingsButton = findViewById(R.id.settingsButton)
        overlay = findViewById(R.id.overlay)
        presetRow = findViewById(R.id.presetRow)
        presetName = findViewById(R.id.presetName)
        qrView = findViewById(R.id.qr)
        uploadUrl = findViewById(R.id.uploadUrl)
        updateText = findViewById(R.id.updateText)
        motionText = findViewById(R.id.motionText)
        guardText = findViewById(R.id.guardText)
        findViewById<GlassLayout>(R.id.guardButton).apply {
            cornerRadius = dp(22f)
            setOnClickListener { openGuardSettings() }
        }
        wallpaperView.scaleType = ImageView.ScaleType.MATRIX
        motionText.text = motionLabel()
        findViewById<GlassLayout>(R.id.motionButton).apply {
            cornerRadius = dp(22f)
            setOnClickListener { toggleMotion() }
        }
        updater = Updater(this)

        val filter = IntentFilter(Updater.ACTION_INSTALL_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(installReceiver, filter)
        }
        findViewById<GlassLayout>(R.id.updateButton).apply {
            cornerRadius = dp(22f)
            setOnClickListener { onUpdateClicked() }
        }

        findViewById<GlassLayout>(R.id.dock).cornerRadius = dp(30f)
        findViewById<GlassLayout>(R.id.settingsCard).cornerRadius = dp(34f)
        settingsButton.cornerRadius = dp(28f)

        settingsButton.setOnClickListener { showSettings() }
        overlay.setOnClickListener { hideSettings() }

        applyWallpaper()
    }

    override fun onDestroy() {
        try { unregisterReceiver(installReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateGuardLabel()
        refreshApps()
    }

    override fun onStart() {
        super.onStart()
        if (store.motion && !motionAnimator.isStarted) motionAnimator.start()
        if (settingsOpen) startServer()
    }

    override fun onStop() {
        motionAnimator.cancel()
        stopServer()
        super.onStop()
    }

    /** Home-Taste, während wir schon sichtbar sind: zurück an den Anfang. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (settingsOpen) hideSettings()
        appScroll.smoothScrollTo(0, 0)
        appRow.getChildAt(0)?.requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Ein Homescreen schließt sich nie selbst.
        if (settingsOpen) hideSettings() else {
            appScroll.smoothScrollTo(0, 0)
            appRow.getChildAt(0)?.requestFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (settingsOpen) hideSettings() else showSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Apps ----------

    private fun refreshApps() {
        thread {
            val installed = loadApps()
            // Jeden Dock-Eintrag der passenden installierten App zuordnen
            val resolved = DockConfig.items.map { item ->
                item to when (item) {
                    is AppItem -> item.packages.firstNotNullOfOrNull { installed[it] }
                        ?: installed.values.firstOrNull { item.labelMatch.containsMatchIn(it.label) }
                    is LinkItem -> null
                }
            }
            runOnUiThread {
                val keys = resolved.map { (item, app) -> "${item.name}:${app?.pkg}" }
                if (keys == currentPackages) return@runOnUiThread
                currentPackages = keys
                val hadFocus = appRow.hasFocus() || appRow.childCount == 0
                appRow.removeAllViews()
                for ((item, app) in resolved) appRow.addView(tileFor(item, app))
                if (hadFocus && !settingsOpen) appRow.getChildAt(0)?.requestFocus()
            }
        }
    }

    private fun tileFor(item: DockItem, app: AppEntry?): View = when {
        item is LinkItem -> TextTile(
            this, item.name, Uri.parse(item.url).host?.removePrefix("www.") ?: item.url, item.color,
            { appName.text = item.name },
            { openLink(item) },
        )
        app != null -> AppTile(this, app, { appName.text = item.name }, { launch(it) })
        else -> TextTile(
            this, item.name, "Nicht installiert", 0xFF4A4E5A.toInt(),
            { appName.text = "${item.name} · OK drücken zum Installieren" },
            { openStore("market://search?q=" + Uri.encode(item.name) + "&c=apps") },
        ).apply { alpha = 0.75f }
    }

    private fun openLink(item: LinkItem) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(packageManager) != null) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        toast("Für Websites brauchst du einen Browser. Ich öffne „TV Bro“ im Play Store.")
        openStore("market://details?id=com.phlox.tvwebbrowser")
    }

    private fun openStore(uri: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Play Store nicht gefunden")
        }
    }

    private fun loadApps(): Map<String, AppEntry> {
        val pm = packageManager
        val found = LinkedHashMap<String, AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val ai = ri.activityInfo
                val pkg = ai.packageName
                if (pkg == packageName || found.containsKey(pkg)) continue
                val banner = try {
                    ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
                } catch (e: Exception) { null }
                val icon = try { ri.loadIcon(pm) } catch (e: Exception) { null }
                found[pkg] = AppEntry(pkg, ri.loadLabel(pm).toString(), banner, icon)
            }
        }
        return found
    }

    private fun launch(app: AppEntry) {
        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(app.pkg) ?: pm.getLaunchIntentForPackage(app.pkg)
        if (intent == null) {
            toast("${app.label} lässt sich nicht starten")
            return
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("${app.label} lässt sich nicht starten")
        }
    }

    // ---------- Hintergrund ----------

    private fun applyWallpaper() {
        val w = screen.x.coerceAtLeast(1)
        val h = screen.y.coerceAtLeast(1)
        thread {
            val bmp = store.load(w, h)
            val blurred = Backdrop.blur(bmp, w, h)
            runOnUiThread {
                wallpaperBitmap = bmp
                wallpaperView.setImageBitmap(bmp)
                Backdrop.set(blurred, w, h)
                updateMotion()
                updateSelectionMarks()
            }
        }
    }

    private fun buildPresetRow() {
        presetRow.removeAllViews()
        Presets.all.forEachIndexed { i, preset ->
            val tile = ThumbTile(this, Presets.render(i, 192, 108), preset.name, { presetName.text = it }) {
                store.selection = "preset:$i"
                applyWallpaper()
            }
            tile.tag = "preset:$i"
            presetRow.addView(tile)
        }
        if (store.hasCustom()) {
            WallpaperStore.decodeCover(store.customFile, 192, 108)?.let { thumb ->
                val tile = ThumbTile(this, thumb, "Dein Foto", { presetName.text = it }) {
                    store.selection = "custom"
                    applyWallpaper()
                }
                tile.tag = "custom"
                presetRow.addView(tile)
            }
        }
        updateSelectionMarks()
    }

    private fun updateSelectionMarks() {
        val sel = store.selection
        for (i in 0 until presetRow.childCount) {
            (presetRow.getChildAt(i) as? GlassTile)?.selectedMark = presetRow.getChildAt(i).tag == sel
        }
    }

    private fun showSettings() {
        buildPresetRow()
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(180).start()
        startServer()
        checkForUpdate()
        val selected = (0 until presetRow.childCount)
            .map { presetRow.getChildAt(it) }
            .firstOrNull { it.tag == store.selection } ?: presetRow.getChildAt(0)
        selected?.requestFocus()
    }

    private fun hideSettings() {
        stopServer()
        overlay.animate().alpha(0f).setDuration(150).withEndAction {
            overlay.visibility = View.GONE
        }.start()
        content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        settingsButton.requestFocus()
    }

    private fun startServer() {
        val ip = WallpaperServer.localIp()
        if (ip == null) {
            uploadUrl.text = "Kein Netzwerk gefunden 😕"
            qrView.setImageDrawable(null)
            return
        }
        val srv = server ?: WallpaperServer(assets) { bytes -> onPhotoUploaded(bytes) }.also { server = it }
        try {
            srv.start()
        } catch (e: IOException) {
            uploadUrl.text = "Upload gerade nicht möglich"
            qrView.setImageDrawable(null)
            return
        }
        val url = "http://$ip:${WallpaperServer.PORT}"
        uploadUrl.text = url
        qrView.setImageBitmap(WallpaperServer.qr(url, 400))
    }

    private fun stopServer() {
        server?.stop()
    }

    /** Wird vom Server-Thread aufgerufen. */
    private fun onPhotoUploaded(bytes: ByteArray): Boolean {
        val ok = store.saveCustom(bytes)
        if (ok) runOnUiThread {
            applyWallpaper()
            if (settingsOpen) hideSettings()
            toast("Neuer Hintergrund ✨")
        }
        return ok
    }

    // ---------- Homescreen-Wächter ----------

    private fun updateGuardLabel() {
        guardText.text = if (HomeGuardService.isEnabled(this)) "Immer TVUI ✓" else "Immer TVUI einrichten"
    }

    private fun openGuardSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            toast("„TVUI Homescreen-Wächter“ auswählen und einschalten")
        } catch (e: Exception) {
            toast("Bedienungshilfen-Einstellungen nicht gefunden 😕")
        }
    }

    // ---------- Bewegung ----------

    /** Langsames Schweben + leichtes Atmen (Zoom). Das Glas bekommt dieselbe Bewegung. */
    private fun updateMotion() {
        val bmp = wallpaperBitmap ?: return
        val w = screen.x.toFloat()
        val h = screen.y.toFloat()
        motionMatrix.reset()
        if (store.motion) {
            val t = motionMs / 1000.0
            val s = (1.08 + 0.02 * sin(t * 2 * PI / 50)).toFloat()
            val dx = (0.02 * w * sin(t * 2 * PI / 70)).toFloat()
            val dy = (0.012 * h * cos(t * 2 * PI / 60)).toFloat()
            motionMatrix.setScale(s, s, w / 2f, h / 2f)
            motionMatrix.postTranslate(dx, dy)
        }
        imageMatrix.set(Backdrop.centerCrop(bmp.width, bmp.height, screen.x, screen.y))
        imageMatrix.postConcat(motionMatrix)
        wallpaperView.imageMatrix = imageMatrix
        Backdrop.setMotion(motionMatrix)
    }

    private fun motionLabel() = if (store.motion) "Bewegung: an" else "Bewegung: aus"

    private fun toggleMotion() {
        store.motion = !store.motion
        motionText.text = motionLabel()
        if (store.motion) motionAnimator.start() else {
            motionAnimator.cancel()
            updateMotion()
        }
    }

    // ---------- Update ----------

    private fun checkForUpdate() {
        if (updating) return
        updateText.text = "Build ${updater.currentBuild()} · suche Update …"
        thread {
            val result = runCatching { updater.fetchLatest() }
            runOnUiThread {
                if (updating) return@runOnUiThread
                val current = updater.currentBuild()
                result.onSuccess { r ->
                    latest = r
                    updateText.text = if (r.build > current) "⬇ Update auf Build ${r.build}" else "Aktuell · Build $current ✓"
                }.onFailure { e ->
                    latest = null
                    updateText.text = "Build $current · " +
                        if (e.message?.contains("404") == true) "kein Update gefunden" else "GitHub nicht erreichbar"
                }
            }
        }
    }

    private fun onUpdateClicked() {
        if (updating) return
        val r = latest
        if (r == null || r.build <= updater.currentBuild()) {
            checkForUpdate()
            return
        }
        updating = true
        updateText.text = "Lade Update …"
        thread {
            try {
                val file = updater.download(r.apkUrl) { pct ->
                    runOnUiThread { updateText.text = "Lade Update … $pct %" }
                }
                runOnUiThread { updateText.text = "Installiere …" }
                updater.install(file)
            } catch (e: Exception) {
                updating = false
                runOnUiThread { updateText.text = "Update fehlgeschlagen 😕" }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
