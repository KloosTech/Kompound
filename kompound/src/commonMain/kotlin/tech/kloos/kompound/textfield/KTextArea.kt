package tech.kloos.kompound.textfield

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Multi-line [KTextField]: wraps text and grows from [minLines] to [maxLines] before scrolling.
 * All other parameters are those of [KTextField].
 */
@Composable
public fun KTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    minLines: Int = 3,
    maxLines: Int = 8,
    maxLength: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    style: Style = Style,
    interactionSource: MutableInteractionSource? = null,
    contentDescription: String? = null,
) {
    KTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, label = label, placeholder = placeholder,
        supportingText = supportingText, isError = isError, enabled = enabled, readOnly = readOnly, singleLine = false,
        minLines = minLines, maxLines = maxLines, maxLength = maxLength, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, style = style, interactionSource = interactionSource, contentDescription = contentDescription,
    )
}
