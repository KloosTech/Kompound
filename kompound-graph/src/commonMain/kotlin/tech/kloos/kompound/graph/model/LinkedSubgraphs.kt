package tech.kloos.kompound.graph.model

import androidx.compose.ui.geometry.Offset

/**
 * What a link node ([LinkedSubgraphs.LinkKind]) holds as its [GraphNode.data]: which document it stands for.
 *
 * @property ref The app's name for the document (a file name, an id); the [GraphResolver] turns it into a graph.
 * @property version Optional version for the resolver (a hash, "latest"); the library gives it no meaning.
 * @property pins Pinned outputs of nodes inside the document, keyed by the node's id inside it (`"up"`, or `"inner::up"` through a nested link), so a pin
 * survives re-expansion. Two links to the same document pin separately.
 */
public data class SubgraphLink(
    public val ref: String,
    public val version: String? = null,
    public val pins: Map<String, Map<PortId, Any?>> = emptyMap(),
)

/** A document the resolver found. Its interface is read from [graph]: see [LinkedSubgraphs.interfaceOf]. */
public class ResolvedGraph(public val graph: Graph)

/**
 * Finds the documents link nodes point to. The library never knows where documents live (files, a database, the network); resolve
 * everything first when it needs I/O (the engine and [LinkedSubgraphs.expand] are synchronous). Return `null` for an unknown [ref].
 */
public fun interface GraphResolver {
    public fun resolve(ref: String, version: String?): ResolvedGraph?
}

/**
 * A [GraphResolver] whose documents can change: [revision] is read inside the engine's observer (make it a Compose state, `by mutableIntStateOf`),
 * so bumping it re-expands every link and the engine re-runs what changed.
 */
public interface ObservableGraphResolver : GraphResolver {
    /** Any number that changes whenever a document may have changed. */
    public val revision: Int
}

/** A problem found while expanding links; the link node stays unexpanded and the engine then reports it as a node without a runner. */
public sealed interface LinkProblem {
    /** The link node that has the problem. */
    public val link: NodeId

    /** The resolver does not know the document. */
    public data class Unresolved(override val link: NodeId, val ref: String) : LinkProblem

    /** The document links (directly or through others) to itself: [chain] is the path of refs. */
    public data class Cycle(override val link: NodeId, val chain: List<String>) : LinkProblem

    /** Links are nested deeper than the allowed depth. */
    public data class TooDeep(override val link: NodeId, val limit: Int) : LinkProblem
}

/**
 * The result of [LinkedSubgraphs.expand]: a flat [graph] for the engine in which every link node is a real subgraph with the target's nodes
 * inside, and a map back to where each node came from.
 *
 * @property origin For a node that came from a document: the path of ids from the outermost link down to the node in its own document
 * (`[Upload, up]`, or `[Upload, Resize, scale]` through a nested link). Nodes that were in the graph all along have no entry.
 * @property problems Links that could not be expanded.
 */
public class ExpandedGraph(
    public val graph: Graph,
    public val origin: Map<NodeId, List<NodeId>>,
    public val problems: List<LinkProblem>,
) {
    /** `Upload › up` for a node inside a link, [labelOf] its own id otherwise; [labelOf] gives the readable name of a node id of the original graph or document. */
    public fun label(id: NodeId, labelOf: (NodeId) -> String = { it.value }): String =
        origin[id]?.joinToString(" › ") { labelOf(it) } ?: labelOf(id)
}

/**
 * Linked subgraphs: a node that stands for another document ("this node is workflow X") instead of holding a copy of it.
 *
 * - A **link node** has kind [LinkKind] and [SubgraphLink] data. Its ports mirror the target's interface: the boundary nodes
 *   ([Subgraphs.InputKind] and [Subgraphs.OutputKind]) at the target's **root**; a boundary node's id is the port's id and its data (a `String`) the label.
 * - [expand] replaces link nodes by real subgraphs (ids prefixed with the link: `"Upload::up"`), recursively, with cycle and depth checks. Hand
 *   the result to the engine, or use `rememberGraphEngine(links = resolver)`, which does it for you.
 * - [syncPorts] updates link nodes whose target's interface changed.
 *
 * The target's content is never copied into the saved file; only [SubgraphLink] is.
 */
public object LinkedSubgraphs {
    /** Kind of a link node. */
    public const val LinkKind: String = "subgraph.link"

    /** Separator between a link's id and the id of a node inside its document in the expanded graph. */
    public const val Separator: String = "::"

    /** Most links that may be nested into each other. */
    public const val MaxDepth: Int = 8

    /** Whether [node] is a link node. */
    public fun isLink(node: GraphNode): Boolean = node.kind == LinkKind

    /** The ports a link to [document] has: one input per root input boundary node, one output per root output boundary node, in node order. */
    public fun interfaceOf(document: Graph): List<PortSpec> = document.nodes.values.filter { it.scope == null && Subgraphs.isBoundary(it) }.map { b ->
        val id = b.id.value
        val label = (b.data as? String)?.ifEmpty { null } ?: id
        val type = b.ports.firstOrNull()?.type ?: PortType.Any
        if (b.kind == Subgraphs.InputKind) PortSpec.input(id, label, type) else PortSpec.output(id, label, type)
    }

    /** A link node [id] to [ref] with the ports of [document]. */
    public fun linkNode(id: String, ref: String, document: Graph, position: Offset = Offset.Zero, version: String? = null, scope: NodeId? = null): GraphNode =
        GraphNode(NodeId(id), LinkKind, position, interfaceOf(document), SubgraphLink(ref, version), scope = scope)

