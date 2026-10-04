package tech.kloos.kompound.graph.model

import androidx.compose.ui.geometry.Offset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal fun node(id: String, vararg ports: PortSpec, at: Offset = Offset.Zero, data: Any? = null) =
    GraphNode(NodeId(id), "test", at, ports.toList(), data)

internal fun math(id: String, at: Offset = Offset.Zero) =
    node(id, PortSpec.input("a"), PortSpec.input("b"), PortSpec.output("out"), at = at)

internal fun ref(node: String, port: String) = PortRef(NodeId(node), PortId(port))

class GraphTest {
    @Test
    fun addsAndFindsNodesAndEdges() {
        val g = Graph.of(listOf(math("n1"), math("n2")), listOf(Edge(EdgeId("e"), ref("n1", "out"), ref("n2", "a"))))
        assertNotNull(g.node(NodeId("n1")))
        assertEquals(listOf(EdgeId("e")), g.edgesAt(ref("n1", "out")).map { it.id })
        assertTrue(g.isConnected(ref("n2", "a")))
        assertFalse(g.isConnected(ref("n2", "b")))
        assertEquals(PortDirection.Output, g.port(ref("n1", "out"))?.direction)
        assertNull(g.port(ref("n1", "missing")))
    }

    @Test
    fun portsDefaultToTheCapacityOfTheirDirection() {
        assertEquals(PortCapacity.One, PortSpec.input("a").capacity)
        assertEquals(PortCapacity.Many, PortSpec.output("o").capacity)
    }

    @Test
    fun edgesWithMissingEndsAreDropped() {
        val g = Graph.of(listOf(math("n1")), listOf(Edge(EdgeId("e"), ref("n1", "out"), ref("ghost", "a")), Edge(EdgeId("f"), ref("n1", "nope"), ref("n1", "a"))))
        assertTrue(g.edges.isEmpty())
        assertSame(g, g.withEdge(Edge(EdgeId("e"), ref("n1", "out"), ref("ghost", "a"))))
    }

    @Test
    fun removingANodeRemovesItsEdges() {
        val g = Graph.of(listOf(math("n1"), math("n2"), math("n3")), listOf(
            Edge(EdgeId("e1"), ref("n1", "out"), ref("n2", "a")), Edge(EdgeId("e2"), ref("n2", "out"), ref("n3", "a"))))
        val after = g.withoutNodes(setOf(NodeId("n2")))
        assertEquals(setOf(NodeId("n1"), NodeId("n3")), after.nodes.keys)
        assertTrue(after.edges.isEmpty())
        assertFalse(after.isConnected(ref("n3", "a")))
    }

    @Test
    fun replacingANodeKeepsEdgesWhosePortsSurvive() {
        val g = Graph.of(listOf(math("n1"), math("n2")), listOf(Edge(EdgeId("e"), ref("n1", "out"), ref("n2", "a"))))
        assertEquals(1, g.withNode(node("n2", PortSpec.input("a"), PortSpec.output("x"))).edges.size)
        assertEquals(0, g.withNode(node("n2", PortSpec.output("x"))).edges.size)
    }

    @Test
    fun movingChangesOnlyPositions() {
        val g = Graph.of(listOf(math("n1", Offset(1f, 2f))))
        assertEquals(Offset(11f, 22f), g.withMoved(mapOf(NodeId("n1") to Offset(10f, 20f))).node(NodeId("n1"))!!.position)
        assertSame(g.nodes.keys.single(), g.withMoved(mapOf(NodeId("zzz") to Offset(1f, 1f))).nodes.keys.single())
    }

    @Test
    fun nodeOrderIsPreserved() {
        val g = Graph.of(listOf(math("c"), math("a"), math("b")))
        assertEquals(listOf("c", "a", "b"), g.nodes.keys.map { it.value })
    }
}

class ConnectionPolicyTest {
    private val graph = Graph.of(listOf(math("n1"), math("n2"), math("n3"), node("src", PortSpec.output("t", type = PortType.of("text")), PortSpec.output("n", type = PortType.of("number"))),
        node("sink", PortSpec.input("n", type = PortType.of("number")), PortSpec.input("any"))))
    private val policy = ConnectionPolicy()

    @Test
    fun connectsAnOutputToAnInputInEitherOrder() {
        val a = policy.check(graph, ref("n1", "out"), ref("n2", "a"))
        val b = policy.check(graph, ref("n2", "a"), ref("n1", "out"))
        assertEquals(ConnectionCheck.Allowed(ref("n1", "out"), ref("n2", "a")), a)
        assertEquals(a, b)
    }

