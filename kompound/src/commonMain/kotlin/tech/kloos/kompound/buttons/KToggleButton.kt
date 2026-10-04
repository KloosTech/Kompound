package tech.kloos.kompound.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.checked
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme

/**
 * Button that stays pressed: outlined when off, filled when on. Announced to screen readers as a
 * toggleable control with its on/off state.
 *
 * @param checked Whether the button is on.
 * @param onCheckedChange Called with the new value when the user toggles it.
 * @param modifier Modifier applied to the outermost node.
 * @param style Overrides merged over [KToggleButtonDefaults.style]; use a `checked { }` block for the on state.
 * @param enabled When false the button ignores input and uses the disabled style block.
 * @param interactionSource Feeds pressed/hovered/focused state into the style.
 * @param content Button content.
 */
@Composable
public fun KToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.isChecked = checked
    }
    ButtonBase(
        modifier = modifier, styleState = styleState, defaultStyle = KToggleButtonDefaults.style(), style = style,
        enabled = enabled, interactionSource = source, role = Role.Checkbox,
        iconColor = KToggleButtonDefaults.contentColor(checked, enabled), contentDescription = null,
        onClick = null, toggle = Toggle(checked, onCheckedChange), content = content,
    )
}

/** Defaults for [KToggleButton]. */
public object KToggleButtonDefaults {
    /** Base style: the outlined look, switching to the filled look in the `checked` block. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        val density = KompoundTheme.tokens.density
        return remember(c, type, l, density) {
            fun overlay(content: Color, alpha: Float, over: Color) = content.copy(alpha = alpha).compositeOver(over)
            val off = c.primary
            Style {
                background(Color.Transparent)
                contentColor(off)
                textStyle(type.labelLarge.copy(color = off))
                shape(CircleShape)
                borderWidth(1.dp)
                borderColor(c.outline)
                contentPadding(horizontal = density.space(24.dp), vertical = density.space(10.dp))
                minHeight(density.height(40.dp))
                hovered { background(overlay(off, l.hovered, Color.Transparent)) }
                focused { background(overlay(off, l.focused, Color.Transparent)) }
                pressed { background(overlay(off, l.pressed, Color.Transparent)) }
                checked {
                    background(c.primary)
                    contentColor(c.onPrimary)
                    textStyle(type.labelLarge.copy(color = c.onPrimary))
                    borderColor(Color.Transparent)
                    hovered { background(overlay(c.onPrimary, l.hovered, c.primary)) }
                    focused { background(overlay(c.onPrimary, l.focused, c.primary)) }
                    pressed { background(overlay(c.onPrimary, l.pressed, c.primary)) }
                }
                disabled {
                    background(Color.Transparent)
                    contentColor(c.onSurface.copy(alpha = l.disabledContent))
                    textStyle(type.labelLarge.copy(color = c.onSurface.copy(alpha = l.disabledContent)))
                    borderColor(c.onSurface.copy(alpha = l.disabledContainer))
                    checked { background(c.onSurface.copy(alpha = l.disabledContainer)) }
                }
            }
        }
    }

    /** The colour text and icons get; icons read it through `LocalKContentColor`. */
    @Composable
    public fun contentColor(checked: Boolean, enabled: Boolean = true): Color {
        val c = MaterialTheme.colorScheme
        return when {
            !enabled -> c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
            checked -> c.onPrimary
            else -> c.primary
        }
    }
}
