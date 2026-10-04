package tech.kloos.kompound.graph.serialization

/** A JSON document as a tree. Small and dependency free: it is what [GraphJson] and your [NodeDataCodec]s read and write. */
public sealed interface JsonValue {
    /** The JSON text of this value; [pretty] indents it by two spaces. */
    public fun toJson(pretty: Boolean = false): String = buildString { JsonWriter(this, pretty).write(this@JsonValue, 0) }

    public companion object {
        /** Parses [text]; throws [GraphJsonException] when it is not valid JSON. */
        public fun parse(text: String): JsonValue = JsonParser(text).parseDocument()
    }
}

/** `null`. */
public data object JsonNull : JsonValue

/** `true` or `false`. */
public data class JsonBool(public val value: Boolean) : JsonValue

/** A number; JSON does not tell integers and floating point apart, and neither does this class. */
public data class JsonNumber(public val value: Double) : JsonValue

/** A string. */
public data class JsonString(public val value: String) : JsonValue

/** An array. */
public data class JsonArray(public val items: List<JsonValue>) : JsonValue

/** An object; field order is kept. */
public data class JsonObject(public val fields: Map<String, JsonValue>) : JsonValue {
    /** The field [name], or `null` when missing. */
    public operator fun get(name: String): JsonValue? = fields[name]
}

/** Something is wrong with a JSON text or with the graph it describes. */
public class GraphJsonException(message: String) : IllegalArgumentException(message)

/** Builds a [JsonObject] from pairs; `null` values are left out. */
public fun jsonObjectOf(vararg fields: Pair<String, JsonValue?>): JsonObject =
    JsonObject(LinkedHashMap<String, JsonValue>().also { map -> for ((k, v) in fields) if (v != null) map[k] = v })

private class JsonWriter(private val out: StringBuilder, private val pretty: Boolean) {
    fun write(value: JsonValue, depth: Int) {
        when (value) {
            JsonNull -> out.append("null")
            is JsonBool -> out.append(value.value)
            is JsonNumber -> out.append(number(value.value))
            is JsonString -> string(value.value)
            is JsonArray -> {
                if (value.items.isEmpty()) { out.append("[]"); return }
                out.append('[')
                value.items.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    newline(depth + 1)
                    write(item, depth + 1)
                }
                newline(depth)
                out.append(']')
            }
            is JsonObject -> {
                if (value.fields.isEmpty()) { out.append("{}"); return }
                out.append('{')
                var first = true
                for ((k, v) in value.fields) {
                    if (!first) out.append(',')
                    first = false
                    newline(depth + 1)
                    string(k)
                    out.append(if (pretty) ": " else ":")
                    write(v, depth + 1)
                }
                newline(depth)
                out.append('}')
            }
        }
    }

    private fun newline(depth: Int) {
        if (!pretty) return
        out.append('\n')
        repeat(depth) { out.append("  ") }
    }

    private fun number(d: Double): String = when {
        d.isNaN() || d.isInfinite() -> "null"
        d == d.toLong().toDouble() && kotlin.math.abs(d) < 1e15 -> d.toLong().toString()
        else -> d.toString()
    }

    private fun string(s: String) {
        out.append('"')
        for (c in s) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> out.append(c)
            }
        }
        out.append('"')
    }
}

private class JsonParser(private val text: String) {
    private var pos = 0

    fun parseDocument(): JsonValue {
        val value = parseValue(0)
        skipWhitespace()
        if (pos != text.length) fail("unexpected text after the value")
        return value
    }

    private fun fail(message: String): Nothing = throw GraphJsonException("Invalid JSON at $pos: $message")

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
    }

    private fun parseValue(depth: Int): JsonValue {
        if (depth > 512) fail("nesting too deep")
        skipWhitespace()
        if (pos >= text.length) fail("unexpected end")
        return when (val c = text[pos]) {
            '{' -> parseObject(depth)
            '[' -> parseArray(depth)
            '"' -> JsonString(parseString())
            't' -> literal("true", JsonBool(true))
            'f' -> literal("false", JsonBool(false))
            'n' -> literal("null", JsonNull)
            else -> if (c == '-' || c in '0'..'9') parseNumber() else fail("unexpected '$c'")
        }
    }

    private fun literal(word: String, value: JsonValue): JsonValue {
        if (!text.startsWith(word, pos)) fail("expected $word")
        pos += word.length
        return value
    }

    private fun parseNumber(): JsonValue {
        val start = pos
        if (text[pos] == '-') pos++
        while (pos < text.length && (text[pos] in '0'..'9' || text[pos] == '.' || text[pos] == 'e' || text[pos] == 'E' || text[pos] == '+' || text[pos] == '-')) pos++
        val number = text.substring(start, pos).toDoubleOrNull() ?: fail("bad number")
        return JsonNumber(number)
    }

    private fun parseString(): String {
        pos++ // opening quote
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("unterminated string")
            val c = text[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= text.length) fail("unterminated escape")
                    when (val e = text[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail("bad unicode escape")
                            sb.append(text.substring(pos, pos + 4).toIntOrNull(16)?.toChar() ?: fail("bad unicode escape"))
                            pos += 4
                        }
                        else -> fail("bad escape '\\$e'")
                    }
                }
                else -> {
                    if (c < ' ') fail("control character in string")
                    sb.append(c)
                }
            }
        }
    }

    private fun parseArray(depth: Int): JsonValue {
        pos++
        val items = ArrayList<JsonValue>()
        skipWhitespace()
        if (pos < text.length && text[pos] == ']') { pos++; return JsonArray(items) }
        while (true) {
            items += parseValue(depth + 1)
            skipWhitespace()
            if (pos >= text.length) fail("unterminated array")
            when (text[pos++]) {
                ',' -> continue
                ']' -> return JsonArray(items)
                else -> fail("expected ',' or ']'")
            }
        }
    }

    private fun parseObject(depth: Int): JsonValue {
        pos++
        val fields = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (pos < text.length && text[pos] == '}') { pos++; return JsonObject(fields) }
        while (true) {
            skipWhitespace()
            if (pos >= text.length || text[pos] != '"') fail("expected a field name")
            val name = parseString()
            skipWhitespace()
            if (pos >= text.length || text[pos] != ':') fail("expected ':'")
            pos++
            fields[name] = parseValue(depth + 1)
            skipWhitespace()
            if (pos >= text.length) fail("unterminated object")
            when (text[pos++]) {
                ',' -> continue
                '}' -> return JsonObject(fields)
                else -> fail("expected ',' or '}'")
            }
        }
    }
}
