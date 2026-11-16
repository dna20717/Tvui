package de.dna.tvui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.max

/**
 * Hält eine stark verkleinerte, weichgezeichnete Kopie des Hintergrunds.
 * Alle Glas-Flächen zeichnen den passenden Ausschnitt davon hinter sich.
 * Das Weichzeichnen passiert nur einmal pro Hintergrund, darum läuft es
 * auch auf älteren Fernsehern flüssig.
 */
object Backdrop {
    var blurred: Bitmap? = null
        private set
    var screenW = 1
        private set
    var screenH = 1
        private set

    /** Aktuelle Bewegung des Hintergrunds (Bildschirm-Koordinaten). */
    val motion = Matrix()

    private val listeners = mutableSetOf<Runnable>()

    /** Läuft im UI-Thread, bei jedem Animations-Frame. */
    fun setMotion(m: Matrix) {
        motion.set(m)
        listeners.toList().forEach { it.run() }
    }

    fun addListener(r: Runnable) { listeners.add(r) }
    fun removeListener(r: Runnable) { listeners.remove(r) }

    /** Läuft im Hintergrund-Thread. */
    fun blur(src: Bitmap, w: Int, h: Int): Bitmap {
        val bw = max(1, w / 8)
        val bh = max(1, h / 8)
        val small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(small)
        canvas.drawBitmap(src, centerCrop(src.width, src.height, bw, bh), Paint(Paint.FILTER_BITMAP_FLAG))
        boxBlur(small, radius = 4, passes = 3)
        return small
    }

    /** Läuft im UI-Thread. */
    fun set(bmp: Bitmap, w: Int, h: Int) {
        blurred = bmp
        screenW = w
        screenH = h
        listeners.toList().forEach { it.run() }
    }

    fun centerCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Matrix {
        val scale = max(dstW / srcW.toFloat(), dstH / srcH.toFloat())
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate((dstW - srcW * scale) / 2f, (dstH - srcH * scale) / 2f)
        return m
    }

    private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int) {
        val w = bmp.width
        val h = bmp.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        bmp.getPixels(a, 0, w, 0, 0, w, h)
        repeat(passes) {
            pass(a, b, w, h, radius, horizontal = true)
            pass(b, a, w, h, radius, horizontal = false)
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var rs = 0
            var gs = 0
            var bs = 0
            for (i in -r..r) {
                val p = src[idx(i)]
                rs += p shr 16 and 0xff
                gs += p shr 8 and 0xff
                bs += p and 0xff
            }
            for (i in 0 until len) {
                dst[idx(i)] = (0xff shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val pOut = src[idx(i - r)]
                val pIn = src[idx(i + r + 1)]
                rs += (pIn shr 16 and 0xff) - (pOut shr 16 and 0xff)
                gs += (pIn shr 8 and 0xff) - (pOut shr 8 and 0xff)
                bs += (pIn and 0xff) - (pOut and 0xff)
            }
        }
    }
}
