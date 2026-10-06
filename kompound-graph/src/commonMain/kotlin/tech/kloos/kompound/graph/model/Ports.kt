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

        /**
         * A port that carries JSON (a [tech.kloos.kompound.json.JsonValue], or text that parses as JSON), optionally of the shape [schema] (the subset
         * [tech.kloos.kompound.json.JsonSchema.validate] understands; unknown keywords are kept, not enforced). Connects to `any` and to other JSON ports.
         * The engine remembers the last value of every JSON output as its sample, which is where the fields of a port without a schema come from.
         */
        public fun json(schema: tech.kloos.kompound.json.JsonValue? = null): PortType = JsonPortType(schema)

        /**
         * A JSON port whose values have the shape of the **items** of the array that arrives at this node's input [input]: the output of a node that
         * emits one signal per item (a `JSON each`). The engine follows the wire at that input, so the item's fields keep flowing after a loop and a changed
         * upstream shape flows through. Connects like [json].
         */
        public fun itemOf(input: String): PortType = ItemOfPortType(input)

        /** A JSON port that carries an array of items of the shape [item] (the collecting side of a loop); [item] `null` for any items. */
        public fun arrayOf(item: tech.kloos.kompound.json.JsonValue? = null): PortType = JsonPortType(
            tech.kloos.kompound.json.jsonObjectOf("type" to tech.kloos.kompound.json.JsonString("array"), "items" to item),
        )
    }
}

/** The type behind [PortType.itemOf]: id `json`, the shape is read from the array at the node's input [input]. */
public data class ItemOfPortType(public val input: String) : PortType {
    override val id: String get() = "json"
    override fun toString(): String = "json item of $input"
}

/** The type behind [PortType.json]: id `json`, with an optional schema that is saved with the port. */
public data class JsonPortType(public val schema: tech.kloos.kompound.json.JsonValue? = null) : PortType {
    override val id: String get() = "json"
    override fun toString(): String = "json"
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
 * @property errorOutput An output that receives the node's failure: when the node's runner throws and this port has a wire, the run is not a
 * failure; a [tech.kloos.kompound.graph.runtime.GraphError] comes out here and the node's other outputs stay silent ([NoSignal] semantics),
 * so the nodes wired to this port can handle the problem (log it, retry, use a default). Without a wire the failure behaves as usual. Declare one with [PortSpec.error].
 * @property secret The values at this port are secrets (an API key, a token): wire labels, the inspector and recorded traces show
 * [KSecret.Mask] instead. The runners and the graph still see the real values. Wrap a single value in [KSecret] for the same effect without marking the port.
 */
public data class PortSpec(
    public val id: PortId,
    public val direction: PortDirection,
    public val label: String = id.value,
    public val type: PortType = PortType.Any,
    public val capacity: PortCapacity = if (direction == PortDirection.Input) PortCapacity.One else PortCapacity.Many,
    public val signal: SignalMode = SignalMode.Latest,
    public val secret: Boolean = false,
    public val errorOutput: Boolean = false,
) {
    public companion object {
        /** An input port. */
        public fun input(id: String, label: String = id, type: PortType = PortType.Any, capacity: PortCapacity = PortCapacity.One, signal: SignalMode = SignalMode.Latest, secret: Boolean = false): PortSpec =
            PortSpec(PortId(id), PortDirection.Input, label, type, capacity, signal, secret)

        /** An output that catches the node's failure (see [errorOutput]); its wire is drawn in the colour of the `error` port type. */
        public fun error(id: String = "error", label: String = "Error"): PortSpec =
            PortSpec(PortId(id), PortDirection.Output, label, PortType.of("error"), PortCapacity.Many, errorOutput = true)

        /** An output port. */
        public fun output(id: String, label: String = id, type: PortType = PortType.Any, capacity: PortCapacity = PortCapacity.Many, secret: Boolean = false): PortSpec =
            PortSpec(PortId(id), PortDirection.Output, label, type, capacity, secret = secret)
    }
}

/**
 * Wraps a value that must not show up in the UI: [toString] is [Mask], and wire labels, the inspector and recorded traces show the mask (the
 * trace stores [Hidden], never the value). A node that outputs `KSecret(apiKey)` hands the next node the wrapper; read it with [value].
 * To hide everything at a port without wrapping, set [PortSpec.secret].
 */
public class KSecret(public val value: Any?) {
    override fun toString(): String = Mask

    public companion object {
        /** What is shown in place of a secret. */
        public const val Mask: String = "••••••"

        /** The stand-in stored in traces. */
        public val Hidden: KSecret = KSecret(null)
    }
}
