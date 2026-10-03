package tech.kloos.kompound.textfield

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleStateKey
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.state
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/** Custom Style state: whether the field is in its error state. Used by the `error` style block. */
internal val ErrorKey: StyleStateKey<Boolean> = StyleStateKey(false)

/**
 * Outlined text field. The label sits above the field (no floating-label animation), the container
 * shows hovered, focused, error and disabled looks from its Style, and clicking anywhere in the
 * container focuses the text.
 *
 * @param value Current text.
 * @param onValueChange Called with the new text; with [maxLength] set, over-long input is cut to the limit.
 * @param modifier Modifier applied to the whole field (label, container and supporting text).
 * @param label Optional label above the field; also its accessibility description.
 * @param placeholder Hint shown while the field is empty.
 * @param supportingText Help or error message below the field.
 * @param isError Marks the field invalid: error colours, and the error is announced by screen readers.
 * @param enabled When false the field cannot be focused or edited and uses the disabled style block.
 * @param readOnly When true the text can be focused and selected but not edited.
 * @param singleLine One line (scrolls horizontally) or wrapping multi-line text.
 * @param minLines Minimum visible lines when not [singleLine].
 * @param maxLines Maximum visible lines before the field scrolls.
 * @param maxLength Optional character limit; shows a counter next to the supporting text.
 * @param leading Slot before the text, usually a `KIcon`.
 * @param trailing Slot after the text, e.g. a clear button.
 * @param keyboardOptions Soft keyboard type and IME action.
 * @param keyboardActions Reactions to IME actions.
 * @param visualTransformation e.g. password masking.
 * @param style Overrides merged over [KTextFieldDefaults.style] (the container).
 * @param interactionSource Feeds hovered/focused state into the style.
 */
@Composable
public fun KTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    maxLength: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    style: Style = Style,
    interactionSource: MutableInteractionSource? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.set(ErrorKey, isError)
    }
    val focusRequester = remember { FocusRequester() }
    val scheme = MaterialTheme.colorScheme
    val iconColor = KTextFieldDefaults.iconColor(isError, enabled)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) KText(label, style = KTextFieldDefaults.labelStyle(isError, enabled))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(enabled) { if (enabled) detectTapGestures { focusRequester.requestFocus() } }
                .styleable(state, KTextFieldDefaults.style(), style),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompositionLocalProvider(LocalKContentColor provides iconColor) {
                leading?.invoke()
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = value,
                        onValueChange = { new -> onValueChange(if (maxLength != null) new.take(maxLength) else new) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .semantics {
                                if (label != null) contentDescription = label
                                if (isError) error(supportingText ?: "Invalid input")
                            },
                        enabled = enabled,
                        readOnly = readOnly,
                        textStyle = KTextFieldDefaults.textStyle(enabled),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        singleLine = singleLine,
                        minLines = minLines,
                        maxLines = maxLines,
                        visualTransformation = visualTransformation,
                        interactionSource = source,
                        cursorBrush = SolidColor(if (isError) scheme.error else scheme.primary),
                        decorationBox = { inner ->
                            // Centred so the placeholder lines up with the text and the caret even when their line boxes differ in height.
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.isEmpty() && placeholder != null) {
                                    KText(placeholder, style = KTextFieldDefaults.placeholderStyle(enabled), maxLines = 1)
                                }
                                inner()
                            }
                        },
                    )
                }
                trailing?.invoke()
            }
        }
        if (supportingText != null || maxLength != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KText(supportingText.orEmpty(), Modifier.weight(1f), style = KTextFieldDefaults.supportingStyle(isError, enabled))
                if (maxLength != null) {
                    KText("${value.length}/$maxLength", style = KTextFieldDefaults.supportingStyle(isError = false, enabled = enabled))
                }
            }
        }
    }
}

/** Defaults for [KTextField], [KTextArea], [KNumberField] and the other text inputs. */
public object KTextFieldDefaults {
    /** Container: 56dp outlined box; thicker `primary` outline when focused, `error` outline when invalid. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, shapes, type, l) {
            Style {
                background(Color.Transparent)
                contentColor(c.onSurface)
                textStyle(type.bodyLarge.copy(color = c.onSurface))
                shape(shapes.small)
                borderWidth(1.dp)
                borderColor(c.outline)
                contentPadding(horizontal = 16.dp, vertical = 8.dp)
                minHeight(56.dp)
                hovered { borderColor(c.onSurface) }
                focused { borderWidth(2.dp); borderColor(c.primary) }
                state(ErrorKey) {
                    borderColor(c.error)
                    hovered { borderColor(c.onErrorContainer) }
                    focused { borderWidth(2.dp); borderColor(c.error) }
                }
                disabled {
                    borderColor(c.onSurface.copy(alpha = l.disabledContainer))
                    contentColor(c.onSurface.copy(alpha = l.disabledContent))
                }
            }
        }
    }

    /** Text style of the typed text; the field cannot inherit it from the Style, so it is passed explicitly. */
    @Composable
    public fun textStyle(enabled: Boolean = true): androidx.compose.ui.text.TextStyle {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return MaterialTheme.typography.bodyLarge.copy(color = if (enabled) c.onSurface else c.onSurface.copy(alpha = l.disabledContent))
    }

    /** Colour of leading and trailing icons for the current state; icons read it through `LocalKContentColor`. */
    @Composable
    public fun iconColor(isError: Boolean, enabled: Boolean = true): Color {
        val c = MaterialTheme.colorScheme
        return when {
            !enabled -> c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
            isError -> c.error
            else -> c.onSurfaceVariant
        }
    }

    /** Style of the label above the field. */
    @Composable
    public fun labelStyle(isError: Boolean = false, enabled: Boolean = true): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val color = when {
            !enabled -> c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
            isError -> c.error
            else -> c.onSurfaceVariant
        }
        return remember(color, type) { Style { contentColor(color); textStyle(type.labelMedium.copy(color = color)) } }
    }

    /** Style of the placeholder text. */
    @Composable
    public fun placeholderStyle(enabled: Boolean = true): Style {
        val c = MaterialTheme.colorScheme
        val color = if (enabled) c.onSurfaceVariant else c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
        return remember(color) { Style { contentColor(color) } }
    }

    /** Style of the supporting text and counter below the field. */
    @Composable
    public fun supportingStyle(isError: Boolean, enabled: Boolean = true): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val color = when {
            !enabled -> c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
            isError -> c.error
            else -> c.onSurfaceVariant
        }
        return remember(color, type) { Style { contentColor(color); textStyle(type.bodySmall.copy(color = color)) } }
    }
}
