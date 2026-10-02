package tech.kloos.kompound.chip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Compact element for an action, a filter or an input value.
 * - [selected] `null`: an assist/suggestion chip, clickable, announced as a button.
 * - [selected] `true`/`false`: a filter chip, toggleable, announced with its checked state.
 *
 * Icons are slots ([leading], [trailing]) of [KChipDefaults.IconSize]; they pick up the chip's content colour and never change the chip's height. Long labels end in an
 * ellipsis once the chip reaches its maximum width (250dp by default, changeable through [style]).
 *
 * @param label Text of the chip.
 * @param onClick Called on click; for filter chips the caller flips its own `selected` state here.
 * @param modifier Modifier applied to the outermost node.
 * @param selected `null` for an action chip, otherwise the filter state.
 * @param enabled When false the chip ignores input and uses the disabled style block.
 * @param style Overrides merged over [KChipDefaults.style].
 * @param interactionSource Feeds pressed/hovered/focused state into the style.
 * @param leading Optional slot before the label, usually a `KIcon`.
 * @param trailing Optional slot after the label, e.g. a remove icon.
 */
@Composable
public fun KChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    enabled: Boolean = true,
    style: Style = Style,
    interactionSource: MutableInteractionSource? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.isSelected = selected == true
    }
    val base = modifier.hoverable(source, enabled)
    val behaviour = if (selected == null) {
        base.clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        base.toggleable(selected, source, null, enabled, Role.Checkbox) { onClick() }
    }
    Row(
        modifier = behaviour.styleable(state, KChipDefaults.style(), style),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalKContentColor provides KChipDefaults.contentColor(selected == true, enabled)) {
            leading?.let { IconSlot(it) }
            KText(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing?.let { IconSlot(it) }
        }
    }
}

/** Fixed-size box so an icon never makes the chip taller than a text-only chip; larger content is clipped to it. */
@Composable
private fun IconSlot(content: @Composable () -> Unit) {
    Box(Modifier.size(KChipDefaults.IconSize), contentAlignment = Alignment.Center) { content() }
}

/** Defaults for [KChip]. */
public object KChipDefaults {
    /** Size of the [KChip] `leading`/`trailing` slots; content larger than this is constrained to it. */
    public val IconSize: Dp = 18.dp

    /** Base style: 32dp outlined chip; `secondaryContainer` and no outline when selected. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, shapes, type, l) {
            fun layer(content: Color, alpha: Float, over: Color) = content.copy(alpha = alpha).compositeOver(over)
            val off = c.onSurfaceVariant
            val on = c.onSecondaryContainer
            Style {
                background(Color.Transparent)
                contentColor(off)
                textStyle(type.labelLarge.copy(color = off))
                shape(shapes.small)
                borderWidth(1.dp)
                borderColor(c.outline)
                contentPadding(horizontal = 12.dp, vertical = 6.dp)
                minHeight(32.dp)
                maxWidth(250.dp)
                hovered { background(layer(off, l.hovered, Color.Transparent)) }
                focused { background(layer(off, l.focused, Color.Transparent)) }
                pressed { background(layer(off, l.pressed, Color.Transparent)) }
                selected {
                    background(c.secondaryContainer)
                    contentColor(on)
                    textStyle(type.labelLarge.copy(color = on))
                    borderColor(Color.Transparent)
                    hovered { background(layer(on, l.hovered, c.secondaryContainer)) }
                    focused { background(layer(on, l.focused, c.secondaryContainer)) }
                    pressed { background(layer(on, l.pressed, c.secondaryContainer)) }
                }
                disabled {
                    background(Color.Transparent)
                    contentColor(c.onSurface.copy(alpha = l.disabledContent))
                    textStyle(type.labelLarge.copy(color = c.onSurface.copy(alpha = l.disabledContent)))
                    borderColor(c.onSurface.copy(alpha = l.disabledContainer))
                    selected { background(c.onSurface.copy(alpha = l.disabledContainer)) }
                }
            }
        }
    }

    /** The colour text and icons get; icons read it through `LocalKContentColor`. */
    @Composable
    public fun contentColor(selected: Boolean, enabled: Boolean = true): Color {
        val c = MaterialTheme.colorScheme
        return when {
            !enabled -> c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
            selected -> c.onSecondaryContainer
            else -> c.onSurfaceVariant
        }
    }
}
