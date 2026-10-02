package tech.kloos.kompound.selection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.triStateToggleIndeterminate
import androidx.compose.foundation.style.triStateToggleOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Checkbox with an optional label. Clicking the label toggles it, and the whole row is at least 48dp.
 *
 * @param checked Whether the box is checked.
 * @param onCheckedChange Called with the new value; `null` makes it display-only (the parent handles input).
 * @param modifier Modifier applied to the outermost node.
 * @param style Overrides merged over [KCheckboxDefaults.style] (the row: label text style, colours).
 * @param boxStyle Overrides merged over [KCheckboxDefaults.boxStyle] (the 18dp box).
 * @param enabled When false the checkbox ignores input and uses the disabled style blocks.
 * @param interactionSource Feeds pressed/hovered/focused state into the styles.
 * @param label Optional label; use `KText`.
 */
@Composable
public fun KCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    style: Style = Style,
    boxStyle: Style = Style,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    label: (@Composable () -> Unit)? = null,
) {
    KCheckbox(
        state = if (checked) ToggleableState.On else ToggleableState.Off,
        onClick = onCheckedChange?.let { change -> { change(!checked) } },
        modifier = modifier, style = style, boxStyle = boxStyle, enabled = enabled,
        interactionSource = interactionSource, label = label,
    )
}

/**
 * Tri-state checkbox: on, off or indeterminate (a parent of partly selected children).
 *
 * @param state Current state.
 * @param onClick Called when the user clicks; the caller decides the next state. `null` makes it display-only.
 * @see KCheckbox
 */
@Composable
public fun KCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    style: Style = Style,
    boxStyle: Style = Style,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    label: (@Composable () -> Unit)? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.triStateToggle = state
    }
    val scheme = MaterialTheme.colorScheme
    val layers = KompoundTheme.tokens.stateLayer
    val checkColor = if (enabled) scheme.onPrimary else scheme.surface
    var behaviour: Modifier = Modifier.hoverable(source, enabled)
    if (onClick != null) behaviour = behaviour.triStateToggleable(state, source, null, enabled, Role.Checkbox, onClick)
    SelectionRow(
        modifier = modifier, behaviour = behaviour, state = styleState,
        defaultStyle = KCheckboxDefaults.style(), style = style,
        control = {
            HaloSlot(styleState, remember(scheme, layers) { haloStyle(scheme.primary, layers) }) {
                Box(Modifier.size(18.dp).styleable(styleState, KCheckboxDefaults.boxStyle(), boxStyle), contentAlignment = Alignment.Center) {
                    if (state != ToggleableState.Off) ProvideIconColor(checkColor) { Mark(state) }
                }
            }
        },
        label = label,
    )
}

@Composable
private fun Mark(state: ToggleableState) {
    val color = LocalKContentColor.current
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val u = size.width / 18f
        val path = Path().apply {
            if (state == ToggleableState.Indeterminate) {
                moveTo(4.5f * u, 9f * u); lineTo(13.5f * u, 9f * u)
            } else {
                moveTo(4.5f * u, 9.5f * u); lineTo(7.8f * u, 12.8f * u); lineTo(13.5f * u, 5.5f * u)
            }
        }
        drawPath(path, color, style = stroke)
    }
}

/** Defaults for [KCheckbox]. */
public object KCheckboxDefaults {
    /** Row style: body text in `onSurface`, dimmed when disabled. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, type, l) {
            Style {
                contentColor(c.onSurface)
                textStyle(type.bodyLarge.copy(color = c.onSurface))
                disabled {
                    contentColor(c.onSurface.copy(alpha = l.disabledContent))
                    textStyle(type.bodyLarge.copy(color = c.onSurface.copy(alpha = l.disabledContent)))
                }
            }
        }
    }

    /** Box style: 18dp, 2dp outline; filled with `primary` when on or indeterminate. */
    @Composable
    public fun boxStyle(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, shapes, l) {
            val disabledFill = c.onSurface.copy(alpha = l.disabledContent)
            val disabledLine = c.onSurface.copy(alpha = l.disabledContent)
            Style {
                shape(shapes.extraSmall)
                background(Color.Transparent)
                borderWidth(2.dp)
                borderColor(c.onSurfaceVariant)
                triStateToggleOn { background(c.primary); borderColor(c.primary) }
                triStateToggleIndeterminate { background(c.primary); borderColor(c.primary) }
                disabled {
                    borderColor(disabledLine)
                    triStateToggleOn { background(disabledFill); borderColor(disabledFill) }
                    triStateToggleIndeterminate { background(disabledFill); borderColor(disabledFill) }
                }
            }
        }
    }
}
