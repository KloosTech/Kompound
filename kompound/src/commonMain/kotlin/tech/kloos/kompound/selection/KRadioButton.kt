package tech.kloos.kompound.selection

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.size
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme

/**
 * Radio button with an optional label. Group several inside a `Modifier.selectableGroup()` container so
 * screen readers announce them as one group.
 *
 * @param selected Whether this option is the selected one.
 * @param onClick Called when the user picks this option; `null` makes it display-only.
 * @param modifier Modifier applied to the outermost node.
 * @param style Overrides merged over [KRadioButtonDefaults.style] (the row).
 * @param circleStyle Overrides merged over [KRadioButtonDefaults.circleStyle] (the 20dp ring).
 * @param dotStyle Overrides merged over [KRadioButtonDefaults.dotStyle] (the inner dot).
 * @param enabled When false the option ignores input and uses the disabled style blocks.
 * @param interactionSource Feeds pressed/hovered/focused state into the styles.
 * @param label Optional label; use `KText`.
 */
@Composable
public fun KRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    style: Style = Style,
    circleStyle: Style = Style,
    dotStyle: Style = Style,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    label: (@Composable () -> Unit)? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.isSelected = selected
    }
    val scheme = MaterialTheme.colorScheme
    val layers = KompoundTheme.tokens.stateLayer
    var behaviour: Modifier = Modifier.hoverable(source, enabled)
    if (onClick != null) behaviour = behaviour.selectable(selected, source, null, enabled, Role.RadioButton, onClick)
    SelectionRow(
        modifier = modifier, behaviour = behaviour, state = styleState,
        defaultStyle = KRadioButtonDefaults.style(), style = style,
        control = {
            HaloSlot(styleState, remember(scheme, layers) { haloStyle(scheme.primary, layers) }) {
                Box(Modifier.size(20.dp).styleable(styleState, KRadioButtonDefaults.circleStyle(), circleStyle), contentAlignment = Alignment.Center) {
                    Box(Modifier.styleable(styleState, KRadioButtonDefaults.dotStyle(), dotStyle))
                }
            }
        },
        label = label,
    )
}

/** Defaults for [KRadioButton]. */
public object KRadioButtonDefaults {
    /** Row style: body text in `onSurface`, dimmed when disabled. */
    @Composable
    public fun style(): Style = KCheckboxDefaults.style()

    /** Ring style: 20dp circle with a 2dp outline that turns `primary` when selected. */
    @Composable
    public fun circleStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            Style {
                shape(CircleShape)
                borderWidth(2.dp)
                borderColor(c.onSurfaceVariant)
                selected { borderColor(c.primary) }
                disabled {
                    borderColor(c.onSurface.copy(alpha = l.disabledContent))
                    selected { borderColor(c.onSurface.copy(alpha = l.disabledContent)) }
                }
            }
        }
    }

    /** Dot style: invisible (zero size) until selected, then a 10dp `primary` dot. */
    @Composable
    public fun dotStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            Style {
                shape(CircleShape)
                background(Color.Transparent)
                size(0.dp)
                selected { size(10.dp); background(c.primary) }
                disabled { selected { background(c.onSurface.copy(alpha = l.disabledContent)) } }
            }
        }
    }
}
