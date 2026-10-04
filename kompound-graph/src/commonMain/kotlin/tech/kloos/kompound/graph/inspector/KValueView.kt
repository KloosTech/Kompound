package tech.kloos.kompound.graph.inspector

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.kloos.kompound.graph.serialization.JsonArray
import tech.kloos.kompound.graph.serialization.JsonBool
import tech.kloos.kompound.graph.serialization.JsonNull
import tech.kloos.kompound.graph.serialization.JsonNumber
import tech.kloos.kompound.graph.serialization.JsonObject
import tech.kloos.kompound.graph.serialization.JsonString
import tech.kloos.kompound.graph.serialization.JsonValue
import tech.kloos.kompound.graph.serialization.ValueJson
import tech.kloos.kompound.text.KText

/** How [KValueView] shows a value. */
public enum class ValueViewMode(public val label: String) {
    /** The structure: every field with its type and a preview. */
    Schema("Schema"),

    /** Rows and columns: a list of objects becomes a table, an object becomes key and value rows. */
    Table("Table"),

    /** Indented, colour-coded JSON. */
    Json("JSON"),
}

/**
 * Turns anything a node passed around into a JSON tree for display: numbers lose their Kotlin type, lists and maps become arrays and
 * objects, types registered in [codecs] use their codec, everything else shows as its `toString()`.
 */
public fun displayValue(value: Any?, codecs: ValueJson = ValueJson()): JsonValue = when (value) {
    null -> JsonNull
    is Boolean -> JsonBool(value)
    is String -> JsonString(value)
    is Number -> JsonNumber(value.toDouble())
    is JsonValue -> value
    is List<*> -> JsonArray(value.map { displayValue(it, codecs) })
    is Map<*, *> -> JsonObject(LinkedHashMap<String, JsonValue>().also { m -> for ((k, v) in value) m[k.toString()] = displayValue(v, codecs) })
    else -> runCatching { (codecs.encode(value) as? JsonObject)?.get("value") }.getOrNull() ?: JsonString(value.toString())
}

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

private class Palette(val key: Color, val string: Color, val number: Color, val keyword: Color, val punctuation: Color, val muted: Color)

@Composable
private fun palette(): Palette {
    val s = MaterialTheme.colorScheme
    return Palette(s.primary, s.tertiary, s.secondary, s.error, s.onSurfaceVariant, s.outline)
}

/**
 * Shows [value] (anything: numbers, strings, lists, maps, [JsonValue], your own types through [valueJson]) as [mode]. The view scrolls
 * by itself, so give it a bounded height; long values are cut off with a note ([maxRows]).
 */
@Composable
public fun KValueView(
    value: Any?,
    modifier: Modifier = Modifier,
    mode: ValueViewMode = ValueViewMode.Json,
    valueJson: ValueJson = ValueJson(),
    maxRows: Int = 400,
) {
    val tree = remember(value, valueJson) { displayValue(value, valueJson) }
    val colors = palette()
    val body = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)
    SelectionContainer(modifier) {
        when (mode) {
            ValueViewMode.Json -> {
                val text = remember(tree, colors) { jsonText(tree, colors, maxRows) }
                KText(text, body.horizontalScroll(rememberScrollState()), textStyle = Mono)
            }
            ValueViewMode.Schema -> {
                val rows = remember(tree) { schemaRows(tree, maxRows) }
                Column(body.horizontalScroll(rememberScrollState())) {
                    for (row in rows) KText(schemaLine(row, colors), textStyle = Mono, maxLines = 1)
                }
            }
            ValueViewMode.Table -> TableView(tree, body, colors, maxRows)
        }
    }
}

// --- JSON ---------------------------------------------------------------------------------------------

