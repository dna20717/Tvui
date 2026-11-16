package de.dna.tvui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/** Eingebaute Verlaufs-Hintergründe, werden direkt im Code gemalt (keine Bilddateien nötig). */
object Presets {

    class Blob(val x: Float, val y: Float, val r: Float, val color: Int)
    class Preset(val name: String, val from: Int, val to: Int, val blobs: List<Blob>)

    private fun c(v: Long) = v.toInt()

    val all = listOf(
        Preset(
            "Aurora", c(0xFF0B1026), c(0xFF1B1F4B), listOf(
                Blob(0.18f, 0.30f, 0.55f, c(0xCC2EE6C5)),
                Blob(0.82f, 0.18f, 0.50f, c(0xCC8A5CFF)),
                Blob(0.62f, 0.95f, 0.45f, c(0xB3FF5CA8)),
            )
        ),
        Preset(
            "Sonnenuntergang", c(0xFF2B1055), c(0xFFD75A4A), listOf(
                Blob(0.78f, 0.78f, 0.60f, c(0xCCFFA24C)),
                Blob(0.15f, 0.15f, 0.50f, c(0xB3E04CFF)),
            )
        ),
        Preset(
            "Ozean", c(0xFF021B2E), c(0xFF0A4D68), listOf(
                Blob(0.28f, 0.75f, 0.60f, c(0xB300C2FF)),
                Blob(0.85f, 0.22f, 0.45f, c(0x995CFFD1)),
            )
        ),
        Preset(
            "Wald", c(0xFF06120C), c(0xFF1E3B2C), listOf(
                Blob(0.25f, 0.25f, 0.55f, c(0x995BE38B)),
                Blob(0.80f, 0.85f, 0.50f, c(0x80C9F27B)),
            )
        ),
        Preset(
            "Graphit", c(0xFF0E0E10), c(0xFF24252B), listOf(
                Blob(0.30f, 0.30f, 0.60f, c(0x804A4E5A)),
                Blob(0.80f, 0.80f, 0.50f, c(0x806B5B95)),
            )
        ),
    )

    fun render(index: Int, w: Int, h: Int): Bitmap {
        val p = all[index.coerceIn(0, all.size - 1)]
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), p.from, p.to, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        for (b in p.blobs) {
            val cx = b.x * w
            val cy = b.y * h
            val r = b.r * w
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(b.color, b.color and 0x00FFFFFF), null, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, r, paint)
        }
        return bmp
    }
}
