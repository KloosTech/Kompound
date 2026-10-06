package tech.kloos.kompound.template

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.combobox.OptionRow
import tech.kloos.kompound.combobox.OptionsPopup
import tech.kloos.kompound.json.FieldInfo
import tech.kloos.kompound.json.Template
import tech.kloos.kompound.json.TemplatePart
import tech.kloos.kompound.search.KFuzzy
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.ErrorKey
import tech.kloos.kompound.textfield.KTextFieldDefaults
import tech.kloos.kompound.theme.KompoundTheme

/**
 * A single-line text field that knows `{{placeholders}}` (see [Template]). Typing `{{` opens a list of the [fields] the text may use, with each
 * field's type and an example value; Down and Up move, Enter or Tab (or a click) put the path in and close the braces, Escape closes the list.
 * Ctrl+Space inserts `{{` and opens it. Placeholders are drawn highlighted; one that names a field that does not exist, and any invalid
 * placeholder, is underlined and explained under the field before anything runs.
 *
 * The check for unknown fields only runs while [fields] is not empty: when nothing is known about the data yet, every path is accepted.
 *
 * @param value The text, with its placeholders.
 * @param onValueChange Called with the new text.
 * @param fields The fields a placeholder may read (what `GraphEngine.fieldsOf` returns).
 * @param label Label above the field.
 * @param placeholder Hint while empty.
 * @param supportingText Help below the field; problems with placeholders replace it.
 * @param contentDescription The accessibility name when there is no visible [label]; default: the label.
 * @param isKnown More paths that are fine though they are not in [fields] (variables such as `env.HOST`, or the same fields behind a port prefix).
 */
@Composable
public fun KTemplateField(
    value: String,
    onValueChange: (String) -> Unit,
    fields: List<FieldInfo>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isKnown: (String) -> Boolean = { false },
    contentDescription: String? = null,
    style: Style = Style,
) {
    TemplateInput(value, onValueChange, fields, modifier, label, placeholder, supportingText, enabled, isError, true, 1, 1, keyboardOptions, isKnown, contentDescription, style)
}

/** [KTemplateField] for longer texts (a request body, a prompt, a script): wraps and grows from [minLines] to [maxLines]. */
@Composable
public fun KTemplateArea(
    value: String,
    onValueChange: (String) -> Unit,
    fields: List<FieldInfo>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    minLines: Int = 3,
    maxLines: Int = 8,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isKnown: (String) -> Boolean = { false },
    contentDescription: String? = null,
    style: Style = Style,
) {
    TemplateInput(value, onValueChange, fields, modifier, label, placeholder, supportingText, enabled, isError, false, minLines, maxLines, keyboardOptions, isKnown, contentDescription, style)
}

private class TemplateHighlight(
    private val knownPaths: Set<String>,
    private val checkUnknown: Boolean,
    private val isKnown: (String) -> Boolean,
    private val ok: SpanStyle,
    private val bad: SpanStyle,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val built = buildAnnotatedString {
            append(text.text)
            for (part in Template.parse(text.text)) when (part) {
                is TemplatePart.Placeholder -> addStyle(if (!checkUnknown || fieldKnown(part.path, knownPaths) || isKnown(part.path)) ok else bad, part.start, part.end)
                is TemplatePart.Invalid -> addStyle(bad, part.start, part.end)
                is TemplatePart.Literal -> {}
            }
        }
        return TransformedText(built, OffsetMapping.Identity)
    }
}

