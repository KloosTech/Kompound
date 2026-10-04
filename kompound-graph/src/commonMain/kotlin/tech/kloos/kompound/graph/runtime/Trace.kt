package tech.kloos.kompound.graph.runtime

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId

/** How a run or an attempt ended (or that it has not yet). */
public enum class TraceStatus { Running, Succeeded, Failed, Cancelled }

/** What started an [Execution]. */
public enum class TraceTrigger {
    /** An edit (or the first graph) with `autoRun` on. */
    Auto,

    /** [GraphEngine.start]. */
    Manual,

    /** [GraphEngine.rerun] or [GraphEngine.rerunAll]. */
    Rerun,

    /** A [NodeTestSession]. */
    Test,
}

/** Severity of a [LogLine]. */
public enum class LogLevel { Debug, Info, Warn, Error }

/** One line a runner logged with [NodeRunContext.log]. [at] is epoch milliseconds from the engine's clock. */
public class LogLine(public val at: Long, public val level: LogLevel, public val message: String)

/** One value a runner emitted at [port] with [NodeRunContext.emit], or its final output. [at] is epoch milliseconds. */
public class Emission(public val at: Long, public val port: PortId, public val value: Any?)

/**
 * What a runner is given besides the inputs: a place to log, report progress and emit values while it runs. Everything is recorded in the
 * node's [NodeAttempt], so it shows up in the inspector. Safe to call from the runner's own dispatcher.
 */
public interface NodeRunContext {
    /** The node being run. */
    public val node: GraphNode

    /** What arrived at its connected inputs. */
    public val inputs: NodeInputs

    /** Records a log line. */
    public fun log(message: String, level: LogLevel = LogLevel.Info)

    /** Reports how far along the node is (`0f..1f`, or `null` for "busy, unknown") with an optional [message]. */
    public fun progress(fraction: Float?, message: String? = null)

    /**
     * Emits [value] at the output [port]. Every emission is recorded in the trace. Until signal modes arrive, downstream nodes read the
     * last value emitted per port (overridden by anything the runner returns) when the runner finishes.
     */
    public fun emit(port: String, value: Any?)
}

/**
 * One run of one node inside an [Execution]: what it received, what it produced, how long it took, what it logged. A node that was
 * cancelled (an edit made it stale) and started again has several attempts; the last one is current.
 *
 * Values are what the runner saw, passed through [TraceOptions.redact]; with [TraceOptions.captureValues] off they are not kept
 * ([valuesCaptured] is `false` and the maps are empty).
 */
@Stable
public class NodeAttempt internal constructor(
    public val nodeId: NodeId,
    /** 1 for the first attempt of the node in its execution, 2 for the next, ... */
    public val number: Int,
    /** Position of this attempt in the order attempts started in the execution. */
    public val order: Int,
    public val startedAt: Long,
    public val inputs: Map<PortId, Any?>,
    public val valuesCaptured: Boolean,
) {
    /** Running, then how it ended. */
    public var status: TraceStatus by mutableStateOf(TraceStatus.Running)
        internal set

    /** When it ended (epoch milliseconds), or `null` while running. */
    public var finishedAt: Long? by mutableStateOf(null)
        internal set

    /** The final value per output port, or `null` until it succeeded. */
    public var outputs: Map<PortId, Any?>? by mutableStateOf(null)
        internal set

    /** Why it failed. */
    public var error: Throwable? by mutableStateOf(null)
        internal set

    /** Last progress reported (`null` when none or unknown). */
    public var progress: Float? by mutableStateOf(null)
        internal set

    /** Last progress message. */
    public var progressMessage: String? by mutableStateOf(null)
        internal set

    private val logLines = mutableStateListOf<LogLine>()
    private val emitted = mutableStateListOf<Emission>()

    /** Log lines in order. */
    public val logs: List<LogLine> get() = logLines

    /** Emitted values in order. */
    public val emissions: List<Emission> get() = emitted

    /** How long it ran, or `null` while running. */
    public val durationMillis: Long? get() = finishedAt?.let { it - startedAt }

    internal fun addLog(line: LogLine) { logLines += line }

    internal fun addEmission(emission: Emission) { emitted += emission }
}

/**
 * A run of the graph: from the moment the engine starts working until nothing is running any more. Observable, so a list or an inspector
 * stays current while it runs.
 */
@Stable
public class Execution internal constructor(
    public val id: Int,
    public val trigger: TraceTrigger,
    public val startedAt: Long,
) {
    /** Running, then how it ended: [TraceStatus.Failed] if any node's last attempt failed, else [TraceStatus.Succeeded]; [TraceStatus.Cancelled] after [GraphEngine.stop]. */
    public var status: TraceStatus by mutableStateOf(TraceStatus.Running)
        internal set

    /** When it ended (epoch milliseconds), or `null` while running. */
    public var finishedAt: Long? by mutableStateOf(null)
        internal set

    private val all = mutableStateListOf<NodeAttempt>()

    /** Every attempt of every node, in the order they started. */
    public val attempts: List<NodeAttempt> get() = all

    /** Attempts of [node], oldest first. */
    public fun attemptsOf(node: NodeId): List<NodeAttempt> = all.filter { it.nodeId == node }

    /** The current (last) attempt of [node], or `null` when it did not run in this execution. */
    public fun latest(node: NodeId): NodeAttempt? = all.lastOrNull { it.nodeId == node }

    // The engine reads these on its own thread; iterating the snapshot list there could collide with a reader on another thread.
    private val plain = ArrayList<NodeAttempt>()
    private val counts = HashMap<NodeId, Int>()

    internal val attemptCount: Int get() = plain.size

    internal fun nextNumber(node: NodeId): Int = (counts[node] ?: 0) + 1

    internal fun latestPlain(node: NodeId): NodeAttempt? = plain.lastOrNull { it.nodeId == node }

    internal fun anyLatestFailed(): Boolean = plain.any { a -> a.status == TraceStatus.Failed && latestPlain(a.nodeId) === a }

    internal fun add(attempt: NodeAttempt) {
        plain += attempt
        counts[attempt.nodeId] = attempt.number
        all += attempt
    }
}

/**
 * What the engine records.
 *
 * @property maxExecutions How many executions to keep (oldest dropped first).
 * @property captureValues Keep the inputs, outputs and emissions of every attempt. Traces then hold real data (secrets included):
 * turn this off, or use [redact], for graphs that handle sensitive values.
 * @property redact Replaces a value before it is stored in a trace; the runner and the graph always see the original.
 */
public class TraceOptions(
    public val maxExecutions: Int = 20,
    public val captureValues: Boolean = true,
    public val redact: (node: GraphNode, port: PortId, value: Any?) -> Any? = { _, _, value -> value },
)
