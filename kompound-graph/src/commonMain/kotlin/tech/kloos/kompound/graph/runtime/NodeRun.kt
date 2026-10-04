package tech.kloos.kompound.graph.runtime

import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId

/** Where a node is in a run of a [GraphEngine]. */
public sealed interface NodeRun {
    /** Nothing was started for this node (the engine is not running, or the node was just changed). */
    public data object Idle : NodeRun

    /** Waiting for the nodes that feed it to finish. */
    public data object Waiting : NodeRun

    /** One of the nodes upstream [by] failed, so this one cannot run. */
    public data class Blocked(public val by: NodeId) : NodeRun

    /** Its runner is suspended or computing right now. */
    public data object Running : NodeRun

    /** Finished; [outputs] holds a value per output port the runner produced. */
    public data class Done(public val outputs: Map<PortId, Any?>) : NodeRun

    /** [GraphEngine]'s `beforeRun` refused to start this node. It is asked again on the next start or re-run. */
    public data object Declined : NodeRun

    /** The runner threw [error]. */
    public data class Failed(public val error: Throwable) : NodeRun
}

/** The values arriving at a node's connected input ports. Unconnected ports are missing. */
public class NodeInputs(private val values: Map<PortId, Any?>) {
    /** Whether the input [port] is connected (and so has a value, which may be `null`). */
    public fun has(port: String): Boolean = PortId(port) in values

    /** The value at [port], or `null` when it is not connected or the upstream value is `null`. */
    public operator fun get(port: String): Any? = values[PortId(port)]

    /** The value at [port] as [T], or `null` when missing or of another type. */
    public inline fun <reified T> getOrNull(port: String): T? = get(port) as? T

    /** The value at [port] as [T]; throws [IllegalStateException] when missing or of another type. */
    public inline fun <reified T> require(port: String): T =
        get(port) as? T ?: error("Input \"$port\" is ${if (has(port)) "not a ${T::class.simpleName}" else "not connected"}")

    /** All values by port. */
    public val all: Map<PortId, Any?> get() = values
}

/**
 * What a node kind does when it runs. It may suspend for as long as it likes (a process, a network call, a timer): the engine keeps
 * running the rest of the graph and passes the result on when it arrives. Throw to fail the node; cancellation (the node or something
 * upstream changed, or the engine stopped) arrives as a normal `CancellationException`, so use `use`/`finally` for clean-up.
 *
 * Read what you need from [NodeRunContext] (`node`, `inputs`) and log, report progress or emit through it. The result maps output port
 * ids to values; ports left out read as `null` downstream.
 */
public fun interface NodeRunner {
    /** Runs the node described by [ctx]. */
    public suspend fun run(ctx: NodeRunContext): Map<String, Any?>
}

/** A runner from a block that takes the node and its inputs and returns the output values by port. */
public fun nodeRunner(block: suspend (node: GraphNode, inputs: NodeInputs) -> Map<String, Any?>): NodeRunner =
    NodeRunner { ctx -> block(ctx.node, ctx.inputs) }

/** Convenience for a runner with a single output named `out`. */
public fun singleOutputRunner(block: suspend (node: GraphNode, inputs: NodeInputs) -> Any?): NodeRunner =
    NodeRunner { ctx -> mapOf("out" to block(ctx.node, ctx.inputs)) }

/** A node cannot run because it is part of a cycle: the engine runs acyclic graphs only. */
public class CycleException(public val nodes: Set<NodeId>) : IllegalStateException("Node is part of a cycle: ${nodes.joinToString()}")

/** No runner is registered for the kind of the node. */
public class MissingRunnerException(public val kind: String) : IllegalStateException("No NodeRunner registered for kind \"$kind\"")