    @Test
    fun rejectsTheObviousMistakes() {
        fun reason(x: PortRef, y: PortRef) = (policy.check(graph, x, y) as ConnectionCheck.Rejected).reason
        assertEquals(ConnectionRejection.SameDirection, reason(ref("n1", "a"), ref("n2", "a")))
        assertEquals(ConnectionRejection.SameDirection, reason(ref("n1", "out"), ref("n2", "out")))
        assertEquals(ConnectionRejection.SameNode, reason(ref("n1", "out"), ref("n1", "a")))
        assertEquals(ConnectionRejection.UnknownPort, reason(ref("n1", "out"), ref("n9", "a")))
        assertEquals(ConnectionRejection.TypeMismatch, reason(ref("src", "t"), ref("sink", "n")))
    }

    @Test
    fun anyAcceptsEverythingAndTypesMatchById() {
        assertTrue(policy.check(graph, ref("src", "t"), ref("sink", "any")) is ConnectionCheck.Allowed)
        assertTrue(policy.check(graph, ref("src", "n"), ref("sink", "n")) is ConnectionCheck.Allowed)
    }

    @Test
    fun anInputTakingOneEdgeReplacesItsOldEdge() {
        val g = graph.withEdge(Edge(EdgeId("old"), ref("n1", "out"), ref("n3", "a")))
        val check = policy.check(g, ref("n2", "out"), ref("n3", "a")) as ConnectionCheck.Allowed
        assertEquals(listOf(EdgeId("old")), check.replaces)
        val strict = ConnectionPolicy(replaceExisting = false).check(g, ref("n2", "out"), ref("n3", "a"))
        assertEquals(ConnectionCheck.Rejected(ConnectionRejection.CapacityExceeded), strict)
    }

    @Test
    fun duplicateConnectionsAreRejected() {
        val g = graph.withEdge(Edge(EdgeId("e"), ref("n1", "out"), ref("n2", "a")))
        assertEquals(ConnectionCheck.Rejected(ConnectionRejection.AlreadyConnected), policy.check(g, ref("n1", "out"), ref("n2", "a")))
    }

    @Test
    fun cyclesAreRejectedUnlessAllowed() {
        val g = graph.withEdge(Edge(EdgeId("e1"), ref("n1", "out"), ref("n2", "a"))).withEdge(Edge(EdgeId("e2"), ref("n2", "out"), ref("n3", "a")))
        assertEquals(ConnectionCheck.Rejected(ConnectionRejection.WouldCreateCycle), policy.check(g, ref("n3", "out"), ref("n1", "a")))
        assertTrue(ConnectionPolicy(allowCycles = true).check(g, ref("n3", "out"), ref("n1", "a")) is ConnectionCheck.Allowed)
    }

    @Test
    fun customTypeRule() {
        val loose = ConnectionPolicy(typeRule = { _, _ -> true })
        assertTrue(loose.check(graph, ref("src", "t"), ref("sink", "n")) is ConnectionCheck.Allowed)
    }
}

class GraphDocumentTest {
    private fun doc() = GraphDocument(Graph.of(listOf(math("n1", Offset(0f, 0f)), math("n2", Offset(100f, 0f)), math("n3", Offset(200f, 0f)))))

    @Test
    fun executeUndoRedoRoundTripsEveryCommandKind() {
        val d = doc()
        val start = d.graph
        val commands = listOf(
            GraphCommand.AddNode(math("n4", Offset(5f, 5f))),
            GraphCommand.MoveNodes(mapOf(NodeId("n1") to Offset(7f, 9f))),
            GraphCommand.UpdateNodeData(NodeId("n1"), "payload"),
            GraphCommand.Connect(Edge(EdgeId("e"), ref("n1", "out"), ref("n2", "a"))),
            GraphCommand.Disconnect(setOf(EdgeId("e"))),
            GraphCommand.Connect(Edge(EdgeId("e"), ref("n1", "out"), ref("n2", "a"))),
            GraphCommand.RemoveNodes(setOf(NodeId("n2"))),
        )
        val states = mutableListOf(d.graph)
        commands.forEach { assertTrue(d.execute(it), "$it changed nothing"); states += d.graph }
        for (i in commands.indices.reversed()) { assertTrue(d.undo()); assertEquals(states[i], d.graph, "after undoing ${commands[i]}") }
        assertEquals(start, d.graph)
        for (i in commands.indices) { assertTrue(d.redo()); assertEquals(states[i + 1], d.graph, "after redoing ${commands[i]}") }
    }

