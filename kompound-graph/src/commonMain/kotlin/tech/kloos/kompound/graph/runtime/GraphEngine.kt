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
import kotlinx.coroutines.cancel
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
import tech.kloos.kompound.graph.model.KSecret
import tech.kloos.kompound.graph.model.JsonPortType
import tech.kloos.kompound.json.jsonObjectOf
import tech.kloos.kompound.json.at
import tech.kloos.kompound.json.JsonString
import tech.kloos.kompound.json.JsonObject
import tech.kloos.kompound.json.JsonArray
import tech.kloos.kompound.json.JsonValue
import tech.kloos.kompound.json.JsonFields
import tech.kloos.kompound.json.FieldInfo
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
 * @param triggers Trigger kinds ([TriggerRunner]): nodes that wait for outside events and start an isolated **event run** of everything downstream of them
 * each time they fire. A trigger is [NodeRun.Listening] while armed. Event runs are recorded as executions with [TraceTrigger.Event].
 * @param eventPolicy What to do with an event that arrives while earlier events of the same trigger still run (default: one at a time, a queue of 64).
 * @param samples Where the last JSON value of every JSON output port is kept between runs of the app (see [SampleStore]); `null` keeps them in memory only.
 * @param maxSampleBytes Samples bigger than this (as JSON text) are cut down to their shape, or dropped.
 * @param kindConcurrency How many runs of nodes of one kind may be active at the same time, by node kind (`"http.request" to 2`), on top of
 * [maxConcurrency] (which counts every kind together). Runs over the limit wait their turn. A node with [SignalMode.Each] inputs already
 * processes its values one after the other; this limit is for many nodes of one kind (a fan-out of requests) that must not all start at once.
 * @param isRelevantChange Asked when a node's `data` changed between two graphs handed to [update]; return `false` for a change that does
 * not affect the result (saved test inputs, a note, a UI setting) so the node keeps its results and is not marked stale. Default: every change counts.
 */
