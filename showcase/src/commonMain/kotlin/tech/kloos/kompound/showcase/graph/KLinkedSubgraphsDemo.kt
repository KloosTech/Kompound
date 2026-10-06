package tech.kloos.kompound.showcase.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.KNode
import tech.kloos.kompound.graph.KNodeGraph
import tech.kloos.kompound.graph.inspector.edgeLabel
import tech.kloos.kompound.graph.inspector.nodeStatus
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.GraphResolver
import tech.kloos.kompound.graph.model.LinkedSubgraphs
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.ResolvedGraph
import tech.kloos.kompound.graph.model.SubgraphLink
import tech.kloos.kompound.graph.model.Subgraphs
import tech.kloos.kompound.graph.runtime.NodeRun
import tech.kloos.kompound.graph.runtime.rememberGraphEngine
import tech.kloos.kompound.graph.runtime.singleOutputRunner
import tech.kloos.kompound.text.KText

private const val Usage_links = """import tech.kloos.kompound.graph.model.*
import tech.kloos.kompound.graph.runtime.rememberGraphEngine

// The app owns the documents; the library only asks for them by name.
val library = GraphResolver { ref, version -> store.load(ref, version)?.let { ResolvedGraph(it) } }

// A document is an ordinary graph whose root has boundary nodes: its interface.
//   Subgraphs.InputKind node with id "x", Subgraphs.OutputKind node with id "y"  ->  a link to it has the ports x and y.
val link = LinkedSubgraphs.linkNode("upload", ref = "upload-workflow", resolver = library)

val state = remember { KGraphState(Graph.of(listOf(source, link, sink), wires)) }
val engine = rememberGraphEngine(state, runners, links = library)   // runs LinkedSubgraphs.expand(graph, library)

KNodeGraph(state, onOpenLink = { node -> open((node.data as SubgraphLink).ref) }) { node -> MyNode(node) }

// Documents changed? One undo step that fixes the ports of every link:
LinkedSubgraphs.syncPorts(state.graph, library)?.let { state.execute(it) }"""

private fun boundary(id: String, input: Boolean, label: String) = GraphNode(
    NodeId(id), if (input) Subgraphs.InputKind else Subgraphs.OutputKind, Offset.Zero,
    listOf(if (input) PortSpec.output("value", label) else PortSpec.input("value", label)), data = label,
)

private fun operation(kind: String): Graph = Graph.of(
    listOf(
        boundary("x", true, "Number"),
        GraphNode(NodeId("op"), kind, Offset(260f, 0f), listOf(PortSpec.input("a"), PortSpec.output("out"))),
        boundary("y", false, "Result"),
    ).let { it.map { n -> if (n.id.value == "y") n.copy(position = Offset(520f, 0f)) else n } },
    listOf(
        Edge(EdgeId("e1"), PortRef(NodeId("x"), PortId("value")), PortRef(NodeId("op"), PortId("a"))),
        Edge(EdgeId("e2"), PortRef(NodeId("op"), PortId("out")), PortRef(NodeId("y"), PortId("value"))),
    ),
)

private val Documents = mapOf("Double" to operation("double"), "Plus ten" to operation("plus10"), "Square" to operation("square"))

private val Library = GraphResolver { ref, _ -> Documents[ref]?.let { ResolvedGraph(it) } }

@KompoundDemo(
    id = "graph.links",
    title = "Linked subgraphs",
    description = "A node that stands for another document instead of a copy: the app resolves it, the engine runs the expansion, links can nest and are saved by name.",
    category = KompoundCategory.Graph,
    tags = ["graph", "subgraph", "link", "reuse", "workflow", "document"],
    since = "0.2.0",
    status = "Experimental",
    usage = Usage_links,
)
@Composable
fun DemoScope.KLinkedSubgraphsDemo() {
    val second = choiceControl("Second link points to", Documents.keys.toList(), "Plus ten")
    val start = floatControl("Start value", 0f..20f, 5f).toInt()
    val state = remember {
        KGraphState(
            Graph.of(
                listOf(
                    GraphNode(NodeId("start"), "const", Offset(0f, 40f), listOf(PortSpec.output("out")), start),
                    LinkedSubgraphs.linkNode("first", "Double", Library, Offset(280f, 0f)),
                    LinkedSubgraphs.linkNode("second", "Plus ten", Library, Offset(560f, 0f)),
                ),
                listOf(
                    Edge(EdgeId("a"), PortRef(NodeId("start"), PortId("out")), PortRef(NodeId("first"), PortId("x"))),
                    Edge(EdgeId("b"), PortRef(NodeId("first"), PortId("y")), PortRef(NodeId("second"), PortId("x"))),
                ),
            ),
            gridStep = 24f,
        )
    }
    val runners = remember {
        mapOf(
            "const" to singleOutputRunner { node, _ -> node.data },
            "double" to singleOutputRunner { _, i -> (i["a"] as Int) * 2 },
            "plus10" to singleOutputRunner { _, i -> (i["a"] as Int) + 10 },
            "square" to singleOutputRunner { _, i -> (i["a"] as Int).let { it * it } },
        )
    }
    val engine = rememberGraphEngine(state, runners, links = Library)
    LaunchedEffect(second) { state.execute(GraphCommand.UpdateNodeData(NodeId("second"), SubgraphLink(second))) }
    LaunchedEffect(start) { state.execute(GraphCommand.UpdateNodeData(NodeId("start"), start)) }
    val result = (engine.runOf(NodeId("second")) as? NodeRun.Done)?.outputs?.get(PortId("y"))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KText("Result of the second link: ${result ?: "…"}")
        GraphFrame(state, 360) { frame ->
            KNodeGraph(
                state, frame, fitOnFirstLayout = true,
                nodeStatus = { engine.nodeStatus(it, null) },
                edgeLabel = { engine.edgeLabel(it, null) },
            ) { node -> KNode(node, "Start") { Output("out", "Out") } }
        }
    }
}
