package tech.kloos.kompound.selection

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.checked
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.size
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.translation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme

/**
 * On/off switch with an optional label. Clicking the label toggles it; the row is at least 48dp tall.
 *
 * @param checked Whether the switch is on.
 * @param onCheckedChange Called with the new value; `null` makes it display-only.
 * @param modifier Modifier applied to the outermost node.
 * @param style Overrides merged over [KSwitchDefaults.style] (the row: label text style).
 * @param trackStyle Overrides merged over [KSwitchDefaults.trackStyle] (the 52x32 track).
 * @param thumbStyle Overrides merged over [KSwitchDefaults.thumbStyle] (the thumb and its halo).
 * @param enabled When false the switch ignores input and uses the disabled style blocks.
 * @param interactionSource Feeds pressed/hovered/focused state into the styles.
 * @param label Optional label; use `KText`.
 */
@Composable
public fun KSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    style: Style = Style,
    trackStyle: Style = Style,
    thumbStyle: Style = Style,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    label: (@Composable () -> Unit)? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.isChecked = checked
    }
    var behaviour: Modifier = Modifier.hoverable(source, enabled)
    if (onCheckedChange != null) behaviour = behaviour.toggleable(checked, source, null, enabled, Role.Switch, onCheckedChange)
    SelectionRow(
        modifier = modifier, behaviour = behaviour, state = styleState,
        defaultStyle = KSwitchDefaults.style(), style = style,
        control = {
            Box(Modifier.size(52.dp, 32.dp).styleable(styleState, KSwitchDefaults.trackStyle(), trackStyle), contentAlignment = Alignment.CenterStart) {
                // The 40dp halo slides with the thumb; the thumb sits centred in it.
                Box(Modifier.size(40.dp).styleable(styleState, KSwitchDefaults.haloStyle(), thumbStyle), contentAlignment = Alignment.Center) {
                    Box(Modifier.styleable(styleState, KSwitchDefaults.thumbStyle()))
                }
            }
        },
        label = label,
    )
}

/** Defaults for [KSwitch]. */
public object KSwitchDefaults {
    /** Row style: body text in `onSurface`, dimmed when disabled. */
    @Composable
    public fun style(): Style = KCheckboxDefaults.style()

    /** Track: 52x32 pill with an outline when off, filled `primary` when on (animated, no recomposition). */
    @Composable
    public fun trackStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            Style {
                shape(CircleShape)
                background(c.surfaceContainerHighest)
                borderWidth(2.dp)
                borderColor(c.outline)
                checked { animate { background(c.primary); borderColor(Color.Transparent) } }
                disabled {
                    background(c.surfaceContainerHighest.copy(alpha = l.disabledContainer))
                    borderColor(c.onSurface.copy(alpha = l.disabledContent))
                    checked { background(c.onSurface.copy(alpha = l.disabledContainer)); borderColor(Color.Transparent) }
                }
            }
        }
    }

    /** Halo behind the thumb: moves with it and shows hovered, focused and pressed state layers. */
    @Composable
    public fun haloStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f   // translation is absolute: mirror it in RTL
        return remember(c, l, dir) {
            Style {
                shape(CircleShape)
                translation(dir * (-4.dp.toPx() - 2.dp.toPx()), 0f)       // thumb centre 16dp from the track start; minus the 2dp border
                hovered { background(c.onSurface.copy(alpha = l.hovered)) }
                focused { background(c.onSurface.copy(alpha = l.focused)) }
                pressed { background(c.onSurface.copy(alpha = l.pressed)) }
                checked {
                    animate { translation(dir * (16.dp.toPx() - 2.dp.toPx()), 0f) }  // thumb centre 36dp from the track start; slides
                    hovered { background(c.primary.copy(alpha = l.hovered)) }
                    focused { background(c.primary.copy(alpha = l.focused)) }
                    pressed { background(c.primary.copy(alpha = l.pressed)) }
                }
                disabled { background(Color.Transparent); checked { background(Color.Transparent) } }
            }
        }
    }

    /** Thumb: 16dp `outline` circle when off, 24dp `onPrimary` when on. */
    @Composable
    public fun thumbStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            Style {
                shape(CircleShape)
                size(16.dp)
                background(c.outline)
                checked { animate { size(24.dp); background(c.onPrimary) } }
                disabled {
                    background(c.onSurface.copy(alpha = l.disabledContent))
                    checked { background(c.surface) }
                }
            }
        }
    }
}
