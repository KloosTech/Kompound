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
 * @property group The group this node belongs to, if any (a node is in at most one group).
 * @property scope The subgraph node this node lives inside, or `null` for the top level. Nodes are only shown, selected and wired
 * within their own scope; removing a subgraph node removes everything inside it.
 */
public data class GraphNode(
    public val id: NodeId,
    public val kind: String,
    public val position: Offset = Offset.Zero,
    public val ports: List<PortSpec> = emptyList(),
    public val data: Any? = null,
    public val group: GroupId? = null,
    public val scope: NodeId? = null,
) {
    /** The port declared with [portId], or `null`. */
    public fun port(portId: PortId): PortSpec? = ports.firstOrNull { it.id == portId }

    /** The port declared with the id [portId], or `null`. */
    public fun port(portId: String): PortSpec? = port(PortId(portId))
}

/**
 * A named frame around some nodes. Which nodes belong to it is stored on the nodes ([GraphNode.group]); the frame's bounds follow its
 * members. A collapsed group shows as one compact box and hides its members.
 *
 * @property id Identity.
 * @property title Text in the frame's title bar.
 * @property collapsed Whether the members are hidden behind one box.
 * @property color Index into the editor's accent palette.
 */
public data class NodeGroup(
    public val id: GroupId,
    public val title: String = "Group",
    public val collapsed: Boolean = false,
    public val color: Int = 0,
)

/** A wire from an output to an input. */
public data class Edge(public val id: EdgeId, public val from: PortRef, public val to: PortRef)

/**
 * Immutable snapshot of nodes, edges and groups. Every change returns a new graph; indices for fast lookups are kept in step.
 * Edges that would dangle (missing node or port) are rejected, so a graph is always consistent.
 */
