package tech.kloos.kompound.graph.model

/** Why two ports cannot be connected. */
public enum class ConnectionRejection {
    /** A port does not exist. */
    UnknownPort,

    /** Both ports are inputs or both are outputs. */
    SameDirection,

    /** Both ports belong to the same node. */
    SameNode,

    /** The ports are already connected to each other. */
    AlreadyConnected,

    /** The port types do not match. */
    TypeMismatch,

    /** The target takes one edge and already has one, and replacing it is switched off. */
    CapacityExceeded,

    /** The edge would close a loop and cycles are not allowed. */
    WouldCreateCycle,
}

/** Result of asking a [ConnectionPolicy] about two ports. */
public sealed interface ConnectionCheck {
    /**
     * The connection is allowed, as an edge from [from] (an output) to [to] (an input).
     * [replaces] lists existing edges that must go because a port takes only one edge.
     */
    public data class Allowed(public val from: PortRef, public val to: PortRef, public val replaces: List<EdgeId> = emptyList()) : ConnectionCheck

    /** The connection is refused for [reason]. */
    public data class Rejected(public val reason: ConnectionRejection) : ConnectionCheck
}

/**
 * Rules for which ports may be connected. It is a pure function of the graph and two ports: the editor calls it
 * while a wire is being dragged (to highlight valid targets) and again when it is dropped.
 *
 * @property allowCycles Whether an edge may close a loop. Off by default (most dataflow graphs are acyclic).
 * @property replaceExisting When a port that takes one edge already has one, drop the old edge instead of refusing.
 * @property typeRule Whether a wire from an output of the first type may end at an input of the second.
 */
public class ConnectionPolicy(
    public val allowCycles: Boolean = false,
    public val replaceExisting: Boolean = true,
    public val typeRule: (output: PortType, input: PortType) -> Boolean = { out, inp -> inp.accepts(out) },
) {
    /** Checks a connection between [a] and [b]; the order does not matter, the direction is worked out from the ports. */
    public fun check(graph: Graph, a: PortRef, b: PortRef): ConnectionCheck {
        val specA = graph.port(a) ?: return ConnectionCheck.Rejected(ConnectionRejection.UnknownPort)
        val specB = graph.port(b) ?: return ConnectionCheck.Rejected(ConnectionRejection.UnknownPort)
        if (specA.direction == specB.direction) return ConnectionCheck.Rejected(ConnectionRejection.SameDirection)
        if (a.node == b.node) return ConnectionCheck.Rejected(ConnectionRejection.SameNode)
        val (from, to) = if (specA.direction == PortDirection.Output) a to b else b to a
        val out = graph.port(from)!!
        val inp = graph.port(to)!!
        if (graph.edgesAt(from).any { it.to == to }) return ConnectionCheck.Rejected(ConnectionRejection.AlreadyConnected)
        if (!typeRule(out.type, inp.type)) return ConnectionCheck.Rejected(ConnectionRejection.TypeMismatch)
        val replaced = ArrayList<EdgeId>()
        for ((ref, spec) in listOf(to to inp, from to out)) {
            if (spec.capacity == PortCapacity.One) {
                val existing = graph.edgesAt(ref)
                if (existing.isNotEmpty()) {
                    if (!replaceExisting) return ConnectionCheck.Rejected(ConnectionRejection.CapacityExceeded)
                    replaced += existing.map { it.id }
                }
            }
        }
        if (!allowCycles && reaches(graph, from = to.node, target = from.node, ignoring = replaced.toSet())) {
            return ConnectionCheck.Rejected(ConnectionRejection.WouldCreateCycle)
        }
        return ConnectionCheck.Allowed(from, to, replaced.distinct())
    }

    /** Whether [target] can be reached from [from] by following edges downstream, ignoring the edges in [ignoring]. */
    private fun reaches(graph: Graph, from: NodeId, target: NodeId, ignoring: Set<EdgeId>): Boolean {
        val seen = HashSet<NodeId>()
        val stack = ArrayList<NodeId>().also { it.add(from) }
        while (stack.isNotEmpty()) {
            val n = stack.removeAt(stack.lastIndex)
            if (n == target) return true
            if (!seen.add(n)) continue
            for (e in graph.edges.values) if (e.from.node == n && e.id !in ignoring) stack.add(e.to.node)
        }
        return false
    }
}
