package tech.kloos.kompound.graph.runtime

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tech.kloos.kompound.graph.KRerouteKind
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortDirection
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.Subgraphs
import tech.kloos.kompound.graph.model.PortId

/**
 * Runs the nodes of a [Graph] in dependency order and passes each node's results to the nodes it feeds. It is a building block: it knows
 * nothing about what nodes do (that is what your [NodeRunner]s are for) and nothing about the editor.
 *
 * - **Suspending nodes.** A runner may take as long as it needs. Nodes that do not depend on each other run concurrently (at most
 *   [maxConcurrency] at once); when a slow node finishes, its outputs flow on and whatever was waiting for it starts.
 * - **Editing while it runs.** Hand every new graph to [update]. A node whose kind, data or ports changed, or whose incoming wires
 *   changed, is cancelled and re-run together with everything downstream of it, and a result of an older run can never overwrite a newer
 *   one. Moving nodes does not disturb anything.
 * - **Manual or automatic.** With [autoRun] changes start runs by themselves; otherwise nothing runs until [start] (or [rerun]).
 * - **Failures** stay local: the node shows [NodeRun.Failed], nodes downstream show [NodeRun.Blocked], independent branches go on.
 * - **Cycles** are not run: every node on one fails with [CycleException].
 *
 * Reroute nodes pass their value on. Subgraphs are routed through: a subgraph node and its boundary nodes never run, wires into and out
 * of them are followed to the real nodes behind them at any nesting depth, and the subgraph node reports the combined state of what
 * is inside it (see [runOf]). Comments are ignored.
 *
 * Thread safety: call everything from one dispatcher (the main thread for UI use); runners run on [runDispatcher]. [runs] is observable
 * Compose state, so composables that read it update as nodes progress.
 *
 * @param scope Owns the runner coroutines; cancelling it cancels all runs. Its dispatcher must be single threaded.
 * @param runners Runner per node kind.
 * @param autoRun Whether changes and the first graph start runs automatically.
 * @param maxConcurrency How many runners may be active at once.
 * @param runDispatcher Where runners execute.
 */