@OptIn(ExperimentalAtomicApi::class)
public class GraphEngine(
    private val scope: CoroutineScope,
    private val runners: Map<String, NodeRunner>,
    private val autoRun: Boolean = true,
    private val maxConcurrency: Int = 4,
    private val runDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val trace: TraceOptions = TraceOptions(),
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val beforeRun: ((node: GraphNode, trigger: TraceTrigger) -> Boolean)? = null,
    private val signalMode: (node: GraphNode, port: PortSpec) -> SignalMode = { _, port -> port.signal },
    private val isRelevantChange: (old: GraphNode, new: GraphNode) -> Boolean = { _, _ -> true },
    private val kindConcurrency: Map<String, Int> = emptyMap(),
    private val triggers: Map<String, TriggerRunner> = emptyMap(),
    private val eventPolicy: (GraphNode) -> EventPolicy = { EventPolicy.Default },
    private val mirror: (() -> Unit)? = null,
    private val samples: SampleStore? = null,
    private val maxSampleBytes: Int = 64 * 1024,
) {
    private val permits = Semaphore(maxConcurrency.coerceAtLeast(1))
    private val kindPermits: Map<String, Semaphore> = kindConcurrency.mapValues { Semaphore(it.value.coerceAtLeast(1)) }

    // The kind's turn comes first: waiting for it must not hold one of the engine-wide permits.
    private suspend fun <T> inTurn(node: GraphNode, block: suspend () -> T): T {
        val kind = kindPermits[node.kind] ?: return permits.withPermit { block() }
        return kind.withPermit { permits.withPermit { block() } }
    }
    private val lockWord = AtomicInt(0)

    /** What a trace stores for a value: the secrets are hidden, the rest goes through [TraceOptions.redact]. */
    private fun recorded(node: GraphNode, port: PortId, value: Any?): Any? =
        if (value is KSecret || node.port(port)?.secret == true) KSecret.Hidden else trace.redact(node, port, value)

    /** The last JSON value seen at the JSON output [port] (a [PortType.json] port), or `null` when none was seen yet. Observable; never counts as a change of the graph. */
    public fun sampleOf(port: PortRef): JsonValue? = sampleMap[port]

    /** All samples now (what a [SampleStore] gets, and what `GraphJson(samples = true)` writes). */
    public fun sampleSnapshot(): Map<PortRef, JsonValue> = locked { sampleMap.toMap() }

    /** Puts [samples] in (for example read from a graph file); existing samples of other ports stay. */
    public fun loadSamples(samples: Map<PortRef, JsonValue>): Unit = locked { sampleMap.putAll(samples) }

    /** Saves the samples to the [SampleStore] now instead of a moment after the last change. */
    public fun flushSamples() {
        val store = samples ?: return
        store.save(sampleSnapshot())
    }

    /**
     * The fields the input [port] of node [id] can see: where its wire comes from (through subgraphs and links), then the shape that port declares
     * ([PortType.json] with a schema) and failing that its last sample. An input in `Collect` mode receives a list, so its paths start with `[*]`.
     * Examples of a secret port are left out. Observable: it follows new samples and edits. Empty when nothing is known.
     */
    public fun fieldsOf(id: NodeId, port: String): List<FieldInfo> {
        val node = graph.node(id) ?: return emptyList()
        val spec = node.port(port)?.takeIf { it.direction == PortDirection.Input } ?: return emptyList()
        val source = Router(graph).source(PortRef(id, spec.id)) ?: return emptyList()
        val sourceSpec = graph.node(source.node)?.port(source.port)
        val secret = sourceSpec?.secret == true
        val sample = if (secret) null else sampleMap[source]
        val schema = (sourceSpec?.type as? JsonPortType)?.schema
        val collect = signalMode(node, spec) == SignalMode.Collect
        val fields = when {
            schema != null -> {
                val shaped = if (collect) jsonObjectOf("type" to JsonString("array"), "items" to schema) else schema
                JsonFields.ofSchema(shaped).map { f ->
                    // Real values beat the schema's placeholders where the sample has the path.
                    val seen = sample?.let { (if (collect) JsonArray(listOf(it)) else it).at(f.path.replace("[*]", "[0]")) }
                    if (seen != null && seen !is JsonObject) f.copy(example = seen) else f
                }
            }
            sample != null -> JsonFields.of(if (collect) JsonArray(listOf(sample)) else sample)
            else -> emptyList()
        }
        return if (secret) fields.map { it.copy(example = null) } else fields
    }

    private fun recordSample(node: NodeId, port: PortId, value: Any?) {
        val spec = graph.node(node)?.port(port) ?: return
        if (spec.direction != PortDirection.Output || spec.type !is JsonPortType || spec.secret) return
        val json = JsonSamples.from(value)?.let { JsonSamples.capped(it, maxSampleBytes) } ?: return
        val ref = PortRef(node, port)
        if (sampleMap[ref] == json) return
        sampleMap[ref] = json
        scheduleSampleSave()
    }

    private fun scheduleSampleSave() {
        if (samples == null) return
        sampleSaveJob?.cancel()
        sampleSaveJob = scope.launch {
            kotlinx.coroutines.delay(SampleSaveDelayMillis)
            flushSamples()
        }
    }

    /** Whether the values at [port] (see [PortSpec.secret]); the UI masks them. */
    public fun isSecret(id: NodeId, port: String): Boolean = graph.node(id)?.port(port)?.secret == true

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
    private val sampleMap = mutableStateMapOf<PortRef, JsonValue>().also { map -> samples?.load()?.let { map.putAll(it) } }
    private var sampleSaveJob: Job? = null
    private val listeners = HashMap<NodeId, Job>()
    private val eventStates = HashMap<NodeId, EventState>()
    private var activeEvents = 0

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
        if (own.any { it is NodeRun.Listening }) return NodeRun.Listening
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

    /** Whether at least one trigger is armed and waiting for events ([NodeRun.Listening]). Observable. `isBusy` is about runners working, not about listening. */
    public val isListening: Boolean get() = states.values.any { it is NodeRun.Listening }

    /** Whether any runner is still working. Observable. */
    public val isBusy: Boolean get() = busy

    /** Suspends until no runner is working (returns at once when none is). Nodes waiting for permission or input do not count as working. */
    public suspend fun awaitIdle() {
        val waiter = locked { if (jobs.isEmpty() && activeEvents == 0) null else kotlinx.coroutines.CompletableDeferred<Unit>().also { idleWaiters += it } } ?: return
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
            if (before == null || before.kind != node.kind || before.after != node.after || (before.data != node.data && isRelevantChange(before, node)) || before.ports != node.ports || before.pin != node.pin) { stale += id; continue }
            // Something about what feeds an input changed: another wire, or a subgraph routed differently.
            for (spec in node.ports) {
                if (spec.direction != PortDirection.Input) continue
                val ref = PortRef(id, spec.id)
                if (oldRouter.source(ref) != newRouter.source(ref)) { stale += id; break }
            }
        }
        // Samples of ports that no longer exist or are no longer JSON outputs are forgotten.
        for (ref in sampleMap.keys.toList()) {
            val sp = new.node(ref.node)?.port(ref.port)
            if (sp == null || sp.direction != PortDirection.Output || sp.type !is JsonPortType) sampleMap.remove(ref)
        }
        invalidate(stale, listOf(newRouter, oldRouter), new)
        if (triggers.isNotEmpty()) {
            // An edit inside what a running event is executing: that event's graph is out of date.
            val deps = newRouter.dependencies()
            for (n in new.nodes.values) {
                if (!triggers.containsKey(n.kind) || eventStates[n.id]?.running.isNullOrEmpty()) continue
                if (n.id in stale || downstreamOf(n.id, deps).any { it in stale }) cancelEvents(n.id)
            }
        }
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
        listeners.remove(id)?.cancel()
        cancelEvents(id)
        generation[id] = (generation[id] ?: 0) + 1
        liveAttempts.remove(id)?.let { attempt ->
            attempt.status = TraceStatus.Cancelled
            attempt.finishedAt = clock()
        }
    }

    private fun refresh() {
        schedule()
        finishIfIdle(cancelled = false)
        mirror?.invoke()
    }

    private fun finishIfIdle(cancelled: Boolean) {
        busy = jobs.isNotEmpty() || activeEvents > 0
        if (jobs.isNotEmpty() || activeEvents > 0) return
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
        val stored = if (captured) inputs.all.mapValues { (port, v) -> recorded(node, port, v) } else emptyMap()
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
            if (trace.captureValues) attempt.addEmission(Emission(clock(), id, recorded(node, id, value)))
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
                for (a in node.after) if (graph.node(a)?.let { isReal(it) } == true) sources += a
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
        recordSample(node, port, value)
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
        val eventManaged = if (triggers.isEmpty()) emptySet() else eventNodes()
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
                val trigger = triggers[node.kind]
                if (trigger != null) { if (armTrigger(node, trigger, current)) changed = true; continue }
                // What event runs produce is mirrored into these nodes; the sweep must not second-guess it.
                if (id in eventManaged) continue
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
                val upstream = wires.map { it.source.node } + node.after.filter { graph.node(it)?.let { n -> isReal(n) } == true }
                val blocker = upstream.firstNotNullOfOrNull { source ->
                    when (val up = states[source]) {
                        is NodeRun.Failed -> NodeRun.Blocked(source)
                        is NodeRun.Declined -> NodeRun.Blocked(source, declined = true)
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
                // Nodes this one runs after (no wire between them) must have finished first; one that was skipped wrote nothing, which is fine.
                if (node.after.any { a -> graph.node(a)?.let { isReal(it) } == true && states[a].let { it !is NodeRun.Done && it !is NodeRun.Skipped } }) {
                    if (jobs[id] != null) cancel(id)
                    if (current != NodeRun.Waiting) { states[id] = NodeRun.Waiting; changed = true }
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
        val stored = if (captured) values.mapValues { (port, v) -> recorded(node, port, v) } else emptyMap()
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
                attempt.outputs = if (captured) last.mapValues { (port, v) -> recorded(node, port, v) } else emptyMap()
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

    // --- triggers and event runs -----------------------------------------------------------------------------

    private class EventState {
        val running = ArrayList<EventRun>()
        val queue = ArrayDeque<Map<PortId, Any?>>()
    }

    /** One event of a trigger: a private engine that runs what is downstream of the trigger once, and what was recorded for it. */
    private class EventRun(
        val node: GraphNode,
        val execution: Execution,
        val scope: CoroutineScope,
        val downstream: Set<NodeId>,
    ) {
        lateinit var child: GraphEngine
        var copied = 0
        var finished = false
    }

    private fun downstreamOf(trigger: NodeId, deps: Map<NodeId, Set<NodeId>>): Set<NodeId> {
        val readers = HashMap<NodeId, MutableList<NodeId>>()
        for ((n, sources) in deps) for (src in sources) readers.getOrPut(src) { ArrayList() } += n
        val seen = LinkedHashSet<NodeId>()
        val queue = ArrayDeque(readers[trigger].orEmpty())
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            readers[id]?.let { queue.addAll(it) }
        }
        return seen
    }

    /** Every real node that reads, directly or not, from a trigger: its state comes from event runs, not from the sweep. */
    private fun eventNodes(): Set<NodeId> {
        val deps = Router(graph).dependencies()
        val result = HashSet<NodeId>()
        for (node in graph.nodes.values) if (isReal(node) && triggers.containsKey(node.kind)) result += downstreamOf(node.id, deps)
        return result
    }

    /** Arms the trigger [node] (starts its listener). Returns whether something changed. */
    private fun armTrigger(node: GraphNode, runner: TriggerRunner, current: NodeRun): Boolean {
        if (current is NodeRun.Listening || current is NodeRun.Done || current is NodeRun.Failed || current is NodeRun.Declined) return false
        val id = node.id
        val gate = beforeRun
        if (gate != null && !gate(node, currentExecution?.trigger ?: pendingTrigger)) {
            states[id] = NodeRun.Declined
            return true
        }
        val pin = node.pin
        if (pin != null) {
            // A pinned trigger does not listen: it fires its pinned values once, so what is downstream can be developed against them.
            states[id] = NodeRun.Done(pin)
            fireLocked(node, pin)
            return true
        }
        val gen = (generation[id] ?: 0) + 1
        generation[id] = gen
        states[id] = NodeRun.Listening
        val context = object : TriggerContext {
            override val node: GraphNode get() = graph.node(id) ?: node
            override fun fire(outputs: Map<String, Any?>) {
                locked { if (generation[id] == gen) fireLocked(this.node, outputs.mapKeys { PortId(it.key) }) }
            }
        }
        listeners[id] = scope.launch {
            try {
                withContext(runDispatcher) { runner.listen(context) }
                locked { if (generation[id] == gen) { listeners.remove(id); states[id] = NodeRun.Done(emptyMap()); refresh() } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                locked { if (generation[id] == gen) { listeners.remove(id); states[id] = NodeRun.Failed(e); refresh() } }
            }
        }
        return true
    }

    private fun fireLocked(node: GraphNode, outputs: Map<PortId, Any?>) {
        val state = eventStates.getOrPut(node.id) { EventState() }
        val policy = eventPolicy(node)
        if (state.running.size >= policy.maxConcurrent.coerceAtLeast(1)) {
            when (policy.mode) {
                EventPolicy.Mode.Drop -> return
                EventPolicy.Mode.Latest -> state.running.toList().forEach { stopEvent(it, state) }
                EventPolicy.Mode.Queue -> {
                    state.queue.addLast(outputs)
                    while (state.queue.size > policy.maxQueued.coerceAtLeast(1)) state.queue.removeFirst()
                    return
                }
            }
        }
        startEvent(node, outputs, state)
    }

    /** The graph one event runs: the trigger pinned to the event, what it needs from upstream pinned to its last result, everything else inert. */
    private fun eventGraph(trigger: GraphNode, outputs: Map<PortId, Any?>, downstream: Set<NodeId>): Graph {
        val deps = Router(graph).dependencies()
        val upstream = LinkedHashSet<NodeId>()
        val queue = ArrayDeque(downstream)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            for (source in deps[id].orEmpty()) {
                if (source == trigger.id || source in downstream || !upstream.add(source)) continue
                // A node that has a result is pinned to it (its own upstream is not needed); one that has not runs in the event.
                if (states[source] !is NodeRun.Done) queue += source
            }
        }
        val nodes = ArrayList<GraphNode>()
        for (n in graph.nodes.values) {
            nodes += when {
                !isReal(n) -> n
                n.id == trigger.id -> n.copy(pin = n.ports.filter { it.direction == PortDirection.Output }.associate { it.id to outputs[it.id] })
                n.id in downstream -> n
                n.id in upstream -> (states[n.id] as? NodeRun.Done)?.let { done -> n.copy(pin = n.pin ?: done.outputs) } ?: n
                else -> n.copy(pin = n.ports.filter { it.direction == PortDirection.Output }.associate { it.id to NoSignal })
            }
        }
        return Graph.of(nodes, graph.edges.values, graph.groups.values)
    }

    private fun startEvent(node: GraphNode, outputs: Map<PortId, Any?>, state: EventState) {
        val execution = Execution(++executionCounter, TraceTrigger.Event, clock())
        history += execution
        while (history.size > trace.maxExecutions.coerceAtLeast(1)) history.removeAt(0)
        val captured = trace.captureValues
        // The trigger's own attempt carries the event's data, so the inspector shows what arrived.
        val attempt = NodeAttempt(node.id, 1, 0, clock(), emptyMap(), captured)
        attempt.outputs = if (captured) outputs.mapValues { (port, v) -> recorded(node, port, v) } else emptyMap()
        attempt.status = TraceStatus.Succeeded
        attempt.finishedAt = clock()
        execution.add(attempt)
        for ((port, value) in outputs) {
            val ref = PortRef(node.id, port)
            latestValues[ref] = value
            signalCounts[ref] = (signalCounts[ref] ?: 0) + 1
            recordSample(node.id, port, value)
        }
        val downstream = downstreamOf(node.id, Router(graph).dependencies())
        val eventScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))   // cancelled with the engine's scope, and when the event ends
        val run = EventRun(node, execution, eventScope, downstream)
        run.child = GraphEngine(
            eventScope, runners, true, maxConcurrency, runDispatcher, trace, clock,
            beforeRun = beforeRun?.let { gate -> { n, _ -> gate(n, TraceTrigger.Event) } },
            signalMode = signalMode, isRelevantChange = isRelevantChange, kindConcurrency = kindConcurrency,
            mirror = { scope.launch { locked { mirrorEvent(run) } } },
        )
        state.running += run
        activeEvents++
        busy = true
        run.child.update(eventGraph(node, outputs, downstream))
        eventScope.launch {
            run.child.awaitIdle()
            locked { finishEvent(run, state) }
        }
    }

    /** Copies what the event's private engine knows into this one: states, newest values, attempts. */
    private fun mirrorEvent(run: EventRun) {
        if (run.finished) return
        val child = run.child
        for (id in run.downstream) {
            val st = child.runOf(id)
            if (states[id] != st) states[id] = st
            val node = graph.node(id) ?: continue
            for (spec in node.ports) if (spec.direction == PortDirection.Output) {
                val count = child.signalCount(id, spec.id.value)
                if (count > 0) {
                    val ref = PortRef(id, spec.id)
                    child.sampleOf(ref)?.let { if (sampleMap[ref] != it) { sampleMap[ref] = it; scheduleSampleSave() } }
                    latestValues[ref] = child.latest(id, spec.id.value)
                    signalCounts[ref] = count
                }
            }
        }
        val attempts = child.executions.lastOrNull()?.attempts ?: return
        while (run.copied < attempts.size) run.execution.add(attempts[run.copied++])
    }

    private fun finishEvent(run: EventRun, state: EventState) {
        if (run.finished) return
        mirrorEvent(run)
        run.finished = true
        state.running.remove(run)
        activeEvents--
        run.execution.status = if (run.execution.anyLatestFailed()) TraceStatus.Failed else TraceStatus.Succeeded
        run.execution.finishedAt = clock()
        run.scope.cancel()
        val next = state.queue.removeFirstOrNull()
        val trigger = graph.node(run.node.id)
        if (next != null && trigger != null && listeners.containsKey(trigger.id)) fireLocked(trigger, next)
        finishIfIdle(cancelled = false)
    }

    private fun stopEvent(run: EventRun, state: EventState) {
        if (run.finished) return
        run.finished = true
        state.running.remove(run)
        activeEvents--
        run.scope.cancel()
        for (a in run.execution.attempts) if (a.status == TraceStatus.Running) { a.status = TraceStatus.Cancelled; a.finishedAt = clock() }
        run.execution.status = TraceStatus.Cancelled
        run.execution.finishedAt = clock()
    }

    private fun cancelEvents(trigger: NodeId) {
        val state = eventStates.remove(trigger) ?: return
        state.queue.clear()
        state.running.toList().forEach { stopEvent(it, state) }
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
                val returned = inTurn(node) { withContext(runDispatcher) { runner.run(context) } }
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
    private fun finishRun(node: GraphNode, gen: Int, attempt: NodeAttempt, context: Context, runOutcome: Result<Map<PortId, Any?>>) {
        val id = node.id
        val outcome = caught(node, runOutcome)
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
            attempt.outputs = if (trace.captureValues) last.mapValues { (port, v) -> recorded(node, port, v) } else emptyMap()
            attempt.status = TraceStatus.Succeeded
            states[id] = NodeRun.Waiting
        }
        refresh()
    }

    /** A failure of a node that has a wired error port becomes a result: the error comes out there, everything else stays silent. */
    private fun caught(node: GraphNode, outcome: Result<Map<PortId, Any?>>): Result<Map<PortId, Any?>> {
        val failure = outcome.exceptionOrNull() ?: return outcome
        val errorPort = node.ports.firstOrNull { it.direction == PortDirection.Output && it.errorOutput && graph.edges.values.any { e -> e.from == PortRef(node.id, it.id) } }
            ?: return outcome
        return Result.success(node.ports.filter { it.direction == PortDirection.Output }.associate { it.id to (if (it.id == errorPort.id) GraphError.of(failure) else NoSignal) })
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
        const val SampleSaveDelayMillis = 500L
        const val COMMENT = "comment"

        /** Whether the node runs; subgraph, boundary and comment nodes only route or annotate. */
        fun isReal(node: GraphNode): Boolean =
            node.kind != Subgraphs.Kind && node.kind != Subgraphs.InputKind && node.kind != Subgraphs.OutputKind && node.kind != COMMENT
    }
}
