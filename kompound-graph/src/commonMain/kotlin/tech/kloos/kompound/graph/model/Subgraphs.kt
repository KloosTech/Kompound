package tech.kloos.kompound.graph.model

import androidx.compose.ui.geometry.Offset

/**
 * Subgraphs: a node that contains a graph of its own.
 *
 * Everything stays in one flat [Graph]. A **subgraph node** (kind [Kind]) lives at some level; the nodes inside it carry its id as
 * their [GraphNode.scope]. Each port of the subgraph node is mirrored by a **boundary node** inside: an [InputKind] node (one output
 * port named `value`) for every input port and an [OutputKind] node (one input port) for every output port. A wire into the subgraph
 * node's input appears inside as the wire that starts at the matching boundary node. So the editor can show any level by filtering on
 * the scope, and undo, copy and removal need no special cases beyond what [GraphCommand] already does.
 *
 * The functions here build the [GraphCommand] for an operation (one undo step); nothing is applied until you execute it.
 */
public object Subgraphs {
    /** Kind of a subgraph node. Its [GraphNode.data] is the title (a `String`). */
    public const val Kind: String = "subgraph"

    /** Kind of the boundary node that stands for an input port of the enclosing subgraph node. */
    public const val InputKind: String = "subgraph.input"

    /** Kind of the boundary node that stands for an output port of the enclosing subgraph node. */
    public const val OutputKind: String = "subgraph.output"

    /** Whether [node] is a subgraph node. */
    public fun isSubgraph(node: GraphNode): Boolean = node.kind == Kind

    /** Whether [node] is a boundary node of a subgraph. */
    public fun isBoundary(node: GraphNode): Boolean = node.kind == InputKind || node.kind == OutputKind

    /** Id of the boundary node inside [subgraph] for its input port [port]. */
    public fun inputBoundary(subgraph: NodeId, port: PortId): NodeId = NodeId("$subgraph/in/$port")

    /** Id of the boundary node inside [subgraph] for its output port [port]. */
    public fun outputBoundary(subgraph: NodeId, port: PortId): NodeId = NodeId("$subgraph/out/$port")

    /** The single port of a boundary node. */
    public val BoundaryPort: PortId = PortId("value")

    private fun edge(from: PortRef, to: PortRef) = Edge(EdgeId("$from->$to"), from, to)

    private fun boundaryNode(id: NodeId, kind: String, at: Offset, spec: PortSpec, scope: NodeId): GraphNode {
        val port = if (kind == InputKind) PortSpec.output(BoundaryPort.value, spec.label, spec.type) else PortSpec.input(BoundaryPort.value, spec.label, spec.type)
        return GraphNode(id, kind, at, listOf(port), data = spec.label, scope = scope)
    }

    /**
     * Wraps [members] in a new subgraph node [id]. The wires that reach into or out of the members become ports of the new node (one per
     * inner port, with its label and type) and are reconnected to it; inside, boundary nodes carry the same wires to the members.
     * Returns `null` when nothing can be wrapped (no members, [id] taken). Members must be at one level; those at another are ignored.
     */
    public fun create(graph: Graph, members: Set<NodeId>, id: NodeId, title: String = "Subgraph"): GraphCommand? {
        if (id in graph.nodes) return null
        val first = members.firstNotNullOfOrNull { graph.node(it) } ?: return null
        val scope = first.scope
        val inner = graph.nodes.values.filter { it.id in members && it.scope == scope && !isBoundary(it) }
        if (inner.isEmpty()) return null
        val innerIds = inner.map { it.id }.toSet()
        val external = graph.edges.values.filter { (it.from.node in innerIds) != (it.to.node in innerIds) }

        val inputs = LinkedHashMap<PortRef, PortSpec>()
        val outputs = LinkedHashMap<PortRef, PortSpec>()
        for (e in external) {
            if (e.to.node in innerIds) inputs.getOrPut(e.to) { PortSpec(PortId("in${inputs.size + 1}"), PortDirection.Input, graph.port(e.to)!!.label, graph.port(e.to)!!.type, PortCapacity.One) }
            else outputs.getOrPut(e.from) { PortSpec(PortId("out${outputs.size + 1}"), PortDirection.Output, graph.port(e.from)!!.label, graph.port(e.from)!!.type, PortCapacity.Many) }
        }
        val minX = inner.minOf { it.position.x }
        val minY = inner.minOf { it.position.y }
        val maxX = inner.maxOf { it.position.x }
        val subgraph = GraphNode(id, Kind, Offset(minX, minY), inputs.values.toList() + outputs.values.toList(), data = title, scope = scope)

        val commands = ArrayList<GraphCommand>()
        if (external.isNotEmpty()) commands += GraphCommand.Disconnect(external.map { it.id }.toSet())
        commands += GraphCommand.AddNode(subgraph)
        inputs.values.forEachIndexed { i, spec ->
            commands += GraphCommand.AddNode(boundaryNode(inputBoundary(id, spec.id), InputKind, Offset(minX - 320f, minY + i * 110f), spec, id))
        }
        outputs.values.forEachIndexed { i, spec ->
            commands += GraphCommand.AddNode(boundaryNode(outputBoundary(id, spec.id), OutputKind, Offset(maxX + 420f, minY + i * 110f), spec, id))
        }
        for (n in inner) commands += GraphCommand.AddNode(n.copy(scope = id, group = null))
        for ((inner, spec) in inputs) commands += GraphCommand.Connect(edge(PortRef(inputBoundary(id, spec.id), BoundaryPort), inner))
        for ((inner, spec) in outputs) commands += GraphCommand.Connect(edge(inner, PortRef(outputBoundary(id, spec.id), BoundaryPort)))
        for (e in external) {
            if (e.to.node in innerIds) commands += GraphCommand.Connect(edge(e.from, PortRef(id, inputs.getValue(e.to).id)))
            else commands += GraphCommand.Connect(edge(PortRef(id, outputs.getValue(e.from).id), e.to))
        }
        return GraphCommand.Batch(commands, "Create subgraph")
    }

