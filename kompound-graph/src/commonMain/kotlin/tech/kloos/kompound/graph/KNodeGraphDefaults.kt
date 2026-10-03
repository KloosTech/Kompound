package tech.kloos.kompound.graph

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.selected
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.graph.model.PortType
import tech.kloos.kompound.theme.KompoundTheme

/** Defaults for [KNodeGraph]: the canvas look, the grid and the colour of wires by port type. */
public object KNodeGraphDefaults {
    /** Canvas: the theme's `surfaceContainerLowest`-like background, clipped to its bounds. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        return remember(c) { Style { background(c.surface); clip(true) } }
    }

    /** Colour of the grid dots. */
    @Composable
    public fun gridColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

    /** Colour of the wires and ports of [type]: a stable pick from the theme's accent colours. */
    @Composable
    public fun portColor(type: PortType): Color = portPalette()[paletteIndex(type, portPalette().size)]

    @Composable
    internal fun portPalette(): List<Color> {
        val c = MaterialTheme.colorScheme
        val k = KompoundTheme.tokens.colors
        return remember(c, k) { listOf(c.primary, c.tertiary, k.success, k.warning, k.info, c.secondary, c.error) }
    }

    internal fun paletteIndex(type: PortType, size: Int): Int {
        if (type.id == PortType.Any.id) return 0
        var h = 7
        for (ch in type.id) h = h * 31 + ch.code
        return (h and Int.MAX_VALUE) % size
    }

    /** Distance between grid dots. */
    public val GridSpacing: Dp = 24.dp
}

/** Defaults for [KNode]. */
public object KNodeDefaults {
    /** Width of a node unless [KNode] is given another. */
    public val Width: Dp = 220.dp

    /** Diameter of the port circle. */
    public val PortSize: Dp = 12.dp

    /** Touch and pointer target around a port circle. */
    public val PortTouchSize: Dp = 28.dp

    /** The node body: `surfaceContainer`, 12dp shape, a 1dp outline that becomes a 2dp primary outline when selected or focused. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        return remember(c, shapes) {
            Style {
                background(c.surfaceContainer)
                shape(shapes.medium)
                borderWidth(1.dp)
                borderColor(c.outlineVariant)
                focused { borderWidth(2.dp); borderColor(c.primary) }
                selected { borderWidth(2.dp); borderColor(c.primary) }
            }
        }
    }

    /** The title bar: slightly raised, title typography, 12dp by 8dp padding. */
    @Composable
    public fun headerStyle(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val type = MaterialTheme.typography
        return remember(c, shapes, type) {
            Style {
                background(c.surfaceContainerHigh)
                shape(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                contentColor(c.onSurface)
                textStyle(type.titleSmall.copy(color = c.onSurface))
                contentPadding(horizontal = 12.dp, vertical = 10.dp)
            }
        }
    }

    /** Port labels: `bodySmall` in `onSurfaceVariant`. */
    @Composable
    public fun portLabelStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurfaceVariant); textStyle(type.bodySmall.copy(color = c.onSurfaceVariant)) } }
    }
}