public class Graph private constructor(
    public val nodes: Map<NodeId, GraphNode>,
    public val edges: Map<EdgeId, Edge>,
    private val byPort: Map<PortRef, List<Edge>>,
    public val groups: Map<GroupId, NodeGroup> = emptyMap(),
) {
    /** The group with [id], or `null`. */
    public fun group(id: GroupId): NodeGroup? = groups[id]

    /** Ids of the nodes that belong to the group [id], in node order. */
    public fun membersOf(id: GroupId): List<NodeId> = nodes.values.filter { it.group == id }.map { it.id }

    /** Adds or replaces a group (its members are the nodes that name it). */
    public fun withGroup(group: NodeGroup): Graph = of(nodes, edges.values, groups + (group.id to group))

    /** Removes the group [id] and releases its members. */
    public fun withoutGroup(id: GroupId): Graph {
        if (id !in groups) return this
        val updated = LinkedHashMap<NodeId, GraphNode>()
        for ((nid, n) in nodes) updated[nid] = if (n.group == id) n.copy(group = null) else n
        return of(updated, edges.values, groups - id)
    }

    /** Puts nodes into groups (or out of them with `null`). Unknown nodes are ignored; so is a group that does not exist. */
    public fun withAssigned(assignments: Map<NodeId, GroupId?>): Graph {
        if (assignments.isEmpty()) return this
        val updated = LinkedHashMap(nodes)
        for ((id, g) in assignments) {
            val n = updated[id] ?: continue
            if (g != null && g !in groups) continue
            updated[id] = n.copy(group = g)
        }
        return Graph(updated, edges, byPort, groups)
    }

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

    /** Ids of the nodes inside the subgraph nodes [ids], at any depth (not including [ids] themselves). */
    public fun descendantsOf(ids: Set<NodeId>): Set<NodeId> {
        val found = LinkedHashSet<NodeId>()
        var frontier: Set<NodeId> = ids
        while (frontier.isNotEmpty()) {
            val next = nodes.values.filter { it.scope in frontier && it.id !in found && it.id !in ids }.map { it.id }.toSet()
            found += next
            frontier = next
        }
        return found
    }

    /** How many subgraph nodes enclose [id]: 0 at the top level. */
    public fun depthOf(id: NodeId): Int {
        var depth = 0
        var current = nodes[id]?.scope
        while (current != null && depth <= nodes.size) { depth++; current = nodes[current]?.scope }
        return depth
    }

    /** The nodes whose scope is [scope] (`null`: the top level). */
    public fun nodesIn(scope: NodeId?): List<GraphNode> = nodes.values.filter { it.scope == scope }

    /** Adds [node], or replaces the node with the same id (its edges are kept when its ports still exist). */
    public fun withNode(node: GraphNode): Graph {
        val updated = LinkedHashMap(nodes).also { it[node.id] = node }
        val kept = edges.values.filter { e -> e.from.node != node.id && e.to.node != node.id || (updated.portOf(e.from) != null && updated.portOf(e.to) != null) }
        return of(updated, kept, groups)
    }

    /** Removes the nodes [requested], everything inside subgraph nodes among them, and every edge touching any of these. */
    public fun withoutNodes(requested: Set<NodeId>): Graph {
        if (requested.none { it in nodes }) return this
        val ids = requested + descendantsOf(requested)
        val updated = LinkedHashMap(nodes).also { m -> ids.forEach { m.remove(it) } }
        return of(updated, edges.values.filter { it.from.node !in ids && it.to.node !in ids }, groups)
    }

    /** Adds [edge]; ignored (returns `this`) when an end does not exist or the id is taken. */
    public fun withEdge(edge: Edge): Graph {
        if (edge.id in edges || nodes.portOf(edge.from) == null || nodes.portOf(edge.to) == null) return this
        return of(nodes, edges.values + edge, groups)
    }

    /** Removes the edges [ids]. */
    public fun withoutEdges(ids: Set<EdgeId>): Graph =
        if (ids.none { it in edges }) this else of(nodes, edges.values.filter { it.id !in ids }, groups)

    /** Puts the nodes at the given absolute positions. */
    public fun withPlaced(positions: Map<NodeId, Offset>): Graph {
        if (positions.isEmpty()) return this
        val updated = LinkedHashMap(nodes)
        for ((id, p) in positions) updated[id]?.let { updated[id] = it.copy(position = p) }
        return Graph(updated, edges, byPort, groups)
    }

    /** Moves the nodes by the given deltas. */
    public fun withMoved(deltas: Map<NodeId, Offset>): Graph {
        if (deltas.isEmpty()) return this
        val updated = LinkedHashMap(nodes)
        for ((id, d) in deltas) updated[id]?.let { updated[id] = it.copy(position = it.position + d) }
        return Graph(updated, edges, byPort, groups)
    }

    override fun equals(other: Any?): Boolean = other is Graph && nodes == other.nodes && edges == other.edges && groups == other.groups
    override fun hashCode(): Int = (nodes.hashCode() * 31 + edges.hashCode()) * 31 + groups.hashCode()
    override fun toString(): String = "Graph(${nodes.size} nodes, ${edges.size} edges, ${groups.size} groups)"

    public companion object {
        /** The empty graph. */
        public val Empty: Graph = Graph(emptyMap(), emptyMap(), emptyMap())

        /** A graph with the given contents. Edges with a missing end are dropped, and nodes naming an unknown group are released from it. */
        public fun of(nodes: Collection<GraphNode>, edges: Collection<Edge> = emptyList(), groups: Collection<NodeGroup> = emptyList()): Graph =
            of(LinkedHashMap<NodeId, GraphNode>().also { m -> nodes.forEach { m[it.id] = it } }, edges, groups.associateBy { it.id })

        private fun of(nodes: Map<NodeId, GraphNode>, edges: Collection<Edge>, groups: Map<GroupId, NodeGroup>): Graph {
            val checked = if (nodes.values.any { (it.group != null && it.group !in groups) || (it.scope != null && it.scope !in nodes) }) {
                LinkedHashMap<NodeId, GraphNode>().also { m ->
                    for ((id, n) in nodes) {
                        var fixed = n
                        if (fixed.group != null && fixed.group !in groups) fixed = fixed.copy(group = null)
                        if (fixed.scope != null && fixed.scope !in nodes) fixed = fixed.copy(scope = null)
                        m[id] = fixed
                    }
                }
            } else nodes
            val valid = LinkedHashMap<EdgeId, Edge>()
            val index = HashMap<PortRef, MutableList<Edge>>()
            for (e in edges) {
                if (e.id in valid || nodes.portOf(e.from) == null || nodes.portOf(e.to) == null) continue
                valid[e.id] = e
                index.getOrPut(e.from) { ArrayList() }.add(e)
                index.getOrPut(e.to) { ArrayList() }.add(e)
            }
            return Graph(checked, valid, index, groups)
        }
    }
}

private fun Map<NodeId, GraphNode>.portOf(ref: PortRef): PortSpec? = this[ref.node]?.port(ref.port)
