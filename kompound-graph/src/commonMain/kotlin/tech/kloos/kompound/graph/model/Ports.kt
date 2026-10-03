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
 * Declaration of one port of a node.
 *
 * @property id Identity inside the node.
 * @property label Text shown next to the handle; defaults to the id.
 * @property direction [PortDirection.Input] or [PortDirection.Output].
 * @property type Value type, used for compatibility and colour.
 * @property capacity How many edges it takes: inputs default to [PortCapacity.One], outputs to [PortCapacity.Many].
 */
public data class PortSpec(
    public val id: PortId,
    public val direction: PortDirection,
    public val label: String = id.value,
    public val type: PortType = PortType.Any,
    public val capacity: PortCapacity = if (direction == PortDirection.Input) PortCapacity.One else PortCapacity.Many,
) {
    public companion object {
        /** An input port. */
        public fun input(id: String, label: String = id, type: PortType = PortType.Any, capacity: PortCapacity = PortCapacity.One): PortSpec =
            PortSpec(PortId(id), PortDirection.Input, label, type, capacity)

        /** An output port. */
        public fun output(id: String, label: String = id, type: PortType = PortType.Any, capacity: PortCapacity = PortCapacity.Many): PortSpec =
            PortSpec(PortId(id), PortDirection.Output, label, type, capacity)
    }
}
