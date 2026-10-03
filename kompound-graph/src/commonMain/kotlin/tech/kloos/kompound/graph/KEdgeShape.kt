package tech.kloos.kompound.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.max

/** How an edge is drawn between its two ports. */
public enum class KEdgeShape {
    /** A smooth S-curve that leaves the output to the right and enters the input from the left. */
    Bezier,

    /** A straight line. */
    Straight,

    /** Horizontal, vertical, horizontal segments with a vertical run halfway. */
    Step,
}

/**
 * Look of one wire, returned by the `edgeStyle` callback of [KNodeGraph]. Unset values fall back to the editor's defaults.
 *
 * @property shape Overrides the editor's wire shape for this wire.
 * @property color Overrides the colour (by default the colour of the output port's type, or the primary colour when selected).
 * @property width Overrides the line width.
 * @property dashed Draws a dashed line, for example for optional or conditional connections.
 * @property animated Moves the dashes along the wire to show the direction of data flow (implies [dashed]).
 */
@androidx.compose.runtime.Immutable
public class KEdgeStyle(
    public val shape: KEdgeShape? = null,
    public val color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    public val width: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    public val dashed: Boolean = false,
    public val animated: Boolean = false,
)

/** Geometry of edges: the path to draw and distance queries for picking. All points are in world units. */
internal object EdgeGeometry {
    /** Horizontal reach of the bezier handles. */
    fun handle(from: Offset, to: Offset): Float = max(abs(to.x - from.x) * 0.5f, 48f)

    fun path(shape: KEdgeShape, from: Offset, to: Offset): Path = Path().apply {
        moveTo(from.x, from.y)
        when (shape) {
            KEdgeShape.Bezier -> {
                val h = handle(from, to)
                cubicTo(from.x + h, from.y, to.x - h, to.y, to.x, to.y)
            }
            KEdgeShape.Straight -> lineTo(to.x, to.y)
            KEdgeShape.Step -> {
                val midX = (from.x + to.x) / 2f
                lineTo(midX, from.y)
                lineTo(midX, to.y)
                lineTo(to.x, to.y)
            }
        }
    }

    /** Points along the edge, dense enough to measure a distance to it. */
    fun sample(shape: KEdgeShape, from: Offset, to: Offset, segments: Int = 24): List<Offset> = when (shape) {
        KEdgeShape.Straight -> listOf(from, to)
        KEdgeShape.Step -> {
            val midX = (from.x + to.x) / 2f
            listOf(from, Offset(midX, from.y), Offset(midX, to.y), to)
        }
        KEdgeShape.Bezier -> {
            val h = handle(from, to)
            val c1 = Offset(from.x + h, from.y)
            val c2 = Offset(to.x - h, to.y)
            List(segments + 1) { i ->
                val t = i / segments.toFloat()
                val u = 1f - t
                from * (u * u * u) + c1 * (3 * u * u * t) + c2 * (3 * u * t * t) + to * (t * t * t)
            }
        }
    }

    /** Smallest distance from [point] to the edge. */
    fun distance(shape: KEdgeShape, from: Offset, to: Offset, point: Offset): Float {
        val pts = sample(shape, from, to)
        var best = Float.MAX_VALUE
        for (i in 0 until pts.size - 1) best = minOf(best, distanceToSegment(point, pts[i], pts[i + 1]))
        return best
    }

    private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        if (len2 == 0f) return (p - a).getDistance()
        val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
        return (p - (a + ab * t)).getDistance()
    }
}
