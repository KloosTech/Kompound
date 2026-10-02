package tech.kloos.kompound

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow

internal fun Color.near(o: Color, tol: Float = 0.06f): Boolean =
    abs(red - o.red) < tol && abs(green - o.green) < tol && abs(blue - o.blue) < tol && abs(alpha - o.alpha) < tol

/** True when any pixel is within [tol] of [c] and (almost) opaque. */
internal fun ImageBitmap.containsColor(c: Color, tol: Float = 0.1f): Boolean {
    val m = toPixelMap()
    for (y in 0 until height) for (x in 0 until width) {
        val p = m[x, y]
        if (p.alpha > 0.9f && p.near(c.copy(alpha = p.alpha), tol)) return true
    }
    return false
}

/** Solid 24x24 square, so a tinted icon is one flat colour. */
internal val SquareIcon: ImageVector = ImageVector.Builder("square", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(0f, 0f); lineTo(24f, 0f); lineTo(24f, 24f); lineTo(0f, 24f); close()
    }
}.build()

/** WCAG contrast ratio between two opaque colours. */
internal fun contrast(a: Color, b: Color): Float {
    fun lin(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    fun lum(c: Color) = 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    val l1 = lum(a); val l2 = lum(b)
    return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
}
