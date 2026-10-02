package tech.kloos.kompound.border

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a dashed border of [shape] around the element, inside its bounds, over its content. Use it for drop
 * zones, placeholders and "add something here" areas.
 *
 * @param color Colour of the dashes.
 * @param shape Shape the dashes follow.
 * @param width Thickness of the line.
 * @param dashLength Length of each dash.
 * @param gapLength Length of each gap.
 * @param cap End cap of the dashes; round caps make the dashes look longer by [width].
 */
public fun Modifier.dashedBorder(
    color: Color,
    shape: Shape = RectangleShape,
    width: Dp = 2.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp,
    cap: StrokeCap = StrokeCap.Round,
): Modifier = dashedBorder(SolidColor(color), shape, width, dashLength, gapLength, cap)

/** [dashedBorder] drawn with a [brush], for example a gradient. */
public fun Modifier.dashedBorder(
    brush: Brush,
    shape: Shape = RectangleShape,
    width: Dp = 2.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp,
    cap: StrokeCap = StrokeCap.Round,
): Modifier = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    if (stroke <= 0f || size.minDimension <= stroke) return@drawWithContent
    // Like Modifier.border: the line is centred half a stroke inside the bounds so nothing is clipped.
    val inner = Size(size.width - stroke, size.height - stroke)
    val outline = shape.createOutline(inner, layoutDirection, this)
    translate(stroke / 2, stroke / 2) {
        drawOutline(
            outline = outline,
            brush = brush,
            style = Stroke(width = stroke, cap = cap, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()))),
        )
    }
}
