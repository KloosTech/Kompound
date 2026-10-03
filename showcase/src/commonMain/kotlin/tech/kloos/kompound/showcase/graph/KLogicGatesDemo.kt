package tech.kloos.kompound.showcase.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.graph.KEdgeStyle
import tech.kloos.kompound.graph.KGraphControls
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.KMiniMap
import tech.kloos.kompound.graph.KNode
import tech.kloos.kompound.graph.KNodeGraph
import tech.kloos.kompound.graph.model.ConnectionPolicy
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.selection.KSwitch
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

private const val Usage_graph_logic = """import tech.kloos.kompound.graph.KEdgeStyle
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.KNode
import tech.kloos.kompound.graph.KNodeGraph
import tech.kloos.kompound.graph.model.ConnectionPolicy
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.theme.KompoundTheme

// Wires that carry a Boolean: colour each wire (and the port circles) by the value on it.
// `values` is whatever you compute from the graph (see LogicSimulation in the catalog sources).
val state = remember { KGraphState(circuit, ConnectionPolicy(allowCycles = true), gridStep = 24f) }   // cycles allowed: latches feed back
var values by remember { mutableStateOf(emptyMap<PortRef, Boolean>()) }
LaunchedEffect(state.graph) { values = LogicSimulation.settle(state.graph, clock = false, previous = values) }

val on = KompoundTheme.tokens.colors.success
val off = MaterialTheme.colorScheme.error

KNodeGraph(
    state,
    Modifier.fillMaxWidth().height(520.dp),
    edgeStyle = { edge -> if (values[edge.from] == true) KEdgeStyle(color = on, animated = true) else KEdgeStyle(color = off) },
    portColor = { port, _ -> if (values[port] == true) on else off },
) { node ->
    KNode(node, node.kind.uppercase()) {
        Input("a", "A")
        Input("b", "B")
        Output("out", "Out")
    }
}

// Toggle a switch with a command so it is one undo step: state.execute(GraphCommand.UpdateNodeData(id, newData))"""

private const val ClockPeriod = 900L

/** A gate symbol (ANSI shapes) filled with the colour of its output. */
@Composable
private fun GateSymbol(kind: String, on: Boolean, modifier: Modifier = Modifier) {
    val tint = if (on) KompoundTheme.tokens.colors.success else MaterialTheme.colorScheme.error
    val line = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(96.dp, 56.dp)) {
        val bubble = 6.dp.toPx()
        val inverted = kind == "nand" || kind == "nor" || kind == "xnor" || kind == "not"
        val h = size.height
        val x0 = if (kind == "xor" || kind == "xnor") 10.dp.toPx() else 0f
        val x1 = size.width - (if (inverted) bubble * 2f else 0f) - 1.dp.toPx()
        val body = when (kind) {
            "and", "nand" -> Path().apply {
                moveTo(x0, 0f)
                lineTo(x1 - h / 2f, 0f)
                arcTo(Rect(x1 - h, 0f, x1, h), -90f, 180f, false)
                lineTo(x0, h)
                close()
            }
            "not" -> Path().apply {
                moveTo(x0, 0f)
                lineTo(x1, h / 2f)
                lineTo(x0, h)
                close()
            }
            else -> Path().apply {
                val c = x0 + (x1 - x0) * 0.6f
                moveTo(x0, 0f)
                quadraticTo(c, 0f, x1, h / 2f)
                quadraticTo(c, h, x0, h)
                quadraticTo(x0 + (x1 - x0) * 0.3f, h / 2f, x0, 0f)
                close()
            }
        }
        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
        drawPath(body, tint.copy(alpha = 0.3f))
        drawPath(body, line, style = stroke)
        if (kind == "xor" || kind == "xnor") {
            drawPath(Path().apply {
                moveTo(0f, 0f)
                quadraticTo((x1 - x0) * 0.3f, h / 2f, 0f, h)
            }, line, style = stroke)
        }
        if (inverted) {
            val centre = Offset(x1 + bubble, h / 2f)
            drawCircle(tint.copy(alpha = 0.3f), bubble, centre)
            drawCircle(line, bubble, centre, style = stroke)
        }
    }
}

