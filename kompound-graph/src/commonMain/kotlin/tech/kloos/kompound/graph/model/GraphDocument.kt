package tech.kloos.kompound.graph.model

/**
 * A [Graph] with its edit history: the pure (Compose-free) core of the editor. All changes go through [execute], so
 * they can be undone and redone; [connect] validates with the [policy] first.
 *
 * Not thread-safe; call it from one thread (the UI thread in the editor).
 *
 * @param initial Starting graph.
 * @param policy Rules for [connect].
 * @param maxHistory Undo steps kept; the oldest is dropped beyond that.
 */
public class GraphDocument(
    initial: Graph = Graph.Empty,
    public val policy: ConnectionPolicy = ConnectionPolicy(),
    public val maxHistory: Int = 200,
) {
    private class Step(val command: GraphCommand, val inverse: GraphCommand)

    private val undoSteps = ArrayList<Step>()
    private val redoSteps = ArrayList<Step>()

    /** The current graph. */
    public var graph: Graph = initial
        private set

    /** Whether [undo] has something to undo. */
    public val canUndo: Boolean get() = undoSteps.isNotEmpty()

    /** Whether [redo] has something to redo. */
    public val canRedo: Boolean get() = redoSteps.isNotEmpty()

    /** Applies [command]. Returns `false` when it changed nothing (then no undo step is recorded). A new edit clears redo. */
    public fun execute(command: GraphCommand): Boolean {
        val applied = command.applyTo(graph) ?: return false
        graph = applied.graph
        undoSteps += Step(command, applied.inverse)
        if (undoSteps.size > maxHistory) undoSteps.removeAt(0)
        redoSteps.clear()
        return true
    }

    /**
     * Connects [a] and [b] in whichever direction the ports allow, replacing edges where a port takes only one.
     * Nothing changes when the [policy] refuses. The edge id is `"<from>-><to>"`.
     */
    public fun connect(a: PortRef, b: PortRef): ConnectionCheck {
        val check = policy.check(graph, a, b)
        if (check is ConnectionCheck.Allowed) {
            val edge = Edge(EdgeId("${check.from}->${check.to}"), check.from, check.to)
            val steps = buildList {
                if (check.replaces.isNotEmpty()) add(GraphCommand.Disconnect(check.replaces.toSet()))
                add(GraphCommand.Connect(edge))
            }
            execute(if (steps.size == 1) steps.single() else GraphCommand.Batch(steps, "Connect"))
        }
        return check
    }

    /** Undoes the last step; `false` when there is none. */
    public fun undo(): Boolean {
        val step = undoSteps.removeLastOrNull() ?: return false
        val applied = step.inverse.applyTo(graph)
        if (applied != null) graph = applied.graph
        redoSteps += step
        return true
    }

    /** Redoes the last undone step; `false` when there is none. */
    public fun redo(): Boolean {
        val step = redoSteps.removeLastOrNull() ?: return false
        val applied = step.command.applyTo(graph)
        if (applied != null) graph = applied.graph
        undoSteps += step
        return true
    }

    /** Forgets all undo and redo steps (for example after loading a file). */
    public fun clearHistory() {
        undoSteps.clear()
        redoSteps.clear()
    }

    /** Replaces the graph without recording a step and clears the history. */
    public fun reset(graph: Graph) {
        this.graph = graph
        clearHistory()
    }
}
