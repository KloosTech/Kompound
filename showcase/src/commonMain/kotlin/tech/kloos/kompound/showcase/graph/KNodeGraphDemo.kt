package tech.kloos.kompound.showcase.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.graph.KEdgeShape
import tech.kloos.kompound.graph.KEdgeStyle
import tech.kloos.kompound.graph.KGraphControls
import tech.kloos.kompound.graph.KMiniMap
import tech.kloos.kompound.graph.KNodeType
import tech.kloos.kompound.graph.commentNode
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.KNode
import tech.kloos.kompound.graph.KNodeGraph
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.GroupId
import tech.kloos.kompound.graph.model.NodeGroup
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.PortType
import tech.kloos.kompound.slider.KSlider
import tech.kloos.kompound.text.KText

private const val Usage_graph_nodes = """import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.KNode
import tech.kloos.kompound.graph.KNodeGraph
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.GroupId
import tech.kloos.kompound.graph.model.NodeGroup
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.PortType
import tech.kloos.kompound.slider.KSlider
import tech.kloos.kompound.text.KText

// 1. Describe the graph: nodes with typed ports, and the wires between them.
val number = PortType.of("number")
val graph = Graph.of(
    nodes = listOf(
        GraphNode(NodeId("a"), "number", Offset(40f, 40f), listOf(PortSpec.output("value", type = number)), data = 3f),
        GraphNode(NodeId("sum"), "add", Offset(340f, 60f), listOf(PortSpec.input("x", type = number), PortSpec.input("y", type = number), PortSpec.output("out", type = number)), data = 1f),
    ),
    edges = listOf(Edge(EdgeId("a->sum"), PortRef(NodeId("a"), PortId("value")), PortRef(NodeId("sum"), PortId("x")))),
)

// 2. The state holds the graph with undo/redo, selection and the viewport.
val state = remember { KGraphState(graph, gridStep = 24f) }

// 3. Draw each node with KNode; any Kompound composable can sit in the body, ports line up with their rows.
KNodeGraph(state, Modifier.fillMaxWidth().height(480.dp), fitOnFirstLayout = true) { node ->
    when (node.kind) {
        "number" -> KNode(node, "Number") {
            Content { KSlider(node.data as Float, { state.execute(GraphCommand.UpdateNodeData(node.id, it)) }, valueRange = 0f..10f) }
            Output("value", "Value")
        }
        else -> KNode(node, "Add") {
            Input("x", "X") { KText("1") }       // shown while nothing is connected
            Input("y", "Y")
            Output("out", "Sum")
        }
    }
}

// 4. Edit from code too: every change is a command, so undo/redo and onGraphChange see it.
state.connect(PortRef(NodeId("a"), PortId("value")), PortRef(NodeId("sum"), PortId("y")))
state.undo()"""

private val Num = PortType.of("number")

private fun numberNode(id: String, at: Offset, v: Float) =
    GraphNode(NodeId(id), "number", at, listOf(PortSpec.output("value", "Value", Num)), v)

private fun mathNode(id: String, kind: String, at: Offset) =
    GraphNode(NodeId(id), kind, at, listOf(PortSpec.input("a", "A", Num), PortSpec.input("b", "B", Num), PortSpec.output("out", "Result", Num)), 0f)

private fun displayNode(id: String, at: Offset) = GraphNode(NodeId(id), "display", at, listOf(PortSpec.input("in", "Value", Num)), null)

private val NodeTypes = listOf(
    KNodeType("number", "Number", listOf(PortSpec.output("value", "Value", Num)), "Input", { 1f }),
    KNodeType("add", "Add", listOf(PortSpec.input("a", "A", Num), PortSpec.input("b", "B", Num), PortSpec.output("out", "Result", Num)), "Math", { 0f }),
    KNodeType("multiply", "Multiply", listOf(PortSpec.input("a", "A", Num), PortSpec.input("b", "B", Num), PortSpec.output("out", "Result", Num)), "Math", { 0f }),
    KNodeType("display", "Display", listOf(PortSpec.input("in", "Value", Num)), "Output"),
)

