package tech.kloos.kompound.template

import androidx.compose.runtime.Immutable
import tech.kloos.kompound.json.FieldInfo
import tech.kloos.kompound.json.JsonString
import tech.kloos.kompound.json.JsonValue
import tech.kloos.kompound.json.Template
import tech.kloos.kompound.json.TemplateScope
import tech.kloos.kompound.json.at

/** The `{{` the caret is inside of: replace [replaceStart] until [replaceEnd] (the caret) with a field path. [query] is what was typed so far. */
internal class TemplateCompletion(val replaceStart: Int, val replaceEnd: Int, val query: String)

/** Whether the caret at [caret] in [text] is inside an unfinished `{{ ... ` placeholder (no closing braces yet, not after a filter bar), and what was typed. */
internal fun templateCompletionAt(text: String, caret: Int): TemplateCompletion? {
    val at = caret.coerceIn(0, text.length)
    val before = text.substring(0, at)
    var open = before.lastIndexOf("{{")
    while (open > 0 && before[open - 1] == '\\') open = if (open >= 2) before.lastIndexOf("{{", open - 1) else -1
    if (open < 0) return null
    val between = before.substring(open + 2)
    if ("}}" in between || '|' in between) return null
    val typed = between.trimStart()
    return TemplateCompletion(open + 2 + (between.length - typed.length), at, typed)
}

/** [text] with [path] put in place of what was typed, closing `}}` added when the placeholder has none, and the new caret position (after the `}}`). */
internal fun applyTemplateCompletion(text: String, completion: TemplateCompletion, path: String): Pair<String, Int> {
    val head = text.substring(0, completion.replaceStart) + path
    val rest = text.substring(completion.replaceEnd)
    val close = Regex("^\\s*\\}\\}").find(rest)
    return if (close != null) (head + rest) to (head.length + close.value.length)
    else (head + "}}" + rest) to (head.length + 2)
}

/** Whether [path] names one of [known] (indexes count as `[*]`: `tasks[0].title` is `tasks[*].title`). */
internal fun fieldKnown(path: String, known: Set<String>): Boolean = path in known || path.replace(Regex("\\[\\d+\\]"), "[*]") in known

/**
 * How a named parameter of a node gets its value (see [KFieldMapper]): from a [Field] of the input, as a fixed [Value], or from a [Template]
 * text. A parameter without a binding is not set.
 */
@Immutable
public sealed interface FieldBinding {
    /** The value of the field at [path] (kept as JSON: a number stays a number). */
    public data class Field(public val path: String) : FieldBinding

    /** The fixed text [text]. */
    public data class Value(public val text: String) : FieldBinding

    /** The text [template] with its placeholders filled in. */
    public data class Template(public val template: String) : FieldBinding

    /** The value this binding gives with [scope]: a field's JSON (or `null` when it is missing), the fixed text, or the rendered template (which throws like [tech.kloos.kompound.json.Template.render]). */
    public fun resolve(scope: TemplateScope): JsonValue? = when (this) {
        is Field -> scope.resolve(path)
        is Value -> JsonString(text)
        is Template -> JsonString(tech.kloos.kompound.json.Template.render(template, scope))
    }
}

/** A named parameter a [KFieldMapper] row sets: [key] in the bindings map, [label] shown, a [type] hint (`string`, `number`, ...) and an optional [description]. */
@Immutable
public data class ParamSpec(public val key: String, public val label: String = key, public val type: String = "string", public val description: String? = null)

/** A scope that answers placeholders with the examples of [fields] (what the template would give with example data), for previews. */
public fun TemplateScope.Companion.ofFieldExamples(fields: List<FieldInfo>): TemplateScope {
    val byPath = fields.associateBy { it.path }
    return TemplateScope { path -> (byPath[path] ?: byPath[path.replace(Regex("\\[\\d+\\]"), "[*]")])?.example }
}

/** The text a preview shows for [binding] with [fields]' examples, or `null` when there is nothing to show (unknown field, no example, bad template). */
internal fun bindingPreview(binding: FieldBinding?, fields: List<FieldInfo>): String? {
    if (binding == null) return null
    val scope = TemplateScope.ofFieldExamples(fields)
    return try {
        when (val v = binding.resolve(scope)) {
            null -> null
            is JsonString -> v.value
            else -> v.toJson()
        }
    } catch (e: tech.kloos.kompound.json.TemplateException) { null }
}