    /**
     * Opens the subgraph node [id] up again: its inner nodes move to its level and every wire that went through a port is joined end to
     * end. The subgraph node and its boundary nodes are removed. Returns `null` when [id] is not a subgraph node.
     */
    public fun dissolve(graph: Graph, id: NodeId): GraphCommand? {
        val node = graph.node(id)?.takeIf { isSubgraph(it) } ?: return null
        val scope = node.scope
        val inside = graph.nodes.values.filter { it.scope == id }
        val boundaries = inside.filter { isBoundary(it) }
        val innerNodes = inside.filter { !isBoundary(it) }
        val gone = boundaries.map { it.id }.toSet() + id
        val touching = graph.edges.values.filter { it.from.node in gone || it.to.node in gone }

        val joins = ArrayList<GraphCommand>()
        for (spec in node.ports) {
            if (spec.direction == PortDirection.Input) {
                val sources = graph.edgesAt(PortRef(id, spec.id)).map { it.from }
                val targets = graph.edgesAt(PortRef(inputBoundary(id, spec.id), BoundaryPort)).map { it.to }
                for (s in sources) for (t in targets) joins += GraphCommand.Connect(edge(s, t))
            } else {
                val sources = graph.edgesAt(PortRef(outputBoundary(id, spec.id), BoundaryPort)).map { it.from }
                val targets = graph.edgesAt(PortRef(id, spec.id)).map { it.to }
                for (s in sources) for (t in targets) joins += GraphCommand.Connect(edge(s, t))
            }
        }
        val commands = ArrayList<GraphCommand>()
        if (touching.isNotEmpty()) commands += GraphCommand.Disconnect(touching.map { it.id }.toSet())
        for (n in innerNodes) commands += GraphCommand.AddNode(n.copy(scope = scope))
        commands += GraphCommand.RemoveNodes(gone)
        commands += joins
        return GraphCommand.Batch(commands, "Open subgraph")
    }

    /** Adds an input port named [label] to the subgraph node [id] together with its boundary node. */
    public fun addInput(graph: Graph, id: NodeId, label: String, type: PortType = PortType.Any): GraphCommand? = addPort(graph, id, label, type, PortDirection.Input)

    /** Adds an output port named [label] to the subgraph node [id] together with its boundary node. */
    public fun addOutput(graph: Graph, id: NodeId, label: String, type: PortType = PortType.Any): GraphCommand? = addPort(graph, id, label, type, PortDirection.Output)

    private fun addPort(graph: Graph, id: NodeId, label: String, type: PortType, direction: PortDirection): GraphCommand? {
        val node = graph.node(id)?.takeIf { isSubgraph(it) } ?: return null
        val prefix = if (direction == PortDirection.Input) "in" else "out"
        var n = node.ports.count { it.direction == direction } + 1
        while (node.port("$prefix$n") != null) n++
        val spec = PortSpec(PortId("$prefix$n"), direction, label, type)
        val inside = graph.nodes.values.filter { it.scope == id }
        val minX = inside.minOfOrNull { it.position.x } ?: 0f
        val maxX = inside.maxOfOrNull { it.position.x } ?: 0f
        val y = inside.filter { it.kind == (if (direction == PortDirection.Input) InputKind else OutputKind) }.maxOfOrNull { it.position.y + 110f } ?: inside.minOfOrNull { it.position.y } ?: 0f
        val boundary = if (direction == PortDirection.Input) boundaryNode(inputBoundary(id, spec.id), InputKind, Offset(minX - 320f, y), spec, id)
        else boundaryNode(outputBoundary(id, spec.id), OutputKind, Offset(maxX + 420f, y), spec, id)
        return GraphCommand.Batch(listOf(GraphCommand.AddNode(node.copy(ports = node.ports + spec)), GraphCommand.AddNode(boundary)), "Add port")
    }
}