private fun sampleGraph(): Graph = Graph.of(
    listOf(
        numberNode("n1", Offset(40f, 70f), 3f).copy(group = GroupId("inputs")),
        numberNode("n2", Offset(40f, 240f), 4f).copy(group = GroupId("inputs")),
        mathNode("add", "add", Offset(340f, 90f)),
        numberNode("n3", Offset(340f, 300f), 2f),
        mathNode("mul", "multiply", Offset(640f, 190f)),
        displayNode("out", Offset(940f, 200f)),
        commentNode("note", Offset(640f, 40f), "(a + b) x c"),
    ),
    listOf(
        Edge(EdgeId("e1"), PortRef(NodeId("n1"), PortId("value")), PortRef(NodeId("add"), PortId("a"))),
        Edge(EdgeId("e2"), PortRef(NodeId("n2"), PortId("value")), PortRef(NodeId("add"), PortId("b"))),
        Edge(EdgeId("e3"), PortRef(NodeId("add"), PortId("out")), PortRef(NodeId("mul"), PortId("a"))),
        Edge(EdgeId("e4"), PortRef(NodeId("n3"), PortId("value")), PortRef(NodeId("mul"), PortId("b"))),
        Edge(EdgeId("e5"), PortRef(NodeId("mul"), PortId("out")), PortRef(NodeId("out"), PortId("in"))),
    ),
    listOf(NodeGroup(GroupId("inputs"), "Inputs", color = 2)),
)

/** Value at an output port, following the wires upstream; unconnected inputs use the value stored in the node. */
private fun valueAt(graph: Graph, node: GraphNode, depth: Int = 0): Float {
    if (depth > 50) return 0f
    fun input(port: String, fallback: Float): Float {
        val edge = graph.edgesAt(PortRef(node.id, PortId(port))).firstOrNull() ?: return fallback
        return valueAt(graph, graph.node(edge.from.node) ?: return fallback, depth + 1)
    }
    return when (node.kind) {
        "number" -> node.data as? Float ?: 0f
        "add" -> input("a", 0f) + input("b", 0f)
        "multiply" -> input("a", 1f) * input("b", 1f)
        else -> 0f
    }
}

