package tech.kloos.kompound.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * One row of a list: optional [leading] content, an [overline], the [headline], [supporting] text and a
 * [bottom] slot in the middle, and [trailing] content. Clickable when [onClick] is set; with [selected]
 * it becomes a selectable row (radio semantics unless [role] says otherwise).
 *
 * Text in the slots inherits sensible styles (overline and supporting text are smaller and quieter), so
 * plain `KText` is enough. Icons in [leading] and [trailing] pick up the quiet content colour.
 *
 * @param headline Main text of the row, usually a `KText`.
 * @param modifier Modifier applied to the whole row.
 * @param supporting Optional secondary text below the headline.
 * @param overline Optional small text above the headline.
 * @param leading Optional content before the text: an icon, avatar or checkbox.
 * @param trailing Optional content after the text: a badge, switch or chevron.
 * @param bottom Optional content below the supporting text, e.g. a row of chips.
 * @param onClick Makes the row clickable; `null` means a static row.
 * @param selected `null` for a normal row; `true`/`false` makes it selectable and shows the selected tint.
 * @param enabled When false the row is dimmed and ignores input.
 * @param role Semantic role override; defaults to button (clickable) or radio button (selectable).
 * @param style Overrides merged over [KListItemDefaults.style].
 * @param interactionSource Feeds pressed/hovered/focused state into the style.
 */
@Composable
public fun KListItem(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    overline: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    role: Role? = null,
    style: Style = Style,
    interactionSource: MutableInteractionSource? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.isSelected = selected == true
    }
    val interactive = onClick != null
    var behaviour: Modifier = Modifier.hoverable(source, enabled && interactive)
    if (onClick != null) {
        behaviour = behaviour.then(
            if (selected != null) {
                Modifier.selectable(selected, source, null, enabled, role ?: Role.RadioButton, onClick)
            } else {
                Modifier.clickable(interactionSource = source, indication = null, enabled = enabled, role = role ?: Role.Button, onClick = onClick)
            },
        )
    }
    val quiet = KListItemDefaults.quietColor(enabled)
    val overlineState = remember { MutableStyleState(null) }
    val supportingState = remember { MutableStyleState(null) }
    Row(
        modifier = modifier.fillMaxWidth().then(behaviour).styleable(state, KListItemDefaults.style(interactive, twoLine = supporting != null), style),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) CompositionLocalProvider(LocalKContentColor provides quiet) { leading() }
        Column(Modifier.weight(1f)) {
            if (overline != null) Box(Modifier.styleable(overlineState, KListItemDefaults.overlineStyle(enabled))) { overline() }
            headline()
            if (supporting != null) Box(Modifier.styleable(supportingState, KListItemDefaults.supportingStyle(enabled))) { supporting() }
            if (bottom != null) bottom()
        }
        if (trailing != null) CompositionLocalProvider(LocalKContentColor provides quiet) { trailing() }
    }
}

/** Convenience for the common text-only row. */
@Composable
public fun KListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    overline: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    role: Role? = null,
    style: Style = Style,
    interactionSource: MutableInteractionSource? = null,
) {
    KListItem(
        headline = { KText(headline, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        supporting = supporting?.let { { KText(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        overline = overline?.let { { KText(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leading = leading, trailing = trailing, bottom = bottom, onClick = onClick, selected = selected,
        enabled = enabled, role = role, style = style, interactionSource = interactionSource,
    )
}

/** Defaults for [KListItem]. */
public object KListItemDefaults {
    /**
     * Row style: 56dp minimum (72dp when [twoLine], i.e. with supporting text), body text, interaction layers
     * when [interactive], tinted when selected.
     */
    @Composable
    public fun style(interactive: Boolean = false, twoLine: Boolean = false): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, type, l, interactive, twoLine) {
            fun layer(alpha: Float, content: Color, over: Color) = content.copy(alpha = alpha).compositeOver(over)
            Style {
                background(Color.Transparent)
                contentColor(c.onSurface)
                textStyle(type.bodyLarge.copy(color = c.onSurface))
                contentPadding(horizontal = 16.dp, vertical = 8.dp)
                minHeight(if (twoLine) 72.dp else 56.dp)
                if (interactive) {
                    hovered { background(layer(l.hovered, c.onSurface, Color.Transparent)) }
                    focused { background(layer(l.focused, c.onSurface, Color.Transparent)) }
                    pressed { background(layer(l.pressed, c.onSurface, Color.Transparent)) }
                }
                selected {
                    background(c.secondaryContainer)
                    contentColor(c.onSecondaryContainer)
                    textStyle(type.bodyLarge.copy(color = c.onSecondaryContainer))
                    if (interactive) {
                        hovered { background(layer(l.hovered, c.onSecondaryContainer, c.secondaryContainer)) }
                        focused { background(layer(l.focused, c.onSecondaryContainer, c.secondaryContainer)) }
                        pressed { background(layer(l.pressed, c.onSecondaryContainer, c.secondaryContainer)) }
                    }
                }
                disabled {
                    contentColor(c.onSurface.copy(alpha = l.disabledContent))
                    textStyle(type.bodyLarge.copy(color = c.onSurface.copy(alpha = l.disabledContent)))
                }
            }
        }
    }

    /** Style of the supporting text: smaller and quieter. */
    @Composable
    public fun supportingStyle(enabled: Boolean = true): Style {
        val type = MaterialTheme.typography
        val color = quietColor(enabled)
        return remember(type, color) { Style { contentColor(color); textStyle(type.bodyMedium.copy(color = color)) } }
    }

    /** Style of the overline text: smallest and quiet. */
    @Composable
    public fun overlineStyle(enabled: Boolean = true): Style {
        val type = MaterialTheme.typography
        val color = quietColor(enabled)
        return remember(type, color) { Style { contentColor(color); textStyle(type.labelSmall.copy(color = color)) } }
    }

    /** The quiet content colour used for secondary text and leading/trailing icons. */
    @Composable
    public fun quietColor(enabled: Boolean = true): Color {
        val c = MaterialTheme.colorScheme
        return if (enabled) c.onSurfaceVariant else c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
    }
}
