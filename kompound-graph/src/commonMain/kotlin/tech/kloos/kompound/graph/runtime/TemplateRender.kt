package tech.kloos.kompound.graph.runtime

import tech.kloos.kompound.graph.model.KSecret
import tech.kloos.kompound.json.JsonArray
import tech.kloos.kompound.json.JsonNull
import tech.kloos.kompound.json.JsonObject
import tech.kloos.kompound.json.JsonValue
import tech.kloos.kompound.json.MissingFieldException
import tech.kloos.kompound.json.Template
import tech.kloos.kompound.json.TemplateScope
import tech.kloos.kompound.json.at
import tech.kloos.kompound.json.then

/**
 * The input [port] as JSON: a [JsonValue] as it is, text that is a JSON object or array parsed, maps, lists, numbers and booleans converted, a
 * [KSecret] unwrapped (a template may use a secret; it is never shown). `null` when the port is not connected or holds something without a JSON form.
 */
public fun NodeInputs.json(port: String): JsonValue? {
    if (!has(port)) return null
    val value = get(port).let { if (it is KSecret) it.value else it }
    return JsonSamples.from(value) ?: if (value == null) JsonNull else null
}

/**
 * What `{{...}}` placeholders in this node's settings can read: the JSON inputs by port id (`{{in.title}}`, `{{in[0].x}}`, `{{in}}` for the whole
 * value), and bare paths (`{{title}}`) against the one connected input that holds an object or array, when there is exactly one. A bare path
 * that starts with a port id means that port.
 */
public fun NodeRunContext.templateScope(): TemplateScope {
    val jsons = inputs.all.keys.mapNotNull { port -> inputs.json(port.value)?.let { port.value to it } }.toMap()
    val sole = jsons.values.filter { it is JsonObject || it is JsonArray }.singleOrNull()
    return TemplateScope { path ->
        val first = path.takeWhile { it != '.' && it != '[' }
        val port = jsons[first]
        when {
            port != null -> path.removePrefix(first).removePrefix(".").let { rest -> if (rest.isEmpty()) port else port.at(rest) }
            else -> sole?.at(path)
        }
    }
}

/**
 * Renders [text] (a URL, a header, a body, a prompt, a shell command) with this node's inputs: `{"name": "{{title}}"}` becomes `{"name": "T"}`
 * when the one JSON input has a `title` field. [extra] scopes (variables, environment) are asked after the inputs. A field that is not there fails
 * the run with a [MissingFieldException] that names this node, the input and the path, and lists the fields the input has.
 * See [Template] for the syntax.
 */
public fun NodeRunContext.render(text: String, vararg extra: TemplateScope): String {
    var scope = templateScope()
    for (e in extra) scope = scope then e
    try {
        return Template.render(text, scope)
    } catch (e: MissingFieldException) {
        val first = e.path.takeWhile { it != '.' && it != '[' }
        val structured = inputs.all.keys.map { it.value }.filter { inputs.json(it).let { v -> v is JsonObject || v is JsonArray } }
        val port = if (inputs.has(first)) first else structured.singleOrNull()
        val shape = port?.let { inputs.json(it) }?.let { v -> (v as? JsonObject)?.fields?.keys?.take(8)?.joinToString() ?: if (v is JsonArray) "an array" else null }
        throw MissingFieldException(e.path, node.id.value, port, shape?.let { "it has: $it" })
    }
}