public class GraphEngine(
    private val scope: CoroutineScope,
    private val runners: Map<String, NodeRunner>,
    private val autoRun: Boolean = true,
    maxConcurrency: Int = 4,
    private val runDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val permits = Semaphore(maxConcurrency.coerceAtLeast(1))
    private var graph: Graph = Graph.Empty
    private var active = autoRun
    private val jobs = HashMap<NodeId, Job>()
    private val generation = HashMap<NodeId, Int>()
    private val states = mutableStateMapOf<NodeId, NodeRun>()

    /** The state of every node that runs (subgraph, boundary and comment nodes do not run; ask [runOf] for them). */
    public val runs: Map<NodeId, NodeRun> get() = states

    /**
     * The state of [id] ([NodeRun.Idle] for unknown nodes). A subgraph node reports what is inside it: [NodeRun.Failed] if anything
     * failed, else [NodeRun.Blocked], [NodeRun.Running], [NodeRun.Waiting] or [NodeRun.Idle] in that order of precedence, and
     * [NodeRun.Done] with the values of its output ports once everything inside is done. A boundary node reports the value it carries.
     */
    public fun runOf(id: NodeId): NodeRun {
        val node = graph.node(id) ?: return NodeRun.Idle
        return when (node.kind) {
            Subgraphs.Kind -> subgraphRun(node)
            Subgraphs.InputKind, Subgraphs.OutputKind -> boundaryRun(node)
            COMMENT -> NodeRun.Done(emptyMap())
            else -> states[id] ?: NodeRun.Idle
        }
    }

    /** The value [id] produced at its output [port], or `null` when it has not finished (or produced none). */
    public fun output(id: NodeId, port: String): Any? = (runOf(id) as? NodeRun.Done)?.outputs?.get(PortId(port))

    private fun subgraphRun(node: GraphNode): NodeRun {
        val inner = graph.nodes.values.filter { isReal(it) && withinScope(it, node.id) }
        val own = inner.map { states[it.id] ?: NodeRun.Idle }
        own.filterIsInstance<NodeRun.Failed>().firstOrNull()?.let { return it }
        own.filterIsInstance<NodeRun.Blocked>().firstOrNull()?.let { return it }
        if (own.any { it is NodeRun.Running }) return NodeRun.Running
        if (own.any { it is NodeRun.Waiting }) return NodeRun.Waiting
        if (own.any { it is NodeRun.Idle }) return NodeRun.Idle
        val router = Router(graph)
        val outputs = LinkedHashMap<PortId, Any?>()
        for (spec in node.ports) {
            if (spec.direction != PortDirection.Output) continue
            val source = router.source(PortRef(Subgraphs.outputBoundary(node.id, spec.id), Subgraphs.BoundaryPort)) ?: continue
            val done = states[source.node] as? NodeRun.Done ?: return NodeRun.Waiting
            outputs[spec.id] = done.outputs[source.port]
        }
        return NodeRun.Done(outputs)
    }

    private fun boundaryRun(node: GraphNode): NodeRun {
        val router = Router(graph)
        val scope = node.scope
        val feeding = if (node.kind == Subgraphs.InputKind) {
            if (scope == null) null else PortRef(scope, PortId(node.id.value.removePrefix("$scope/in/")))
        } else PortRef(node.id, Subgraphs.BoundaryPort)
        val source = feeding?.let { router.source(it) } ?: return NodeRun.Done(emptyMap())
        return when (val up = states[source.node]) {
            is NodeRun.Done -> NodeRun.Done(mapOf(Subgraphs.BoundaryPort to up.outputs[source.port]))
            is NodeRun.Failed -> NodeRun.Blocked(source.node)
            is NodeRun.Blocked -> up
            else -> NodeRun.Waiting
        }
    }

    private fun withinScope(node: GraphNode, scope: NodeId): Boolean {
        var current = node.scope
        var guard = 0
        while (current != null && guard++ < 64) {
            if (current == scope) return true
            current = graph.node(current)?.scope
        }
        return false
    }

    /** Whether a run is wanted (started, or [autoRun]). */
    public val isActive: Boolean get() = active

    /** Whether any runner is still working. */
    public val isBusy: Boolean get() = jobs.isNotEmpty()

    /**
     * Tells the engine the graph is now [new]: cancels what became stale, forgets removed nodes, and (when active) starts what can start.
     * Cheap to call on every edit.
     */
    public fun update(new: Graph) {
        val old = graph
        graph = new
        val oldRouter = Router(old)
        val newRouter = Router(new)
        val stale = HashSet<NodeId>()
        for (id in old.nodes.keys) if (id !in new.nodes) {
            cancel(id)
            states.remove(id)
        }
        for ((id, node) in new.nodes) {
            if (!isReal(node)) continue
            val before = old.nodes[id]
            if (before == null || before.kind != node.kind || before.data != node.data || before.ports != node.ports) { stale += id; continue }
            // Something about what feeds an input changed: another wire, or a subgraph routed differently.
            for (spec in node.ports) {
                if (spec.direction != PortDirection.Input) continue
                val ref = PortRef(id, spec.id)
                if (oldRouter.source(ref) != newRouter.source(ref)) { stale += id; break }
            }
        }
        invalidate(stale, listOf(newRouter, oldRouter), new)
        for (node in new.nodes.values) if (isReal(node) && node.id !in states) states[node.id] = NodeRun.Idle
        schedule()
    }

    /** Starts running: every node that has not finished runs as soon as what it needs is there. Stays active for later edits. */
    public fun start() {
        active = true
        schedule()
    }

    /** Cancels everything that is running and stops reacting to edits (until [start]). Finished results are kept. */
    public fun stop() {
        active = false
        for (id in jobs.keys.toList()) {
            cancel(id)
            states[id] = NodeRun.Idle
        }
    }

    /** Throws away the result of [id] and everything downstream of it and runs them again. */
    public fun rerun(id: NodeId) {
        if (id !in graph.nodes) return
        invalidate(setOf(id), listOf(Router(graph)), graph)
        active = true
        schedule()
    }

    /** Throws away every result and runs the whole graph again. */
    public fun rerunAll() {
        invalidate(graph.nodes.keys.filter { isReal(graph.nodes.getValue(it)) }.toSet(), listOf(Router(graph)), graph)
        active = true
        schedule()
    }

    // --- internals ----------------------------------------------------------------------------------------

    private fun cancel(id: NodeId) {
        jobs.remove(id)?.cancel()
        generation[id] = (generation[id] ?: 0) + 1
    }

    /** Resets [ids] and every node that (transitively) reads from them, following the wires of each of [routers] through subgraphs. */
    private fun invalidate(ids: Set<NodeId>, routers: List<Router>, current: Graph) {
        val readers = HashMap<NodeId, MutableSet<NodeId>>()
        for (router in routers) for ((node, sources) in router.dependencies()) for (s in sources) readers.getOrPut(s) { HashSet() } += node
        val seen = LinkedHashSet<NodeId>()
        val queue = ArrayDeque(ids)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            readers[id]?.let { queue.addAll(it) }
        }
        for (id in seen) if (current.nodes[id]?.let { isReal(it) } == true) {
            cancel(id)
            states[id] = NodeRun.Idle
        }
    }

    /** Real nodes that lie on a cycle or downstream of one. */
    private fun cyclic(): Set<NodeId> {
        val deps = Router(graph).dependencies()
        val indegree = HashMap<NodeId, Int>()
        val next = HashMap<NodeId, MutableList<NodeId>>()
        for ((node, sources) in deps) {
            indegree[node] = sources.size
            for (s in sources) next.getOrPut(s) { ArrayList() } += node
        }
        val queue = ArrayDeque(indegree.filterValues { it == 0 }.keys)
        val peeled = HashSet<NodeId>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            peeled += id
            for (n in next[id].orEmpty()) {
                val d = indegree.getValue(n) - 1
                indegree[n] = d
                if (d == 0) queue += n
            }
        }
        return deps.keys.filterTo(HashSet()) { it !in peeled }
    }

    /**
     * Follows wires through subgraphs: [source] says which real output port feeds an input port (`null` when nothing does), however many
     * subgraph boundaries lie in between.
     */
    private class Router(private val graph: Graph) {
        fun source(input: PortRef): PortRef? {
            var current = input
            var guard = 0
            while (guard++ < 128) {
                val edge = graph.edgesAt(current).firstOrNull { it.to == current } ?: return null
                val from = edge.from
                val node = graph.node(from.node) ?: return null
                current = when (node.kind) {
                    // Out of a subgraph node: continue inside, at what feeds the boundary node of that output port.
                    Subgraphs.Kind -> PortRef(Subgraphs.outputBoundary(node.id, from.port), Subgraphs.BoundaryPort)
                    // Out of an input boundary node: continue outside, at what feeds the subgraph node's port.
                    Subgraphs.InputKind -> PortRef(node.scope ?: return null, PortId(node.id.value.removePrefix("${node.scope}/in/")))
                    else -> return if (isReal(node)) from else null
                }
            }
            return null
        }

        /** For every real node, the real nodes it reads from. */
        fun dependencies(): Map<NodeId, Set<NodeId>> {
            val result = LinkedHashMap<NodeId, Set<NodeId>>()
            for (node in graph.nodes.values) {
                if (!isReal(node)) continue
                val sources = LinkedHashSet<NodeId>()
                for (spec in node.ports) if (spec.direction == PortDirection.Input) source(PortRef(node.id, spec.id))?.let { sources += it.node }
                result[node.id] = sources
            }
            return result
        }
    }

    private fun runnerFor(node: GraphNode): NodeRunner? = runners[node.kind] ?: when (node.kind) {
        KRerouteKind -> NodeRunner { _, inputs -> mapOf("out" to inputs["in"]) }
        else -> null
    }

    private fun schedule() {
        if (!active) return
        val cycle = cyclic()
        for (id in cycle) {
            if (states[id] !is NodeRun.Failed) {
                cancel(id)
                states[id] = NodeRun.Failed(CycleException(cycle))
            }
        }
        // Marking a node Blocked can block the nodes after it, which may come earlier in node order: sweep until nothing changes.
        var changed = true
        while (changed) {
            changed = false
            for (node in graph.nodes.values) {
                if (!isReal(node)) continue
                val id = node.id
                if (id in cycle) continue
                val current = states[id] ?: NodeRun.Idle
                if (current is NodeRun.Running || current is NodeRun.Done || current is NodeRun.Failed) continue
                val ready = readiness(node)
                val blocked = ready.state
                if (blocked != null) {
                    if (blocked != current) {
                        states[id] = blocked
                        changed = changed || blocked is NodeRun.Blocked
                    }
                    continue
                }
                val runner = runnerFor(node)
                if (runner == null) states[id] = NodeRun.Failed(MissingRunnerException(node.kind)).also { changed = true }
                else launchRun(node, runner, ready.inputs)
            }
        }
    }

    /** [state] is `null` when the node can run now. */
    private class Readiness(val state: NodeRun?, val inputs: NodeInputs)

    private fun readiness(node: GraphNode): Readiness {
        val values = LinkedHashMap<PortId, Any?>()
        var waiting = false
        val router = Router(graph)
        for (spec in node.ports) {
            if (spec.direction != PortDirection.Input) continue
            val from = router.source(PortRef(node.id, spec.id)) ?: continue
            when (val up = states[from.node]) {
                is NodeRun.Done -> values[spec.id] = up.outputs[from.port]
                is NodeRun.Failed -> return Readiness(NodeRun.Blocked(from.node), NodeInputs(emptyMap()))
                is NodeRun.Blocked -> return Readiness(NodeRun.Blocked(up.by), NodeInputs(emptyMap()))
                else -> waiting = true
            }
        }
        return Readiness(if (waiting) NodeRun.Waiting else null, NodeInputs(values))
    }

    private fun launchRun(node: GraphNode, runner: NodeRunner, inputs: NodeInputs) {
        val id = node.id
        val gen = (generation[id] ?: 0) + 1
        generation[id] = gen
        states[id] = NodeRun.Running
        jobs[id] = scope.launch {
            val result: NodeRun = try {
                permits.withPermit { withContext(runDispatcher) { runner.run(node, inputs) } }
                    .let { out -> NodeRun.Done(out.mapKeys { PortId(it.key) }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                NodeRun.Failed(e)
            }
            if (generation[id] != gen) return@launch
            jobs.remove(id)
            states[id] = result
            schedule()
        }
    }

    private companion object {
        const val COMMENT = "comment"

        /** Whether the node runs; subgraph, boundary and comment nodes only route or annotate. */
        fun isReal(node: GraphNode): Boolean =
            node.kind != Subgraphs.Kind && node.kind != Subgraphs.InputKind && node.kind != Subgraphs.OutputKind && node.kind != COMMENT
    }
}
