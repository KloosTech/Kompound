package tech.kloos.kompound.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.buttons.KIconButtonSize
import tech.kloos.kompound.combobox.KCombobox
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.json.FieldInfo
import tech.kloos.kompound.segmented.KSegmentedControl
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.theme.KompoundTheme

private fun FieldInfo.describe(): String? = listOfNotNull(type, example?.toJson()?.take(40)).joinToString(" · ").ifEmpty { null }

/**
 * A field of the data picked from a list, for a setting that holds one path (JSON get, sort by, filter by). The list narrows as you type (fuzzy)
 * and shows each field's type and an example; a path that is not in the list can be typed, and is marked as unknown while [fields] is not empty.
 *
 * @param fields What can be picked (what `GraphEngine.fieldsOf` returns).
 * @param selected The path now ("" for none).
 * @param onPick Called with the path chosen or typed.
 * @param contentDescription The accessibility name when there is no visible [label] (a picker inside a [KFieldMapper] row).
 * @param types Offer only fields of these types (`setOf("string", "integer", "number")` for a number setting); `null` offers all.
 */
@Composable
public fun KFieldPicker(
    fields: List<FieldInfo>,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    types: Set<String>? = null,
    contentDescription: String? = null,
    style: Style = Style,
) {
    val strings = KompoundTheme.strings
    val offered = remember(fields, types) { if (types == null) fields else fields.filter { it.type in types || it.type == "any" } }
    val known = remember(fields) { fields.mapTo(HashSet()) { it.path } }
    val unknown = selected.isNotBlank() && fields.isNotEmpty() && !fieldKnown(selected, known)
    KCombobox(
        value = selected, onValueChange = onPick, options = offered, onOptionSelected = { onPick(it.path) }, optionLabel = { it.path },
        modifier = modifier, label = label, placeholder = placeholder,
        supportingText = if (unknown) strings.unknownField(selected) else supportingText ?: if (fields.isEmpty()) strings.noFieldsKnown else null,
        isError = unknown, enabled = enabled, optionSupportingText = { it.describe() }, noResultsText = null, contentDescription = contentDescription, style = style,
    )
}

/**
 * Rows "parameter ← field | value | template" for a node whose settings are named parameters (a `Math` node's `b`, a `Delay`'s seconds).
 * Each parameter is bound to a field of the input (picked from [fields]), to a fixed value, or to a template text; the row shows what the
 * binding gives with the fields' example values. Resolve the bindings in your runner with [FieldBinding.resolve].
 *
 * @param fields The fields the input offers.
 * @param parameters The parameters, in order.
 * @param bindings The current bindings by [ParamSpec.key]; a parameter without an entry is not set.
 * @param onBindingsChange Called with the new map (an emptied binding is removed).
 */
@Composable
public fun KFieldMapper(
    fields: List<FieldInfo>,
    parameters: List<ParamSpec>,
    bindings: Map<String, FieldBinding>,
    onBindingsChange: (Map<String, FieldBinding>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val strings = KompoundTheme.strings
    val modes = listOf(strings.fieldMode, strings.valueMode, strings.templateMode)
    // The chosen mode is remembered per parameter even while it has no binding yet.
    val chosen = remember { mutableStateOf(emptyMap<String, Int>()) }
    fun set(key: String, binding: FieldBinding?) {
        val blank = when (binding) { null -> true; is FieldBinding.Field -> binding.path.isBlank(); is FieldBinding.Value -> binding.text.isEmpty(); is FieldBinding.Template -> binding.template.isEmpty() }
        onBindingsChange(if (blank) bindings - key else bindings + (key to binding!!))
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (param in parameters) {
            val binding = bindings[param.key]
            val mode = chosen.value[param.key] ?: when (binding) { is FieldBinding.Value -> 1; is FieldBinding.Template -> 2; else -> 0 }
            val numeric = param.type == "number" || param.type == "integer"
            Column(Modifier.semantics { contentDescription = param.label }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KText(param.label, Modifier.weight(1f))
                    if (binding != null) {
                        KIconButton({ set(param.key, null) }, "${strings.remove} ${param.label}", size = KIconButtonSize.Small, enabled = enabled) { KIcon(KompoundIcons.Close, contentDescription = null) }
                    }
                }
                if (param.description != null) KText(param.description)
                KSegmentedControl(
                    modes, mode,
                    { next ->
                        chosen.value = chosen.value + (param.key to next)
                        set(param.key, convertBinding(binding, next))
                    },
                    Modifier.fillMaxWidth(), enabled = enabled,
                )
                when (mode) {
                    0 -> KFieldPicker(
                        fields, (binding as? FieldBinding.Field)?.path.orEmpty(), { set(param.key, FieldBinding.Field(it)) }, Modifier.fillMaxWidth(),
                        enabled = enabled, types = if (numeric) setOf("integer", "number", "string") else null, contentDescription = param.label,
                    )
                    1 -> KTextField((binding as? FieldBinding.Value)?.text.orEmpty(), { set(param.key, FieldBinding.Value(it)) }, Modifier.fillMaxWidth(), enabled = enabled, contentDescription = param.label)
                    else -> KTemplateField((binding as? FieldBinding.Template)?.template.orEmpty(), { set(param.key, FieldBinding.Template(it)) }, fields, Modifier.fillMaxWidth(), enabled = enabled, contentDescription = param.label)
                }
                bindingPreview(binding, fields)?.let { KText("${strings.resultLabel}: ${it.take(80)}", Modifier.padding(start = 4.dp)) }
            }
        }
    }
}

/** What a binding becomes when the user switches the row to [mode] (0 field, 1 value, 2 template): a field turns into `{{path}}`, a template that is one placeholder into a field. */
internal fun convertBinding(binding: FieldBinding?, mode: Int): FieldBinding? = when (mode) {
    0 -> when (binding) {
        is FieldBinding.Field -> binding
        is FieldBinding.Template -> tech.kloos.kompound.json.Template.parse(binding.template).singleOrNull()
            .let { it as? tech.kloos.kompound.json.TemplatePart.Placeholder }?.takeIf { it.filters.isEmpty() }?.let { FieldBinding.Field(it.path) }
        else -> null
    }
    1 -> when (binding) { is FieldBinding.Value -> binding; else -> null }
    else -> when (binding) {
        is FieldBinding.Template -> binding
        is FieldBinding.Field -> FieldBinding.Template("{{${binding.path}}}")
        is FieldBinding.Value -> FieldBinding.Template(binding.text)
        null -> null
    }
}
