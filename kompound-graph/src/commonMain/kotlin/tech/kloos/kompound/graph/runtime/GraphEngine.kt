package tech.kloos.kompound.graph.runtime

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import tech.kloos.kompound.graph.KRerouteKind
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortDirection
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.SignalMode
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
 * - **Streams.** A runner may emit values while it runs ([NodeRunContext.emit]); each is a signal. A downstream input reacts per its
 *   [SignalMode]: `Latest` re-runs on every new value (cancelling the run in progress), `Each` runs once per value in order,
 *   `Collect` and `Final` wait for the upstream node to finish. A node is [NodeRun.Done] when everything feeding it has finished and
 *   been processed. Signals are kept in memory until the node is reset by an edit, so unbounded streams should be windowed by the app.
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
 * @param autoRun Whether changes and the first graph start runs automatically. With `false` (manual mode) edits only mark results stale;
 * nothing runs until [start], [rerun] or [rerunAll], which run once and then return to idle.
 * @param maxConcurrency How many runners may be active at once.
 * @param runDispatcher Where runners execute.
 * @param trace What is recorded in [executions].
 * @param clock Epoch milliseconds for trace timestamps (replace it in tests).
 * @param signalMode Which [SignalMode] an input port uses. Defaults to what is saved in the port's spec; give a function (for example one
 * that looks at the node kind) to change the behaviour of nodes already saved in old graphs.
 * @param beforeRun Asked just before a node's runner would start, with what started the run (see [TraceTrigger]); return `false` to refuse.
 * A refused node is [NodeRun.Declined], nodes after it are [NodeRun.Blocked], and it is asked again on the next [start] or [rerun]. Use it
 * to keep runners with side effects (processes, network) from running on an automatic run, or to ask the user first. Also asked for
 * [testNode] (with [TraceTrigger.Test]).
 */
