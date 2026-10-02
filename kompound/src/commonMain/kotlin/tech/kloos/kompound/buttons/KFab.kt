package tech.kloos.kompound.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.style.Style
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
 * Floating action button: the primary action of a screen. A 56dp rounded square for an icon; use
 * [KFabDefaults.extendedStyle] with a label next to the icon for an extended FAB.
 *
 * @param onClick Called when the button is clicked.
 * @param contentDescription What the button does. Required for icon-only use; screen readers announce it.
 * @param modifier Modifier applied to the outermost node.
 * @param style Overrides merged over [KFabDefaults.style].
 * @param enabled When false the button is not clickable and uses the disabled style block.
 * @param interactionSource Feeds pressed/hovered/focused state into the style.
 * @param content Icon, or icon and label for the extended form.
 */
@Composable
public fun KFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
    ButtonBase(
        modifier = modifier, styleState = styleState, defaultStyle = KFabDefaults.style(), style = style,
        enabled = enabled, interactionSource = source, role = Role.Button,
        iconColor = KFabDefaults.contentColor(enabled), contentDescription = contentDescription,
        onClick = onClick, toggle = null, content = content,
    )
}

/** Defaults for [KFab]. */
public object KFabDefaults {
    /** Base style: 56dp rounded square in `primaryContainer`. */
    @Composable
    public fun style(): Style = fabStyle(extended = false)

    /** Style for an extended FAB with a label: grows with its content. */
    @Composable
    public fun extendedStyle(): Style = fabStyle(extended = true)

    /** The colour text and icons get; icons read it through `LocalKContentColor`. */
    @Composable
    public fun contentColor(enabled: Boolean = true): Color {
        val scheme = MaterialTheme.colorScheme
        return if (enabled) scheme.onPrimaryContainer else scheme.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
    }

    @Composable
    private fun fabStyle(extended: Boolean): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, shapes, type, l, extended) {
            fun layered(alpha: Float) = c.onPrimaryContainer.copy(alpha = alpha).compositeOver(c.primaryContainer)
            Style {
                background(c.primaryContainer)
                contentColor(c.onPrimaryContainer)
                textStyle(type.labelLarge.copy(color = c.onPrimaryContainer))
                shape(shapes.large)
                minHeight(56.dp)
                minWidth(56.dp)
                contentPadding(horizontal = if (extended) 16.dp else 0.dp, vertical = 0.dp)
                hovered { background(layered(l.hovered)) }
                focused { background(layered(l.focused)) }
                pressed { background(layered(l.pressed)) }
                disabled {
                    background(c.onSurface.copy(alpha = l.disabledContainer))
                    contentColor(c.onSurface.copy(alpha = l.disabledContent))
                    textStyle(type.labelLarge.copy(color = c.onSurface.copy(alpha = l.disabledContent)))
                }
            }
        }
    }
}
