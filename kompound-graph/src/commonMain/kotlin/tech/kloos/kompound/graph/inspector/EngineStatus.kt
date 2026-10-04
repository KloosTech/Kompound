package tech.kloos.kompound.graph.inspector

import tech.kloos.kompound.graph.KNodeStatus
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.runtime.Execution
import tech.kloos.kompound.graph.runtime.GraphEngine
import tech.kloos.kompound.graph.runtime.NodeRun
import tech.kloos.kompound.graph.runtime.TraceStatus

/**
 * The status mark for [node] to pass to `KNodeGraph(nodeStatus = ...)`. With [execution] `null` it follows the live run; with a recorded
 * [Execution] it shows how that run went (nodes that did not run in it get no mark). Nodes with a pin show [KNodeStatus.Pinned] unless
 * they ran. Reads observable state, so a graph using it updates as the engine progresses.
 */
public fun GraphEngine.nodeStatus(node: GraphNode, execution: Execution? = null): KNodeStatus? {
    if (execution == null) {
        return when (val run = runOf(node.id)) {
            NodeRun.Idle -> null
            NodeRun.Waiting -> KNodeStatus.Waiting
            NodeRun.Running -> KNodeStatus.Running
            is NodeRun.Done -> if (node.pin != null) KNodeStatus.Pinned else KNodeStatus.Done
            is NodeRun.Failed -> KNodeStatus.Failed
            NodeRun.Declined -> KNodeStatus.Declined
            is NodeRun.Blocked -> KNodeStatus.Blocked.also { run.by }
        }
    }
    val attempt = execution.latest(node.id) ?: return if (node.pin != null) KNodeStatus.Pinned else null
    return when (attempt.status) {
        TraceStatus.Running -> KNodeStatus.Running
        TraceStatus.Succeeded -> KNodeStatus.Done
        TraceStatus.Failed -> KNodeStatus.Failed
        TraceStatus.Cancelled -> KNodeStatus.Cancelled
    }
}

/**
 * A short label for the wire [edge] to pass to `KNodeGraph(edgeLabel = ...)`: a preview of the value that went across it ("1 item",
 * "3 items", a short string or number), or how many values a streaming node sent. In a recorded [execution] it uses what that run
 * captured; live it uses the newest value. `null` when nothing passed yet.
 */
public fun GraphEngine.edgeLabel(edge: Edge, execution: Execution? = null): String? {
    if (execution != null) {
        val attempt = execution.latest(edge.from.node) ?: return null
        if (!attempt.valuesCaptured) return null
        val outputs = attempt.outputs ?: return null
        if (edge.from.port !in outputs) return null
        val emitted = attempt.emissions.count { it.port == edge.from.port }
        return if (emitted > 1) "$emitted values" else previewValue(outputs[edge.from.port])
    }
    val count = signalCount(edge.from.node, edge.from.port.value)
    if (count == 0) return null
    return if (count > 1) "$count values" else previewValue(latest(edge.from.node, edge.from.port.value))
}

/** A few characters describing [value] for a label: strings quoted and cut, collections as a count. */
public fun previewValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> if (value.length > 14) "\"${value.take(13)}…\"" else "\"$value\""
    is Boolean -> value.toString()
    is Number -> numberText(value.toDouble())
    is Collection<*> -> if (value.size == 1) "1 item" else "${value.size} items"
    is Map<*, *> -> if (value.size == 1) "1 field" else "${value.size} fields"
    else -> value::class.simpleName ?: "value"
}
