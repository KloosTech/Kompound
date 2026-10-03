package tech.kloos.kompound.graph.model

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubgraphTest {
    private val num = PortType.of("number")
    private fun calc(id: String, at: Offset = Offset.Zero) = node(id, PortSpec.input("a", "A", num), PortSpec.input("b", "B", num), PortSpec.output("out", "Result", num), at = at)
    private fun src(id: String, at: Offset = Offset.Zero) = node(id, PortSpec.output("out", "Value", num), at = at)

    private fun e(a: String, ap: String, b: String, bp: String) = Edge(EdgeId("$a.$ap->$b.$bp"), ref(a, ap), ref(b, bp))

    /** s1, s2 -> add -> mul -> sink ; s3 -> mul.b */
    private fun graph() = Graph.of(
        listOf(src("s1", Offset(0f, 0f)), src("s2", Offset(0f, 150f)), calc("add", Offset(300f, 50f)), calc("mul", Offset(600f, 80f)), src("s3", Offset(300f, 300f)), node("sink", PortSpec.input("in", type = num), at = Offset(900f, 80f))),
        listOf(e("s1", "out", "add", "a"), e("s2", "out", "add", "b"), e("add", "out", "mul", "a"), e("s3", "out", "mul", "b"), e("mul", "out", "sink", "in")),
    )

    private fun edgeSet(g: Graph) = g.edges.values.map { "${it.from}->${it.to}" }.toSet()

    @Test
    fun wrappingNodesTurnsTheCrossingWiresIntoPorts() {
        val d = GraphDocument(graph())
        val cmd = Subgraphs.create(d.graph, setOf(NodeId("add"), NodeId("mul")), NodeId("calc"), "Calc")!!
        assertTrue(d.execute(cmd))
        val g = d.graph
        val sub = g.node(NodeId("calc"))!!
        assertEquals(Subgraphs.Kind, sub.kind)
        assertEquals("Calc", sub.data)
        // inputs: add.a (from s1), add.b (from s2), mul.b (from s3); output: mul.out -> sink
        assertEquals(3, sub.ports.count { it.direction == PortDirection.Input })
        assertEquals(1, sub.ports.count { it.direction == PortDirection.Output })
        assertEquals(setOf("A", "B"), sub.ports.filter { it.direction == PortDirection.Input }.map { it.label }.toSet())
        // the nodes moved inside; the rest stayed at the top
        assertEquals(NodeId("calc"), g.node(NodeId("add"))!!.scope)
        assertEquals(NodeId("calc"), g.node(NodeId("mul"))!!.scope)
        assertNull(g.node(NodeId("s1"))!!.scope)
        // the top level now sees s1,s2,s3 -> calc -> sink
        val top = edgeSet(g).filter { it.startsWith("s") || it.startsWith("calc") }
        assertEquals(3, top.count { it.startsWith("s") && "calc." in it })
        assertTrue(edgeSet(g).any { it.startsWith("calc.out1->sink.in") })
        // inside: boundary nodes feed the members, and the wire between them is kept
        assertTrue("add.out->mul.a" in edgeSet(g))
        assertEquals(3, g.nodes.values.count { it.kind == Subgraphs.InputKind && it.scope == NodeId("calc") })
        assertEquals(1, g.nodes.values.count { it.kind == Subgraphs.OutputKind && it.scope == NodeId("calc") })
        assertTrue(edgeSet(g).any { it.startsWith("calc/in/in1.value->") })
    }

    @Test
    fun wrappingIsOneUndoStepAndDissolvingBringsTheGraphBack() {
        val d = GraphDocument(graph())
        val before = d.graph
        d.execute(Subgraphs.create(d.graph, setOf(NodeId("add"), NodeId("mul")), NodeId("calc"))!!)
        val wrapped = d.graph
        d.execute(Subgraphs.dissolve(d.graph, NodeId("calc"))!!)
        assertEquals(before.nodes.keys, d.graph.nodes.keys)
        assertEquals(edgeSet(before), edgeSet(d.graph), "wires are joined end to end again")
        assertTrue(d.graph.nodes.values.all { it.scope == null })
        assertEquals(before.nodes.mapValues { it.value.position }, d.graph.nodes.mapValues { it.value.position })
        d.undo()
        assertEquals(wrapped, d.graph)
        d.undo()
        assertEquals(before, d.graph)
        assertFalse(d.canUndo)
    }

    @Test
    fun removingASubgraphRemovesItsContentAndUndoRestoresAllOfIt() {
        val d = GraphDocument(graph())
        d.execute(Subgraphs.create(d.graph, setOf(NodeId("add"), NodeId("mul")), NodeId("calc"))!!)
        val wrapped = d.graph
        d.execute(GraphCommand.RemoveNodes(setOf(NodeId("calc"))))
        assertEquals(setOf("s1", "s2", "s3", "sink"), d.graph.nodes.keys.map { it.value }.toSet(), "everything inside went with it")
        assertTrue(d.graph.edges.isEmpty())
        d.undo()
        assertEquals(wrapped, d.graph)
    }

    @Test
    fun subgraphsNest() {
        val d = GraphDocument(graph())
        d.execute(Subgraphs.create(d.graph, setOf(NodeId("add"), NodeId("mul")), NodeId("outer"))!!)
        // wrap just "add" (now inside outer) into an inner subgraph
        d.execute(Subgraphs.create(d.graph, setOf(NodeId("add")), NodeId("inner"))!!)
        assertEquals(NodeId("outer"), d.graph.node(NodeId("inner"))!!.scope)
        assertEquals(NodeId("inner"), d.graph.node(NodeId("add"))!!.scope)
        assertEquals(2, d.graph.depthOf(NodeId("add")))
        assertEquals(setOf(NodeId("add"), NodeId("inner")).size, d.graph.descendantsOf(setOf(NodeId("outer"))).count { it == NodeId("add") || it == NodeId("inner") })
        d.execute(GraphCommand.RemoveNodes(setOf(NodeId("outer"))))
        assertFalse(d.graph.nodes.containsKey(NodeId("add")))
        d.undo()
        assertEquals(NodeId("inner"), d.graph.node(NodeId("add"))!!.scope)
    }

    @Test
    fun wiresNeverCrossAScopeBoundaryExceptThroughThePorts() {
        val d = GraphDocument(graph())
        d.execute(Subgraphs.create(d.graph, setOf(NodeId("add"), NodeId("mul")), NodeId("calc"))!!)
        val check = d.policy.check(d.graph, ref("s1", "out"), ref("add", "a"))
        assertEquals(ConnectionCheck.Rejected(ConnectionRejection.ScopeMismatch), check)
        // but the subgraph node itself is a normal node at the top level
        assertTrue(d.policy.check(d.graph, ref("s3", "out"), PortRef(NodeId("calc"), PortId("in1"))) is ConnectionCheck.Allowed)
    }

    @Test
    fun aPortWithSeveralConsumersKeepsAllOfThemOnOneOutputPort() {
        val g = Graph.of(listOf(src("s"), calc("c1"), calc("c2")), listOf(e("s", "out", "c1", "a"), e("s", "out", "c2", "a")))
        val d = GraphDocument(g)
        d.execute(Subgraphs.create(d.graph, setOf(NodeId("s")), NodeId("box"))!!)
        val sub = d.graph.node(NodeId("box"))!!
        assertEquals(1, sub.ports.size)
        assertEquals(2, d.graph.edgesAt(PortRef(NodeId("box"), sub.ports.single().id)).size)
        d.execute(Subgraphs.dissolve(d.graph, NodeId("box"))!!)
        assertEquals(edgeSet(g), edgeSet(d.graph))
    }

    @Test
    fun addingAPortAddsItsBoundaryNode() {
        val d = GraphDocument(graph())
        d.execute(Subgraphs.create(d.graph, setOf(NodeId("add")), NodeId("box"))!!)
        val before = d.graph.node(NodeId("box"))!!.ports.size
        d.execute(Subgraphs.addInput(d.graph, NodeId("box"), "Extra", num)!!)
        assertEquals(before + 1, d.graph.node(NodeId("box"))!!.ports.size)
        assertNotNull(d.graph.node(Subgraphs.inputBoundary(NodeId("box"), PortId("in3"))))
        d.undo()
        assertEquals(before, d.graph.node(NodeId("box"))!!.ports.size)
        assertNull(Subgraphs.addInput(d.graph, NodeId("s1"), "x"), "only subgraph nodes take ports")
    }

    @Test
    fun invalidRequestsDoNothing() {
        val g = graph()
        assertNull(Subgraphs.create(g, emptySet(), NodeId("x")))
        assertNull(Subgraphs.create(g, setOf(NodeId("ghost")), NodeId("x")))
        assertNull(Subgraphs.create(g, setOf(NodeId("add")), NodeId("add")), "id taken")
        assertNull(Subgraphs.dissolve(g, NodeId("add")), "not a subgraph")
    }

    @Test
    fun aNodeWithNoOutsideWiresMakesAnInterfaceFreeSubgraph() {
        val g = Graph.of(listOf(src("s"), calc("c")), listOf(e("s", "out", "c", "a")))
        val d = GraphDocument(g)
        d.execute(Subgraphs.create(d.graph, setOf(NodeId("s"), NodeId("c")), NodeId("all"))!!)
        assertTrue(d.graph.node(NodeId("all"))!!.ports.isEmpty())
        assertEquals(setOf("s.out->c.a"), edgeSet(d.graph))
    }
}
