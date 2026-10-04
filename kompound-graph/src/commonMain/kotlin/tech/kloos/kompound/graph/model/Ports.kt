package tech.kloos.kompound.graph.model

/** Which way data flows through a port. Edges always run from an [Output] to an [Input]. */
public enum class PortDirection { Input, Output }

/** How many edges a port accepts. */
public enum class PortCapacity { One, Many }

/**
 * Kind of value a port carries. Two ports connect when [PortType.accepts] says so; the default rule is "same id, or
 * either side is [Any]". Make your own: `object Number : PortType { override val id = "number" }`, or use [PortType.of].
 */
public interface PortType {
    /** Stable key; also selects the wire colour. */
    public val id: String

    /** Whether a wire from an output of type [source] may end at an input of this type. */
    public fun accepts(source: PortType): Boolean = id == source.id || id == Any.id || source.id == Any.id

    public companion object {
        /** Matches every other type. */
        public val Any: PortType = of("any")

        /** A type with the given [id]. */
        public fun of(id: String): PortType = SimpleType(id)
    }
}

private data class SimpleType(override val id: String) : PortType {
    override fun toString(): String = id
}

/**
 * What an input port does with the values its upstream node produces over time (a node may emit several, see the runtime's
 * `NodeRunContext.emit`; a node that emits nothing produces one value when it finishes). Only the execution engine looks at it.
 */
public enum class SignalMode {
    /** Run again on every new value (a run that is still going is cancelled); uses the newest value. The default. */
    Latest,

    /** Run once per value, in order, one after the other; none is dropped. */
    Each,

    /** Wait until the upstream node has finished, then run once with the list of all its values. */
    Collect,

    /** Wait until the upstream node has finished, then run once with its last value. */
    Final,

    /**
     * An optional input: the node does not wait for a value here. It runs once every `Any` input has a value or its upstream node has
     * finished, and gets `null` for an input that never got one (a branch not taken). Like [Latest] it runs again on a newer value. A node
     * whose inputs are all `Any` runs only when at least one has a value, so `Merge` (take whichever branch has a value) is two `Any` inputs.
     */
    Any,
}

/**
 * Declaration of one port of a node.
 *
 * @property id Identity inside the node.
 * @property label Text shown next to the handle; defaults to the id.
 * @property direction [PortDirection.Input] or [PortDirection.Output].
 * @property type Value type, used for compatibility and colour.
 * @property capacity How many edges it takes: inputs default to [PortCapacity.One], outputs to [PortCapacity.Many].
 * @property signal For inputs: how the engine treats a stream of values arriving here.
 */
public data class PortSpec(
    public val id: PortId,
    public val direction: PortDirection,
    public val label: String = id.value,
    public val type: PortType = PortType.Any,
    public val capacity: PortCapacity = if (direction == PortDirection.Input) PortCapacity.One else PortCapacity.Many,
    public val signal: SignalMode = SignalMode.Latest,
) {
    public companion object {
        /** An input port. */
        public fun input(id: String, label: String = id, type: PortType = PortType.Any, capacity: PortCapacity = PortCapacity.One, signal: SignalMode = SignalMode.Latest): PortSpec =
            PortSpec(PortId(id), PortDirection.Input, label, type, capacity, signal)

        /** An output port. */
        public fun output(id: String, label: String = id, type: PortType = PortType.Any, capacity: PortCapacity = PortCapacity.Many): PortSpec =
            PortSpec(PortId(id), PortDirection.Output, label, type, capacity)
    }
}
