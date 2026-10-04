package tech.kloos.kompound.date

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.material3.DatePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.PickerField

/**
 * Field that shows a date and opens a calendar dialog to change it. The calendar logic is Material 3's
 * `DatePicker` (themed through the M3 theme). Dates are epoch milliseconds at UTC midnight, which is what
 * the M3 picker produces and consumes, so no time zone conversion is involved.
 *
 * @param value Selected date in epoch milliseconds (UTC midnight), or `null` for none.
 * @param onValueChange Called with the chosen date when the user confirms.
 * @param modifier Modifier applied to the whole field.
 * @param label Optional label above the field; also its accessibility description.
 * @param placeholder Text shown while no date is selected.
 * @param supportingText Help or error message below the field.
 * @param isError Marks the field invalid (error colours).
 * @param enabled When false the dialog cannot be opened.
 * @param formatDate Text of a date in the field; defaults to ISO `yyyy-MM-dd` ([KDateFormat.iso]).
 * @param confirmText Label of the dialog's confirm button (pass a localised string).
 * @param dismissText Label of the dialog's cancel button (pass a localised string).
 * @param style Overrides merged over the text field container style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun KDateField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    formatDate: (Long) -> String = KDateFormat::iso,
    confirmText: String = KompoundTheme.strings.ok,
    dismissText: String = KompoundTheme.strings.cancel,
    style: Style = Style,
) {
    var open by remember { mutableStateOf(false) }
    PickerField(
        modifier = modifier, label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, enabled = enabled, style = style,
        displayText = value?.let(formatDate).orEmpty(),
        open = open, onClick = { open = true }, icon = KompoundIcons.Calendar, role = Role.Button, rotateIconWhenOpen = false,
    ) {
        if (open) {
            val state = rememberDatePickerState(initialSelectedDateMillis = value)
            DatePickerDialog(
                onDismissRequest = { open = false },
                confirmButton = { DialogButton(confirmText) { onValueChange(state.selectedDateMillis); open = false } },
                dismissButton = { DialogButton(dismissText) { open = false } },
            ) { DatePicker(state = state) }
        }
    }
}

/**
 * Like [KDateField] for a range of dates: a calendar dialog where the user picks a start and an end.
 *
 * @param start Start of the range in epoch milliseconds (UTC midnight), or `null`.
 * @param end End of the range, or `null`.
 * @param onRangeChange Called with the chosen start and end when the user confirms.
 * @param rangeSeparator Text between the two dates in the field.
 * @see KDateField
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun KDateRangeField(
    start: Long?,
    end: Long?,
    onRangeChange: (start: Long?, end: Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    formatDate: (Long) -> String = KDateFormat::iso,
    rangeSeparator: String = " – ",
    confirmText: String = KompoundTheme.strings.ok,
    dismissText: String = KompoundTheme.strings.cancel,
    style: Style = Style,
) {
    var open by remember { mutableStateOf(false) }
    val display = when {
        start == null -> ""
        end == null -> formatDate(start)
        else -> formatDate(start) + rangeSeparator + formatDate(end)
    }
    PickerField(
        modifier = modifier, label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, enabled = enabled, style = style,
        displayText = display,
        open = open, onClick = { open = true }, icon = KompoundIcons.Calendar, role = Role.Button, rotateIconWhenOpen = false,
    ) {
        if (open) {
            val state = rememberDateRangePickerState(initialSelectedStartDateMillis = start, initialSelectedEndDateMillis = end)
            DatePickerDialog(
                onDismissRequest = { open = false },
                confirmButton = { DialogButton(confirmText) { onRangeChange(state.selectedStartDateMillis, state.selectedEndDateMillis); open = false } },
                dismissButton = { DialogButton(dismissText) { open = false } },
            ) {
                // The calendar is a lazy list: without a bounded height it runs under the dialog buttons.
                val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
                DateRangePicker(
                    state = state,
                    modifier = Modifier.fillMaxWidth().height((windowHeight - 200.dp).coerceIn(320.dp, 480.dp)),
                    title = {
                        DateRangePickerDefaults.DateRangePickerTitle(
                            displayMode = state.displayMode,
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 20.dp),
                        )
                    },
                    headline = {
                        // The dialog is its own window: it does not inherit the theme's root text style.
                        val headlineStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                        // One line, ellipsised, instead of M3's wrapping "26.10.20 / 26" headline.
                        KText(
                            (state.selectedStartDateMillis?.let(formatDate) ?: "…") + rangeSeparator +
                                (state.selectedEndDateMillis?.let(formatDate) ?: "…"),
                            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = Style { textStyle(headlineStyle) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DialogButton(text: String, onClick: () -> Unit) {
    KButton(onClick = onClick, variant = KButtonVariant.Text) { KText(text) }
}
