package de.dna.tvui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

data class AppEntry(
    val pkg: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable?,
)

/** Gemeinsamer Glas-Look für Kacheln: halbtransparente Füllung + Lichtrand. */
@SuppressLint("ViewConstructor")
open class GlassTile(
    context: Context,
    widthDp: Float,
    heightDp: Float,
    private val radiusDp: Float,
    private val onFocused: () -> Unit,
    onClick: () -> Unit,
) : FrameLayout(context) {

    var selectedMark = false
        set(v) { field = v; refresh(isFocused) }

    init {
        isFocusable = true
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(
            context.dp(widthDp).toInt(),
            context.dp(heightDp).toInt(),
        ).apply { marginEnd = context.dp(16f).toInt() }
        background = GradientDrawable().apply {
            cornerRadius = context.dp(radiusDp)
            setColor(0x1FFFFFFF)
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        refresh(false)
        setOnClickListener { onClick() }
        setOnFocusChangeListener { _, focused ->
            refresh(focused)
            val s = if (focused) 1.1f else 1f
            animate().scaleX(s).scaleY(s)
                .translationZ(if (focused) context.dp(14f) else 0f)
                .setDuration(140).start()
            if (focused) onFocused()
        }
    }

    private fun refresh(focused: Boolean) {
        foreground = GradientDrawable().apply {
            cornerRadius = context.dp(radiusDp)
            setColor(if (focused) 0x14FFFFFF else 0x00000000)
            when {
                focused -> setStroke(context.dp(2.5f).toInt(), 0xF2FFFFFF.toInt())
                selectedMark -> setStroke(context.dp(2f).toInt(), 0x99FFFFFF.toInt())
                else -> setStroke(context.dp(1f).toInt(), 0x40FFFFFF)
            }
        }
    }
}

/** App-Kachel: zeigt das TV-Banner der App, sonst Icon + Name. */
@SuppressLint("ViewConstructor")
class AppTile(
    context: Context,
    val entry: AppEntry,
    onFocused: (AppEntry) -> Unit,
    onClick: (AppEntry) -> Unit,
) : GlassTile(context, 176f, 99f, 18f, { onFocused(entry) }, { onClick(entry) }) {

    init {
        contentDescription = entry.label
        if (entry.banner != null) {
            addView(ImageView(context).apply {
                setImageDrawable(entry.banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else {
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            col.addView(ImageView(context).apply {
                setImageDrawable(entry.icon)
            }, LinearLayout.LayoutParams(context.dp(44f).toInt(), context.dp(44f).toInt()))
            col.addView(TextView(context).apply {
                text = entry.label
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                setPadding(context.dp(8f).toInt(), context.dp(6f).toInt(), context.dp(8f).toInt(), 0)
            })
            addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }
}

/** Vorschau-Kachel für Hintergründe in den Einstellungen. */
@SuppressLint("ViewConstructor")
class ThumbTile(
    context: Context,
    thumb: Bitmap,
    label: String,
    onFocused: (String) -> Unit,
    onClick: () -> Unit,
) : GlassTile(context, 96f, 54f, 12f, { onFocused(label) }, onClick) {
    init {
        contentDescription = label
        (layoutParams as LinearLayout.LayoutParams).marginEnd = context.dp(14f).toInt()
        addView(ImageView(context).apply {
            setImageBitmap(thumb)
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
