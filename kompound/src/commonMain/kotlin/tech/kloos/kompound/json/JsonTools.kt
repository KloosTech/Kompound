package tech.kloos.kompound.json

/** The value at [path] (`items[0].name`, `a.b`, `[2]`, `items[*].id`), or `null` when something on the way is missing; `*` collects from every item into an array. */
public fun JsonValue.at(path: String): JsonValue? {
    var current: List<JsonValue> = listOf(this)
    var wildcard = false
    for (step in parsePath(path)) {
        val next = ArrayList<JsonValue>()
        for (v in current) when (step) {
            is PathStep.Key -> (v as? JsonObject)?.get(step.name)?.let { next += it }
            is PathStep.Index -> (v as? JsonArray)?.items?.getOrNull(step.index)?.let { next += it }
            PathStep.All -> if (v is JsonArray) { next += v.items; wildcard = true } else if (v is JsonObject) { next += v.fields.values; wildcard = true }
        }
        if (next.isEmpty()) return null
        current = next
    }
    return if (wildcard) JsonArray(current) else current.single()
}

private sealed interface PathStep {
    data class Key(val name: String) : PathStep
    data class Index(val index: Int) : PathStep
    data object All : PathStep
}

private fun parsePath(path: String): List<PathStep> {
    val steps = ArrayList<PathStep>()
    var i = 0
    val n = path.length
    while (i < n) {
        when (path[i]) {
            '.' -> i++
            '[' -> {
                val end = path.indexOf(']', i)
                if (end < 0) throw KJsonException("Unclosed [ in path \"$path\"")
                val inner = path.substring(i + 1, end).trim()
                steps += when {
                    inner == "*" -> PathStep.All
                    inner.toIntOrNull() != null -> PathStep.Index(inner.toInt())
                    inner.length >= 2 && (inner.first() == '"' || inner.first() == '\'') -> PathStep.Key(inner.substring(1, inner.length - 1))
                    else -> throw KJsonException("Bad index \"$inner\" in path \"$path\"")
                }
                i = end + 1
            }
            else -> {
                var j = i
                while (j < n && path[j] != '.' && path[j] != '[') j++
                val name = path.substring(i, j)
                steps += if (name == "*") PathStep.All else PathStep.Key(name)
                i = j
            }
        }
    }
    return steps
}

/**
 * Turns a Kotlin value into JSON: `null`, `Boolean`, numbers, `String` (and `Char`), `Enum` (by name), `Map<String, *>`, `Iterable`, `Array`
 * and an existing [JsonValue] (kept). Anything else becomes its `toString()`.
 */
public fun JsonValue.Companion.of(value: Any?): JsonValue = when (value) {
    null -> JsonNull
    is JsonValue -> value
    is Boolean -> JsonBool(value)
    is Number -> JsonNumber(value.toDouble())
    is String -> JsonString(value)
    is Char -> JsonString(value.toString())
    is Enum<*> -> JsonString(value.name)
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to of(v) })
    is Iterable<*> -> JsonArray(value.map { of(it) })
    is Array<*> -> JsonArray(value.map { of(it) })
    else -> JsonString(value.toString())
}

/** [JsonValue.parse] that returns `null` instead of throwing for text that is not JSON. */
public fun JsonValue.Companion.parseOrNull(text: String): JsonValue? = try { parse(text) } catch (e: KJsonException) { null }

/** This object with the fields of [other] added; where both have a field, [other] wins. Objects in both are merged one level down only when [deep] is set. */
public fun JsonObject.merge(other: JsonObject, deep: Boolean = false): JsonObject {
    val out = LinkedHashMap(fields)
    for ((k, v) in other.fields) {
        val mine = out[k]
        out[k] = if (deep && mine is JsonObject && v is JsonObject) mine.merge(v, deep = true) else v
    }
    return JsonObject(out)
}

/** `a + b`: the fields of both, [other] wins on a clash. */
public operator fun JsonObject.plus(other: JsonObject): JsonObject = merge(other)

/** One problem [JsonSchema.validate] found: where ([path], like `items[2].name`, empty for the root) and what ([message]). */
public data class JsonProblem(val path: String, val message: String) {
    override fun toString(): String = if (path.isEmpty()) message else "$path: $message"
}

/**
 * Checks a value against a JSON Schema subset: `type` (`string`, `number`, `integer`, `boolean`, `object`, `array`, `null`; a list of them too),
 * `properties`, `required`, `additionalProperties: false`, `items`, `enum`, `minimum`, `maximum`, `minLength`, `maxLength`, `minItems`,
 * `maxItems`. Unknown keywords are ignored (no `pattern`, `$ref` or `oneOf`). Schemas are plain [JsonValue]s, so you can parse them from text.
 */
public object JsonSchema {
    /** The problems of [value] against [schema] (empty when it is valid). */
    public fun validate(value: JsonValue, schema: JsonValue): List<JsonProblem> = ArrayList<JsonProblem>().also { check(value, schema, "", it) }

    /** The shorthand `{"name": "string", "age": "number"}` as a schema: an object with these required properties. */
    public fun fromShorthand(shorthand: JsonObject): JsonObject = jsonObjectOf(
        "type" to JsonString("object"),
        "properties" to JsonObject(shorthand.fields.mapValues { (_, v) -> if (v is JsonString) jsonObjectOf("type" to v) else v }),
        "required" to JsonArray(shorthand.fields.keys.map { JsonString(it) }),
    )