private fun jsonText(value: JsonValue, c: Palette, maxRows: Int): AnnotatedString = buildAnnotatedString {
    var rows = 1
    fun nl(depth: Int) { append('\n'); rows++; repeat(depth) { append("  ") } }
    fun punct(s: String) = withStyle(SpanStyle(color = c.punctuation)) { append(s) }
    fun write(v: JsonValue, depth: Int) {
        if (rows > maxRows) return
        when (v) {
            JsonNull -> withStyle(SpanStyle(color = c.keyword)) { append("null") }
            is JsonBool -> withStyle(SpanStyle(color = c.keyword)) { append(v.value.toString()) }
            is JsonNumber -> withStyle(SpanStyle(color = c.number)) { append(numberText(v.value)) }
            is JsonString -> withStyle(SpanStyle(color = c.string)) { append(quote(v.value)) }
            is JsonArray -> {
                if (v.items.isEmpty()) { punct("[]"); return }
                punct("[")
                v.items.forEachIndexed { i, item ->
                    if (i > 0) punct(",")
                    nl(depth + 1)
                    write(item, depth + 1)
                }
                nl(depth); punct("]")
            }
            is JsonObject -> {
                if (v.fields.isEmpty()) { punct("{}"); return }
                punct("{")
                var first = true
                for ((k, item) in v.fields) {
                    if (!first) punct(",")
                    first = false
                    nl(depth + 1)
                    withStyle(SpanStyle(color = c.key)) { append(quote(k)) }
                    punct(": ")
                    write(item, depth + 1)
                }
                nl(depth); punct("}")
            }
        }
    }
    write(value, 0)
    if (rows > maxRows) { append('\n'); withStyle(SpanStyle(color = c.muted)) { append("… cut off after $maxRows lines") } }
}

internal fun numberText(d: Double): String = if (d == d.toLong().toDouble() && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()

private fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

// --- schema -------------------------------------------------------------------------------------------

internal class SchemaRow(val depth: Int, val name: String, val type: String, val preview: String?)

internal fun schemaRows(value: JsonValue, maxRows: Int): List<SchemaRow> {
    val rows = ArrayList<SchemaRow>()
    fun typeOf(v: JsonValue) = when (v) {
        JsonNull -> "null"
        is JsonBool -> "boolean"
        is JsonNumber -> "number"
        is JsonString -> "string"
        is JsonArray -> "array(${v.items.size})"
        is JsonObject -> "object(${v.fields.size})"
    }
    fun preview(v: JsonValue): String? = when (v) {
        is JsonBool -> v.value.toString()
        is JsonNumber -> numberText(v.value)
        is JsonString -> quote(v.value.take(60)) + if (v.value.length > 60) "…" else ""
        else -> null
    }
    fun walk(v: JsonValue, name: String, depth: Int) {
        if (rows.size > maxRows) return
        rows += SchemaRow(depth, name, typeOf(v), preview(v))
        when (v) {
            is JsonObject -> for ((k, item) in v.fields) walk(item, k, depth + 1)
            is JsonArray -> {
                // Show the first item as the shape of the rest.
                v.items.firstOrNull()?.let { walk(it, "[0]", depth + 1) }
                if (v.items.size > 1) rows += SchemaRow(depth + 1, "… ${v.items.size - 1} more", "", null)
            }
            else -> {}
        }
    }
    walk(value, "value", 0)
    return rows
}

private fun schemaLine(row: SchemaRow, c: Palette): AnnotatedString = buildAnnotatedString {
    append("  ".repeat(row.depth))
    withStyle(SpanStyle(color = c.key)) { append(row.name) }
    if (row.type.isNotEmpty()) withStyle(SpanStyle(color = c.punctuation)) { append("  ${row.type}") }
    row.preview?.let { withStyle(SpanStyle(color = c.string)) { append("  $it") } }
}

// --- table --------------------------------------------------------------------------------------------

@Composable
private fun TableView(value: JsonValue, modifier: Modifier, c: Palette, maxRows: Int) {
    val (columns, rows) = remember(value) { tableOf(value, maxRows) }
    Column(modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (col in columns) KText(AnnotatedString(col), Modifier.width(160.dp), textStyle = Mono.copy(color = c.key), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (cell in row) KText(AnnotatedString(cell), Modifier.width(160.dp), textStyle = Mono, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun tableOf(value: JsonValue, maxRows: Int): Pair<List<String>, List<List<String>>> {
    fun cell(v: JsonValue?): String = when (v) {
        null -> ""
        is JsonString -> v.value
        is JsonNumber -> numberText(v.value)
        is JsonBool -> v.value.toString()
        JsonNull -> "null"
        else -> v.toJson()
    }
    return when {
        value is JsonArray && value.items.isNotEmpty() && value.items.all { it is JsonObject } -> {
            val columns = LinkedHashSet<String>()
            for (item in value.items) columns += (item as JsonObject).fields.keys
            columns.toList() to value.items.take(maxRows).map { item -> columns.map { cell((item as JsonObject)[it]) } }
        }
        value is JsonArray -> listOf("index", "value") to value.items.take(maxRows).mapIndexed { i, v -> listOf(i.toString(), cell(v)) }
        value is JsonObject -> listOf("key", "value") to value.fields.entries.take(maxRows).map { (k, v) -> listOf(k, cell(v)) }
        else -> listOf("value") to listOf(listOf(cell(value)))
    }
}
