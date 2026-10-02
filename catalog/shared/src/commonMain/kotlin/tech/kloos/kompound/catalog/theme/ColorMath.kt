package tech.kloos.kompound.catalog.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Hue 0..360, saturation 0..1, lightness 0..1. */
internal data class Hsl(val h: Float, val s: Float, val l: Float)

internal fun hsl(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val light = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * light - 1f)) * sat
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = light - c / 2f
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

internal fun Color.toHsl(): Hsl {
    val mx = max(red, max(green, blue))
    val mn = min(red, min(green, blue))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d == 0f) return Hsl(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (mx) {
        red -> 60f * (((green - blue) / d) % 6f)
        green -> 60f * ((blue - red) / d + 2f)
        else -> 60f * ((red - green) / d + 4f)
    }
    return Hsl((h + 360f) % 360f, s, l)
}

/** WCAG contrast ratio between two opaque colours (1..21). */
internal fun contrastRatio(a: Color, b: Color): Float {
    fun lin(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    fun lum(c: Color) = 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    val l1 = lum(a)
    val l2 = lum(b)
    return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
}

/** White or near-black, whichever reads better on [background]. */
internal fun onColorFor(background: Color): Color {
    val white = Color.White
    val ink = Color(0xFF14141A)
    return if (contrastRatio(background, white) >= contrastRatio(background, ink)) white else ink
}

/**
 * Moves [color]'s lightness away from [against] (darker when [darker], else lighter) in small steps until the
 * contrast reaches [minContrast] or the end of the scale is reached.
 */
internal fun ensureContrast(color: Color, against: Color, minContrast: Float, darker: Boolean): Color {
    var hsl = color.toHsl()
    var current = color
    var guard = 0
    while (contrastRatio(current, against) < minContrast && guard++ < 100) {
        val next = hsl.l + if (darker) -0.01f else 0.01f
        if (next < 0f || next > 1f) break
        hsl = hsl.copy(l = next)
        current = hsl(hsl.h, hsl.s, hsl.l)
    }
    return current
}

/** `0xAARRGGBB` literal for generated code, e.g. `Color(0xFF6750A4)`. */
internal fun Color.toKotlin(): String {
    fun hex(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
    return "Color(0xFF${hex(red)}${hex(green)}${hex(blue)})"
}