    @Test
    fun noOpCommandsAreNotRecorded() {
        val d = doc()
        assertFalse(d.execute(GraphCommand.RemoveNodes(setOf(NodeId("ghost")))))
        assertFalse(d.execute(GraphCommand.MoveNodes(mapOf(NodeId("n1") to Offset.Zero))))
        assertFalse(d.execute(GraphCommand.UpdateNodeData(NodeId("n1"), null)))
        assertFalse(d.canUndo)
    }

    @Test
    fun aNewEditClearsRedo() {
        val d = doc()
        d.execute(GraphCommand.MoveNodes(mapOf(NodeId("n1") to Offset(1f, 1f))))
        d.undo()
        assertTrue(d.canRedo)
        d.execute(GraphCommand.MoveNodes(mapOf(NodeId("n2") to Offset(1f, 1f))))
        assertFalse(d.canRedo)
        assertFalse(d.redo())
    }

    @Test
    fun removingANodeIsUndoneWithItsEdges() {
        val d = doc()
        d.connect(ref("n1", "out"), ref("n2", "a"))
        d.connect(ref("n2", "out"), ref("n3", "a"))
        val before = d.graph
        d.execute(GraphCommand.RemoveNodes(setOf(NodeId("n2"))))
        assertEquals(0, d.graph.edges.size)
        d.undo()
        assertEquals(before, d.graph)
    }

    @Test
    fun connectingReplacesAnOldEdgeInOneUndoStep() {
        val d = doc()
        d.connect(ref("n1", "out"), ref("n3", "a"))
        val one = d.graph
        d.connect(ref("n2", "out"), ref("n3", "a"))
        assertEquals(listOf("n2.out->n3.a"), d.graph.edges.keys.map { it.value })
        d.undo()
        assertEquals(one, d.graph)
    }

    @Test
    fun rejectedConnectionsChangeNothing() {
        val d = doc()
        val check = d.connect(ref("n1", "a"), ref("n2", "a"))
        assertTrue(check is ConnectionCheck.Rejected)
        assertFalse(d.canUndo)
    }

    @Test
    fun historyIsBounded() {
        val d = GraphDocument(Graph.of(listOf(math("n1"))), maxHistory = 3)
        repeat(10) { d.execute(GraphCommand.MoveNodes(mapOf(NodeId("n1") to Offset(1f, 0f)))) }
        var undone = 0
        while (d.undo()) undone++
        assertEquals(3, undone)
    }

    @Test
    fun resetReplacesTheGraphAndForgetsHistory() {
        val d = doc()
        d.execute(GraphCommand.MoveNodes(mapOf(NodeId("n1") to Offset(1f, 1f))))
        d.reset(Graph.Empty)
        assertEquals(Graph.Empty, d.graph)
        assertFalse(d.canUndo)
    }

    /** Random edit sequences keep the graph consistent, and undo/redo restore every intermediate state exactly. */
    @Test
    fun randomEditingKeepsInvariantsAndUndoRedoAreExact() {
        for (seed in 1..40) {
            val random = Random(seed)
            val d = GraphDocument(Graph.of((1..6).map { math("n$it", Offset(it * 10f, 0f)) }), maxHistory = 1_000)
            val states = mutableListOf(d.graph)
            repeat(80) {
                val id = "n${random.nextInt(1, 8)}"
                val other = "n${random.nextInt(1, 8)}"
                val before = d.graph
                val gid = GroupId("g${random.nextInt(1, 4)}")
                when (random.nextInt(9)) {
                    0 -> d.connect(ref(id, listOf("a", "b", "out").random(random)), ref(other, listOf("a", "b", "out").random(random)))
                    1 -> d.execute(GraphCommand.RemoveNodes(setOf(NodeId(id))))
                    2 -> d.execute(GraphCommand.AddNode(math(id, Offset(random.nextFloat() * 100, 0f))))
                    3 -> d.execute(GraphCommand.MoveNodes(mapOf(NodeId(id) to Offset(random.nextFloat() * 10 + 1, 1f))))
                    4 -> d.execute(GraphCommand.Disconnect(d.graph.edges.keys.take(random.nextInt(0, 3)).toSet()))
                    5 -> d.execute(GraphCommand.UpdateNodeData(NodeId(id), random.nextInt()))
                    6 -> d.execute(GraphCommand.PutGroup(NodeGroup(gid, "Group ${random.nextInt(5)}", random.nextBoolean(), random.nextInt(7))))
                    7 -> d.execute(GraphCommand.AssignGroups(mapOf(NodeId(id) to (if (random.nextBoolean()) gid else null), NodeId(other) to gid)))
                    else -> d.execute(GraphCommand.RemoveGroup(gid))
                }
                assertConsistent(d.graph)
                assertAcyclic(d.graph)
                if (d.graph != before) states += d.graph
            }
            for (i in states.indices.reversed().drop(1)) { assertTrue(d.undo()); assertEquals(states[i], d.graph, "seed $seed undo to $i") }
            assertFalse(d.undo())
            for (i in 1 until states.size) { assertTrue(d.redo()); assertEquals(states[i], d.graph, "seed $seed redo to $i") }
        }
    }

