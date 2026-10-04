package tech.kloos.kompound.graph.runtime

import kotlinx.coroutines.Job
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId

/**
 * A single-node test run started with [GraphEngine.testNode]. [attempt] is observable while it runs (logs, progress, emissions); its
 * [execution] is in [GraphEngine.executions].
 */
public class NodeTestRun internal constructor(
    public val execution: Execution,
    public val attempt: NodeAttempt,
    private val job: Job,
    private val outputsOrNull: () -> Map<PortId, Any?>?,
) {
    /** Running, then how the test ended. */
    public val status: TraceStatus get() = attempt.status

    /** The real (not redacted) output values once the test succeeded: what [pin] stores. */
    public val outputs: Map<PortId, Any?>? get() = outputsOrNull()

    /** Cancels the run if it is still going. */
    public fun cancel() { job.cancel() }
}

/** Pins [outputs] on the node [id] as one undo step (see [tech.kloos.kompound.graph.model.GraphNode.pin]). */
public fun KGraphState.pin(id: NodeId, outputs: Map<PortId, Any?>) {
    execute(GraphCommand.SetPin(id, outputs))
}

/** Removes the pin of [id] as one undo step. */
public fun KGraphState.unpin(id: NodeId) {
    execute(GraphCommand.SetPin(id, null))
}

/** Pins what [id] produced in the engine's current results (its outputs, or the output of a finished test); returns whether there was anything to pin. */
public fun KGraphState.pinCurrentOutputs(engine: GraphEngine, id: NodeId): Boolean {
    val outputs = engine.outputsOf(id) ?: return false
    pin(id, outputs)
    return true
}