@OptIn(ExperimentalLayoutApi::class)
@KompoundDemo(
    id = "graph.nodes",
    title = "KNodeGraph",
    description = "Pannable, zoomable canvas of draggable nodes with typed ports and wires; nodes hold any Kompound component.",
    category = KompoundCategory.Graph,
    tags = ["graph", "node", "editor", "canvas", "wire", "dataflow", "pan", "zoom"],
    since = "0.1.0",
    status = "Experimental",
    usage = Usage_graph_nodes,
)
@Composable
fun DemoScope.KNodeGraphDemo() {
    val grid = boolControl("Dotted grid", true)
    val snap = boolControl("Snap nodes to grid", true)
    val guides = boolControl("Alignment guides", true)
    val overlays = boolControl("Minimap and controls", true)
    val flow = boolControl("Animated wires", false)
    val shape = choiceControl("Wire shape", KEdgeShape.entries)
    val state = remember { KGraphState(sampleGraph()) }
    SideEffect { state.gridStep = if (snap) 24f else 0f; state.snapToNodes = guides }
    val counter = remember { intArrayOf(10) }
    fun add(kind: String) {
        val id = "${kind}${counter[0]++}"
        val centre = state.viewport.screenToWorld(Offset(state.canvasSize.width / 2f, state.canvasSize.height / 2f))
        val node = when (kind) {
            "number" -> numberNode(id, centre, 1f)
            "display" -> displayNode(id, centre)
            else -> mathNode(id, kind, centre)
        }
        state.execute(GraphCommand.AddNode(node))
        state.select(node.id)
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton({ add("number") }, variant = KButtonVariant.Tonal) { KText("+ Number") }
            KButton({ add("add") }, variant = KButtonVariant.Tonal) { KText("+ Add") }
            KButton({ add("multiply") }, variant = KButtonVariant.Tonal) { KText("+ Multiply") }
            KButton({ add("display") }, variant = KButtonVariant.Tonal) { KText("+ Display") }
            KButton({ state.undo() }, variant = KButtonVariant.Outlined, enabled = state.canUndo) { KText("Undo") }
            KButton({ state.redo() }, variant = KButtonVariant.Outlined, enabled = state.canRedo) { KText("Redo") }
            KButton({ state.createSubgraph("Subgraph") }, variant = KButtonVariant.Outlined, enabled = state.selection.isNotEmpty()) { KText("Subgraph") }
            KButton({ state.selection.toList().forEach { state.dissolveSubgraph(it) } }, variant = KButtonVariant.Outlined, enabled = state.selection.any { state.graph.node(it)?.kind == "subgraph" }) { KText("Open up") }
            KButton({ state.groupSelection() }, variant = KButtonVariant.Outlined, enabled = state.selection.isNotEmpty()) { KText("Group") }
            KButton({ state.ungroupSelection() }, variant = KButtonVariant.Outlined, enabled = state.selection.any { state.graph.node(it)?.group != null }) { KText("Ungroup") }
            KButton({ state.duplicateSelection() }, variant = KButtonVariant.Outlined, enabled = state.selection.isNotEmpty()) { KText("Duplicate") }
            KButton({ state.autoLayout(selectedOnly = true, fit = true) }, variant = KButtonVariant.Outlined) { KText("Arrange") }
            KButton({ state.fitView() }, variant = KButtonVariant.Outlined) { KText("Fit") }
            KButton({ state.removeSelection() }, variant = KButtonVariant.Text, enabled = state.selection.isNotEmpty() || state.selectedEdges.isNotEmpty()) { KText("Delete") }
        }
        KText("Drag the title bar to move a node (all selected nodes move together), drag from a port to wire. Mouse: drag the background to select; to pan use the hand button (or key H, V goes back), the middle or right button, or hold Space; scroll to zoom, Shift+click adds to the selection. Touch: drag to pan, pinch to zoom, press and hold then drag to select. Double-click the canvas (or drop a wire on it) for the node menu, double-click a wire to add a reroute. Ctrl or Cmd+G groups the selection (Shift ungroups); L (or the Arrange button) lays the graph out in columns; Ctrl+Alt+G (or the Subgraph button) wraps the selection in a subgraph node you open by double-clicking it, Escape goes back up; drag a group's title bar to move it, use its arrow to collapse it. Delete removes, Ctrl or Cmd with Z, C, V, D undoes, copies, pastes, duplicates; F fits.")
        GraphFrame(state, 520) { frame ->
            KNodeGraph(
                state,
                frame,
                edgeShape = shape,
                fitOnFirstLayout = true,
                showGrid = grid,
                nodeTypes = NodeTypes,
                edgeStyle = { if (flow) KEdgeStyle(animated = true) else KEdgeStyle() },
                overlay = if (overlays) ({
                    KGraphControls(state, Modifier.align(Alignment.TopEnd).padding(8.dp))
                    KMiniMap(state, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                }) else null,
            ) { node ->
                when (node.kind) {
                    "number" -> KNode(node, "Number") {
                        val value = node.data as? Float ?: 0f
                        Content {
                            KText(value.toString().take(4))
                            KSlider(value, { state.execute(GraphCommand.UpdateNodeData(node.id, it)) }, valueRange = 0f..10f)
                        }
                        Output("value", "Value")
                    }
                    "display" -> KNode(node, "Display") {
                        Input("in", "Value")
                        val source = state.graph.edgesAt(PortRef(node.id, PortId("in"))).firstOrNull()?.from?.node?.let { state.graph.node(it) }
                        Content { KText(if (source == null) "No input" else valueAt(state.graph, source).toString().take(7)) }
                    }
                    else -> KNode(node, if (node.kind == "add") "Add" else "Multiply") {
                        Input("a", "A") { KText(if (node.kind == "add") "0" else "1") }
                        Input("b", "B") { KText(if (node.kind == "add") "0" else "1") }
                        Output("out", "Result")
                    }
                }
            }
        }
    }
}
