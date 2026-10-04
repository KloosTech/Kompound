package tech.kloos.kompound

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onAllNodesWithText
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

/** True when any pixel in the rectangle [x0, x1) by [y0, y1) is within [tol] of [c] and (almost) opaque. */
internal fun androidx.compose.ui.graphics.PixelMap.hasColorIn(c: Color, x0: Int, x1: Int, y0: Int, y1: Int, tol: Float = 0.1f): Boolean {
    for (y in y0.coerceAtLeast(0) until y1.coerceAtMost(height)) for (x in x0.coerceAtLeast(0) until x1.coerceAtMost(width)) {
        val p = this[x, y]
        if (p.alpha > 0.9f && p.near(c.copy(alpha = p.alpha), tol)) return true
    }
    return false
}

/** How many nodes show [text] (0 when it is not on screen). */
@androidx.compose.ui.test.ExperimentalTestApi
internal fun androidx.compose.ui.test.ComposeUiTest.countText(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes().size
