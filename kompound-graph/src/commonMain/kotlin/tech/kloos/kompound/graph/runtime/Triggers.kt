package tech.kloos.kompound.graph.runtime

import tech.kloos.kompound.graph.model.GraphNode

/**
 * What a trigger node does: it waits for something outside the graph (a timer, a webhook, a file change, a message) and calls
 * [TriggerContext.fire] each time it happens. `listen` suspends for as long as the trigger is armed; the node is [NodeRun.Listening] meanwhile.
 * Cancellation (the node was edited or removed, the engine stopped) arrives as a normal `CancellationException`: clean up in `finally`.
 *
 * Register one per node kind in `GraphEngine(triggers = mapOf("timer" to TriggerRunner { ctx -> ... }))`. A trigger has no inputs; its
 * settings are its [GraphNode.data]. Every [TriggerContext.fire] starts one **event run**: the nodes downstream of the trigger run once with
 * those values, isolated from every other event (see `GraphEngine`).
 */
public fun interface TriggerRunner {
    /** Waits for events and reports them; returns when the trigger is finished for good (a one-shot schedule). */
    public suspend fun listen(ctx: TriggerContext)
}

/** What a [TriggerRunner] can do. */
public interface TriggerContext {
    /** The trigger node, as it is now. */
    public val node: GraphNode

    /**
     * Starts an event run with [outputs] (by output port name) as the values of the trigger's ports. What happens when events arrive faster than
     * they finish is the node's [EventPolicy]. Safe to call from any thread.
     */
    public fun fire(outputs: Map<String, Any?> = emptyMap())
}

/**
 * What the engine does with a new event while earlier events of the same trigger are still running.
 *
 * @property mode [Mode.Queue] keeps up to [maxQueued] events and runs them in order (the oldest is dropped when the queue is full),
 * [Mode.Drop] ignores the new event, [Mode.Latest] cancels the running events for the new one.
 * @property maxConcurrent How many events of this trigger may run at once.
 * @property maxQueued Size of the queue for [Mode.Queue].
 */
public class EventPolicy(
    public val mode: Mode = Mode.Queue,
    public val maxConcurrent: Int = 1,
    public val maxQueued: Int = 64,
) {
    /** How an event that cannot start right away is treated. */
    public enum class Mode { Queue, Drop, Latest }

    public companion object {
        /** One event at a time, up to 64 waiting. */
        public val Default: EventPolicy = EventPolicy()
    }
}
