package tech.kloos.kompound.showcase.graph

import androidx.compose.ui.geometry.Offset
import tech.kloos.kompound.graph.KNodeType
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.PortType
import tech.kloos.kompound.graph.serialization.GraphJson
import tech.kloos.kompound.graph.serialization.JsonBool
import tech.kloos.kompound.graph.serialization.JsonObject
import tech.kloos.kompound.graph.serialization.JsonString
import tech.kloos.kompound.graph.serialization.jsonObjectOf
import tech.kloos.kompound.graph.serialization.nodeDataCodec

/** Data of a `switch` node: a name and whether it is on. */
data class LogicSwitch(val label: String, val on: Boolean)

/** JSON codec of the logic demo: switches keep their label and state. */
val LogicJson: GraphJson by lazy { GraphJson(
    nodeData = mapOf(
        LogicKinds.Switch to nodeDataCodec<LogicSwitch>(
            encode = { jsonObjectOf("label" to JsonString(it.label), "on" to JsonBool(it.on)) },
            decode = { v -> (v as JsonObject).let { LogicSwitch((it["label"] as? JsonString)?.value ?: "Switch", (it["on"] as? JsonBool)?.value ?: false) } },
        ),
    ),
    portTypes = listOf(LogicBit),
) }

/** Boolean wires: every port of the logic demo has this type. */
val LogicBit: PortType = PortType.of("bit")

/** Kinds of node of the logic demo. */
object LogicKinds {
    const val Switch = "switch"
    const val Clock = "clock"
    const val Led = "led"
    const val Not = "not"
    val TwoInput = listOf("and", "or", "xor", "nand", "nor", "xnor")
    val Gates = TwoInput + Not

    fun title(kind: String): String = kind.uppercase()
}

/**
 * Evaluates a circuit of switches, a clock, gates and LEDs. Gates are updated one after another (in node order, using the values
 * just computed) until nothing changes, so circuits with feedback such as an SR latch settle and keep their state when `previous`
 * (the result of the last call) is passed back in. Inputs that are not connected read as false.
 */
object LogicSimulation {
    private const val MaxPasses = 64

    fun gate(kind: String, a: Boolean, b: Boolean): Boolean = when (kind) {
        "and" -> a && b
        "or" -> a || b
        "xor" -> a != b
        "nand" -> !(a && b)
        "nor" -> !(a || b)
        "xnor" -> a == b
        "not" -> !a
        else -> false
    }

    /** The value on every output port after settling. */
    fun settle(graph: Graph, clock: Boolean, previous: Map<PortRef, Boolean> = emptyMap()): Map<PortRef, Boolean> {
        val values = HashMap<PortRef, Boolean>()
        for ((ref, v) in previous) if (graph.port(ref) != null) values[ref] = v
        val out = PortId("out")
        fun input(node: GraphNode, port: String): Boolean =
            graph.edgesAt(PortRef(node.id, PortId(port))).firstOrNull()?.let { values[it.from] } ?: false
        val producers = graph.nodes.values.filter { it.kind == LogicKinds.Switch || it.kind == LogicKinds.Clock || it.kind in LogicKinds.Gates }
        repeat(MaxPasses) {
            var changed = false
            for (node in producers) {
                val next = when (node.kind) {
                    LogicKinds.Switch -> (node.data as? LogicSwitch)?.on ?: false
                    LogicKinds.Clock -> clock
                    else -> gate(node.kind, input(node, "a"), if (node.kind == LogicKinds.Not) false else input(node, "b"))
                }
                val ref = PortRef(node.id, out)
                if (values[ref] != next) { values[ref] = next; changed = true }
            }
            if (!changed) return values
        }
        return values
    }

    /** The value arriving at the input [ref] (false when nothing is connected). */
    fun inputValue(graph: Graph, values: Map<PortRef, Boolean>, ref: PortRef): Boolean =
        graph.edgesAt(ref).firstOrNull()?.let { values[it.from] } ?: false
}

private fun gatePorts(kind: String): List<PortSpec> =
    if (kind == LogicKinds.Not) listOf(PortSpec.input("a", "In", LogicBit), PortSpec.output("out", "Out", LogicBit))
    else listOf(PortSpec.input("a", "A", LogicBit), PortSpec.input("b", "B", LogicBit), PortSpec.output("out", "Out", LogicBit))

/** The nodes offered by the add menu of the logic demo. */
val LogicNodeTypes: List<KNodeType> = buildList {
    add(KNodeType(LogicKinds.Switch, "Switch", listOf(PortSpec.output("out", "Out", LogicBit)), "Inputs", { LogicSwitch("Switch", false) }))
    add(KNodeType(LogicKinds.Clock, "Clock", listOf(PortSpec.output("out", "Out", LogicBit)), "Inputs"))
    for (kind in LogicKinds.Gates) add(KNodeType(kind, LogicKinds.title(kind), gatePorts(kind), "Gates"))
    add(KNodeType(LogicKinds.Led, "LED", listOf(PortSpec.input("in", "In", LogicBit)), "Outputs", { "LED" }))
}

