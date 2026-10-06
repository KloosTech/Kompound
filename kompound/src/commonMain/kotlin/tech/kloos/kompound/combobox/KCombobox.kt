package tech.kloos.kompound.combobox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.search.KFuzzy
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.theme.KompoundTheme

/**
 * A text field with a list of suggestions that narrows as you type. Typing filters [options] (fuzzy, best match first); Down and Up move through
 * the list, Enter picks the highlighted option, Escape closes it, and clicking an option picks it. Focus stays in the field the whole time.
 *
 * The text is yours ([value], [onValueChange]). Picking an option writes its label into the text (through [onValueChange]) and then calls
 * [onOptionSelected]; keep the chosen option in your own state. For free text, read [value] (or pass [onSubmit] to hear Enter when no option is highlighted).
 * For a server-side search set [filterOptions] to `false` and put the results in [options]: they are shown as given.
 *
 * Screen readers hear whether the list is open and which option is highlighted.
 *
 * @param value The text in the field.
 * @param onValueChange Called when the user types or picks.
 * @param options The choices.
 * @param onOptionSelected Called with the picked option.
 * @param optionLabel Text of an option (shown in the list, written into the field when picked, matched against the query).
 * @param label Label above the field.
 * @param placeholder Hint while the field is empty.
 * @param supportingText Help or error message below the field.
 * @param isError Marks the field invalid.
 * @param enabled When false the field cannot be edited.
 * @param optionSupportingText A second line for an option (a description, an e-mail address).
 * @param filterOptions Narrow and rank [options] by the text (fuzzy). Turn off when the list is already filtered by you.
 * @param maxOptions At most this many options are listed.
 * @param noResultsText Shown when nothing matches; `null` shows no list then.
 * @param onSubmit Called with the text when Enter is pressed and no option is highlighted.
 * @param contentDescription The accessibility name when there is no visible [label]; default: the label.
 * @param style Overrides merged over the field container style.
 */
@Composable
public fun <T> KCombobox(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<T>,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    optionSupportingText: ((T) -> String?)? = null,
    filterOptions: Boolean = true,
    maxOptions: Int = 100,
    noResultsText: String? = KompoundTheme.strings.noResults,
    onSubmit: ((String) -> Unit)? = null,
    contentDescription: String? = null,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val strings = KompoundTheme.strings
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var active by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableIntStateOf(0) }
    val shown = remember(options, value, filterOptions, maxOptions) {
        (if (filterOptions) KFuzzy.filter(options, value, optionLabel) else options).take(maxOptions)
    }
    val expanded = enabled && focused && !dismissed
    val activeIndex = active.coerceIn(0, (shown.size - 1).coerceAtLeast(0))

    fun pick(option: T) {
        onValueChange(optionLabel(option))
        onOptionSelected(option)
        dismissed = true
    }

    Box(
        modifier
            .onSizeChanged { widthPx = it.width }
            .semantics {
                stateDescription = if (expanded && shown.isNotEmpty()) "${strings.expanded}: ${optionLabel(shown[activeIndex])}" else if (expanded) strings.expanded else strings.collapsed
            },
    ) {
        KTextField(
            value = value,
            onValueChange = { active = 0; dismissed = false; onValueChange(it) },
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) dismissed = false }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || !enabled) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { if (!expanded) dismissed = false else active = (activeIndex + 1).coerceAtMost((shown.size - 1).coerceAtLeast(0)); true }
                        Key.DirectionUp -> if (expanded) { active = (activeIndex - 1).coerceAtLeast(0); true } else false
                        Key.Enter, Key.NumPadEnter -> when {
                            expanded && shown.isNotEmpty() -> { pick(shown[activeIndex]); true }
                            onSubmit != null -> { onSubmit(value); true }
                            else -> false
                        }
                        Key.Escape -> if (expanded) { dismissed = true; true } else false
                        else -> false
                    }
                },
            label = label, placeholder = placeholder, supportingText = supportingText, isError = isError, enabled = enabled,
            trailing = { KIcon(KompoundIcons.ChevronDown, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke(value) }),
            contentDescription = contentDescription,
            style = style,
        )
        if (expanded && (shown.isNotEmpty() || (noResultsText != null && value.isNotEmpty()))) {
            val width = with(LocalDensity.current) { widthPx.toDp() }
            OptionsPopup(
                rows = shown.map { OptionRow(optionLabel(it), optionSupportingText?.invoke(it)) },
                active = activeIndex, width = width, emptyText = noResultsText,
                onPick = { pick(shown[it]) }, onDismiss = { dismissed = true },
            )
        }
    }
}