    private fun assertConsistent(g: Graph) {
        for (n in g.nodes.values) n.group?.let { assertNotNull(g.group(it), "${n.id} is in missing group $it") }
        for (gr in g.groups.keys) assertTrue(g.membersOf(gr).all { g.node(it)?.group == gr })
        for (e in g.edges.values) {
            assertNotNull(g.port(e.from), "dangling ${e.from}")
            assertNotNull(g.port(e.to), "dangling ${e.to}")
            assertEquals(PortDirection.Output, g.port(e.from)!!.direction)
            assertEquals(PortDirection.Input, g.port(e.to)!!.direction)
            assertTrue(g.edgesAt(e.from).contains(e) && g.edgesAt(e.to).contains(e))
        }
        for (n in g.nodes.values) for (p in n.ports) if (p.capacity == PortCapacity.One) {
            assertTrue(g.edgesAt(PortRef(n.id, p.id)).size <= 1, "${n.id}.${p.id} takes one edge but has several")
        }
    }

    private fun assertAcyclic(g: Graph) {
        val state = HashMap<NodeId, Int>()
        fun visit(n: NodeId): Boolean {
            when (state[n]) { 1 -> return false; 2 -> return true }
            state[n] = 1
            for (e in g.edges.values) if (e.from.node == n && !visit(e.to.node)) return false
            state[n] = 2
            return true
        }
        for (n in g.nodes.keys) assertTrue(visit(n), "cycle through $n")
    }
}

class GroupModelTest {
    private fun graph() = Graph.of(listOf(math("n1"), math("n2"), math("n3")), groups = listOf(NodeGroup(GroupId("g"), "Group")))

    @Test
    fun nodesJoinAndLeaveGroups() {
        val g = graph().withAssigned(mapOf(NodeId("n1") to GroupId("g"), NodeId("n2") to GroupId("g")))
        assertEquals(listOf(NodeId("n1"), NodeId("n2")), g.membersOf(GroupId("g")))
        assertEquals(listOf(NodeId("n2")), g.withAssigned(mapOf(NodeId("n1") to null)).membersOf(GroupId("g")))
        assertEquals(emptyList(), g.withAssigned(mapOf(NodeId("n3") to GroupId("missing"))).membersOf(GroupId("missing")))
    }

    @Test
    fun removingAGroupReleasesItsMembersAndUndoRestoresThem() {
        val d = GraphDocument(graph())
        d.execute(GraphCommand.AssignGroups(mapOf(NodeId("n1") to GroupId("g"), NodeId("n2") to GroupId("g"))))
        val grouped = d.graph
        d.execute(GraphCommand.RemoveGroup(GroupId("g")))
        assertTrue(d.graph.groups.isEmpty() && d.graph.nodes.values.all { it.group == null })
        d.undo()
        assertEquals(grouped, d.graph)
    }

    @Test
    fun removingMembersKeepsTheGroupAndUndoPutsThemBack() {
        val d = GraphDocument(graph())
        d.execute(GraphCommand.AssignGroups(mapOf(NodeId("n1") to GroupId("g"))))
        val before = d.graph
        d.execute(GraphCommand.RemoveNodes(setOf(NodeId("n1"))))
        assertEquals(emptyList(), d.graph.membersOf(GroupId("g")))
        assertNotNull(d.graph.group(GroupId("g")))
        d.undo()
        assertEquals(before, d.graph)
        assertEquals(GroupId("g"), d.graph.node(NodeId("n1"))!!.group)
    }

    @Test
    fun aNodeIsInAtMostOneGroupAndNoOpsAreNotRecorded() {
        val d = GraphDocument(graph().withGroup(NodeGroup(GroupId("h"))))
        d.execute(GraphCommand.AssignGroups(mapOf(NodeId("n1") to GroupId("g"))))
        d.execute(GraphCommand.AssignGroups(mapOf(NodeId("n1") to GroupId("h"))))
        assertEquals(GroupId("h"), d.graph.node(NodeId("n1"))!!.group)
        assertFalse(d.execute(GraphCommand.AssignGroups(mapOf(NodeId("n1") to GroupId("h")))))
        assertFalse(d.execute(GraphCommand.RemoveGroup(GroupId("nope"))))
        assertFalse(d.execute(GraphCommand.PutGroup(NodeGroup(GroupId("h")))))
    }