@OptIn(ExperimentalAtomicApi::class)
public class GraphEngine(
    private val scope: CoroutineScope,
    private val runners: Map<String, NodeRunner>,
    private val autoRun: Boolean = true,
    maxConcurrency: Int = 4,
    private val runDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val trace: TraceOptions = TraceOptions(),
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val beforeRun: ((node: GraphNode, trigger: TraceTrigger) -> Boolean)? = null,
    private val signalMode: (node: GraphNode, port: PortSpec) -> SignalMode = { _, port -> port.signal },
) {
    private val permits = Semaphore(maxConcurrency.coerceAtLeast(1))
    private val lockWord = AtomicInt(0)

    /** The engine's bookkeeping is not thread safe; entry points (and runner completions) take this short, never-suspending lock. */
    private inline fun <T> locked(block: () -> T): T {
        while (!lockWord.compareAndSet(0, 1)) { /* another thread is inside for a few microseconds */ }
        try { return block() } finally { lockWord.store(0) }
    }
    // Observable so that a composable asking about a node before the first graph arrived is told when it does.
    private var graph: Graph by mutableStateOf(Graph.Empty)
    // Edits start runs while autoRun is on and the engine is not deactivated; a requested one-shot run (start, rerun) is active until it finishes.
    private var deactivated by mutableStateOf(false)
    private var runRequested by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private val idleWaiters = ArrayList<kotlinx.coroutines.CompletableDeferred<Unit>>()
    private val active: Boolean get() = (autoRun && !deactivated) || runRequested
    private val jobs = HashMap<NodeId, Job>()
    private val generation = HashMap<NodeId, Int>()
    private val states = mutableStateMapOf<NodeId, NodeRun>()
    private val rts = HashMap<NodeId, Rt>()
    private val latestValues = mutableStateMapOf<PortRef, Any?>()
    private val signalCounts = mutableStateMapOf<PortRef, Int>()
    private val history = mutableStateListOf<Execution>()
    private val liveAttempts = HashMap<NodeId, NodeAttempt>()
    private var currentExecution: Execution? = null
    private var executionCounter = 0
    private var pendingTrigger = TraceTrigger.Auto

    /** Recorded runs, oldest first (at most [TraceOptions.maxExecutions]); the last one may still be running. Observable. */
    public val executions: List<Execution> get() = history

    /** The execution in progress, or `null` when nothing is running. */
    public val currentRun: Execution? get() = currentExecution

    /** The newest attempt of [id] across the recorded executions (what the node last did), or `null` if it never ran. */
    public fun lastAttempt(id: NodeId): NodeAttempt? = history.lastOrNull { it.latest(id) != null }?.latest(id)

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

    /** The newest value [id] has produced at its output [port] so far, finished or not (`null` before the first). Observable. */
    public fun latest(id: NodeId, port: String): Any? = latestValues[PortRef(id, PortId(port))]

    /** How many values [id] has produced at its output [port] so far. Observable. */
    public fun signalCount(id: NodeId, port: String): Int = signalCounts[PortRef(id, PortId(port))] ?: 0

    /** The value [id] produced at its output [port], or `null` when it has not finished (or produced none). */
    public fun output(id: NodeId, port: String): Any? = (runOf(id) as? NodeRun.Done)?.outputs?.get(PortId(port))

    private fun subgraphRun(node: GraphNode): NodeRun {
        val inner = graph.nodes.values.filter { isReal(it) && withinScope(it, node.id) }
        val own = inner.map { states[it.id] ?: NodeRun.Idle }
        own.filterIsInstance<NodeRun.Failed>().firstOrNull()?.let { return it }
        own.filterIsInstance<NodeRun.Declined>().firstOrNull()?.let { return it }
        own.filterIsInstance<NodeRun.Blocked>().firstOrNull()?.let { return it }
        if (own.any { it is NodeRun.Running }) return NodeRun.Running
        if (own.any { it is NodeRun.Waiting }) return NodeRun.Waiting
        if (own.any { it is NodeRun.Idle }) return NodeRun.Idle
        val router = Router(graph)
        val outputs = LinkedHashMap<PortId, Any?>()
        var skipped = false
        for (spec in node.ports) {
            if (spec.direction != PortDirection.Output) continue
            val source = router.source(PortRef(Subgraphs.outputBoundary(node.id, spec.id), Subgraphs.BoundaryPort)) ?: continue
            when (val st = states[source.node]) {
                is NodeRun.Done -> outputs[spec.id] = st.outputs[source.port]
                NodeRun.Skipped -> skipped = true
                else -> return NodeRun.Waiting
            }
        }
        return if (outputs.isEmpty() && skipped) NodeRun.Skipped else NodeRun.Done(outputs)
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
            is NodeRun.Declined -> NodeRun.Blocked(source.node, declined = true)
            NodeRun.Skipped -> NodeRun.Skipped
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

    /**
     * Whether the engine is running things on its own right now: [autoRun] and not [deactivate]d or [stop]ped, or a requested run
     * ([start], [rerun]) is in progress. Observable.
     */
    public val isActive: Boolean get() = active

    /** Whether any runner is still working. Observable. */
    public val isBusy: Boolean get() = busy

    /** Suspends until no runner is working (returns at once when none is). Nodes waiting for permission or input do not count as working. */
    public suspend fun awaitIdle() {
        val waiter = locked { if (jobs.isEmpty()) null else kotlinx.coroutines.CompletableDeferred<Unit>().also { idleWaiters += it } } ?: return
        waiter.await()
    }

    /** [start]s and suspends until the run has finished (headless use: tests, command line tools). */
    public suspend fun runToCompletion() {
        start()
        awaitIdle()
    }

    /**
     * Stops reacting to edits (an [autoRun] engine behaves like a manual one) without cancelling or discarding anything: runs in
     * progress finish, results and errors stay. [start] turns it back on.
     */
    public fun deactivate(): Unit = locked { deactivated = true }

    /**
     * Tells the engine the graph is now [new]: cancels what became stale, forgets removed nodes, and (when active) starts what can start.
     * Cheap to call on every edit.
     */
    public fun update(new: Graph): Unit = locked { updateLocked(new) }

    private fun updateLocked(new: Graph) {
        val old = graph
        graph = new
        val oldRouter = Router(old)
        val newRouter = Router(new)
        val stale = HashSet<NodeId>()
        for (id in old.nodes.keys) if (id !in new.nodes) {
            cancel(id)
            dropSignals(id)
            states.remove(id)
        }
        for ((id, node) in new.nodes) {
            if (!isReal(node)) continue
            val before = old.nodes[id]
            if (before == null || before.kind != node.kind || before.data != node.data || before.ports != node.ports || before.pin != node.pin) { stale += id; continue }
            // Something about what feeds an input changed: another wire, or a subgraph routed differently.
            for (spec in node.ports) {
                if (spec.direction != PortDirection.Input) continue
                val ref = PortRef(id, spec.id)
                if (oldRouter.source(ref) != newRouter.source(ref)) { stale += id; break }
            }
        }
        invalidate(stale, listOf(newRouter, oldRouter), new)
        for (node in new.nodes.values) if (isReal(node) && node.id !in states) states[node.id] = NodeRun.Idle
        refresh()
    }

    /**
     * Runs every node that has not finished, as soon as what it needs is there, and asks [beforeRun] again for nodes it refused. In
     * manual mode ([autoRun] false) it is a one-shot run: the engine goes back to idle when it is done. With [autoRun] it also turns
     * reacting to edits back on after a [stop] or [deactivate].
     */
    public fun start(): Unit = locked { startLocked() }

    private fun startLocked() {
        deactivated = false
        runRequested = true
        pendingTrigger = TraceTrigger.Manual
        val declined = states.filterValues { it is NodeRun.Declined }.keys
        if (declined.isNotEmpty()) invalidate(declined, listOf(Router(graph)), graph)
        refresh()
    }

    /**
     * Cancels everything that is running and stops reacting to edits (until [start]). Finished results are kept, and so are the errors of
     * nodes that failed (and the [NodeRun.Blocked] and [NodeRun.Declined] states); nodes that were unfinished go back to [NodeRun.Idle].
     */
    public fun stop(): Unit = locked { stopLocked() }

    private fun stopLocked() {
        deactivated = true
        runRequested = false
        // Whatever was unfinished starts over on the next start(): a half-consumed stream cannot be resumed.
        for (node in graph.nodes.values) {
            val state = states[node.id]
            if (!isReal(node) || state is NodeRun.Done || state is NodeRun.Failed || state is NodeRun.Blocked || state is NodeRun.Declined) continue
            cancel(node.id)
            dropSignals(node.id)
            states[node.id] = NodeRun.Idle
        }
        finishIfIdle(cancelled = true)
    }

    /** Throws away the result of [id] and everything downstream of it and runs them again. */
    public fun rerun(id: NodeId): Unit = locked { rerunLocked(id) }

    private fun rerunLocked(id: NodeId) {
        if (id !in graph.nodes) return
        invalidate(setOf(id), listOf(Router(graph)), graph)
        runRequested = true
        pendingTrigger = TraceTrigger.Rerun
        refresh()
    }

    /** Throws away every result and runs the whole graph again. */
    public fun rerunAll(): Unit = locked { rerunAllLocked() }

    private fun rerunAllLocked() {
        invalidate(graph.nodes.keys.filter { isReal(graph.nodes.getValue(it)) }.toSet(), listOf(Router(graph)), graph)
        runRequested = true
        pendingTrigger = TraceTrigger.Rerun
        refresh()
    }

    // --- internals ----------------------------------------------------------------------------------------

    private fun cancel(id: NodeId) {
        jobs.remove(id)?.cancel()
        generation[id] = (generation[id] ?: 0) + 1
        liveAttempts.remove(id)?.let { attempt ->
            attempt.status = TraceStatus.Cancelled
            attempt.finishedAt = clock()
        }
    }

    private fun refresh() {
        schedule()
        finishIfIdle(cancelled = false)
    }

    private fun finishIfIdle(cancelled: Boolean) {
        busy = jobs.isNotEmpty()
        if (jobs.isNotEmpty()) return
        runRequested = false   // a requested one-shot run is over
        if (idleWaiters.isNotEmpty()) {
            idleWaiters.forEach { it.complete(Unit) }
            idleWaiters.clear()
        }
        val execution = currentExecution ?: return
        val failed = execution.anyLatestFailed()
        execution.status = when {
            cancelled -> TraceStatus.Cancelled
            failed -> TraceStatus.Failed
            else -> TraceStatus.Succeeded
        }
        execution.finishedAt = clock()
        currentExecution = null
    }

    private fun beginAttempt(node: GraphNode, inputs: NodeInputs): NodeAttempt {
        val execution = currentExecution ?: Execution(++executionCounter, pendingTrigger, clock()).also {
            pendingTrigger = TraceTrigger.Auto
            currentExecution = it
            history += it
            while (history.size > trace.maxExecutions.coerceAtLeast(1)) history.removeAt(0)
        }
        val captured = trace.captureValues
        val stored = if (captured) inputs.all.mapValues { (port, v) -> trace.redact(node, port, v) } else emptyMap()
        val attempt = NodeAttempt(node.id, execution.nextNumber(node.id), execution.attemptCount, clock(), stored, captured)
        execution.add(attempt)
        liveAttempts[node.id] = attempt
        return attempt
    }

    private inner class Context(
        override val node: GraphNode,
        override val inputs: NodeInputs,
        private val attempt: NodeAttempt,
        private val gen: Int,
    ) : NodeRunContext {
        val emitted = LinkedHashMap<PortId, Any?>()

        override fun log(message: String, level: LogLevel) {
            attempt.addLog(LogLine(clock(), level, message))
        }

        override fun progress(fraction: Float?, message: String?) {
            attempt.progress = fraction?.coerceIn(0f, 1f)
            if (message != null) attempt.progressMessage = message
        }

        override fun emit(port: String, value: Any?) {
            val id = PortId(port)
            emitted[id] = value
            if (gen != Int.MIN_VALUE) scope.launch { onEmit(node.id, gen, id, value) }
            if (trace.captureValues) attempt.addEmission(Emission(clock(), id, trace.redact(node, id, value)))
        }
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
            dropSignals(id)
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
        KRerouteKind -> NodeRunner { ctx -> mapOf("out" to ctx.inputs["in"]) }
        else -> null
    }

    // --- signals ------------------------------------------------------------------------------------------

    /** The values a node has produced at one output port, in order; [complete] once the node has finished for good. */
    private class Feed {
        val values = ArrayList<Any?>()
        var complete = false
    }

    /** Per-node bookkeeping of the streams: what it produced, and how much of each input it has already consumed. */
    private class Rt {
        val feeds = HashMap<PortId, Feed>()
        /** Per input port: values consumed so far ([Used] once a final or collected value was taken). */
        val cursor = HashMap<PortId, Int>()
        var started = false
        var complete = false
        /** Output ports a runner reported [NoSignal] at: they stay empty instead of getting the `null` fill. */
        val silent = HashSet<PortId>()
    }

    private fun rt(id: NodeId): Rt = rts.getOrPut(id) { Rt() }

    private fun dropSignals(id: NodeId) {
        rts.remove(id)
        for (key in latestValues.keys.filter { it.node == id }) latestValues.remove(key)
        for (key in signalCounts.keys.filter { it.node == id }) signalCounts.remove(key)
    }

    private fun publish(node: NodeId, port: PortId, value: Any?) {
        if (value === NoSignal) {
            rt(node).also { it.feeds.getOrPut(port) { Feed() }; it.silent += port }
            return
        }
        val feed = rt(node).feeds.getOrPut(port) { Feed() }
        feed.values += value
        val ref = PortRef(node, port)
        latestValues[ref] = value
        signalCounts[ref] = feed.values.size
    }

    /** What to run a node with next, and how to record that the values were taken. */
    private class Plan(val inputs: NodeInputs, val streaming: Boolean, val commit: () -> Unit)

    /** The feeds behind a node's connected inputs. */
    private class Wire(val spec: PortSpec, val source: PortRef, val feed: Feed?)

    private fun wiresOf(node: GraphNode, router: Router): List<Wire> = node.ports.mapNotNull { spec ->
        if (spec.direction != PortDirection.Input) return@mapNotNull null
        val source = router.source(PortRef(node.id, spec.id)) ?: return@mapNotNull null
        Wire(spec, source, rts[source.node]?.feeds?.get(source.port))
    }

    /** Next run for [node], or `null` when there is nothing new to run with (or an input has no value yet). */
    private fun plan(node: GraphNode, wires: List<Wire>): Plan? {
        val rt = rt(node.id)
        if (wires.isEmpty()) return if (rt.started) null else Plan(NodeInputs(emptyMap()), false) { rt.started = true }
        for (w in wires) {
            val feed = w.feed ?: return null
            val ready = when (signalMode(node, w.spec)) {
                SignalMode.Latest, SignalMode.Each -> feed.values.isNotEmpty()
                SignalMode.Collect -> feed.complete
                SignalMode.Final -> feed.complete && feed.values.isNotEmpty()
                SignalMode.Any -> feed.values.isNotEmpty() || feed.complete
            }
            if (!ready) return null
        }
        // Inputs that are all optional need at least one value between them.
        if (wires.all { signalMode(node, it.spec) == SignalMode.Any } && wires.none { it.feed!!.values.isNotEmpty() }) return null
        var eachPort: PortId? = null
        var pending = false
        for (w in wires) {
            val feed = w.feed!!
            val taken = rt.cursor[w.spec.id] ?: 0
            when (signalMode(node, w.spec)) {
                SignalMode.Each -> if (feed.values.size > taken) { pending = true; if (eachPort == null) eachPort = w.spec.id }
                SignalMode.Latest, SignalMode.Any -> if (feed.values.size > taken) pending = true
                SignalMode.Collect, SignalMode.Final -> if (taken != Used) pending = true
            }
        }
        if (!pending) return null
        val values = LinkedHashMap<PortId, Any?>()
        val commits = ArrayList<() -> Unit>()
        for (w in wires) {
            val feed = w.feed!!
            val id = w.spec.id
            val taken = rt.cursor[id] ?: 0
            when (signalMode(node, w.spec)) {
                SignalMode.Each -> {
                    if (id == eachPort) { values[id] = feed.values[taken]; commits += { rt.cursor[id] = taken + 1 } }
                    else values[id] = feed.values[(taken - 1).coerceAtLeast(0)]
                }
                SignalMode.Latest -> { values[id] = feed.values.last(); val n = feed.values.size; commits += { rt.cursor[id] = n } }
                SignalMode.Any -> { values[id] = feed.values.lastOrNull(); val n = feed.values.size; commits += { rt.cursor[id] = n } }
                SignalMode.Final -> { values[id] = feed.values.lastOrNull(); commits += { rt.cursor[id] = Used } }
                SignalMode.Collect -> { values[id] = feed.values.toList(); commits += { rt.cursor[id] = Used } }
            }
        }
        return Plan(NodeInputs(values), streaming = eachPort != null) { rt.started = true; commits.forEach { it() } }
    }

    private fun schedule() {
        // A run already in progress carries on to the nodes that depend on it even after deactivate().
        if (!active && currentExecution == null) return
        val cycle = cyclic()
        for (id in cycle) {
            if (states[id] !is NodeRun.Failed) {
                cancel(id)
                states[id] = NodeRun.Failed(CycleException(cycle))
            }
        }
        val demanded = demanded(Router(graph))
        // A node's state can unblock or block the nodes after it, which may come earlier in node order: sweep until nothing changes.
        var changed = true
        var sweeps = 0
        while (changed && sweeps++ < 1000) {
            changed = false
            val router = Router(graph)
            for (node in graph.nodes.values) {
                if (!isReal(node)) continue
                val id = node.id
                if (id in cycle) continue
                val current = states[id] ?: NodeRun.Idle
                if (current is NodeRun.Done || current is NodeRun.Failed || current is NodeRun.Declined || current is NodeRun.Skipped) continue
                if (id !in demanded) {
                    // Only pinned nodes would have read it: nothing needs its output, so it stays idle.
                    if (jobs[id] != null || current != NodeRun.Idle) {
                        cancel(id)
                        dropSignals(id)
                        states[id] = NodeRun.Idle
                    }
                    continue
                }
                val pin = node.pin
                if (pin != null) {
                    val rt = rt(id)
                    for ((port, value) in pin) publish(id, port, value)
                    for (spec in node.ports) if (spec.direction == PortDirection.Output) rt.feeds.getOrPut(spec.id) { Feed() }.complete = true
                    rt.started = true
                    rt.complete = true
                    states[id] = NodeRun.Done(pin)
                    changed = true
                    continue
                }
                val wires = wiresOf(node, router)
                val blocker = wires.firstNotNullOfOrNull { w ->
                    when (val up = states[w.source.node]) {
                        is NodeRun.Failed -> NodeRun.Blocked(w.source.node)
                        is NodeRun.Declined -> NodeRun.Blocked(w.source.node, declined = true)
                        is NodeRun.Blocked -> up
                        else -> null
                    }
                }
                if (blocker != null) {
                    cancel(id)
                    val blocked = blocker
                    if (current != blocked) { states[id] = blocked; changed = true }
                    continue
                }
                val running = jobs[id] != null
                val plan = plan(node, wires)
                if (plan != null) {
                    // A run that is still going yields to a newer value (Latest) unless values must be processed one by one (Each).
                    if (running && plan.streaming) continue
                    val runner = runnerFor(node)
                    if (runner == null) { states[id] = NodeRun.Failed(MissingRunnerException(node.kind)); changed = true; continue }
                    val gate = beforeRun
                    if (gate != null && !gate(node, currentExecution?.trigger ?: pendingTrigger)) {
                        if (running) cancel(id)
                        states[id] = NodeRun.Declined
                        changed = true
                        continue
                    }
                    if (running) cancel(id)
                    plan.commit()
                    launchRun(node, runner, plan.inputs)
                    changed = true
                    continue
                }
                if (running) continue
                val upstreamDone = wires.all { it.feed?.complete == true }
                val modes = wires.map { signalMode(node, it.spec) }
                val neverGetsAValue = wires.indices.any { i -> wires[i].feed?.complete == true && wires[i].feed!!.values.isEmpty() && modes[i] != SignalMode.Collect && modes[i] != SignalMode.Any } ||
                    (wires.isNotEmpty() && modes.all { it == SignalMode.Any } && wires.all { it.feed?.complete == true && it.feed.values.isEmpty() })
                if (!rt(id).started && neverGetsAValue) {
                    // An input that can never get a value (no signal upstream): this node cannot run, and neither can what follows.
                    val rt = rt(id)
                    for (spec in node.ports) if (spec.direction == PortDirection.Output) rt.feeds.getOrPut(spec.id) { Feed() }.complete = true
                    rt.complete = true
                    states[id] = NodeRun.Skipped
                    changed = true
                    continue
                }
                val finished = upstreamDone && (wires.isNotEmpty() || rt(id).started)
                if (finished) {
                    val rt = rt(id)
                    for (spec in node.ports) if (spec.direction == PortDirection.Output) rt.feeds.getOrPut(spec.id) { Feed() }.complete = true
                    rt.complete = true
                    states[id] = NodeRun.Done(rt.feeds.filterValues { it.values.isNotEmpty() }.mapValues { it.value.values.last() })
                    changed = true
                } else if (current != NodeRun.Waiting) {
                    states[id] = NodeRun.Waiting
                }
            }
        }
    }

    /** Nodes whose output is needed: every node nothing reads from, and whatever an unpinned needed node reads. */
    private fun demanded(router: Router): Set<NodeId> {
        val deps = router.dependencies()
        val read = HashSet<NodeId>()
        for (sources in deps.values) read += sources
        val result = HashSet<NodeId>()
        val queue = ArrayDeque(deps.keys.filter { it !in read })
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!result.add(id)) continue
            if (graph.nodes[id]?.pin != null) continue
            deps[id]?.let { queue.addAll(it) }
        }
        return result
    }

    /**
     * What is arriving at the connected inputs of [id] right now: the newest value its upstream nodes have produced (empty for ports
     * with nothing yet). Handy to prefill a test run with real data.
     */
    public fun currentInputs(id: NodeId): Map<PortId, Any?> {
        val node = graph.node(id) ?: return emptyMap()
        val result = LinkedHashMap<PortId, Any?>()
        for (w in wiresOf(node, Router(graph))) w.feed?.values?.takeIf { it.isNotEmpty() }?.let { result[w.spec.id] = it.last() }
        return result
    }

    /** The outputs of [id] once it is done (a pinned node's pin included), or `null` before. */
    public fun outputsOf(id: NodeId): Map<PortId, Any?>? = (runOf(id) as? NodeRun.Done)?.outputs

    /**
     * Runs the single node [id] once with [inputs] (by port name), outside the graph: nothing upstream runs, nothing downstream sees the
     * result, the node's state in the graph does not change. It is recorded as an [Execution] with trigger [TraceTrigger.Test], so the
     * inspector shows its logs, progress and output like any other run. Returns `null` for an unknown or non-running node, or when [beforeRun] refuses.
     */
    public fun testNode(id: NodeId, inputs: Map<String, Any?>): NodeTestRun? = locked { testNodeLocked(id, inputs) }

    private fun testNodeLocked(id: NodeId, inputs: Map<String, Any?>): NodeTestRun? {
        val node = graph.node(id)?.takeIf { isReal(it) } ?: return null
        if (beforeRun?.invoke(node, TraceTrigger.Test) == false) return null
        val values = inputs.mapKeys { PortId(it.key) }
        val execution = Execution(++executionCounter, TraceTrigger.Test, clock())
        history += execution
        while (history.size > trace.maxExecutions.coerceAtLeast(1)) history.removeAt(0)
        val captured = trace.captureValues
        val stored = if (captured) values.mapValues { (port, v) -> trace.redact(node, port, v) } else emptyMap()
        val attempt = NodeAttempt(id, 1, 0, clock(), stored, captured)
        execution.add(attempt)
        val context = Context(node, NodeInputs(values), attempt, gen = Int.MIN_VALUE)
        val runner = runnerFor(node)
        var testResult: Map<PortId, Any?>? = null
        val job = scope.launch {
            try {
                if (runner == null) throw MissingRunnerException(node.kind)
                val returned = permits.withPermit { withContext(runDispatcher) { runner.run(context) } }
                val last = context.emitted + returned.mapKeys { PortId(it.key) }
                attempt.outputs = if (captured) last.mapValues { (port, v) -> trace.redact(node, port, v) } else emptyMap()
                attempt.status = TraceStatus.Succeeded
                testResult = last
            } catch (e: CancellationException) {
                attempt.status = TraceStatus.Cancelled
                throw e
            } catch (e: Throwable) {
                attempt.error = e
                attempt.status = TraceStatus.Failed
            } finally {
                attempt.finishedAt = clock()
                execution.status = attempt.status
                execution.finishedAt = attempt.finishedAt
            }
        }
        return NodeTestRun(execution, attempt, job) { testResult }
    }

    private fun launchRun(node: GraphNode, runner: NodeRunner, inputs: NodeInputs) {
        val id = node.id
        val gen = (generation[id] ?: 0) + 1
        generation[id] = gen
        states[id] = NodeRun.Running
        val attempt = beginAttempt(node, inputs)
        val context = Context(node, inputs, attempt, gen)
        jobs[id] = scope.launch {
            val outcome: Result<Map<PortId, Any?>> = try {
                val returned = permits.withPermit { withContext(runDispatcher) { runner.run(context) } }
                Result.success(returned.mapKeys { PortId(it.key) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
            locked { finishRun(node, gen, attempt, context, outcome) }
        }
    }

    /** Records how a run ended and moves on. Called with the lock held, on whichever thread the runner resumed. */
    private fun finishRun(node: GraphNode, gen: Int, attempt: NodeAttempt, context: Context, outcome: Result<Map<PortId, Any?>>) {
        val id = node.id
        if (generation[id] != gen) return
        jobs.remove(id)
        liveAttempts.remove(id)
        attempt.finishedAt = clock()
        val failure = outcome.exceptionOrNull()
        if (failure != null) {
            attempt.error = failure
            attempt.status = TraceStatus.Failed
            states[id] = NodeRun.Failed(failure)
        } else {
            val returned = outcome.getOrThrow()
            val feeds = rt(id).feeds
            for ((port, value) in returned) publish(id, port, value)
            // A port nothing was ever produced at reads as one null, so downstream nodes are not left waiting.
            for (spec in node.ports) if (spec.direction == PortDirection.Output && spec.id !in rt(id).silent && feeds[spec.id]?.values.isNullOrEmpty()) publish(id, spec.id, null)
            val last = context.emitted + returned
            attempt.outputs = if (trace.captureValues) last.mapValues { (port, v) -> trace.redact(node, port, v) } else emptyMap()
            attempt.status = TraceStatus.Succeeded
            states[id] = NodeRun.Waiting
        }
        refresh()
    }

    /** An emission arrives from the runner's thread; the feed is only touched on the engine's. */
    private fun onEmit(node: NodeId, gen: Int, port: PortId, value: Any?) = locked {
        if (generation[node] == gen) {
            publish(node, port, value)
            refresh()
        }
    }

    private companion object {
        const val Used = Int.MAX_VALUE
        const val COMMENT = "comment"

        /** Whether the node runs; subgraph, boundary and comment nodes only route or annotate. */
        fun isReal(node: GraphNode): Boolean =
            node.kind != Subgraphs.Kind && node.kind != Subgraphs.InputKind && node.kind != Subgraphs.OutputKind && node.kind != COMMENT
    }
}
