package tech.kloos.kompound.search

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.theme.KompoundTheme

/**
 * Search field: a filled pill with a search icon, a clear button that appears while there is text, and
 * the keyboard's Search action. Built on [KTextField], so label-less single-line editing, focus on click
 * and screen-reader behaviour are the same.
 *
 * @param query Current text.
 * @param onQueryChange Called with the new text.
 * @param modifier Modifier applied to the bar.
 * @param onSearch Called with the query when the user presses the keyboard's Search action.
 * @param placeholder Hint shown while empty.
 * @param enabled When false the bar cannot be edited.
 * @param clearContentDescription Accessibility description of the clear button (pass a localised string).
 * @param trailingActions Optional slot after the clear button, e.g. a filter button.
 * @param style Overrides merged over [KSearchBarDefaults.style].
 * @param interactionSource Feeds hovered/focused state into the style.
 */
@Composable
public fun KSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    placeholder: String? = "Search",
    enabled: Boolean = true,
    clearContentDescription: String = "Clear search",
    trailingActions: (@Composable () -> Unit)? = null,
    style: Style = Style,
    interactionSource: MutableInteractionSource? = null,
) {
    KTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        leading = { KIcon(KompoundIcons.Search, contentDescription = null) },
        trailing = if (query.isNotEmpty() || trailingActions != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        KIconButton(onClick = { onQueryChange("") }, contentDescription = clearContentDescription, enabled = enabled) {
                            KIcon(KompoundIcons.Close, contentDescription = null)
                        }
                    }
                    trailingActions?.invoke()
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        style = Style(KSearchBarDefaults.style(), style),
        interactionSource = interactionSource,
    )
}

/** Defaults for [KSearchBar]. */
public object KSearchBarDefaults {
    /** Pill with a `surfaceContainerHigh` fill and no outline; a state layer on hover and a primary outline on focus. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            val fill = c.surfaceContainerHigh
            Style {
                shape(CircleShape)
                background(fill)
                borderWidth(0.dp)
                borderColor(Color.Transparent)
                hovered { background(c.onSurface.copy(alpha = l.hovered).compositeOver(fill)); borderColor(Color.Transparent) }
                focused { borderWidth(2.dp); borderColor(c.primary) }
            }
        }
    }
}
