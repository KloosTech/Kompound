package tech.kloos.kompound.showcase.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.KNode
import tech.kloos.kompound.graph.KNodeGraph
import tech.kloos.kompound.graph.inspector.KExecutionList
import tech.kloos.kompound.graph.inspector.edgeLabel
import tech.kloos.kompound.graph.inspector.nodeStatus
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.runtime.EventPolicy
import tech.kloos.kompound.graph.runtime.Execution
import tech.kloos.kompound.graph.runtime.NodeRun
import tech.kloos.kompound.graph.runtime.TriggerContext
import tech.kloos.kompound.graph.runtime.TriggerRunner
import tech.kloos.kompound.graph.runtime.rememberGraphEngine
import tech.kloos.kompound.graph.runtime.singleOutputRunner
import tech.kloos.kompound.text.KText

private const val Usage_triggers = """import tech.kloos.kompound.graph.runtime.*

// A trigger waits for something outside the graph and fires events; every event runs what is downstream of it once, isolated from the others.
val triggers = mapOf(
    "clock" to TriggerRunner { ctx ->
        var tick = 0
        while (true) { delay(period); ctx.fire(mapOf("out" to ++tick)) }      // suspends while armed: the node is Listening
    },
    "webhook" to TriggerRunner { ctx -> server.onRequest { body -> ctx.fire(mapOf("out" to body)) }; awaitCancellation() },
)

val engine = rememberGraphEngine(
    state, runners,
    triggers = triggers,
    eventPolicy = { node -> EventPolicy(EventPolicy.Mode.Queue, maxConcurrent = 2, maxQueued = 16) },
)
// engine.isListening: a trigger is armed. engine.isBusy: a runner is working (awaitIdle() returns between events).
// Every event is an Execution with trigger = TraceTrigger.Event: pick it in KExecutionList to see what it received and produced."""

private class WebhookHolder { var ctx: TriggerContext? = null }

@OptIn(ExperimentalLayoutApi::class)
@KompoundDemo(
    id = "graph.triggers",
    title = "Triggers and event runs",
    description = "Trigger nodes that wait for outside events (a timer, a simulated webhook): each event runs what is downstream once, isolated, and shows up as its own run.",
    category = KompoundCategory.Graph,
    tags = ["graph", "trigger", "event", "schedule", "webhook", "listen", "engine"],
    since = "0.2.0",
    status = "Experimental",
    usage = Usage_triggers,
)
@Composable
fun DemoScope.KTriggersDemo() {
    val periodMs = floatControl("Clock period (ms)", 300f..3000f, 1200f).toLong()
    val mode = choiceControl("When events pile up", EventPolicy.Mode.entries, EventPolicy.Mode.Queue) { it.name }
    val clockOn = boolControl("Clock armed", true)
    val webhook = remember { WebhookHolder() }
    val state = remember {
        KGraphState(
            Graph.of(
                listOf(
                    GraphNode(NodeId("clock"), "clock", Offset(0f, 0f), listOf(PortSpec.output("out", "Tick")), 0),
                    GraphNode(NodeId("hook"), "webhook", Offset(0f, 160f), listOf(PortSpec.output("out", "Body"))),
                    GraphNode(NodeId("square"), "square", Offset(300f, 60f), listOf(PortSpec.input("a", "In"), PortSpec.output("out", "Squared"))),
                ),
                listOf(
                    Edge(EdgeId("c"), PortRef(NodeId("clock"), PortId("out")), PortRef(NodeId("square"), PortId("a"))),
                    Edge(EdgeId("h"), PortRef(NodeId("hook"), PortId("out")), PortRef(NodeId("square"), PortId("a"))),
                ),
            ),
            gridStep = 24f,
        )
    }
    // The clock's period is its node data: editing it re-arms the trigger.
    val period = periodMs
    val triggers = remember {
        mapOf(
            "clock" to TriggerRunner { ctx ->
                var tick = 0
                while (true) {
                    delay((ctx.node.data as? Long) ?: 1200L)
                    ctx.fire(mapOf("out" to ++tick))
                }
            },
            "webhook" to TriggerRunner { ctx -> webhook.ctx = ctx; kotlinx.coroutines.awaitCancellation() },
        )
    }
    val engine = rememberGraphEngine(
        state, mapOf("square" to singleOutputRunner { _, i -> delay(400); (i["a"] as Int).let { it * it } }),
        triggers = triggers, eventPolicy = { EventPolicy(mode, maxConcurrent = 1, maxQueued = 8) },
    )
    androidx.compose.runtime.LaunchedEffect(period, clockOn) {
        state.execute(tech.kloos.kompound.graph.model.GraphCommand.UpdateNodeData(NodeId("clock"), period))
        state.execute(tech.kloos.kompound.graph.model.GraphCommand.SetPin(NodeId("clock"), null))
        if (!clockOn) engine.stop() else engine.start()
    }
    var selected by remember { mutableStateOf<Execution?>(null) }
    var hits by remember { mutableStateOf(0) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton({ hits++; webhook.ctx?.fire(mapOf("out" to hits)) }, enabled = engine.isListening) { KText("Send webhook") }
            KButton({ repeat(5) { hits++; webhook.ctx?.fire(mapOf("out" to hits)) } }, variant = KButtonVariant.Tonal, enabled = engine.isListening) { KText("Send 5 at once") }
        }
        KText(if (engine.isListening) "Listening: ${engine.runs.values.count { it is NodeRun.Listening }} trigger(s) armed. Busy: ${engine.isBusy}" else "Not listening")
        KExecutionList(engine, selected, { selected = it }, Modifier.fillMaxWidth().height(160.dp), showTriggerFilter = true)
        GraphFrame(state, 320) { frame ->
            KNodeGraph(
                state, frame, fitOnFirstLayout = true,
                nodeStatus = { engine.nodeStatus(it, selected) },
                edgeLabel = { engine.edgeLabel(it, selected) },
            ) { node ->
                KNode(node, when (node.kind) { "clock" -> "Clock"; "webhook" -> "Webhook"; else -> "Square" }) {
                    for (p in node.ports) if (p.direction == tech.kloos.kompound.graph.model.PortDirection.Input) Input(p.id.value, p.label) else Output(p.id.value, p.label)
                }
            }
        }
    }
}
