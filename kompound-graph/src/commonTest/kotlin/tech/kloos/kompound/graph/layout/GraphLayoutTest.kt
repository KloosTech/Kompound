package tech.kloos.kompound.graph.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.math
import tech.kloos.kompound.graph.model.ref
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphLayoutTest {
    private val size = Size(200f, 100f)
    private fun sizes(g: Graph) = g.nodes.keys.associateWith { size }
    private fun edge(a: String, b: String, port: String = "a") = Edge(EdgeId("$a->$b.$port"), ref(a, "out"), ref(b, port))
    private fun layout(g: Graph, nodes: Set<NodeId> = g.nodes.keys) = GraphLayout.layered(g, sizes(g), nodes)

    @Test
    fun aChainRunsLeftToRightWithTheLayerSpacing() {
        val g = Graph.of(listOf(math("c", Offset(500f, 0f)), math("a", Offset(0f, 300f)), math("b", Offset(200f, 100f))), listOf(edge("a", "b"), edge("b", "c")))
        val p = layout(g)
        val a = p.getValue(NodeId("a")); val b = p.getValue(NodeId("b")); val c = p.getValue(NodeId("c"))
        assertEquals(b.x, a.x + 200f + 120f, 0.01f)
        assertEquals(c.x, b.x + 200f + 120f, 0.01f)
        assertEquals(a.y, b.y, 0.01f, "a straight chain stays on one line")
        assertEquals(b.y, c.y, 0.01f)
    }

    @Test
    fun theLayoutStaysWhereTheNodesWere() {
        val g = Graph.of(listOf(math("a", Offset(1000f, 700f)), math("b", Offset(1500f, 900f))), listOf(edge("a", "b")))
        val p = layout(g)
        assertEquals(1000f, p.values.minOf { it.x }, 0.01f)
        assertEquals(700f, p.values.minOf { it.y }, 0.01f)
    }

    @Test
    fun aDiamondPutsTheBranchesInOneColumnAndTheJoinAfterThem() {
        val g = Graph.of(listOf(math("s"), math("l"), math("r"), math("j")), listOf(edge("s", "l"), edge("s", "r"), edge("l", "j", "a"), edge("r", "j", "b")))
        val p = layout(g)
        assertEquals(p.getValue(NodeId("l")).x, p.getValue(NodeId("r")).x, 0.01f)
        assertTrue(p.getValue(NodeId("j")).x > p.getValue(NodeId("l")).x)
        assertTrue(kotlin.math.abs(p.getValue(NodeId("l")).y - p.getValue(NodeId("r")).y) >= 140f, "branches do not overlap")
        val sMid = p.getValue(NodeId("s")).y + 50f
        val branchMid = (p.getValue(NodeId("l")).y + p.getValue(NodeId("r")).y) / 2f + 50f
        assertEquals(branchMid, sMid, 1f, "the source sits between its two branches")
    }

    @Test
    fun crossingWiresAreUntangled() {
        // a1->b2, a2->b1 with the nodes initially listed so the wires cross
        val g = Graph.of(
            listOf(math("a1", Offset(0f, 0f)), math("a2", Offset(0f, 200f)), math("b1", Offset(400f, 0f)), math("b2", Offset(400f, 200f))),
            listOf(edge("a1", "b2"), edge("a2", "b1")),
        )
        val p = layout(g)
        // components are independent here, so each pair sits on one line and no wire has to cross
        assertEquals(p.getValue(NodeId("a1")).y, p.getValue(NodeId("b2")).y, 0.01f)
        assertEquals(p.getValue(NodeId("a2")).y, p.getValue(NodeId("b1")).y, 0.01f)
    }

    @Test
    fun barycentreOrderingRemovesACrossing() {
        // one component: s feeds x and y; x->p2, y->p1; p1 and p2 both feed t. Naive order has x above y but p2 above p1.
        val g = Graph.of(
            listOf(math("s"), math("x"), math("y"), math("p1"), math("p2"), math("t")),
            listOf(edge("s", "x"), edge("s", "y", "b"), edge("x", "p2"), edge("y", "p1"), edge("p1", "t", "a"), edge("p2", "t", "b")),
        )
        val p = layout(g)
        val xAboveY = p.getValue(NodeId("x")).y < p.getValue(NodeId("y")).y
        val p2AboveP1 = p.getValue(NodeId("p2")).y < p.getValue(NodeId("p1")).y
        assertEquals(xAboveY, p2AboveP1, "x->p2 and y->p1 must not cross")
    }

    @Test
    fun loopsAreTolerated() {
        val g = Graph.of(listOf(math("a"), math("b"), math("c")), listOf(edge("a", "b"), edge("b", "c"), Edge(EdgeId("loop"), ref("c", "out"), ref("a", "b"))))
        val p = layout(g)
        assertEquals(3, p.size)
        assertTrue(p.getValue(NodeId("a")).x < p.getValue(NodeId("b")).x && p.getValue(NodeId("b")).x < p.getValue(NodeId("c")).x)
    }

    @Test
    fun unconnectedPartsAreStackedAndNeverOverlap() {
        val g = Graph.of(listOf(math("a"), math("b"), math("x"), math("y"), math("lonely")), listOf(edge("a", "b"), edge("x", "y")))
        val rects = layout(g).values.map { Rect(it, size) }
        for (i in rects.indices) for (j in i + 1 until rects.size) assertTrue(!rects[i].overlaps(rects[j]), "${rects[i]} overlaps ${rects[j]}")
    }

    @Test
    fun onlyTheChosenNodesMoveAndOnlyTheirWiresCount() {
        val g = Graph.of(listOf(math("a", Offset(0f, 0f)), math("b", Offset(900f, 500f)), math("c", Offset(0f, 700f))), listOf(edge("a", "b"), edge("b", "c")))
        val p = layout(g, setOf(NodeId("a"), NodeId("b")))
        assertEquals(setOf(NodeId("a"), NodeId("b")), p.keys)
        assertEquals(Offset(0f, 0f), p.getValue(NodeId("a")))
    }

    @Test
    fun emptyAndSingleNodeInputs() {
        assertTrue(GraphLayout.layered(Graph.Empty).isEmpty())
        val g = Graph.of(listOf(math("only", Offset(30f, 40f))))
        assertEquals(Offset(30f, 40f), layout(g).getValue(NodeId("only")))
    }

    @Test
    fun noSizesFallBackToTheDefault() {
        val g = Graph.of(listOf(math("a"), math("b")), listOf(edge("a", "b")))
        val p = GraphLayout.layered(g)
        assertEquals(220f + 120f, p.getValue(NodeId("b")).x - p.getValue(NodeId("a")).x, 0.01f)
    }

    /** Random graphs, loops included: every node is placed, nothing overlaps and the result is deterministic. */
    @Test
    fun randomGraphsKeepTheInvariants() {
        for (seed in 1..60) {
            val random = Random(seed)
            val n = random.nextInt(2, 36)
            val nodes = (0 until n).map { math("n$it", Offset(random.nextFloat() * 2000, random.nextFloat() * 2000)) }
            val edges = LinkedHashMap<String, Edge>()
            repeat(random.nextInt(0, n * 2)) {
                val a = random.nextInt(n); val b = random.nextInt(n)
                if (a != b) { val port = listOf("a", "b").random(random); edges["$a->$b.$port"] = edge("n$a", "n$b", port) }
            }
            val g = Graph.of(nodes, edges.values)
            val sizes = g.nodes.keys.associateWith { Size(100f + random.nextInt(150), 60f + random.nextInt(120)) }
            val p = GraphLayout.layered(g, sizes)
            assertEquals(g.nodes.keys, p.keys)
            assertEquals(p, GraphLayout.layered(g, sizes), "deterministic, seed $seed")
            val rects = p.mapValues { (id, o) -> Rect(o, sizes.getValue(id)) }
            val list = rects.values.toList()
            for (i in list.indices) for (j in i + 1 until list.size) assertTrue(!list[i].overlaps(list[j]), "seed $seed: ${list[i]} overlaps ${list[j]}")
        }
    }

    @Test
    fun fiveHundredNodesLayOutQuickly() {
        val random = Random(9)
        val nodes = (0 until 500).map { math("n$it", Offset(random.nextFloat() * 5000, random.nextFloat() * 5000)) }
        val edges = (0 until 800).mapNotNull { i -> val a = random.nextInt(500); val b = random.nextInt(500); if (a >= b) null else Edge(EdgeId("e$i"), ref("n$a", "out"), ref("n$b", "a")) }
        val g = Graph.of(nodes, edges)
        assertEquals(500, GraphLayout.layered(g).size)
    }
}