@Composable
private fun TemplateInput(
    value: String,
    onValueChange: (String) -> Unit,
    fields: List<FieldInfo>,
    modifier: Modifier,
    label: String?,
    placeholder: String?,
    supportingText: String?,
    enabled: Boolean,
    isError: Boolean,
    singleLine: Boolean,
    minLines: Int,
    maxLines: Int,
    keyboardOptions: KeyboardOptions,
    isKnown: (String) -> Boolean,
    description: String?,
    style: Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val strings = KompoundTheme.strings
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    var edited by remember { mutableStateOf(TextFieldValue(value)) }
    val field = if (edited.text == value) edited else TextFieldValue(value, TextRange(minOf(edited.selection.end, value.length)))
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var active by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    val knownPaths = remember(fields) { fields.mapTo(HashSet()) { it.path } }
    val checkUnknown = fields.isNotEmpty()
    val problems = remember(value, knownPaths, isKnown) {
        buildList {
            for (part in Template.parse(value)) when (part) {
                is TemplatePart.Placeholder -> if (checkUnknown && !fieldKnown(part.path, knownPaths) && !isKnown(part.path)) add(strings.unknownField(part.path))
                is TemplatePart.Invalid -> add(part.message)
                is TemplatePart.Literal -> {}
            }
        }.distinct()
    }
    val completion = if (enabled && focused) templateCompletionAt(field.text, field.selection.end) else null
    val matches = remember(completion?.query, fields) { if (completion == null) emptyList() else KFuzzy.filter(fields, completion.query) { it.path }.take(8) }
    val open = completion != null && !dismissed && matches.isNotEmpty()
    val activeIndex = active.coerceIn(0, (matches.size - 1).coerceAtLeast(0))
    val failed = isError || problems.isNotEmpty()
    val shownSupport = problems.takeIf { it.isNotEmpty() }?.take(3)?.joinToString("; ") ?: supportingText ?: if (completion != null && fields.isEmpty()) strings.noFieldsKnown else null
    val state = rememberUpdatedStyleState(source) { it.isEnabled = enabled; it.set(ErrorKey, failed) }
    val okSpan = remember(scheme) { SpanStyle(color = scheme.primary, fontWeight = FontWeight.SemiBold, background = scheme.primary.copy(alpha = 0.12f)) }
    val badSpan = remember(scheme) { SpanStyle(color = scheme.error, textDecoration = TextDecoration.Underline, background = scheme.error.copy(alpha = 0.12f)) }
    val transformation = remember(knownPaths, checkUnknown, isKnown, okSpan, badSpan) { TemplateHighlight(knownPaths, checkUnknown, isKnown, okSpan, badSpan) }

    fun change(next: TextFieldValue) {
        if (next.text != field.text) { dismissed = false; active = 0 }
        edited = next
        if (next.text != value) onValueChange(next.text)
    }
    fun pick(f: FieldInfo) {
        val c = completion ?: return
        val (text, caret) = applyTemplateCompletion(field.text, c, f.path)
        dismissed = true
        change(TextFieldValue(text, TextRange(caret)))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) KText(label, style = KTextFieldDefaults.labelStyle(failed, enabled))
        Box(Modifier.onSizeChanged { widthPx = it.width }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .styleable(state, KTextFieldDefaults.style(), style),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                BasicTextField(
                    value = field,
                    onValueChange = ::change,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged { focused = it.isFocused; if (it.isFocused) dismissed = false }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown || !enabled) return@onPreviewKeyEvent false
                            when {
                                e.isCtrlPressed && e.key == Key.Spacebar -> {
                                    if (completion == null) {
                                        val sel = field.selection
                                        val text = field.text.substring(0, sel.min) + "{{" + field.text.substring(sel.max)
                                        change(TextFieldValue(text, TextRange(sel.min + 2)))
                                    } else dismissed = false
                                    true
                                }
                                !open -> false
                                e.key == Key.DirectionDown -> { active = (activeIndex + 1).coerceAtMost(matches.lastIndex); true }
                                e.key == Key.DirectionUp -> { active = (activeIndex - 1).coerceAtLeast(0); true }
                                e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.Tab -> { pick(matches[activeIndex]); true }
                                e.key == Key.Escape -> { dismissed = true; true }
                                else -> false
                            }
                        }
                        .semantics {
                            (description ?: label)?.let { contentDescription = it }
                            if (failed) error(shownSupport ?: "Invalid input")
                        },
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    textStyle = KTextFieldDefaults.textStyle(enabled),
                    cursorBrush = SolidColor(if (failed) scheme.error else scheme.primary),
                    keyboardOptions = keyboardOptions,
                    visualTransformation = transformation,
                    interactionSource = source,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (field.text.isEmpty() && placeholder != null) KText(placeholder, style = KTextFieldDefaults.placeholderStyle(enabled), maxLines = 1)
                            inner()
                        }
                    },
                )
            }
            if (open) {
                OptionsPopup(
                    rows = matches.map { f -> OptionRow(f.path, listOfNotNull(f.type, f.example?.let { ex -> ex.toJson().take(40) }).joinToString(" · ")) },
                    active = activeIndex, width = with(LocalDensity.current) { widthPx.toDp() }, emptyText = null,
                    onPick = { pick(matches[it]) }, onDismiss = { dismissed = true },
                )
            }
        }
        if (shownSupport != null) KText(shownSupport, Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = KTextFieldDefaults.supportingStyle(failed, enabled))
    }
}
