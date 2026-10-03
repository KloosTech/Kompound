package tech.kloos.kompound.graph

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.PortDirection
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.Subgraphs
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.text.KText

/** Defaults for subgraph nodes and their boundary nodes. */
public object KSubgraphDefaults {
    /** Title bar of a subgraph node: the tertiary container colour, so it stands out from ordinary nodes. */
    @Composable
    public fun headerStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) {
            Style {
                background(c.tertiaryContainer)
                shape(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                contentColor(c.onTertiaryContainer)
                textStyle(type.titleSmall.copy(color = c.onTertiaryContainer))
                contentPadding(horizontal = 12.dp, vertical = 10.dp)
            }
        }
    }

    /** A boundary node: a small pill with the port's name; a primary outline when selected. */
    @Composable
    public fun boundaryStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) {
            Style {
                background(c.tertiaryContainer)
                shape(androidx.compose.foundation.shape.RoundedCornerShape(50))
                borderWidth(1.dp)
                borderColor(c.outlineVariant)
                contentColor(c.onTertiaryContainer)
                textStyle(type.labelLarge.copy(color = c.onTertiaryContainer))
                contentPadding(horizontal = 14.dp, vertical = 6.dp)
                selected { borderWidth(2.dp); borderColor(c.primary) }
            }
        }
    }
}

/** A subgraph node as the editor draws it: title bar with an "open" button, one row per port; double-click opens it. */
@Composable
internal fun KSubgraphNode(node: GraphNode) {
    val state = LocalKGraphState.current ?: error("KSubgraphNode must be used inside KNodeGraph")
    val title = state.subgraphTitle(node.id)
    KNode(
        node = node,
        title = title,
        headerStyle = KSubgraphDefaults.headerStyle(),
        onDoubleClick = { state.enterSubgraph(node.id) },
        actions = { KIconButton({ state.enterSubgraph(node.id) }, "Open $title") { KIcon(GraphIcons.ChevronRight, null) } },
    ) {
        for (p in node.ports.filter { it.direction == PortDirection.Input }) Input(p.id.value, p.label)
        for (p in node.ports.filter { it.direction == PortDirection.Output }) Output(p.id.value, p.label)
        if (node.ports.isEmpty()) Content { KText("No ports yet", style = KNodeDefaults.portLabelStyle()) }
    }
}

/** A boundary node inside a subgraph: the end of the wire that comes in through (or goes out of) a port of the subgraph node. */
@Composable
internal fun KBoundaryNode(node: GraphNode) {
    remember { KompoundStyles.ensureEnabled() }
    val state = LocalKGraphState.current ?: error("KBoundaryNode must be used inside KNodeGraph")
    val source = remember { MutableInteractionSource() }
    val selected = node.id in state.selection
    val styleState = rememberUpdatedStyleState(source) { it.isSelected = selected }
    val label = node.data as? String ?: ""
    val spec = node.ports.firstOrNull() ?: return
    val isInput = node.kind == Subgraphs.InputKind
    Row(
        Modifier
            .onSizeChanged { state.sizes[node.id] = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics { contentDescription = (if (isInput) "Input " else "Output ") + label }
            .focusable(true, source)
            .nodeSelectOnClick(state, node.id)
            .nodeDragHandle(state, node.id)
            .styleable(styleState, KSubgraphDefaults.boundaryStyle()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isInput) KPortHandle(PortRef(node.id, spec.id), spec, Modifier.halfEdge(start = true))
        KText(label.ifEmpty { "Port" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (isInput) KPortHandle(PortRef(node.id, spec.id), spec, Modifier.halfEdge(start = false))
    }
}

private fun Modifier.halfEdge(start: Boolean): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val reported = p.width / 2
    layout(reported, p.height) { p.place(if (start) -reported else 0, 0) }
}

/**
 * The path to the open level as chips: "Top level", then each open subgraph. Click a chip to go back to that level. [KNodeGraph]
 * shows it by itself inside a subgraph; use this to place it elsewhere.
 */
@Composable
public fun KGraphBreadcrumbs(state: KGraphState, modifier: Modifier = Modifier, topLevelLabel: String = "Top level") {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        KChip(topLevelLabel, onClick = { state.exitTo(0) }, selected = state.scopePath.isEmpty())
        state.scopePath.forEachIndexed { i, id ->
            KText("›", style = KNodeDefaults.portLabelStyle())
            KChip(state.subgraphTitle(id), onClick = { state.exitTo(i + 1) }, selected = i == state.scopePath.lastIndex)
        }
    }
}
