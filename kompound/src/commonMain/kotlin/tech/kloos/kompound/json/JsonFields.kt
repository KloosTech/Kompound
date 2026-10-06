package tech.kloos.kompound.json

/**
 * One field a JSON value offers: where it is ([path], in the syntax of [at]: `title`, `owner.name`, `tasks[*].title`), what it holds ([type]: `string`,
 * `number`, `integer`, `boolean`, `object`, `array`, `null`, or `any` when unknown) and an [example] value when one is known.
 */
public data class FieldInfo(public val path: String, public val type: String, public val example: JsonValue? = null)

/** Lists the paths of a JSON value or of a JSON Schema, for pickers and autocomplete. */
public object JsonFields {
    /** Deepest nesting listed. */
    public const val MaxDepth: Int = 6

    /** Most fields listed. */
    public const val MaxFields: Int = 200

    /**
     * The fields of [value]. Objects list each key (containers too, then their children); an array contributes the fields of its first item
     * behind `[*]` (`tasks[*].title`) and is itself listed as `array` with the first items as the example. Examples are the real values, strings cut at [exampleLength].
     */
    public fun of(value: JsonValue, maxDepth: Int = MaxDepth, maxFields: Int = MaxFields, exampleLength: Int = 60): List<FieldInfo> {
        val out = ArrayList<FieldInfo>()
        walk(value, "", 0, maxDepth, maxFields, exampleLength, out)
        return out
    }

    /**
     * The fields a JSON Schema (the subset [JsonSchema.validate] understands) describes: `properties` of objects, `items` of arrays. Types come from
     * `type`; an example comes from `example`, `default`, the first of `examples` or `enum`.
     */
    public fun ofSchema(schema: JsonValue, maxDepth: Int = MaxDepth, maxFields: Int = MaxFields): List<FieldInfo> {
        val out = ArrayList<FieldInfo>()
        walkSchema(schema, "", 0, maxDepth, maxFields, out)
        return out
    }

    private fun join(path: String, key: String) = if (path.isEmpty()) safeKey(key) else path + "." + safeKey(key)

    // Keys with dots, brackets or spaces are written as ["key"], which `at` reads.
    private fun safeKey(key: String): String =
        if (key.isNotEmpty() && key.all { it.isLetterOrDigit() || it == '_' || it == '-' }) key else "[\"" + key.replace("\"", "\\\"") + "\"]"

    private fun typeOf(v: JsonValue): String = when (v) {
        JsonNull -> "null"
        is JsonBool -> "boolean"
        is JsonNumber -> if (v.value == kotlin.math.floor(v.value) && !v.value.isInfinite()) "integer" else "number"
        is JsonString -> "string"
        is JsonArray -> "array"
        is JsonObject -> "object"
    }

    private fun short(v: JsonValue, length: Int): JsonValue = when (v) {
        is JsonString -> if (v.value.length > length) JsonString(v.value.take(length) + "…") else v
        is JsonArray -> if (v.items.size > 3) JsonArray(v.items.take(3).map { short(it, length) }) else JsonArray(v.items.map { short(it, length) })
        is JsonObject -> if (v.fields.size > 6) JsonObject(v.fields.entries.take(6).associate { it.key to short(it.value, length) }) else JsonObject(v.fields.mapValues { short(it.value, length) })
        else -> v
    }

    private fun walk(v: JsonValue, path: String, depth: Int, maxDepth: Int, maxFields: Int, exampleLength: Int, out: MutableList<FieldInfo>) {
        when (v) {
            is JsonObject -> for ((key, child) in v.fields) {
                if (out.size >= maxFields) return
                val p = join(path, key)
                out += FieldInfo(p, typeOf(child), short(child, exampleLength).takeIf { child !is JsonObject })
                if (depth + 1 < maxDepth) walk(child, p, depth + 1, maxDepth, maxFields, exampleLength, out)
            }
            is JsonArray -> {
                val first = v.items.firstOrNull { it != JsonNull } ?: return
                val p = path + "[*]"
                if (first is JsonObject || first is JsonArray) { if (depth + 1 < maxDepth) walk(first, p, depth + 1, maxDepth, maxFields, exampleLength, out) }
                else if (out.size < maxFields) out += FieldInfo(p, typeOf(first), short(first, exampleLength))
            }
            else -> {}
        }
    }