/** Example circuits of the logic demo. */
enum class LogicPreset(val title: String) {
    HalfAdder("Half adder"),
    FullAdder("Full adder"),
    SrLatch("SR latch"),
    Blinker("Clock and inverter"),
    Sandbox("Empty canvas"),
}

private class Builder {
    val nodes = ArrayList<GraphNode>()
    val edges = ArrayList<Edge>()

    fun switch(id: String, label: String, x: Float, y: Float, on: Boolean = false) {
        nodes += GraphNode(NodeId(id), LogicKinds.Switch, Offset(x, y), listOf(PortSpec.output("out", "Out", LogicBit)), LogicSwitch(label, on))
    }

    fun clock(id: String, x: Float, y: Float) {
        nodes += GraphNode(NodeId(id), LogicKinds.Clock, Offset(x, y), listOf(PortSpec.output("out", "Out", LogicBit)))
    }

    fun gate(id: String, kind: String, x: Float, y: Float) {
        nodes += GraphNode(NodeId(id), kind, Offset(x, y), gatePorts(kind))
    }

    fun led(id: String, label: String, x: Float, y: Float) {
        nodes += GraphNode(NodeId(id), LogicKinds.Led, Offset(x, y), listOf(PortSpec.input("in", "In", LogicBit)), label)
    }

    /** `wire("a", "xor.a")` connects the `out` port of node `a` to the input `a` of node `xor`. */
    fun wire(from: String, to: String) {
        val (toNode, toPort) = to.split(".")
        val fromNode = from.substringBefore(".")
        val fromPort = if ("." in from) from.substringAfter(".") else "out"
        edges += Edge(EdgeId("$fromNode.$fromPort->$to"), PortRef(NodeId(fromNode), PortId(fromPort)), PortRef(NodeId(toNode), PortId(toPort)))
    }

    fun build(): Graph = Graph.of(nodes, edges)
}

fun logicCircuit(preset: LogicPreset): Graph {
    val b = Builder()
    when (preset) {
        LogicPreset.HalfAdder -> {
            b.switch("a", "A", 0f, 0f, on = true)
            b.switch("b", "B", 0f, 240f, on = true)
            b.gate("sum", "xor", 360f, 0f)
            b.gate("carry", "and", 360f, 300f)
            b.led("led_sum", "Sum", 720f, 20f)
            b.led("led_carry", "Carry", 720f, 320f)
            b.wire("a", "sum.a"); b.wire("b", "sum.b")
            b.wire("a", "carry.a"); b.wire("b", "carry.b")
            b.wire("sum", "led_sum.in"); b.wire("carry", "led_carry.in")
        }
        LogicPreset.FullAdder -> {
            b.switch("a", "A", 0f, 0f, on = true)
            b.switch("b", "B", 0f, 220f, on = true)
            b.switch("cin", "Carry in", 0f, 480f)
            b.gate("x1", "xor", 340f, 40f)
            b.gate("a1", "and", 340f, 330f)
            b.gate("x2", "xor", 700f, 100f)
            b.gate("a2", "and", 700f, 400f)
            b.gate("or", "or", 1060f, 380f)
            b.led("led_sum", "Sum", 1060f, 100f)
            b.led("led_cout", "Carry out", 1400f, 400f)
            b.wire("a", "x1.a"); b.wire("b", "x1.b")
            b.wire("a", "a1.a"); b.wire("b", "a1.b")
            b.wire("x1", "x2.a"); b.wire("cin", "x2.b")
            b.wire("x1", "a2.a"); b.wire("cin", "a2.b")
            b.wire("a1", "or.a"); b.wire("a2", "or.b")
            b.wire("x2", "led_sum.in"); b.wire("or", "led_cout.in")
        }
        LogicPreset.SrLatch -> {
            b.switch("r", "Reset", 0f, 0f)
            b.switch("s", "Set", 0f, 380f)
            b.gate("nor1", "nor", 380f, 0f)
            b.gate("nor2", "nor", 380f, 380f)
            b.led("q", "Q", 760f, 0f)
            b.led("qn", "not Q", 760f, 380f)
            b.wire("r", "nor1.a"); b.wire("nor2", "nor1.b")
            b.wire("s", "nor2.a"); b.wire("nor1", "nor2.b")
            b.wire("nor1", "q.in"); b.wire("nor2", "qn.in")
        }
        LogicPreset.Blinker -> {
            b.clock("clk", 0f, 0f)
            b.switch("invert", "Invert", 0f, 200f)
            b.gate("x", "xor", 340f, 60f)
            b.gate("n", "not", 340f, 340f)
            b.led("led_x", "Output", 700f, 80f)
            b.led("led_n", "Clock inverted", 700f, 340f)
            b.wire("clk", "x.a"); b.wire("invert", "x.b")
            b.wire("clk", "n.a")
            b.wire("x", "led_x.in"); b.wire("n", "led_n.in")
        }
        LogicPreset.Sandbox -> {
            b.switch("a", "A", 0f, 0f)
            b.switch("b", "B", 0f, 220f)
            b.gate("and", "and", 340f, 60f)
            b.led("led", "Output", 700f, 80f)
        }
    }
    return b.build()
}
