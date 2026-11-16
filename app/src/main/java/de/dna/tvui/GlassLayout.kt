package de.dna.tvui

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Liquid-Glass-Fläche: zeigt den weichgezeichneten Hintergrund genau an
 * der eigenen Bildschirmposition, mit leichtem Weiß-Schleier, Lichtkante
 * oben und feinem Rand. Ist sie fokussierbar, leuchtet sie beim Fokus auf.
 */
class GlassLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var cornerRadius: Float = context.dp(28f)
        set(value) { field = value; invalidate() }

    private val rect = RectF()
    private val strokeRect = RectF()
    private val loc = IntArray(2)
    private val shaderMatrix = Matrix()
    private var shaderSource: android.graphics.Bitmap? = null
    private var shader: BitmapShader? = null

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(1.45f)
            postConcat(ColorMatrix().apply { setScale(1.06f, 1.06f, 1.06f, 1f) })
        })
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1.2f)
    }

    private val onBackdrop = Runnable { invalidate() }

    init {
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Backdrop.addListener(onBackdrop)
    }

    override fun onDetachedFromWindow() {
        Backdrop.removeListener(onBackdrop)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        val half = strokePaint.strokeWidth / 2f
        strokeRect.set(half, half, w - half, h - half)
        highlightPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.55f,
            0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP,
        )
        strokePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            0x9AFFFFFF.toInt(), 0x22FFFFFF, Shader.TileMode.CLAMP,
        )
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        val s = if (gainFocus) 1.12f else 1f
        animate().scaleX(s).scaleY(s).setDuration(140).start()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = cornerRadius.coerceAtMost(rect.height() / 2f)

        val bmp = Backdrop.blurred
        if (bmp != null) {
            if (bmp !== shaderSource) {
                shaderSource = bmp
                shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                glassPaint.shader = shader
            }
            getLocationOnScreen(loc)
            shaderMatrix.setScale(
                Backdrop.screenW / bmp.width.toFloat(),
                Backdrop.screenH / bmp.height.toFloat(),
            )
            shaderMatrix.postConcat(Backdrop.motion)
            shaderMatrix.postTranslate(-loc[0].toFloat(), -loc[1].toFloat())
            shader?.setLocalMatrix(shaderMatrix)
            canvas.drawRoundRect(rect, r, r, glassPaint)
        }

        val focused = isFocused
        fillPaint.color = if (focused) 0x38FFFFFF else 0x14FFFFFF
        canvas.drawRoundRect(rect, r, r, fillPaint)
        canvas.drawRoundRect(rect, r, r, highlightPaint)

        strokePaint.alpha = if (focused) 255 else 200
        canvas.drawRoundRect(strokeRect, r, r, strokePaint)

        super.onDraw(canvas)
    }
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
