package tech.kloos.kompound.form

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.textfield.KTextFieldDefaults
import tech.kloos.kompound.theme.KompoundTheme

/**
 * A form: its fields in a column, with a status line above them while there is something to say (how many fields need attention after
 * a refused submit, or why the last submit failed). The status is a live region, so screen readers announce it when it appears.
 *
 * @param state The form's [KFormState].
 * @param spacing Space between the children.
 * @param showStatus Show the status line; turn it off to place [KFormStatus] yourself.
 */
@Composable
public fun KForm(
    state: KFormState,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    showStatus: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        if (showStatus) KFormStatus(state)
        content()
    }
}

/** The form's status line: "2 fields need attention" after a refused submit, or the reason a submit failed. Nothing while all is well. */
@Composable
public fun KFormStatus(state: KFormState, modifier: Modifier = Modifier) {
    val strings = KompoundTheme.strings
    val message = when {
        state.submitError != null -> "${strings.submitFailed}: ${state.submitError}"
        state.submitAttempted && state.errorCount > 0 -> strings.formErrors(state.errorCount)
        else -> null
    }
    if (message != null) {
        val color = MaterialTheme.colorScheme.error
        val style = remember(color) { Style { textStyle(androidx.compose.ui.text.TextStyle(color = color)) } }
        KText(message, modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }, style = style)
    }
}

/**
 * A text field bound to a [KFieldState]: value, edits, the error message (when [KFieldState.error] says it is time) and "touched" when
 * focus leaves. A refused submit moves focus here when this is the first invalid field. Everything else is [KTextField]'s.
 *
 * @param helperText Shown under the field while there is no error.
 */
@Composable
public fun KFormTextField(
    field: KFieldState<String>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    style: Style = Style,
) {
    var hadFocus by remember { mutableStateOf(false) }
    val error = field.error
    KTextField(
        value = field.value,
        onValueChange = field::onValueChange,
        modifier = modifier
            .focusRequester(field.focusRequester)
            .onFocusChanged { state ->
                if (hadFocus && !state.isFocused) field.markTouched()
                hadFocus = state.isFocused
            },
        label = label, placeholder = placeholder, supportingText = error ?: helperText, isError = error != null, enabled = enabled,
        singleLine = singleLine, minLines = minLines, maxLength = maxLength, leading = leading, trailing = trailing,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = visualTransformation, style = style,
    )
}

/**
 * Binds any control to a [KFieldState]: a checkbox, a dropdown, a date field, a slider. [content] gets the value, a callback for edits and
 * the message to show; hand them to the control (`isError = error != null`, `supportingText = error`). Where the control has no place for
 * the message, this shows it under the control. Focus returns here after a refused submit, and the field counts as touched when focus leaves.
 *
 * @param showError Show the message under the control; turn it off when the control shows it itself.
 */
@Composable
public fun <T> KFormField(
    field: KFieldState<T>,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    showError: Boolean = true,
    content: @Composable (value: T, onValueChange: (T) -> Unit, error: String?) -> Unit,
) {
    var hadFocus by remember { mutableStateOf(false) }
    val error = field.error
    Column(
        modifier
            .focusRequester(field.focusRequester)
            .onFocusChanged { state ->
                if (hadFocus && !state.hasFocus) field.markTouched()
                hadFocus = state.hasFocus
            }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content(field.value, field::onValueChange, error)
        val below = if (showError) error ?: helperText else helperText
        if (below != null) KText(below, Modifier.padding(horizontal = 16.dp), style = KTextFieldDefaults.supportingStyle(error != null, true))
    }
}

/**
 * A titled section of a form (a group of related fields). The title is a heading for screen readers.
 *
 * @param description Optional line under the title.
 */
@Composable
public fun KFieldGroup(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().focusGroup(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(title, Modifier.semantics { heading() }, style = Style { textStyle(androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) })
            if (description != null) KText(description, style = KTextFieldDefaults.supportingStyle(false, true))
        }
        content()
    }
}

/**
 * The submit button of a form: calls [KFormState.submit] with [onSubmit], shows a busy button with "Submitting" while it runs and cannot be
 * pressed twice. A refused validation keeps the form as it is and moves focus to the first problem.
 *
 * @param text Label; default: the localised "Submit".
 * @param enabled Extra condition (for example a terms checkbox); the button is also disabled while submitting.
 */
@Composable
public fun KSubmitButton(
    state: KFormState,
    onSubmit: suspend (KFormState) -> Unit,
    modifier: Modifier = Modifier,
    text: String = KompoundTheme.strings.submit,
    enabled: Boolean = true,
    variant: KButtonVariant = KButtonVariant.Filled,
) {
    val strings = KompoundTheme.strings
    KButton(
        onClick = { state.submit(onSubmit) },
        modifier = modifier.semantics { if (state.isSubmitting) stateDescription = strings.submitting },
        variant = variant,
        enabled = enabled && !state.isSubmitting,
        loading = state.isSubmitting,
    ) { KText(if (state.isSubmitting) strings.submitting else text) }
}
