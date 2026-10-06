package tech.kloos.kompound.graph.model

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import tech.kloos.kompound.graph.runtime.GraphEngine
import tech.kloos.kompound.graph.runtime.NodeRun
import tech.kloos.kompound.graph.runtime.singleOutputRunner
import tech.kloos.kompound.graph.serialization.GraphJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LinkedSubgraphsTest {
    private fun ref(node: String, port: String) = PortRef(NodeId(node), PortId(port))
    private fun wire(from: PortRef, to: PortRef) = Edge(EdgeId("$from->$to"), from, to)

    /** A document with one input `x`, one output `y`, and a `double` node between them. */
    private fun doubler(inner: String = "double", innerKind: String = "double"): Graph = Graph.of(
        listOf(
            GraphNode(NodeId("x"), Subgraphs.InputKind, Offset.Zero, listOf(PortSpec.output("value", "X")), data = "Number"),
            GraphNode(NodeId(inner), innerKind, Offset(100f, 0f), listOf(PortSpec.input("a"), PortSpec.output("out"))),
            GraphNode(NodeId("y"), Subgraphs.OutputKind, Offset(200f, 0f), listOf(PortSpec.input("value", "Y")), data = "Result"),
        ),
        listOf(wire(ref("x", "value"), ref(inner, "a")), wire(ref(inner, "out"), ref("y", "value"))),
    )

    private fun resolver(vararg docs: Pair<String, Graph>): GraphResolver {
        val map = docs.toMap()
        return GraphResolver { ref, _ -> map[ref]?.let { ResolvedGraph(it) } }
    }

    private fun outer(link: GraphNode): Graph = Graph.of(
        listOf(GraphNode(NodeId("five"), "const", Offset.Zero, listOf(PortSpec.output("out")), 5), link),
        listOf(wire(ref("five", "out"), ref(link.id.value, "x"))),
    )

    @Test
    fun theInterfaceIsTheRootBoundaryNodes() {
        val ports = LinkedSubgraphs.interfaceOf(doubler())
        assertEquals(listOf("x", "y"), ports.map { it.id.value })
        assertEquals(listOf(PortDirection.Input, PortDirection.Output), ports.map { it.direction })
        assertEquals(listOf("Number", "Result"), ports.map { it.label })
    }

    @Test
    fun expandingReplacesTheLinkByARealSubgraphWithPrefixedIds() {
        val r = resolver("dbl" to doubler())
        val link = LinkedSubgraphs.linkNode("L", "dbl", r)
        val expanded = LinkedSubgraphs.expand(outer(link), r)
        assertTrue(expanded.problems.isEmpty())
        val g = expanded.graph
        assertEquals(Subgraphs.Kind, g.node(NodeId("L"))!!.kind)
        val inner = g.node(NodeId("L::double"))!!
        assertEquals(NodeId("L"), inner.scope)
        assertNotNull(g.node(Subgraphs.inputBoundary(NodeId("L"), PortId("x"))))
        assertNotNull(g.node(Subgraphs.outputBoundary(NodeId("L"), PortId("y"))))
        // the wire from outside still ends at the link's own port, and inside the boundary feeds the inner node
        assertTrue(g.edges.values.any { it.from == ref("five", "out") && it.to == ref("L", "x") })
        assertTrue(g.edges.values.any { it.from.node == Subgraphs.inputBoundary(NodeId("L"), PortId("x")) && it.to == ref("L::double", "a") })
        assertEquals(listOf(NodeId("L"), NodeId("double")), expanded.origin[NodeId("L::double")])
        assertEquals("L › double", expanded.label(NodeId("L::double")))
        assertEquals("five", expanded.label(NodeId("five")))
    }

    @Test
    fun expandedGraphRunsInTheEngine() = runTest {
        val r = resolver("dbl" to doubler())
        val expanded = LinkedSubgraphs.expand(outer(LinkedSubgraphs.linkNode("L", "dbl", r)), r).graph
        val e = GraphEngine(
            this,
            mapOf("const" to singleOutputRunner { n, _ -> n.data }, "double" to singleOutputRunner { _, i -> (i["a"] as Int) * 2 }),
            runDispatcher = StandardTestDispatcher(testScheduler),
        )
        e.update(expanded)
        advanceUntilIdle()
        assertEquals(10, (e.runOf(NodeId("L")) as NodeRun.Done).outputs[PortId("y")])
        assertTrue(e.runOf(NodeId("L::double")) is NodeRun.Done)
    }

    @Test
    fun twoLinksToTheSameDocumentStaySeparate() {
        val r = resolver("dbl" to doubler())
        val g = Graph.of(listOf(LinkedSubgraphs.linkNode("A", "dbl", r), LinkedSubgraphs.linkNode("B", "dbl", r)), emptyList())
        val expanded = LinkedSubgraphs.expand(g, r).graph
        assertNotNull(expanded.node(NodeId("A::double")))
        assertNotNull(expanded.node(NodeId("B::double")))
    }

    @Test
    fun nestedLinksExpandWithChainedPrefixesAndOrigin() {
        // outerDoc contains a link to dbl
        val dbl = doubler()
        val wrapper = Graph.of(
            listOf(
                GraphNode(NodeId("x"), Subgraphs.InputKind, Offset.Zero, listOf(PortSpec.output("value")), data = "In"),
                LinkedSubgraphs.linkNode("M", "dbl", dbl),
                GraphNode(NodeId("y"), Subgraphs.OutputKind, Offset.Zero, listOf(PortSpec.input("value")), data = "Out"),
            ),
            listOf(wire(ref("x", "value"), ref("M", "x")), wire(ref("M", "y"), ref("y", "value"))),
        )
        val r = resolver("dbl" to dbl, "wrap" to wrapper)
        val expanded = LinkedSubgraphs.expand(outer(LinkedSubgraphs.linkNode("L", "wrap", r)), r)
        assertTrue(expanded.problems.isEmpty(), "${expanded.problems}")
        val g = expanded.graph
        assertNotNull(g.node(NodeId("L::M::double")))
        assertEquals(NodeId("L::M"), g.node(NodeId("L::M::double"))!!.scope)
        assertEquals(listOf(NodeId("L"), NodeId("M"), NodeId("double")), expanded.origin[NodeId("L::M::double")])
    }

    @Test
    fun cyclesAndUnknownDocumentsAndDepthAreReportedNotLooped() {
        val selfLinking = Graph.of(listOf(LinkedSubgraphs.linkNode("again", "loop", doubler())), emptyList())
        val r = resolver("loop" to selfLinking)
        val top = Graph.of(listOf(LinkedSubgraphs.linkNode("L", "loop", doubler())), emptyList())
        val cycle = LinkedSubgraphs.expand(top, r)
        assertTrue(cycle.problems.any { it is LinkProblem.Cycle }, "${cycle.problems}")
        assertEquals(LinkedSubgraphs.LinkKind, cycle.graph.node(NodeId("L::again"))!!.kind, "the cyclic link stays unexpanded")

        val missing = LinkedSubgraphs.expand(Graph.of(listOf(LinkedSubgraphs.linkNode("Z", "nope", doubler())), emptyList()), r)
        assertEquals(listOf<LinkProblem>(LinkProblem.Unresolved(NodeId("Z"), "nope")), missing.problems)

        val chain = (0 until 12).associate { "d$it" to Graph.of(listOf(LinkedSubgraphs.linkNode("n", "d${it + 1}", doubler())), emptyList()) }
        val deep = LinkedSubgraphs.expand(Graph.of(listOf(LinkedSubgraphs.linkNode("start", "d0", doubler())), emptyList()), GraphResolver { ref, _ -> chain[ref]?.let { ResolvedGraph(it) } }, maxDepth = 4)
        assertTrue(deep.problems.any { it is LinkProblem.TooDeep }, "${deep.problems}")
    }

    @Test
    fun pinsOnALinkLandOnTheInnerNode() {
        val r = resolver("dbl" to doubler())
        val link = LinkedSubgraphs.linkNode("L", "dbl", r).let { it.copy(data = SubgraphLink("dbl", pins = mapOf("double" to mapOf(PortId("out") to 99)))) }
        val g = LinkedSubgraphs.expand(Graph.of(listOf(link), emptyList()), r).graph
        assertEquals(mapOf(PortId("out") to 99), g.node(NodeId("L::double"))!!.pin)
    }

    @Test
    fun syncPortsFollowsTheTargetsInterface() {
        val v1 = doubler()
        val r1 = resolver("dbl" to v1)
        val link = LinkedSubgraphs.linkNode("L", "dbl", r1)
        val graph = outer(link)
        assertNull(LinkedSubgraphs.syncPorts(graph, r1), "nothing changed")
        // v2 gains an output z
        val v2 = Graph.of(v1.nodes.values + GraphNode(NodeId("z"), Subgraphs.OutputKind, Offset.Zero, listOf(PortSpec.input("value")), data = "Extra"), v1.edges.values)
        val command = LinkedSubgraphs.syncPorts(graph, resolver("dbl" to v2))
        assertNotNull(command)
        val updated = command.applyTo(graph)!!.graph
        assertEquals(listOf("x", "y", "z"), updated.node(NodeId("L"))!!.ports.map { it.id.value })
        assertTrue(updated.edges.values.any { it.to == ref("L", "x") }, "the wire on a surviving port stays")
    }

    @Test
    fun linksSurviveASaveAndLoadWithoutCopyingTheTarget() {
        val r = resolver("dbl" to doubler())
        val link = LinkedSubgraphs.linkNode("L", "dbl", r, version = "v2").let {
            it.copy(data = SubgraphLink("dbl", "v2", pins = mapOf("double" to mapOf(PortId("out") to 7))))
        }
        val text = GraphJson().encode(outer(link))
        assertTrue("double" !in text.replace("\"double\":", "").replace("pins", ""), "the target's nodes are not in the file")
        val back = GraphJson().decode(text).graph.node(NodeId("L"))!!
        assertEquals(SubgraphLink("dbl", "v2", mapOf("double" to mapOf(PortId("out") to 7))), back.data)
        assertEquals(LinkedSubgraphs.LinkKind, back.kind)
    }
}
