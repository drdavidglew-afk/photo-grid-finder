package com.bookshelf.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.color(id: Int): Int = getColor(id)

fun Context.text(
    value: CharSequence,
    sizeSp: Float,
    colorRes: Int,
    bold: Boolean = false,
): TextView = TextView(this).apply {
    text = value
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color(colorRes))
    if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
}

fun Context.rounded(fillRes: Int, radiusDp: Int, strokeRes: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color(fillRes))
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeRes != null) setStroke(dp(1), color(strokeRes))
    }

fun Context.primaryButton(label: String): TextView = text(label, 15f, R.color.on_accent, bold = true).apply {
    gravity = android.view.Gravity.CENTER
    minHeight = dp(48)
    setPadding(dp(18), 0, dp(18), 0)
    background = rounded(R.color.accent, 12)
    isClickable = true
    isFocusable = true
}

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}
