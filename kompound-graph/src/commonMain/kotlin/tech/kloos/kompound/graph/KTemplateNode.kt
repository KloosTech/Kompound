package tech.kloos.kompound.graph

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.PortDirection
import tech.kloos.kompound.graph.runtime.GraphEngine
import tech.kloos.kompound.json.FieldInfo
import tech.kloos.kompound.template.KFieldPicker
import tech.kloos.kompound.template.KTemplateArea
import tech.kloos.kompound.template.KTemplateField

/** What the template components of a node need: the [fields] its placeholders may read, and [isKnown] for the port-prefixed forms. */
public class NodeTemplateFields(public val fields: List<FieldInfo>, public val isKnown: (String) -> Boolean)

/**
 * The fields the placeholders in [node]'s settings can read, from what `fieldsOf` knows about its inputs ([from]: only that input; `null`: all).
 * With exactly one input that has fields the paths are bare (`title`), as the runtime resolves them; with several they carry the port id
 * (`in.title`, `a.tasks[*].id`). Prefixed forms are accepted as known in both cases. Observable.
 */
public fun GraphEngine.templateFields(node: GraphNode, from: String? = null): NodeTemplateFields {
    // Whether a bare path works at run time depends on all inputs that hold structured data, not on which one is offered here.
    val perPort = node.ports.filter { it.direction == PortDirection.Input }
        .associate { it.id.value to fieldsOf(node.id, it.id.value) }
        .filterValues { it.isNotEmpty() }
    fun prefixed(port: String, path: String) = if (path.startsWith("[")) port + path else "$port.$path"
    val bare = perPort.size == 1
    val fields = perPort.filterKeys { from == null || it == from }
        .flatMap { (port, list) -> if (bare) list else list.map { it.copy(path = prefixed(port, it.path)) } }
    val knownByPort = perPort.mapValues { (_, list) -> list.mapTo(HashSet()) { it.path } }
    val isKnown: (String) -> Boolean = { path ->
        val first = path.takeWhile { it != '.' && it != '[' }
        val known = knownByPort[first]
        if (known == null) false
        else {
            val rest = path.removePrefix(first).removePrefix(".")
            rest.isEmpty() || rest in known || rest.replace(Regex("\\[\\d+\\]"), "[*]") in known
        }
    }
    return NodeTemplateFields(fields, isKnown)
}

/**
 * A [KTemplateField] for a setting of this node (a URL, a header, a path): `{{` offers the fields of the node's input(s) as `engine` knows them,
 * and unknown fields are marked before the run. Use it inside `Content { }`. At run time `ctx.render(text)` resolves the same paths.
 *
 * @param from Offer the fields of this input only; `null` offers every input's (with the port id in front when there is more than one).
 */
@Composable
public fun KNodeScope.TemplateField(
    engine: GraphEngine,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    from: String? = null,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = !(LocalKGraphState.current?.readOnly ?: false),
    style: Style = Style,
) {
    val info = engine.templateFields(node, from)
    KTemplateField(value, onValueChange, info.fields, modifier, label, placeholder, supportingText, enabled, isKnown = info.isKnown, style = style)
}

/** [TemplateField] for longer settings (a body, a prompt, a script). */
@Composable
public fun KNodeScope.TemplateArea(
    engine: GraphEngine,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    from: String? = null,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = !(LocalKGraphState.current?.readOnly ?: false),
    minLines: Int = 3,
    maxLines: Int = 8,
    style: Style = Style,
) {
    val info = engine.templateFields(node, from)
    KTemplateArea(value, onValueChange, info.fields, modifier, label, placeholder, supportingText, enabled, minLines = minLines, maxLines = maxLines, isKnown = info.isKnown, style = style)
}

/** A [KFieldPicker] over the fields of the node's input [from], for a setting that holds one path (JSON get, sort by). */
@Composable
public fun KNodeScope.FieldPicker(
    engine: GraphEngine,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    from: String? = null,
    label: String? = null,
    types: Set<String>? = null,
    enabled: Boolean = !(LocalKGraphState.current?.readOnly ?: false),
) {
    // The picker writes the path the runtime reads: bare for one input, prefixed otherwise.
    KFieldPicker(engine.templateFields(node, from).fields, selected, onPick, modifier, label = label, enabled = enabled, types = types)
}
