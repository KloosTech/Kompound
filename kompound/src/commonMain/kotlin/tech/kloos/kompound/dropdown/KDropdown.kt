package tech.kloos.kompound.dropdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.menu.KMenu
import tech.kloos.kompound.menu.KMenuItem
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.ErrorKey
import tech.kloos.kompound.textfield.KTextFieldDefaults
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Dropdown for choosing one option. Looks like [tech.kloos.kompound.textfield.KTextField] with a chevron;
 * clicking opens a [KMenu] whose current choice is marked. Announced as a dropdown list.
 *
 * @param options Choices, in display order.
 * @param selected The current choice, or `null` for none (the [placeholder] is shown).
 * @param onSelect Called with the chosen option; the menu closes afterwards.
 * @param modifier Modifier applied to the whole field.
 * @param optionLabel Text of an option.
 * @param label Optional label above the field; also its accessibility description.
 * @param placeholder Text shown while nothing is selected.
 * @param supportingText Help or error message below the field.
 * @param isError Marks the field invalid (error colours).
 * @param enabled When false the dropdown cannot be opened.
 * @param style Overrides merged over [KDropdownDefaults.style] (the field container).
 * @param menuStyle Overrides merged over the menu's default style.
 */
@Composable
public fun <T> KDropdown(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    style: Style = Style,
    menuStyle: Style = Style,
) {
    var expanded by remember { mutableStateOf(false) }
    DropdownField(
        modifier = modifier, label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, enabled = enabled, style = style, menuStyle = menuStyle,
        displayText = selected?.let(optionLabel).orEmpty(),
        expanded = expanded, onExpandedChange = { expanded = it },
    ) {
        options.forEach { option ->
            KMenuItem(
                text = optionLabel(option),
                onClick = { onSelect(option); expanded = false },
                selected = option == selected, showCheck = true, role = Role.RadioButton,
            )
        }
    }
}

/**
 * Dropdown for choosing several options. The menu stays open while the user toggles items; the field shows
 * a summary of the choices.
 *
 * @param selected The currently chosen options.
 * @param onSelectionChange Called with the new set whenever an item is toggled.
 * @param summary Text of the field for the chosen option labels; defaults to a comma separated list.
 * @see KDropdown
 */
@Composable
public fun <T> KMultiDropdown(
    options: List<T>,
    selected: Set<T>,
    onSelectionChange: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    summary: (List<String>) -> String = { it.joinToString(", ") },
    style: Style = Style,
    menuStyle: Style = Style,
) {
    var expanded by remember { mutableStateOf(false) }
    DropdownField(
        modifier = modifier, label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, enabled = enabled, style = style, menuStyle = menuStyle,
        displayText = if (selected.isEmpty()) "" else summary(options.filter { it in selected }.map(optionLabel)),
        expanded = expanded, onExpandedChange = { expanded = it },
    ) {
        options.forEach { option ->
            val isSelected = option in selected
            KMenuItem(
                text = optionLabel(option),
                onClick = { onSelectionChange(if (isSelected) selected - option else selected + option) },
                selected = isSelected, showCheck = true, role = Role.Checkbox,
            )
        }
    }
}

@Composable
private fun DropdownField(
    modifier: Modifier,
    label: String?,
    placeholder: String?,
    supportingText: String?,
    isError: Boolean,
    enabled: Boolean,
    style: Style,
    menuStyle: Style,
    displayText: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.isSelected = expanded
        it.set(ErrorKey, isError)
    }
    val density = LocalDensity.current
    var triggerWidth by remember { mutableStateOf(0) }
    val iconColor = KTextFieldDefaults.iconColor(isError, enabled)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) KText(label, style = KTextFieldDefaults.labelStyle(isError, enabled))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { triggerWidth = it.width }
                    .semantics { if (label != null) contentDescription = label }
                    .hoverable(source, enabled)
                    .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.DropdownList) { onExpandedChange(!expanded) }
                    .styleable(state, KDropdownDefaults.style(), style),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (displayText.isEmpty() && placeholder != null) {
                    KText(placeholder, Modifier.weight(1f), style = KTextFieldDefaults.placeholderStyle(enabled), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    KText(displayText, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CompositionLocalProvider(LocalKContentColor provides iconColor) {
                    KIcon(KompoundIcons.ChevronDown, contentDescription = null, Modifier.rotate(if (expanded) 180f else 0f))
                }
            }
            KMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                minWidth = with(density) { triggerWidth.toDp() },
                style = menuStyle,
                content = menuContent,
            )
        }
        if (supportingText != null) {
            KText(supportingText, Modifier.padding(horizontal = 16.dp), style = KTextFieldDefaults.supportingStyle(isError, enabled))
        }
    }
}

/** Defaults for [KDropdown] and [KMultiDropdown]. */
public object KDropdownDefaults {
    /** The text field container look, with the focused (primary, thick) outline kept while the menu is open. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val base = KTextFieldDefaults.style()
        return remember(base, c) { Style(base, Style { selected { borderWidth(2.dp); borderColor(c.primary) } }) }
    }
}
