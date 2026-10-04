package tech.kloos.kompound.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.graph.model.ConnectionCheck
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortDirection
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.menu.KMenu
import tech.kloos.kompound.menu.KMenuItem
import kotlin.math.roundToInt

/**
 * A kind of node the user can add from the canvas menu.
 *
 * @property kind Value of [GraphNode.kind] for created nodes; also the prefix of their ids.
 * @property title Name in the menu.
 * @property ports Ports of a new node.
 * @property category Optional second line in the menu (and the sort key).
 * @property data Creates the initial [GraphNode.data] of a new node.
 */
@Immutable
public class KNodeType(
    public val kind: String,
    public val title: String,
    public val ports: List<PortSpec>,
    public val category: String? = null,
    public val data: () -> Any? = { null },
)

/** Why the canvas menu opened: where, and which port a dragged wire came from (if any). */
@Immutable
public class KNodeMenuRequest(public val world: Offset, public val from: PortRef? = null)

/**
 * Adds [node] to the graph. With [connectFrom] the new node is wired to that port at the first of its ports that the connection policy accepts,
 * and node and wire are one undo step. Returns whether a wire was made.
 */
public fun KGraphState.addNode(node: GraphNode, connectFrom: PortRef? = null): Boolean {
    var edgeCommands: List<GraphCommand> = emptyList()
    if (connectFrom != null) {
        val trial = graph.withNode(node)
        for (spec in node.ports) {
            val check = policy.check(trial, connectFrom, PortRef(node.id, spec.id))
            if (check is ConnectionCheck.Allowed) {
                val edge = Edge(EdgeId("${check.from}->${check.to}"), check.from, check.to)
                edgeCommands = buildList {
                    if (check.replaces.isNotEmpty()) add(GraphCommand.Disconnect(check.replaces.toSet()))
                    add(GraphCommand.Connect(edge))
                }
                break
            }
        }
    }
    execute(if (edgeCommands.isEmpty()) GraphCommand.AddNode(node) else GraphCommand.Batch(listOf(GraphCommand.AddNode(node)) + edgeCommands, "Add node"))
    select(node.id)
    return edgeCommands.isNotEmpty()
}

/** Creates a node of [type] at [position] with a fresh id (`<kind>_<n>`) and adds it, wiring it to [connectFrom] if given. */
public fun KGraphState.addNode(type: KNodeType, position: Offset, connectFrom: PortRef? = null): GraphNode {
    var n = graph.nodes.size + 1
    while (NodeId("${type.kind}_$n") in graph.nodes) n++
    val node = GraphNode(NodeId("${type.kind}_$n"), type.kind, position, type.ports, type.data())
    addNode(node, connectFrom)
    return node
}

/**
 * Turns the wire [edge] into two wires joined by a new reroute node at [position] (world units). One undo step.
 * Returns the reroute's id, or `null` when the edge does not exist.
 */
public fun KGraphState.insertReroute(edge: EdgeId, position: Offset): NodeId? {
    if (readOnly) return null
    val e = graph.edge(edge) ?: return null
    val type = graph.port(e.from)?.type ?: tech.kloos.kompound.graph.model.PortType.Any
    var n = 1
    while (NodeId("reroute_$n") in graph.nodes) n++
    val reroute = rerouteNode("reroute_$n", position - Offset(14f, 14f), type)
    val into = PortRef(reroute.id, PortId("in"))
    val out = PortRef(reroute.id, PortId("out"))
    execute(
        GraphCommand.Batch(
            listOf(
                GraphCommand.Disconnect(setOf(edge)),
                GraphCommand.AddNode(reroute),
                GraphCommand.Connect(Edge(EdgeId("${e.from}->$into"), e.from, into)),
                GraphCommand.Connect(Edge(EdgeId("$out->${e.to}"), out, e.to)),
            ),
            "Add reroute",
        ),
    )
    select(reroute.id)
    return reroute.id
}

/** The menu of [types] at the screen position of [request]; types that cannot connect to the dragged wire's port are left out. */
@Composable
internal fun BoxScope.NodeTypeMenu(state: KGraphState, types: List<KNodeType>, request: KNodeMenuRequest, onDismiss: () -> Unit) {
    val from = request.from
    val fromSpec = from?.let { state.graph.port(it) }
    val offered = types.filter { type ->
        fromSpec == null || type.ports.any { p ->
            p.direction != fromSpec.direction &&
                (if (fromSpec.direction == PortDirection.Output) state.policy.typeRule(fromSpec.type, p.type) else state.policy.typeRule(p.type, fromSpec.type))
        }
    }.sortedWith(compareBy({ it.category ?: "" }, { it.title }))
    val screen = state.viewport.worldToScreen(request.world)
    Box(Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        layout(0, 0) { p.place(IntOffset(screen.x.roundToInt(), screen.y.roundToInt())) }
    }) {
        KMenu(expanded = true, onDismissRequest = onDismiss) {
            if (offered.isEmpty()) KMenuItem("No matching nodes", {}, enabled = false)
            for (type in offered) {
                KMenuItem(type.title, supportingText = type.category, onClick = {
                    state.addNode(type, request.world, from)
                    onDismiss()
                })
            }
        }
    }
}
