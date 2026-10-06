package tech.kloos.kompound.graph.runtime

import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.json.JsonArray
import tech.kloos.kompound.json.JsonNull
import tech.kloos.kompound.json.JsonObject
import tech.kloos.kompound.json.JsonString
import tech.kloos.kompound.json.JsonValue
import tech.kloos.kompound.json.of
import tech.kloos.kompound.json.parseOrNull

/**
 * Where the engine keeps the **samples**: the last JSON value of every JSON output port (see `PortType.json`), which is what tells the editor
 * which fields a port offers before an expensive node runs again. A sample is not graph data: it never makes a node stale and is not part of
 * undo. Samples of secret ports are never stored.
 *
 * The app owns the storage (a file, a database, nothing): [load] is called once when the engine is created, [save] after samples changed
 * (a moment after the last change, so a burst of values is one save). Samples can hold personal data, which is why they are not saved into the
 * graph file unless you ask for it (`GraphJson(samples = true)`).
 */
public interface SampleStore {
    /** The samples saved earlier (empty at the first start). */
    public fun load(): Map<PortRef, JsonValue>

    /** Saves all current samples. */
    public fun save(samples: Map<PortRef, JsonValue>)
}

/** A [SampleStore] that keeps the samples in memory (they survive a restart of the engine, not of the app). */
public class InMemorySampleStore(initial: Map<PortRef, JsonValue> = emptyMap()) : SampleStore {
    private var stored: Map<PortRef, JsonValue> = initial
    override fun load(): Map<PortRef, JsonValue> = stored
    override fun save(samples: Map<PortRef, JsonValue>) { stored = samples }
}

internal object JsonSamples {
    /** The JSON for a value a runner produced, or `null` when it has none (null, functions, unknown types). Text that parses as JSON is parsed. */
    fun from(value: Any?): JsonValue? = when (value) {
        null -> null
        is JsonValue -> value
        is String -> JsonValue.parseOrNull(value)?.takeIf { it is JsonObject || it is JsonArray } ?: JsonString(value)
        is Number, is Boolean, is Map<*, *>, is Iterable<*> -> JsonValue.of(value)
        else -> null
    }.takeUnless { it == JsonNull }

    /** [value] if its JSON text fits in [maxBytes]; otherwise a smaller value of the same shape (first array items, cut strings), or `null` when even that is too big. */
    fun capped(value: JsonValue, maxBytes: Int): JsonValue? {
        if (value.toJson().length <= maxBytes) return value
        var shrunk = shrink(value, 0, 3, 120)
        if (shrunk.toJson().length <= maxBytes) return shrunk
        shrunk = shrink(value, 0, 1, 40)
        return shrunk.takeIf { it.toJson().length <= maxBytes }
    }

    private fun shrink(v: JsonValue, depth: Int, items: Int, text: Int): JsonValue = when (v) {
        is JsonString -> if (v.value.length > text) JsonString(v.value.take(text) + "…") else v
        is JsonArray -> JsonArray(v.items.take(items).map { shrink(it, depth + 1, items, text) })
        is JsonObject -> if (depth > 8) JsonNull else JsonObject(v.fields.mapValues { shrink(it.value, depth + 1, items, text) })
        else -> v
    }
}
