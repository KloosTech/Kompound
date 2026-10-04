package tech.kloos.kompound.graph.serialization

/**
 * Turns a value of one of your own types into JSON and back, for [ValueJson]. The value is stored as `{"$type": name, "value": ...}`, so
 * [name] must be stable and unique.
 */
public interface ValueCodec {
    /** Stable key written to the file. */
    public val name: String

    /** Whether this codec handles [value]. */
    public fun accepts(value: Any): Boolean

    /** The JSON for [value]. */
    public fun encode(value: Any): JsonValue

    /** The value for [json]. */
    public fun decode(json: JsonValue): Any
}

/** A [ValueCodec] for the class [T] from two lambdas. */
public inline fun <reified T : Any> valueCodec(
    name: String,
    noinline encode: (T) -> JsonValue,
    noinline decode: (JsonValue) -> T,
): ValueCodec = object : ValueCodec {
    override val name: String = name
    override fun accepts(value: Any): Boolean = value is T
    @Suppress("UNCHECKED_CAST")
    override fun encode(value: Any): JsonValue = encode(value as T)
    override fun decode(json: JsonValue): Any = decode(json)
}

/**
 * Saves any value the engine passes around (pinned outputs, test inputs, trace values) as JSON and reads it back with its type intact:
 * `null`, booleans, strings, `Double`, `Float`, `Int`, `Long`, [JsonValue], lists, maps with string keys (nested to any depth), and
 * your own types through [codecs]. Anything else fails with a [GraphJsonException] that names the type.
 *
 * Plain JSON values are written as plain JSON; the types JSON cannot tell apart (`Int`, `Long`, `Float`, [JsonValue], custom) are
 * written as a one-key object such as `{"$int": 5}`, so a file reads back exactly what was saved.
 */
public class ValueJson(private val codecs: List<ValueCodec> = emptyList()) {
    /** The JSON for [value]. */
    public fun encode(value: Any?): JsonValue = when (value) {
        null -> JsonNull
        is Boolean -> JsonBool(value)
        is String -> JsonString(value)
        is Double -> JsonNumber(value)
        is Int -> tag("\$int", JsonNumber(value.toDouble()))
        is Long -> tag("\$long", JsonString(value.toString()))
        is Float -> tag("\$float", JsonNumber(value.toString().toDouble()))
        is JsonValue -> tag("\$json", value)
        is List<*> -> JsonArray(value.map(::encode))
        is Map<*, *> -> {
            val fields = LinkedHashMap<String, JsonValue>()
            for ((k, v) in value) fields[k as? String ?: throw GraphJsonException("Map keys must be strings, found ${k?.let { it::class.simpleName }}")] = encode(v)
            if (fields.keys.any { it.startsWith("$") }) tag("\$map", JsonObject(fields)) else JsonObject(fields)
        }
        else -> {
            val codec = codecs.firstOrNull { it.accepts(value) } ?: throw GraphJsonException("No ValueCodec for ${value::class.simpleName}")
            jsonObjectOf("\$type" to JsonString(codec.name), "value" to codec.encode(value))
        }
    }

    /** The value for [json]. */
    public fun decode(json: JsonValue): Any? = when (json) {
        JsonNull -> null
        is JsonBool -> json.value
        is JsonString -> json.value
        is JsonNumber -> json.value
        is JsonArray -> json.items.map(::decode)
        is JsonObject -> decodeObject(json)
    }

    private fun decodeObject(o: JsonObject): Any? {
        val only = o.fields.entries.singleOrNull()
        if (only != null) {
            val v = only.value
            when (only.key) {
                "\$int" -> return (v as? JsonNumber)?.value?.toInt() ?: throw GraphJsonException("\$int must hold a number")
                "\$long" -> return (v as? JsonString)?.value?.toLongOrNull() ?: throw GraphJsonException("\$long must hold a string of digits")
                "\$float" -> return (v as? JsonNumber)?.value?.toFloat() ?: throw GraphJsonException("\$float must hold a number")
                "\$json" -> return v
                "\$map" -> return (v as? JsonObject)?.fields?.mapValues { decode(it.value) } ?: throw GraphJsonException("\$map must hold an object")
            }
        }
        val type = (o["\$type"] as? JsonString)?.value
        if (type != null && o.fields.size == 2 && o["value"] != null) {
            val codec = codecs.firstOrNull { it.name == type } ?: throw GraphJsonException("No ValueCodec named \"$type\"")
            return codec.decode(o.fields.getValue("value"))
        }
        return o.fields.mapValues { decode(it.value) }
    }

    private fun tag(key: String, value: JsonValue): JsonObject = JsonObject(mapOf(key to value))
}