    private fun typeName(v: JsonValue) = when (v) {
        JsonNull -> "null"
        is JsonBool -> "boolean"
        is JsonNumber -> if (v.value == kotlin.math.floor(v.value) && !v.value.isInfinite()) "integer" else "number"
        is JsonString -> "string"
        is JsonArray -> "array"
        is JsonObject -> "object"
    }

    private fun matches(v: JsonValue, type: String) = when (type) {
        "number" -> v is JsonNumber
        "integer" -> v is JsonNumber && typeName(v) == "integer"
        else -> typeName(v) == type
    }

    private fun check(value: JsonValue, schema: JsonValue, path: String, out: MutableList<JsonProblem>) {
        val s = schema as? JsonObject ?: return
        fun problem(message: String) { out += JsonProblem(path, message) }
        val type = s["type"]
        val types = when (type) { is JsonString -> listOf(type.value); is JsonArray -> type.items.filterIsInstance<JsonString>().map { it.value }; else -> emptyList() }
        if (types.isNotEmpty() && types.none { matches(value, it) }) { problem("expected ${types.joinToString(" or ")}, found ${typeName(value)}"); return }
        (s["enum"] as? JsonArray)?.let { if (value !in it.items) problem("must be one of ${it.items.joinToString { v -> v.toJson() }}") }
        when (value) {
            is JsonNumber -> {
                (s["minimum"] as? JsonNumber)?.let { if (value.value < it.value) problem("must be at least ${it.value}") }
                (s["maximum"] as? JsonNumber)?.let { if (value.value > it.value) problem("must be at most ${it.value}") }
            }
            is JsonString -> {
                (s["minLength"] as? JsonNumber)?.let { if (value.value.length < it.value) problem("must have at least ${it.value.toInt()} characters") }
                (s["maxLength"] as? JsonNumber)?.let { if (value.value.length > it.value) problem("must have at most ${it.value.toInt()} characters") }
            }
            is JsonArray -> {
                (s["minItems"] as? JsonNumber)?.let { if (value.items.size < it.value) problem("must have at least ${it.value.toInt()} items") }
                (s["maxItems"] as? JsonNumber)?.let { if (value.items.size > it.value) problem("must have at most ${it.value.toInt()} items") }
                s["items"]?.let { items -> value.items.forEachIndexed { i, v -> check(v, items, "$path[$i]", out) } }
            }
            is JsonObject -> {
                val props = s["properties"] as? JsonObject
                (s["required"] as? JsonArray)?.items?.filterIsInstance<JsonString>()?.forEach { r ->
                    if (r.value !in value.fields) out += JsonProblem(join(path, r.value), "is required")
                }
                for ((k, v) in value.fields) {
                    val sub = props?.get(k)
                    if (sub != null) check(v, sub, join(path, k), out)
                    else if ((s["additionalProperties"] as? JsonBool)?.value == false) out += JsonProblem(join(path, k), "is not allowed")
                }
            }
            else -> {}
        }
    }

    private fun join(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"
}

/**
 * Why a value of the shape [source] may not satisfy [target] (both JSON Schemas, the subset [JsonSchema.validate] knows): a different `type`
 * (`integer` fits `number`; a list of types fits if one matches), a `required` property the source's `properties` do not have, or the same
 * problem inside a property or the items of an array. Empty when it can fit or when the source says too little to tell. A hint, not a proof: schemas are never enforced.
 */
public fun JsonSchema.incompatibilities(source: JsonValue, target: JsonValue): List<String> = ArrayList<String>().also { compare(source, target, "", 0, it) }

private fun schemaTypes(s: JsonObject): Set<String> = when (val t = s["type"]) {
    is JsonString -> setOf(t.value)
    is JsonArray -> t.items.filterIsInstance<JsonString>().map { it.value }.toSet()
    else -> emptySet()
}

private fun typesFit(source: Set<String>, target: Set<String>): Boolean =
    source.isEmpty() || target.isEmpty() || source.any { s -> s in target || (s == "integer" && "number" in target) }

private fun compare(source: JsonValue, target: JsonValue, path: String, depth: Int, out: MutableList<String>) {
    val s = source as? JsonObject ?: return
    val t = target as? JsonObject ?: return
    if (depth > 6) return
    fun where(text: String) = if (path.isEmpty()) text else "$path: $text"
    val st = schemaTypes(s)
    val tt = schemaTypes(t)
    if (!typesFit(st, tt)) { out += where("expected ${tt.joinToString(" or ")}, gets ${st.joinToString(" or ")}"); return }
    val sp = s["properties"] as? JsonObject
    val tp = t["properties"] as? JsonObject
    if (sp != null) {
        (t["required"] as? JsonArray)?.items?.filterIsInstance<JsonString>()?.forEach { r ->
            if (r.value !in sp.fields) out += where("missing field \"${r.value}\"")
        }
        if (tp != null) for ((key, sub) in tp.fields) sp[key]?.let { compare(it, sub, if (path.isEmpty()) key else "$path.$key", depth + 1, out) }
    }
    val si = s["items"]
    val ti = t["items"]
    if (si != null && ti != null) compare(si, ti, "$path[*]", depth + 1, out)
}