/** A lamp: glows green when on, dim red when off. */
@Composable
private fun Lamp(on: Boolean, modifier: Modifier = Modifier) {
    val tint = if (on) KompoundTheme.tokens.colors.success else MaterialTheme.colorScheme.error
    Canvas(modifier.size(40.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        if (on) drawCircle(tint.copy(alpha = 0.25f), size.minDimension / 2f, c)
        drawCircle(tint.copy(alpha = if (on) 1f else 0.45f), size.minDimension / 3.2f, c)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@KompoundDemo(
    id = "graph.logic",
    title = "Logic gates",
    description = "Logic circuits on KNodeGraph: flip switches and watch wires turn green (1) or red (0) through AND, OR, XOR, NAND, NOR, XNOR and NOT gates.",
    category = KompoundCategory.Graph,
    tags = ["graph", "node", "logic", "gate", "boolean", "circuit", "adder", "latch", "wire"],
    since = "0.1.0",
    status = "Experimental",
    usage = Usage_graph_logic,
)
@Composable
fun DemoScope.KLogicGatesDemo() {
    val preset = choiceControl("Circuit", LogicPreset.entries, label = { it.title })
    val running = boolControl("Run the clock", true)
    val flow = boolControl("Animated true wires", true)
    val grid = boolControl("Dotted grid", true)

    val state = remember(preset) { KGraphState(logicCircuit(preset), ConnectionPolicy(allowCycles = true), gridStep = 24f) }
    var clock by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running) {
            delay(ClockPeriod)
            clock = !clock
        }
    }
    var values by remember(state) { mutableStateOf(emptyMap<PortRef, Boolean>()) }
    LaunchedEffect(state, state.graph, clock) { values = LogicSimulation.settle(state.graph, clock, values) }

    val on = KompoundTheme.tokens.colors.success
    val off = MaterialTheme.colorScheme.error
    fun bit(ref: PortRef): Boolean = values[ref] == true
    fun incoming(ref: PortRef): Boolean = LogicSimulation.inputValue(state.graph, values, ref)

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Lamp(true, Modifier.size(20.dp))
                KText("1 true")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Lamp(false, Modifier.size(20.dp))
                KText("0 false")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton({ state.undo() }, variant = KButtonVariant.Outlined, enabled = state.canUndo) { KText("Undo") }
            KButton({ state.redo() }, variant = KButtonVariant.Outlined, enabled = state.canRedo) { KText("Redo") }
            KButton({ state.fitView() }, variant = KButtonVariant.Outlined) { KText("Fit") }
            KButton({ state.removeSelection() }, variant = KButtonVariant.Text, enabled = state.selection.isNotEmpty() || state.selectedEdges.isNotEmpty()) { KText("Delete") }
        }
        KText("Flip the switches to change the inputs. Double-click the canvas (or drop a wire on it) to add gates, switches and lamps; drag from an output to an input to wire them, drag a wire off the end to remove it. Feedback loops are allowed: in the SR latch, Set and Reset make the lamps remember their state.")
        KNodeGraph(
            state,
            Modifier.fillMaxWidth().height(540.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            fitOnFirstLayout = true,
            showGrid = grid,
            nodeTypes = LogicNodeTypes,
            edgeStyle = { edge -> if (values[edge.from] == true) KEdgeStyle(color = on, animated = flow) else KEdgeStyle(color = off) },
            portColor = { port, spec ->
                if (spec.direction == tech.kloos.kompound.graph.model.PortDirection.Output) (if (bit(port)) on else off)
                else if (incoming(port)) on else off
            },
            overlay = {
                KGraphControls(state, Modifier.align(Alignment.TopEnd).padding(8.dp))
                KMiniMap(state, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            },
        ) { node ->
            val out = PortRef(node.id, PortId("out"))
            when (node.kind) {
                LogicKinds.Switch -> {
                    val sw = node.data as? LogicSwitch ?: LogicSwitch("Switch", false)
                    KNode(node, sw.label) {
                        Content {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                KSwitch(sw.on, { state.execute(GraphCommand.UpdateNodeData(node.id, sw.copy(on = it))) })
                                KText(if (sw.on) "1" else "0")
                            }
                        }
                        Output("out", "Out")
                    }
                }
                LogicKinds.Clock -> KNode(node, "Clock") {
                    Content {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Lamp(clock)
                            KText(if (clock) "1" else "0")
                        }
                    }
                    Output("out", "Out")
                }
                LogicKinds.Led -> KNode(node, node.data as? String ?: "LED") {
                    Input("in", "In")
                    Content {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            val lit = incoming(PortRef(node.id, PortId("in")))
                            Lamp(lit)
                            KText(if (lit) "1" else "0")
                        }
                    }
                }
                else -> KNode(node, LogicKinds.title(node.kind)) {
                    Content { GateSymbol(node.kind, bit(out)) }
                    Input("a", if (node.kind == LogicKinds.Not) "In" else "A")
                    if (node.kind != LogicKinds.Not) Input("b", "B")
                    Output("out", "Out")
                }
            }
        }
    }
}
