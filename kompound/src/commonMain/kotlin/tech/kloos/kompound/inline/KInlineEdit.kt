package tech.kloos.kompound.inline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.textfield.KTextFieldDefaults
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Text that becomes an editable field when clicked. Press Enter (single-line) or the check button to
 * save, Escape or the close button to cancel. There is no save-on-blur: moving focus to the buttons
 * would otherwise save before a cancel click could register. [onValueChange] is only called when the
 * text actually changed and passes [validate].
 *
 * @param value The saved text shown in view mode.
 * @param onValueChange Called with the new text when the user saves a changed, valid value.
 * @param modifier Modifier applied to the outermost node.
 * @param placeholder Shown in view mode while [value] is empty, and in the field while editing.
 * @param enabled When false the text cannot be edited.
 * @param singleLine One line (Enter saves) or multi-line (Enter inserts a new line; use the check button).
 * @param keyboardOptions Soft keyboard options of the editing field, e.g. a number keyboard.
 * @param validate Returns an error message for text that must not be saved, or `null` when it is fine.
 * @param editContentDescription Accessibility description of the view-mode button (pass a localised string).
 * @param saveContentDescription Accessibility description of the save button.
 * @param cancelContentDescription Accessibility description of the cancel button.
 * @param style Overrides merged over [KInlineEditDefaults.style] (the view-mode row).
 */
@Composable
public fun KInlineEdit(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    validate: (String) -> String? = { null },
    editContentDescription: String = "Edit",
    saveContentDescription: String = "Save",
    cancelContentDescription: String = "Cancel",
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun save() {
        val problem = validate(draft)
        if (problem != null) { error = problem; return }
        if (draft != value) onValueChange(draft)
        editing = false
    }
    fun cancel() { editing = false; error = null }

    if (!editing) {
        val source = remember { MutableInteractionSource() }
        val state = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
        Row(
            modifier = modifier
                .semantics { contentDescription = "$editContentDescription ${value.ifEmpty { placeholder }}".trim() }
                .hoverable(source, enabled)
                .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button) {
                    draft = value; error = null; editing = true
                }
                .styleable(state, KInlineEditDefaults.style(), style),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (value.isEmpty()) KText(placeholder, style = KTextFieldDefaults.placeholderStyle(enabled), maxLines = 1, overflow = TextOverflow.Ellipsis)
            else KText(value)
            CompositionLocalProvider(LocalKContentColor provides KInlineEditDefaults.iconColor(enabled)) {
                KIcon(KompoundIcons.Edit, contentDescription = null)
            }
        }
    } else {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) { cancel(); true } else false
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            KTextField(
                value = draft,
                onValueChange = { draft = it; error = null },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = placeholder.ifEmpty { null },
                supportingText = error,
                isError = error != null,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions.copy(imeAction = if (singleLine) ImeAction.Done else keyboardOptions.imeAction),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            KIconButton(onClick = { save() }, contentDescription = saveContentDescription, variant = KButtonVariant.Text) {
                KIcon(KompoundIcons.Check, contentDescription = null)
            }
            KIconButton(onClick = { cancel() }, contentDescription = cancelContentDescription, variant = KButtonVariant.Text) {
                KIcon(KompoundIcons.Close, contentDescription = null)
            }
        }
    }
}

/** Defaults for [KInlineEdit]. */
public object KInlineEditDefaults {
    /** View-mode row: transparent, rounded, body text, with hover/focus/press layers to hint that it is editable. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, shapes, type, l) {
            fun layer(alpha: Float) = c.onSurface.copy(alpha = alpha).compositeOver(Color.Transparent)
            Style {
                background(Color.Transparent)
                contentColor(c.onSurface)
                textStyle(type.bodyLarge.copy(color = c.onSurface))
                shape(shapes.small)
                contentPadding(horizontal = 8.dp, vertical = 4.dp)
                minHeight(32.dp)
                hovered { background(layer(l.hovered)) }
                focused { background(layer(l.focused)) }
                pressed { background(layer(l.pressed)) }
                disabled { contentColor(c.onSurface.copy(alpha = l.disabledContent)); textStyle(type.bodyLarge.copy(color = c.onSurface.copy(alpha = l.disabledContent))) }
            }
        }
    }

    /** Colour of the pencil icon. */
    @Composable
    public fun iconColor(enabled: Boolean = true): Color {
        val c = MaterialTheme.colorScheme
        return if (enabled) c.onSurfaceVariant else c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
    }
}
