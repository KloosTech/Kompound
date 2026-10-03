package tech.kloos.kompound.graph.model

import androidx.compose.ui.geometry.Offset

/**
 * A node of the graph.
 *
 * @property id Identity.
 * @property kind Which kind of node this is (`"math.add"`); the editor uses it to pick the composable.
 * @property position Top-left corner in world units.
 * @property ports Declared ports, in display order.
 * @property data Your payload (values, settings). The framework never looks inside; replace it with `UpdateNodeData`.
 */
public data class GraphNode(
    public val id: NodeId,
    public val kind: String,
    public val position: Offset = Offset.Zero,
    public val ports: List<PortSpec> = emptyList(),
    public val data: Any? = null,
) {
    /** The port declared with [portId], or `null`. */
    public fun port(portId: PortId): PortSpec? = ports.firstOrNull { it.id == portId }

    /** The port declared with the id [portId], or `null`. */
    public fun port(portId: String): PortSpec? = port(PortId(portId))
}

/** A wire from an output to an input. */
public data class Edge(public val id: EdgeId, public val from: PortRef, public val to: PortRef)

/**
 * Immutable snapshot of nodes and edges. Every change returns a new graph; indices for fast lookups are kept in step.
 * Edges that would dangle (missing node or port) are rejected, so a graph is always consistent.
 */
public class Graph private constructor(
    public val nodes: Map<NodeId, GraphNode>,
    public val edges: Map<EdgeId, Edge>,
    private val byPort: Map<PortRef, List<Edge>>,
) {
    /** The node with [id], or `null`. */
    public fun node(id: NodeId): GraphNode? = nodes[id]

    /** The edge with [id], or `null`. */
    public fun edge(id: EdgeId): Edge? = edges[id]

    /** Edges that start or end at [ref]. */
    public fun edgesAt(ref: PortRef): List<Edge> = byPort[ref].orEmpty()

    /** Whether any edge touches [ref]. */
    public fun isConnected(ref: PortRef): Boolean = byPort[ref]?.isNotEmpty() == true

    /** Edges that touch the node [id]. */
    public fun edgesOf(id: NodeId): List<Edge> = edges.values.filter { it.from.node == id || it.to.node == id }

    /** The spec of the port [ref], or `null` when the node or port does not exist. */
    public fun port(ref: PortRef): PortSpec? = nodes[ref.node]?.port(ref.port)

    /** Adds [node], or replaces the node with the same id (its edges are kept when its ports still exist). */
    public fun withNode(node: GraphNode): Graph {
        val updated = LinkedHashMap(nodes).also { it[node.id] = node }
        val kept = edges.values.filter { e -> e.from.node != node.id && e.to.node != node.id || (updated.portOf(e.from) != null && updated.portOf(e.to) != null) }
        return of(updated, kept)
    }

    /** Removes the nodes [ids] and every edge touching them. */
    public fun withoutNodes(ids: Set<NodeId>): Graph {
        if (ids.none { it in nodes }) return this
        val updated = LinkedHashMap(nodes).also { m -> ids.forEach { m.remove(it) } }
        return of(updated, edges.values.filter { it.from.node !in ids && it.to.node !in ids })
    }

    /** Adds [edge]; ignored (returns `this`) when an end does not exist or the id is taken. */
    public fun withEdge(edge: Edge): Graph {
        if (edge.id in edges || nodes.portOf(edge.from) == null || nodes.portOf(edge.to) == null) return this
        return of(nodes, edges.values + edge)
    }

    /** Removes the edges [ids]. */
    public fun withoutEdges(ids: Set<EdgeId>): Graph =
        if (ids.none { it in edges }) this else of(nodes, edges.values.filter { it.id !in ids })

    /** Puts the nodes at the given absolute positions. */
    public fun withPlaced(positions: Map<NodeId, Offset>): Graph {
        if (positions.isEmpty()) return this
        val updated = LinkedHashMap(nodes)
        for ((id, p) in positions) updated[id]?.let { updated[id] = it.copy(position = p) }
        return Graph(updated, edges, byPort)
    }

    /** Moves the nodes by the given deltas. */
    public fun withMoved(deltas: Map<NodeId, Offset>): Graph {
        if (deltas.isEmpty()) return this
        val updated = LinkedHashMap(nodes)
        for ((id, d) in deltas) updated[id]?.let { updated[id] = it.copy(position = it.position + d) }
        return Graph(updated, edges, byPort)
    }

    override fun equals(other: Any?): Boolean = other is Graph && nodes == other.nodes && edges == other.edges
    override fun hashCode(): Int = nodes.hashCode() * 31 + edges.hashCode()
    override fun toString(): String = "Graph(${nodes.size} nodes, ${edges.size} edges)"

    public companion object {
        /** The empty graph. */
        public val Empty: Graph = Graph(emptyMap(), emptyMap(), emptyMap())

        /** A graph with the given contents. Edges with a missing end are dropped. */
        public fun of(nodes: Collection<GraphNode>, edges: Collection<Edge> = emptyList()): Graph =
            of(LinkedHashMap<NodeId, GraphNode>().also { m -> nodes.forEach { m[it.id] = it } }, edges)

        private fun of(nodes: Map<NodeId, GraphNode>, edges: Collection<Edge>): Graph {
            val valid = LinkedHashMap<EdgeId, Edge>()
            val index = HashMap<PortRef, MutableList<Edge>>()
            for (e in edges) {
                if (e.id in valid || nodes.portOf(e.from) == null || nodes.portOf(e.to) == null) continue
                valid[e.id] = e
                index.getOrPut(e.from) { ArrayList() }.add(e)
                index.getOrPut(e.to) { ArrayList() }.add(e)
            }
            return Graph(nodes, valid, index)
        }
    }
}

private fun Map<NodeId, GraphNode>.portOf(ref: PortRef): PortSpec? = this[ref.node]?.port(ref.port)
