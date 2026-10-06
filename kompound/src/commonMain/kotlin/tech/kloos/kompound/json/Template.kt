package tech.kloos.kompound.json

/** Something is wrong with a template: an unknown filter, an unclosed `{{`, an empty placeholder. */
public open class TemplateException(message: String) : IllegalArgumentException(message)

/**
 * A placeholder named a field that is not there. [path] is the path in the template (`title`, `in.title`); [node] and [port] say where it was
 * rendered when something that knows (a graph node) rendered it, otherwise they are `null`.
 */
public class MissingFieldException(
    public val path: String,
    public val node: String? = null,
    public val port: String? = null,
    detail: String? = null,
) : TemplateException(
    buildString {
        if (node != null) append("Node \"$node\"")
        if (port != null) append(if (node != null) ", input \"$port\"" else "Input \"$port\"")
        if (node != null || port != null) append(": ")
        append("no field \"$path\"")
        if (detail != null) append(" ($detail)")
    },
)

/** Where placeholders get their values. [resolve] returns the JSON at [path], or `null` when there is nothing (a missing field; a present JSON `null` is [JsonNull]). */
public fun interface TemplateScope {
    /** The value at [path] (the syntax of [JsonValue.at]), or `null` when this scope has nothing there. */
    public fun resolve(path: String): JsonValue?

    public companion object {
        /** A scope that reads paths from [value]: `{{title}}` is `value.at("title")`; the empty path is [value] itself. */
        public fun of(value: JsonValue): TemplateScope = TemplateScope { path -> if (path.isEmpty()) value else value.at(path) }

        /** A scope of named values: `{{name.field}}` reads `field` of the value called `name`, `{{name}}` is the whole value. Values are turned into JSON with [JsonValue.of]. */
        public fun ofNamed(values: Map<String, Any?>): TemplateScope = TemplateScope { path ->
            val key = path.takeWhile { it != '.' && it != '[' }
            if (key !in values) null
            else {
                val json = JsonValue.of(values[key])
                val rest = path.removePrefix(key).removePrefix(".")
                if (rest.isEmpty()) json else json.at(rest)
            }
        }
    }
}

/** This scope first, then [next] for what it does not have. */
public infix fun TemplateScope.then(next: TemplateScope): TemplateScope = TemplateScope { path -> resolve(path) ?: next.resolve(path) }

/** One piece of a parsed template. [start] (inclusive) and [end] (exclusive) are offsets in the text, for editors that mark placeholders. */
public sealed interface TemplatePart {
    public val start: Int
    public val end: Int

    /** Plain text (a `\{{` is already the literal `{{` here). */
    public class Literal(public val text: String, override val start: Int, override val end: Int) : TemplatePart

    /** `{{ path | filter | filter:arg }}`. */
    public class Placeholder(public val path: String, public val filters: List<TemplateFilter>, override val start: Int, override val end: Int) : TemplatePart

    /** Something that looked like a placeholder but is not valid; [message] says why. Rendering fails on it. */
    public class Invalid(public val text: String, public val message: String, override val start: Int, override val end: Int) : TemplatePart
}

/** A filter of a placeholder: `json`, `url`, `default:text`. */
public class TemplateFilter(public val name: String, public val argument: String? = null)

/**
 * Text with `{{path}}` placeholders filled from JSON: `POST /issues` with the body `{"name": "{{title}}", "tags": {{tags|json}}}`.
 *
 * - **Paths** use the syntax of [JsonValue.at]: `title`, `owner.name`, `tasks[0].title`, `tasks[*].title` (all items, as a JSON array). Spaces inside the braces are fine.
 * - **Literal braces**: `\{{` is the text `{{`.
 * - **Filters** after `|`, applied left to right: `json` (a string's characters escaped for the inside of a JSON string, without the quotes; any
 *   other value as JSON text), `url` (percent-encoded UTF-8), `default:text` (used when the field is missing, `null` or empty).
 * - **Values** become text as they are: strings as they are, numbers and booleans as JSON, objects and arrays as compact JSON, `null` as nothing.
 * - **A missing field** is a [MissingFieldException], unless a `default` is given. There are no expressions and no conditions; write a node for that.
 */