    @Test
    fun editingAGroupIsUndoable() {
        val d = GraphDocument(graph())
        d.execute(GraphCommand.PutGroup(NodeGroup(GroupId("g"), "Renamed", collapsed = true, color = 3)))
        assertEquals("Renamed", d.graph.group(GroupId("g"))!!.title)
        d.undo()
        assertEquals("Group", d.graph.group(GroupId("g"))!!.title)
        d.undo()
        assertFalse(d.undo(), "nothing left to undo")
    }

    @Test
    fun nodesNamingAnUnknownGroupAreReleasedWhenBuildingAGraph() {
        val g = Graph.of(listOf(math("n1").copy(group = GroupId("ghost"))))
        assertNull(g.node(NodeId("n1"))!!.group)
    }
}

class UpdateNodePortsTest {
    private val out = tech.kloos.kompound.graph.model.PortId("out")

    private fun graph(): Graph = Graph.of(
        listOf(node("a", PortSpec.output("out"), PortSpec.output("extra")), node("b", PortSpec.input("in"), PortSpec.input("old"), PortSpec.output("out"))),
        listOf(
            Edge(EdgeId("e1"), ref("a", "out"), ref("b", "in")),
            Edge(EdgeId("e2"), ref("a", "extra"), ref("b", "old")),
        ),
    )

    @Test
    fun newPortsKeepTheWiresOnPortsThatStillExistAndUndoBringsEverythingBack() {
        val g = graph()
        val newPorts = listOf(PortSpec.input("in"), PortSpec.input("fresh"), PortSpec.output("out"))
        val applied = GraphCommand.UpdateNodePorts(NodeId("b"), newPorts).applyTo(g)!!
        assertEquals(newPorts, applied.graph.node(NodeId("b"))!!.ports)
        assertEquals(setOf("e1"), applied.graph.edges.keys.map { it.value }.toSet(), "wires to the removed port are gone")
        val undone = applied.inverse.applyTo(applied.graph)!!.graph
        assertEquals(g.node(NodeId("b"))!!.ports, undone.node(NodeId("b"))!!.ports)
        assertEquals(g.edges.keys, undone.edges.keys)
    }

    @Test
    fun aPortThatChangesDirectionLosesItsWireAndSamePortsAreANoOp() {
        val g = graph()
        val flipped = listOf(PortSpec.output("in"), PortSpec.input("old"), PortSpec.output("out"))
        val applied = GraphCommand.UpdateNodePorts(NodeId("b"), flipped).applyTo(g)!!
        assertEquals(setOf("e2"), applied.graph.edges.keys.map { it.value }.toSet(), "the wire into the port that turned into an output is dropped")
        assertEquals(null, GraphCommand.UpdateNodePorts(NodeId("b"), g.node(NodeId("b"))!!.ports).applyTo(g))
        assertEquals(null, GraphCommand.UpdateNodePorts(NodeId("nope"), flipped).applyTo(g))
    }

    @Test
    fun theDocumentRecordsItAsOneUndoStepAndKeepsDataAndPin() {
        val doc = GraphDocument(Graph.of(listOf(node("n", PortSpec.input("a")).copy(data = 7, pin = mapOf(out to 1)))))
        doc.execute(GraphCommand.UpdateNodePorts(NodeId("n"), listOf(PortSpec.input("a"), PortSpec.input("b"))))
        val n = doc.graph.node(NodeId("n"))!!
        assertEquals(2, n.ports.size)
        assertEquals(7, n.data)
        assertEquals(mapOf(out to 1), n.pin)
        doc.undo()
        assertEquals(1, doc.graph.node(NodeId("n"))!!.ports.size)
    }
}

class ResizeNodesTest {
    @Test
    fun widthsAreSetAndUndoneExactlyAndNullRemovesThem() {
        val g = Graph.of(listOf(node("a"), node("b").copy(width = 300f)))
        val applied = GraphCommand.ResizeNodes(mapOf(NodeId("a") to 420.5f, NodeId("b") to null)).applyTo(g)!!
        assertEquals(420.5f, applied.graph.node(NodeId("a"))!!.width)
        assertEquals(null, applied.graph.node(NodeId("b"))!!.width)
        val back = applied.inverse.applyTo(applied.graph)!!.graph
        assertEquals(null, back.node(NodeId("a"))!!.width)
        assertEquals(300f, back.node(NodeId("b"))!!.width)
        assertEquals(null, GraphCommand.ResizeNodes(mapOf(NodeId("b") to 300f, NodeId("zzz") to 1f)).applyTo(g), "nothing changes")
    }
}
