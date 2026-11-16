package de.dna.tvui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Merkt sich den gewählten Hintergrund und speichert hochgeladene Fotos. */
class WallpaperStore(context: Context) {

    private val prefs = context.getSharedPreferences("tvui", Context.MODE_PRIVATE)
    val customFile = File(context.filesDir, "wallpaper.jpg")

    var selection: String
        get() = prefs.getString("wallpaper", "preset:0") ?: "preset:0"
        set(v) { prefs.edit().putString("wallpaper", v).apply() }

    val presetIndex: Int
        get() = if (selection == "custom") -1 else selection.removePrefix("preset:").toIntOrNull() ?: 0

    fun hasCustom() = customFile.exists() && customFile.length() > 0

    fun load(w: Int, h: Int): Bitmap {
        if (selection == "custom" && hasCustom()) {
            decodeCover(customFile, w, h)?.let { return it }
        }
        return Presets.render(presetIndex.coerceAtLeast(0), w / 2, h / 2)
    }

    @Synchronized
    fun saveCustom(bytes: ByteArray): Boolean {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return false
        return try {
            val tmp = File(customFile.parentFile, "wallpaper.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(customFile)) {
                customFile.writeBytes(bytes)
                tmp.delete()
            }
            selection = "custom"
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /** Lädt ein Bild sparsam: nur so groß, dass es den Bildschirm füllt. */
        fun decodeCover(file: File, w: Int, h: Int): Bitmap? {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return null
            var sample = 1
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2
            val bmp = BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return null
            val scale = max(w / bmp.width.toFloat(), h / bmp.height.toFloat())
            if (scale < 0.8f) {
                val scaled = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).roundToInt().coerceAtLeast(1),
                    (bmp.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
                if (scaled !== bmp) bmp.recycle()
                return scaled
            }
            return bmp
        }
    }
}