public object Template {
    /** The pieces of [text] in order. Never throws: bad placeholders come back as [TemplatePart.Invalid]. */
    public fun parse(text: String): List<TemplatePart> {
        val parts = ArrayList<TemplatePart>()
        val literal = StringBuilder()
        var literalStart = 0
        fun flush(end: Int) {
            if (literal.isNotEmpty()) parts += TemplatePart.Literal(literal.toString(), literalStart, end)
            literal.clear()
        }
        var i = 0
        while (i < text.length) {
            if (text.startsWith("\\{{", i)) {
                if (literal.isEmpty()) literalStart = i
                literal.append("{{")
                i += 3
                continue
            }
            if (text.startsWith("{{", i)) {
                flush(i)
                val close = text.indexOf("}}", i + 2)
                if (close < 0) {
                    parts += TemplatePart.Invalid(text.substring(i), "Unclosed {{", i, text.length)
                    i = text.length
                    literalStart = i
                    break
                }
                parts += placeholder(text.substring(i + 2, close), i, close + 2, text.substring(i, close + 2))
                i = close + 2
                literalStart = i
                continue
            }
            if (literal.isEmpty()) literalStart = i
            literal.append(text[i])
            i++
        }
        flush(text.length)
        return parts
    }

    private val knownFilters = setOf("json", "url", "default")

    private fun placeholder(body: String, start: Int, end: Int, raw: String): TemplatePart {
        val pieces = body.split('|')
        val path = pieces[0].trim()
        if (path.isEmpty()) return TemplatePart.Invalid(raw, "Empty placeholder", start, end)
        val filters = pieces.drop(1).map { piece ->
            val name = piece.substringBefore(':').trim()
            val arg = if (':' in piece) piece.substringAfter(':').trim() else null
            if (name !in knownFilters) return TemplatePart.Invalid(raw, "Unknown filter \"$name\"", start, end)
            if (name == "default" && arg == null) return TemplatePart.Invalid(raw, "default needs a value: default:text", start, end)
            TemplateFilter(name, arg)
        }
        return TemplatePart.Placeholder(path, filters, start, end)
    }

    /** The paths [text] reads (`title`, `in.owner.name`), without filters, in order, each once. */
    public fun names(text: String): Set<String> = parse(text).filterIsInstance<TemplatePart.Placeholder>().mapTo(LinkedHashSet()) { it.path }

    /** The invalid placeholders of [text], for an editor to mark before anything runs. */
    public fun problems(text: String): List<TemplatePart.Invalid> = parse(text).filterIsInstance<TemplatePart.Invalid>()

    /** Fills [text] from [scope]. Throws [MissingFieldException] for a field the scope does not have (and no `default` filter), [TemplateException] for invalid placeholders. */
    public fun render(text: String, scope: TemplateScope): String {
        val out = StringBuilder()
        for (part in parse(text)) when (part) {
            is TemplatePart.Literal -> out.append(part.text)
            is TemplatePart.Invalid -> throw TemplateException("${part.message} at ${part.start}: ${part.text}")
            is TemplatePart.Placeholder -> out.append(value(part, scope))
        }
        return out.toString()
    }

    private fun value(part: TemplatePart.Placeholder, scope: TemplateScope): String {
        val default = part.filters.firstOrNull { it.name == "default" }?.argument
        val found = scope.resolve(part.path)
        var current: JsonValue? = found
        if (default != null && (current == null || current == JsonNull || (current is JsonString && current.value.isEmpty()))) current = JsonString(default)
        if (current == null) throw MissingFieldException(part.path)
        // `default` is applied above; the others run in order on the text. While nothing changed the text yet, `json` still sees the value itself.
        var text = text(current)
        var untouched: JsonValue? = current
        for (f in part.filters) when (f.name) {
            "json" -> { text = untouched?.let { jsonText(it) } ?: jsonText(JsonString(text)); untouched = null }
            "url" -> { text = percentEncode(text); untouched = null }
        }
        return text
    }

    private fun text(v: JsonValue): String = when (v) {
        is JsonString -> v.value
        JsonNull -> ""
        else -> v.toJson()
    }

    // A string's characters escaped for use inside a JSON string literal; anything else is its JSON text.
    private fun jsonText(v: JsonValue): String = if (v is JsonString) v.toJson().let { it.substring(1, it.length - 1) } else v.toJson()

    private const val Unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"

    private fun percentEncode(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c in Unreserved) append(c) else append('%').append("0123456789ABCDEF"[(b.toInt() shr 4) and 0xF]).append("0123456789ABCDEF"[b.toInt() and 0xF])
        }
    }
}
