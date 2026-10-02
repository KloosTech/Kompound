package tech.kloos.kompound.textfield

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * [KTextField] that only accepts a number: digits, an optional leading minus ([allowNegative]) and one
 * decimal point ([allowDecimal]). The value stays a `String` so partial input like `-` or `3.` is valid
 * while typing; use [String.toDoubleOrNull] or [String.toIntOrNull] to read it.
 * All other parameters are those of [KTextField].
 */
@Composable
public fun KNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = false,
    allowNegative: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    maxLength: Int? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    style: Style = Style,
    interactionSource: MutableInteractionSource? = null,
) {
    KTextField(
        value = value,
        onValueChange = { new -> if (isNumberInput(new, allowDecimal, allowNegative)) onValueChange(new) },
        modifier = modifier, label = label, placeholder = placeholder, supportingText = supportingText,
        isError = isError, enabled = enabled, readOnly = readOnly, maxLength = maxLength,
        keyboardOptions = KeyboardOptions(keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number),
        keyboardActions = keyboardActions, style = style, interactionSource = interactionSource,
    )
}

/** True when [text] is a valid in-progress number: empty, `-`, `3`, `-3.`, `.5` ... as allowed. */
internal fun isNumberInput(text: String, allowDecimal: Boolean, allowNegative: Boolean): Boolean {
    var body = text
    if (body.startsWith("-")) {
        if (!allowNegative) return false
        body = body.substring(1)
    }
    var dots = 0
    for (ch in body) {
        when {
            ch.isDigit() -> Unit
            ch == '.' && allowDecimal -> if (++dots > 1) return false
            else -> return false
        }
    }
    return true
}
