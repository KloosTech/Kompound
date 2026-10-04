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
 * Reroute nodes pass their value on; comments and subgraph nodes are not executed (a subgraph's inner nodes are ordinary nodes of
 * their own scope, wired through its boundary nodes, which this engine does not route through yet).
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

    /** The state of every node of the current graph. */
    public val runs: Map<NodeId, NodeRun> get() = states

    /** The state of [id] ([NodeRun.Idle] for unknown nodes). */
    public fun runOf(id: NodeId): NodeRun = states[id] ?: NodeRun.Idle

    /** The value [id] produced at its output [port], or `null` when it has not finished (or produced none). */
    public fun output(id: NodeId, port: String): Any? = (states[id] as? NodeRun.Done)?.outputs?.get(PortId(port))

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
        val stale = HashSet<NodeId>()
        for (id in old.nodes.keys) if (id !in new.nodes) {
            cancel(id)
            states.remove(id)
            stale += id
        }
        for ((id, node) in new.nodes) {
            val before = old.nodes[id]
            if (before == null || before.kind != node.kind || before.data != node.data || before.ports != node.ports) stale += id
        }
        val oldIn = old.edges.values.filter { it.to.node in new.nodes }.toSet()
        val newIn = new.edges.values.toSet()
        for (e in oldIn - newIn) stale += e.to.node
        for (e in newIn - oldIn) stale += e.to.node
        // Edges that vanished with a removed node still change what their target receives.
        for (e in old.edges.values) if (e.from.node !in new.nodes) stale += e.to.node
        invalidate(stale.filter { it in new.nodes }.toSet(), downstreamIn = new, alsoIn = old)
        for (id in new.nodes.keys) if (id !in states) states[id] = NodeRun.Idle
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
        invalidate(setOf(id), graph, graph)
        active = true
        schedule()
    }

    /** Throws away every result and runs the whole graph again. */
    public fun rerunAll() {
        invalidate(graph.nodes.keys.toSet(), graph, graph)
        active = true
        schedule()
    }

    // --- internals ----------------------------------------------------------------------------------------

    private fun cancel(id: NodeId) {
        jobs.remove(id)?.cancel()
        generation[id] = (generation[id] ?: 0) + 1
    }

    /** Resets [ids] and everything reachable from them along wires of either graph. */
    private fun invalidate(ids: Set<NodeId>, downstreamIn: Graph, alsoIn: Graph) {
        val seen = LinkedHashSet<NodeId>()
        val queue = ArrayDeque(ids)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            for (g in listOf(downstreamIn, alsoIn)) {
                g.nodes[id]?.ports?.forEach { p ->
                    if (p.direction == PortDirection.Output) {
                        g.edgesAt(tech.kloos.kompound.graph.model.PortRef(id, p.id)).forEach { queue += it.to.node }
                    }
                }
            }
        }
        for (id in seen) if (id in downstreamIn.nodes) {
            cancel(id)
            states[id] = NodeRun.Idle
        }
    }

    private fun cyclic(): Set<NodeId> {
        // Kahn: whatever cannot be peeled off lies on a cycle or downstream of one; keep only nodes that can reach themselves.
        val indegree = HashMap<NodeId, Int>()
        val next = HashMap<NodeId, MutableList<NodeId>>()
        for (id in graph.nodes.keys) indegree[id] = 0
        for (e in graph.edges.values) {
            if (e.from.node !in graph.nodes || e.to.node !in graph.nodes) continue
            indegree[e.to.node] = indegree.getValue(e.to.node) + 1
            next.getOrPut(e.from.node) { ArrayList() } += e.to.node
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
        return graph.nodes.keys.filterTo(HashSet()) { it !in peeled }
    }

    private fun runnerFor(node: GraphNode): NodeRunner? = runners[node.kind] ?: when (node.kind) {
        KRerouteKind -> NodeRunner { _, inputs -> mapOf("out" to inputs["in"]) }
        "comment", "subgraph", "subgraph.input", "subgraph.output" -> NodeRunner { _, _ -> emptyMap() }
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
        for (spec in node.ports) {
            if (spec.direction != PortDirection.Input) continue
            val edge = graph.edgesAt(tech.kloos.kompound.graph.model.PortRef(node.id, spec.id)).firstOrNull() ?: continue
            when (val up = states[edge.from.node]) {
                is NodeRun.Done -> values[spec.id] = up.outputs[edge.from.port]
                is NodeRun.Failed -> return Readiness(NodeRun.Blocked(edge.from.node), NodeInputs(emptyMap()))
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
}
