package tech.kloos.kompound.catalog

import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.showcase.graph.LogicKinds
import tech.kloos.kompound.showcase.graph.LogicPreset
import tech.kloos.kompound.showcase.graph.LogicSimulation
import tech.kloos.kompound.showcase.graph.LogicSwitch
import tech.kloos.kompound.showcase.graph.logicCircuit
import kotlin.test.Test
import kotlin.test.assertEquals

class LogicSimulationTest {
    private fun out(id: String) = PortRef(NodeId(id), PortId("out"))

    private fun Graph.with(id: String, on: Boolean): Graph {
        val node = node(NodeId(id))!!
        return withNode(node.copy(data = (node.data as LogicSwitch).copy(on = on)))
    }

    private fun Graph.settle(prev: Map<PortRef, Boolean> = emptyMap()) = LogicSimulation.settle(this, clock = false, previous = prev)

    @Test
    fun gatesFollowTheirTruthTables() {
        val table = mapOf(
            "and" to listOf(false, false, false, true),
            "or" to listOf(false, true, true, true),
            "xor" to listOf(false, true, true, false),
            "nand" to listOf(true, true, true, false),
            "nor" to listOf(true, false, false, false),
            "xnor" to listOf(true, false, false, true),
        )
        for ((kind, expected) in table) {
            val got = listOf(false to false, false to true, true to false, true to true).map { (a, b) -> LogicSimulation.gate(kind, a, b) }
            assertEquals(expected, got, kind)
        }
        assertEquals(true, LogicSimulation.gate("not", false, false))
        assertEquals(false, LogicSimulation.gate("not", true, false))
    }

    @Test
    fun halfAdderAddsTwoBits() {
        val base = logicCircuit(LogicPreset.HalfAdder)
        for (a in listOf(false, true)) for (b in listOf(false, true)) {
            val g = base.with("a", a).with("b", b)
            val values = g.settle()
            assertEquals(a != b, values[out("sum")], "sum $a $b")
            assertEquals(a && b, values[out("carry")], "carry $a $b")
        }
    }

    @Test
    fun fullAdderAddsThreeBits() {
        val base = logicCircuit(LogicPreset.FullAdder)
        for (a in listOf(false, true)) for (b in listOf(false, true)) for (c in listOf(false, true)) {
            val values = base.with("a", a).with("b", b).with("cin", c).settle()
            val total = listOf(a, b, c).count { it }
            assertEquals(total % 2 == 1, values[out("x2")], "sum $a $b $c")
            assertEquals(total >= 2, values[out("or")], "carry $a $b $c")
        }
    }

    @Test
    fun theSrLatchHoldsItsStateBetweenEdits() {
        var g = logicCircuit(LogicPreset.SrLatch)
        var values = g.settle()
        assertEquals(true, values[out("nor1")] != values[out("nor2")], "settles in one of its two states")
        g = g.with("s", true)
        values = g.settle(values)
        assertEquals(true, values[out("nor1")], "set: Q is true")
        assertEquals(false, values[out("nor2")])
        g = g.with("s", false)
        values = g.settle(values)
        assertEquals(true, values[out("nor1")], "holds after set is released")
        g = g.with("r", true)
        values = g.settle(values)
        assertEquals(false, values[out("nor1")], "reset: Q is false")
        assertEquals(true, values[out("nor2")])
        g = g.with("r", false)
        values = g.settle(values)
        assertEquals(false, values[out("nor1")], "holds after reset is released")
    }

    @Test
    fun theClockDrivesTheBlinker() {
        val g = logicCircuit(LogicPreset.Blinker)
        val low = LogicSimulation.settle(g, clock = false)
        val high = LogicSimulation.settle(g, clock = true)
        assertEquals(true, low[out("n")])
        assertEquals(false, high[out("n")])
        assertEquals(false, low[out("x")])
        assertEquals(true, high[out("x")])
    }

    @Test
    fun unconnectedInputsReadFalseAndKindsAreKnown() {
        val g = Graph.of(listOf(GraphNode(NodeId("g"), "nor", androidx.compose.ui.geometry.Offset.Zero, listOf(
            tech.kloos.kompound.graph.model.PortSpec.input("a"), tech.kloos.kompound.graph.model.PortSpec.input("b"), tech.kloos.kompound.graph.model.PortSpec.output("out")))))
        assertEquals(true, g.settle()[out("g")])
        assertEquals(7, LogicKinds.Gates.size)
    }

    @Test
    fun everyPresetSurvivesAJsonRoundTrip() {
        for (preset in LogicPreset.entries) {
            val g = logicCircuit(preset)
            val back = tech.kloos.kompound.showcase.graph.LogicJson.let { it.decode(it.encode(g)).graph }
            assertEquals(g.nodes, back.nodes, preset.name)
            assertEquals(g.edges, back.edges, preset.name)
        }
    }
}
