package tech.kloos.kompound.showcase.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import tech.kloos.kompound.graph.KNodeType
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.graph.inspector.KExecutionList
import tech.kloos.kompound.graph.inspector.KNodeInspector
import tech.kloos.kompound.graph.inspector.edgeLabel
import tech.kloos.kompound.graph.inspector.nodeStatus
import tech.kloos.kompound.graph.runtime.Execution
import tech.kloos.kompound.graph.runtime.NodeRun
import tech.kloos.kompound.graph.runtime.NodeRunner
import tech.kloos.kompound.graph.runtime.rememberGraphEngine
import tech.kloos.kompound.graph.runtime.singleOutputRunner
import tech.kloos.kompound.graph.serialization.JsonValue
import tech.kloos.kompound.graph.serialization.ValueJson
import tech.kloos.kompound.progress.KCircularProgress
import tech.kloos.kompound.segmented.KSegmentedControl
import tech.kloos.kompound.slider.KSlider
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

private const val Usage_graph_engine = """import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.graph.inspector.KExecutionList
import tech.kloos.kompound.graph.inspector.KNodeInspector
import tech.kloos.kompound.graph.inspector.edgeLabel
import tech.kloos.kompound.graph.inspector.nodeStatus
import tech.kloos.kompound.graph.runtime.Execution
import tech.kloos.kompound.graph.runtime.NodeRun
import tech.kloos.kompound.graph.runtime.rememberGraphEngine
import tech.kloos.kompound.graph.runtime.singleOutputRunner

// 1. One runner per node kind. It may suspend as long as it likes; throw to fail the node.
val runners = mapOf(
    "number" to singleOutputRunner { node, _ -> node.data as Float },
    "delay" to singleOutputRunner { node, inputs ->
        delay(((node.data as Float) * 1000).toLong())          // a process, a request, a timer ...
        inputs.require<Float>("in")
    },
)

// 2. The engine follows the editor: every edit goes to engine.update, stale runs are cancelled, downstream nodes re-run.
val engine = rememberGraphEngine(state, runners)

// 3. Draw progress from engine.runs (observable state): Waiting, Running, Done(outputs), Failed(error), Blocked(by).
KNodeGraph(
    state,
    edgeStyle = { edge -> if (engine.runOf(edge.from.node) is NodeRun.Running) KEdgeStyle(animated = true) else KEdgeStyle() },
) { node ->
    KNode(node, node.kind) {
        KText(engine.runOf(node.id).toString())
        Output("out")
    }
}

// Manual mode: rememberGraphEngine(state, runners, autoRun = false) and call engine.start(), engine.rerun(id), engine.stop()."""

private val Num = tech.kloos.kompound.graph.model.PortType.of("number")

private fun nodeOf(id: String, kind: String, at: Offset, data: Any?): GraphNode {
    val ports = when (kind) {
        "number" -> listOf(PortSpec.output("out", "Value", Num))
        "delay" -> listOf(PortSpec.input("in", "In", Num), PortSpec.output("out", "Out", Num))
        "result" -> listOf(PortSpec.input("in", "Value", Num))
        else -> listOf(PortSpec.input("a", "A", Num), PortSpec.input("b", "B", Num), PortSpec.output("out", "Result", Num))
    }
    return GraphNode(NodeId(id), kind, at, ports, data)
}

private val PipelineNodeTypes = listOf(
    KNodeType("number", "Number", listOf(PortSpec.output("out", "Value", Num)), "Input", { 2f }),
    KNodeType("delay", "Slow step", listOf(PortSpec.input("in", "In", Num), PortSpec.output("out", "Out", Num)), "Async", { 1.5f }),
    KNodeType("add", "Add", listOf(PortSpec.input("a", "A", Num), PortSpec.input("b", "B", Num), PortSpec.output("out", "Result", Num)), "Math", { null }),
    KNodeType("divide", "Divide", listOf(PortSpec.input("a", "A", Num), PortSpec.input("b", "B", Num), PortSpec.output("out", "Result", Num)), "Math", { null }),
    KNodeType("result", "Result", listOf(PortSpec.input("in", "Value", Num)), "Output", { null }),
)