    /** A link node [id] to [ref], with ports resolved by [resolver] (none when it does not know the document). */
    public fun linkNode(id: String, ref: String, resolver: GraphResolver, position: Offset = Offset.Zero, version: String? = null, scope: NodeId? = null): GraphNode =
        linkNode(id, ref, resolver.resolve(ref, version)?.graph ?: Graph.Empty, position, version, scope)

    /**
     * Expands every link node of [graph] (at any level of subgraphs). A link node that cannot be expanded stays as it is and is listed in
     * [ExpandedGraph.problems]. Pure: the same inputs give the same graph.
     */
    public fun expand(graph: Graph, resolver: GraphResolver, maxDepth: Int = MaxDepth): ExpandedGraph {
        val problems = ArrayList<LinkProblem>()
        val result = expandInto(graph, resolver, emptyList(), maxDepth, problems)
        return ExpandedGraph(result.graph, result.origin, problems)
    }

    private class Expanded(val graph: Graph, val origin: Map<NodeId, List<NodeId>>)

    private fun expandInto(graph: Graph, resolver: GraphResolver, stack: List<String>, maxDepth: Int, problems: MutableList<LinkProblem>): Expanded {
        val links = graph.nodes.values.filter { isLink(it) }
        if (links.isEmpty()) return Expanded(graph, emptyMap())
        val nodes = LinkedHashMap(graph.nodes)
        val edges = LinkedHashMap(graph.edges)
        val origin = LinkedHashMap<NodeId, List<NodeId>>()
        for (link in links) {
            val data = link.data as? SubgraphLink ?: continue
            if (data.ref in stack) { problems += LinkProblem.Cycle(link.id, stack + data.ref); continue }
            if (stack.size >= maxDepth) { problems += LinkProblem.TooDeep(link.id, maxDepth); continue }
            val target = resolver.resolve(data.ref, data.version)?.graph
            if (target == null) { problems += LinkProblem.Unresolved(link.id, data.ref); continue }
            // Links inside the target first (their own prefixes), then this link's prefix goes on top.
            val inner = expandInto(target, resolver, stack + data.ref, maxDepth, problems)
            val doc = inner.graph
            val interfacePorts = interfaceOf(doc)
            val prefix = link.id.value + Separator
            fun mapped(id: NodeId): NodeId {
                val node = doc.node(id)
                return if (node != null && node.scope == null && Subgraphs.isBoundary(node)) {
                    if (node.kind == Subgraphs.InputKind) Subgraphs.inputBoundary(link.id, PortId(node.id.value)) else Subgraphs.outputBoundary(link.id, PortId(node.id.value))
                } else NodeId(prefix + id.value)
            }
            nodes[link.id] = link.copy(kind = Subgraphs.Kind, ports = interfacePorts, data = data.ref)
            for (b in doc.nodes.values.filter { it.scope == null && Subgraphs.isBoundary(it) }) {
                val id = mapped(b.id)
                nodes[id] = b.copy(id = id, scope = link.id, group = null, pin = null)
            }
            for (n in doc.nodes.values) {
                if (n.scope == null && Subgraphs.isBoundary(n)) continue
                val id = mapped(n.id)
                val pin = data.pins[n.id.value] ?: n.pin
                nodes[id] = n.copy(id = id, scope = n.scope?.let { mapped(it) } ?: link.id, group = null, pin = pin)
                origin[id] = listOf(link.id) + (inner.origin[n.id] ?: listOf(n.id))
            }
            for (e in doc.edges.values) {
                val from = PortRef(mapped(e.from.node), if (doc.node(e.from.node)?.let { it.scope == null && Subgraphs.isBoundary(it) } == true) Subgraphs.BoundaryPort else e.from.port)
                val to = PortRef(mapped(e.to.node), if (doc.node(e.to.node)?.let { it.scope == null && Subgraphs.isBoundary(it) } == true) Subgraphs.BoundaryPort else e.to.port)
                val id = EdgeId(prefix + e.id.value)
                edges[id] = Edge(id, from, to)
            }
        }
        return Expanded(Graph.of(nodes.values, edges.values, graph.groups.values), origin)
    }

    /**
     * One command that brings the ports of every link node in line with its target's current interface ([GraphCommand.UpdateNodePorts]:
     * wires on ports that still exist stay). `null` when nothing differs or no link can be resolved. Run it when documents change.
     */
    public fun syncPorts(graph: Graph, resolver: GraphResolver): GraphCommand? {
        val commands = ArrayList<GraphCommand>()
        for (node in graph.nodes.values) {
            if (!isLink(node)) continue
            val data = node.data as? SubgraphLink ?: continue
            val wanted = interfaceOf(resolver.resolve(data.ref, data.version)?.graph ?: continue)
            if (!samePorts(node.ports, wanted)) commands += GraphCommand.UpdateNodePorts(node.id, wanted)
        }
        return when (commands.size) { 0 -> null; 1 -> commands.single(); else -> GraphCommand.Batch(commands, "Update linked subgraphs") }
    }

    private fun samePorts(a: List<PortSpec>, b: List<PortSpec>): Boolean =
        a.size == b.size && a.indices.all { a[it].id == b[it].id && a[it].direction == b[it].direction && a[it].label == b[it].label && a[it].type.id == b[it].type.id }
}
