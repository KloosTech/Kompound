package tech.kloos.kompound.graph

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.size
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.PortType

/** The node kind of reroute (dot) nodes. `KNodeGraph` draws them itself; [nodeContent] never sees them. */
public const val KRerouteKind: String = "reroute"

/** Creates a reroute node: a tiny pass-through with one input and one output, used to bend a wire around other nodes. */
public fun rerouteNode(id: String, position: androidx.compose.ui.geometry.Offset, type: PortType = PortType.Any): GraphNode =
    GraphNode(NodeId(id), KRerouteKind, position, listOf(PortSpec.input("in", "", type), PortSpec.output("out", "", type)))

/**
 * A reroute node: a small pill with the two ports side by side. Drag it to move it, select it like any node. Insert one into a
 * wire by double-clicking the wire.
 *
 * @param node The reroute node (kind [KRerouteKind]).
 * @param modifier Modifier applied to the pill.
 * @param style Overrides merged over [KRerouteDefaults.style].
 */
@Composable
public fun KReroute(node: GraphNode, modifier: Modifier = Modifier, style: Style = Style) {
    remember { KompoundStyles.ensureEnabled() }
    val state = LocalKGraphState.current ?: error("KReroute must be used inside KNodeGraph")
    val source = remember { MutableInteractionSource() }
    val selected = node.id in state.selection
    val styleState = rememberUpdatedStyleState(source) { it.isSelected = selected }
    val input = node.port(PortId("in"))
    val output = node.port(PortId("out"))
    Row(
        modifier
            .onSizeChanged { state.sizes[node.id] = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics { contentDescription = "Reroute" }
            .focusable(true, source)
            .nodeSelectOnClick(state, node.id)
            .nodeDragHandle(state, node.id)
            .styleable(styleState, KRerouteDefaults.style(), style),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (input != null) KPortHandle(PortRef(node.id, input.id), input, Modifier.halfWidthOf(start = true))
        if (output != null) KPortHandle(PortRef(node.id, output.id), output, Modifier.halfWidthOf(start = false))
    }
}

private fun Modifier.halfWidthOf(start: Boolean): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val reported = p.width / 2
    layout(reported, p.height) { p.place(if (start) -reported else 0, 0) }
}

/** Defaults for [KReroute]. */
public object KRerouteDefaults {
    /** A 28dp by 28dp pill; a primary outline when selected. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        return remember(c) {
            Style {
                background(c.surfaceContainerHigh)
                shape(androidx.compose.foundation.shape.CircleShape)
                borderWidth(1.dp)
                borderColor(c.outlineVariant)
                size(28.dp)
                selected { borderWidth(2.dp); borderColor(c.primary) }
            }
        }
    }
}