private fun pipeline(): Graph {
    fun wire(from: String, to: String, port: String) =
        Edge(EdgeId("$from->$to.$port"), PortRef(NodeId(from), PortId("out")), PortRef(NodeId(to), PortId(port)))
    return Graph.of(
        listOf(
            nodeOf("a", "number", Offset(0f, 0f), 6f),
            nodeOf("b", "number", Offset(0f, 260f), 3f),
            nodeOf("slow1", "delay", Offset(340f, 0f), 2f),
            nodeOf("slow2", "delay", Offset(340f, 260f), 0.5f),
            nodeOf("sum", "add", Offset(680f, 130f), null),
            nodeOf("div", "divide", Offset(1020f, 130f), null),
            nodeOf("zero", "number", Offset(680f, 420f), 2f),
            nodeOf("out", "result", Offset(1360f, 130f), null),
        ),
        listOf(
            wire("a", "slow1", "in"), wire("b", "slow2", "in"),
            wire("slow1", "sum", "a"), wire("slow2", "sum", "b"),
            wire("sum", "div", "a"), wire("zero", "div", "b"),
            wire("div", "out", "in"),
        ),
    )
}

private val Runners: Map<String, NodeRunner> = mapOf(
    "number" to singleOutputRunner { node, _ -> node.data as Float },
    "delay" to singleOutputRunner { node, inputs ->
        delay(((node.data as Float) * 1000).toLong())
        inputs.require<Float>("in")
    },
    "add" to singleOutputRunner { _, i -> i.require<Float>("a") + i.require<Float>("b") },
    "divide" to singleOutputRunner { _, i ->
        val b = i.require<Float>("b")
        if (b == 0f) error("Division by zero")
        i.require<Float>("a") / b
    },
    "result" to singleOutputRunner { _, i -> i.require<Float>("in") },
)