    private fun exampleOf(schema: JsonObject): JsonValue? =
        schema["example"] ?: schema["default"] ?: (schema["examples"] as? JsonArray)?.items?.firstOrNull() ?: (schema["enum"] as? JsonArray)?.items?.firstOrNull()

    private fun schemaType(schema: JsonObject): String = when (val t = schema["type"]) {
        is JsonString -> t.value
        is JsonArray -> t.items.filterIsInstance<JsonString>().map { it.value }.firstOrNull { it != "null" } ?: "any"
        else -> if (schema["properties"] != null) "object" else if (schema["items"] != null) "array" else "any"
    }

    private fun walkSchema(schema: JsonValue, path: String, depth: Int, maxDepth: Int, maxFields: Int, out: MutableList<FieldInfo>) {
        val s = schema as? JsonObject ?: return
        when (schemaType(s)) {
            "object" -> (s["properties"] as? JsonObject)?.fields?.forEach { (key, child) ->
                if (out.size >= maxFields) return
                val c = child as? JsonObject
                val p = join(path, key)
                val type = c?.let { schemaType(it) } ?: "any"
                out += FieldInfo(p, type, c?.let { exampleOf(it) })
                if (depth + 1 < maxDepth) walkSchema(child, p, depth + 1, maxDepth, maxFields, out)
            }
            "array" -> {
                val items = s["items"] as? JsonObject ?: return
                val p = path + "[*]"
                if (schemaType(items) in setOf("object", "array")) { if (depth + 1 < maxDepth) walkSchema(items, p, depth + 1, maxDepth, maxFields, out) }
                else if (out.size < maxFields) out += FieldInfo(p, schemaType(items), exampleOf(items))
            }
            else -> {}
        }
    }
}

/**
 * A value of the shape [schema] describes, for fields that are known before anything ran: the first of `example`, `default`, `examples` or `enum`;
 * otherwise a placeholder of the type (`""`, `0`, `false`, `[]`, an object with every property, an array with one item). Depth is limited.
 */
public fun JsonSchema.example(schema: JsonValue, depth: Int = 0): JsonValue {
    val s = schema as? JsonObject ?: return JsonNull
    (s["example"] ?: s["default"] ?: (s["examples"] as? JsonArray)?.items?.firstOrNull() ?: (s["enum"] as? JsonArray)?.items?.firstOrNull())?.let { return it }
    if (depth > 8) return JsonNull
    val type = (s["type"] as? JsonString)?.value ?: ((s["type"] as? JsonArray)?.items?.filterIsInstance<JsonString>()?.map { it.value }?.firstOrNull { it != "null" })
        ?: if (s["properties"] != null) "object" else if (s["items"] != null) "array" else "null"
    return when (type) {
        "string" -> JsonString("")
        "number", "integer" -> JsonNumber(0.0)
        "boolean" -> JsonBool(false)
        "array" -> JsonArray(listOfNotNull((s["items"] as? JsonObject)?.let { example(it, depth + 1) }))
        "object" -> JsonObject((s["properties"] as? JsonObject)?.fields?.mapValues { (_, v) -> example(v, depth + 1) } ?: emptyMap())
        else -> JsonNull
    }
}

/** The text of a JSON Schema [shorthand] with nested objects and arrays: `{"name": "string", "tags": ["string"], "owner": {"id": "integer"}}`; flat shorthands work as with [JsonSchema.fromShorthand]. */
public fun JsonSchema.fromNestedShorthand(shorthand: JsonValue): JsonObject = when (shorthand) {
    is JsonString -> jsonObjectOf("type" to shorthand)
    is JsonArray -> jsonObjectOf("type" to JsonString("array"), "items" to (shorthand.items.firstOrNull()?.let { fromNestedShorthand(it) } ?: JsonObject(emptyMap())))
    is JsonObject -> jsonObjectOf(
        "type" to JsonString("object"),
        "properties" to JsonObject(shorthand.fields.mapValues { (_, v) -> fromNestedShorthand(v) }),
        "required" to JsonArray(shorthand.fields.keys.map { JsonString(it) }),
    )
    else -> JsonObject(emptyMap())
}