@Composable
private fun StatusLine(run: NodeRun) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (run) {
            NodeRun.Running -> { KCircularProgress(null, size = 16.dp, strokeWidth = 2.dp); KText("Running") }
            NodeRun.Waiting -> KText("Waiting for inputs")
            NodeRun.Idle -> KText("Idle")
            is NodeRun.Blocked -> KText("Blocked by ${run.by}")
            is NodeRun.Done -> KText("Done")
            is NodeRun.Failed -> KText(run.error.message ?: "Failed", maxLines = 2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@KompoundDemo(
    id = "graph.engine",
    title = "Async node engine",
    description = "GraphEngine runs nodes that suspend: slow steps finish later and their result flows on; edits cancel stale runs; failures block only downstream.",
    category = KompoundCategory.Graph,
    tags = ["graph", "node", "async", "suspend", "engine", "pipeline", "coroutine"],
    since = "0.1.0",
    status = "Experimental",
    usage = Usage_graph_engine,
)
@Composable
fun DemoScope.KGraphEngineDemo() {
    val auto = boolControl("Run automatically", true)
    val state = remember { KGraphState(pipeline(), gridStep = 24f) }
    val engine = rememberGraphEngine(state, Runners, autoRun = auto)
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Execution?>(null) }
    var inspecting by remember { mutableStateOf<NodeId?>(null) }
    val viewed = if (tab == 1) selected else null
    val on = KompoundTheme.tokens.colors.success
    val off = MaterialTheme.colorScheme.error
    val busy = MaterialTheme.colorScheme.primary

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton({ engine.start() }, enabled = !auto || !engine.isActive) { KText("Run") }
            KButton({ engine.rerunAll() }, variant = KButtonVariant.Tonal) { KText("Run again") }
            KButton({ engine.stop() }, variant = KButtonVariant.Outlined, enabled = engine.isBusy) { KText("Stop") }
            KButton({ state.undo() }, variant = KButtonVariant.Outlined, enabled = state.canUndo) { KText("Undo") }
            KButton({ state.redo() }, variant = KButtonVariant.Outlined, enabled = state.canRedo) { KText("Redo") }
            KButton({ state.fitView() }, variant = KButtonVariant.Outlined) { KText("Fit") }
        }
        KSegmentedControl(listOf("Editor", "Executions"), tab, { tab = it; if (it == 0) selected = null }, Modifier.fillMaxWidth())
        if (tab == 1) {
            KExecutionList(engine, selected, { selected = it }, Modifier.fillMaxWidth().height(180.dp))
            KText(if (selected == null) "Showing the live state. Pick a run to see how it went; double-click a node for its input and output." else "Showing run #${selected!!.id}. Double-click a node to inspect what it received and produced.")
        }
        KText("Change a number or a delay while it runs: the stale step is cancelled and everything after it starts again. Set the second input of Divide to 0 to see a failure block only what comes after it. Slow steps run side by side; Add waits for both. The chevron in a title bar folds the node, and the Status section folds on its own.")
        GraphFrame(state, 520) { frame ->
            KNodeGraph(
                state, frame,
                fitOnFirstLayout = true,
                nodeTypes = PipelineNodeTypes,
                nodeStatus = { engine.nodeStatus(it, viewed) },
                edgeLabel = { engine.edgeLabel(it, viewed) },
                edgeStyle = { edge ->
                    when (engine.runOf(edge.from.node)) {
                        NodeRun.Running -> KEdgeStyle(color = busy, animated = true)
                        is NodeRun.Done -> KEdgeStyle(color = on)
                        is NodeRun.Failed, is NodeRun.Blocked -> KEdgeStyle(color = off, dashed = true)
                        else -> KEdgeStyle()
                    }
                },
                overlay = {
                    KGraphControls(state, Modifier.align(Alignment.TopEnd).padding(8.dp))
                    KMiniMap(state, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                },
            ) { node ->
                val run = engine.runOf(node.id)
                val title = when (node.kind) {
                    "number" -> "Number"
                    "delay" -> "Slow step"
                    "add" -> "Add"
                    "divide" -> "Divide"
                    else -> "Result"
                }
                KNode(node, title, onDoubleClick = { inspecting = node.id }, collapsible = true) {
                    when (node.kind) {
                        "number" -> Content {
                            KText((node.data as Float).toString().take(4))
                            KSlider(node.data as Float, { state.execute(GraphCommand.UpdateNodeData(node.id, it)) }, valueRange = 0f..10f)
                        }
                        "delay" -> Content {
                            KText("Takes ${((node.data as Float) * 10).toInt() / 10f} s")
                            KSlider(node.data as Float, { state.execute(GraphCommand.UpdateNodeData(node.id, it)) }, valueRange = 0f..5f)
                        }
                    }
                    when (node.kind) {
                        "number" -> Output("out", "Value")
                        "delay" -> { Input("in", "In"); Output("out", "Out") }
                        "result" -> {
                            Input("in", "Value")
                            Content { KText((run as? NodeRun.Done)?.outputs?.get(PortId("out"))?.toString()?.take(8) ?: "-") }
                        }
                        else -> { Input("a", "A"); Input("b", "B"); Output("out", "Result") }
                    }
                    if (node.kind != "number") Collapsible("Status") { Content { StatusLine(run) } }
                }
            }
        }
    }
    inspecting?.let { id ->
        val node = state.graph.node(id)
        if (node == null) inspecting = null
        else KDialog(onDismissRequest = { inspecting = null }, title = "Inspect ${node.kind} ${node.id}", fullScreen = true, showCloseButton = true) {
            KNodeInspector(
                engine, node, Modifier.weight(1f), state = state, execution = viewed,
                parameters = {
                    when (node.kind) {
                        "number", "delay" -> {
                            KText(if (node.kind == "number") "Value" else "Delay in seconds")
                            KSlider(node.data as Float, { state.execute(GraphCommand.UpdateNodeData(node.id, it)) }, valueRange = 0f..(if (node.kind == "number") 10f else 5f))
                        }
                        else -> KText("This node only combines its inputs.")
                    }
                },
                parseInput = { _, text -> (ValueJson().decode(JsonValue.parse(text)) as? Double)?.toFloat() ?: ValueJson().decode(JsonValue.parse(text)) },
            )
        }
    }
}

