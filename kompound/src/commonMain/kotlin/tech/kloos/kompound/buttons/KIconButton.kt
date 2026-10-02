package tech.kloos.kompound.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.externalPadding
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme

/**
 * Round button that holds an icon: a 40dp circle inside a 48dp touch target. With
 * [KButtonVariant.Text] it is the "standard" icon button without a container.
 *
 * @param onClick Called when the button is clicked.
 * @param contentDescription What the button does, announced by screen readers. Required: the content is
 * an icon, which has no text of its own.
 * @param modifier Modifier applied to the outermost node.
 * @param variant Look of the button; defaults to [KButtonVariant.Text].
 * @param style Overrides merged over [KIconButtonDefaults.style].
 * @param enabled When false the button is not clickable and uses the disabled style block.
 * @param interactionSource Feeds pressed/hovered/focused state into the style.
 * @param icon Typically a [tech.kloos.kompound.icon.KIcon]; it picks up the button's content colour.
 */
@Composable
public fun KIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: KButtonVariant = KButtonVariant.Text,
    style: Style = Style,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    icon: @Composable RowScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
    ButtonBase(
        modifier = modifier, styleState = styleState, defaultStyle = KIconButtonDefaults.style(variant), style = style,
        enabled = enabled, interactionSource = source, role = Role.Button,
        iconColor = KIconButtonDefaults.contentColor(variant, enabled), contentDescription = contentDescription,
        onClick = onClick, toggle = null, content = icon,
    )
}

/** Like [buttonColors], but the standard (text) icon button is `onSurfaceVariant`, as in Material 3. */
private fun iconButtonColors(variant: KButtonVariant, scheme: androidx.compose.material3.ColorScheme, layers: tech.kloos.kompound.theme.KompoundStateLayer): ButtonColors =
    buttonColors(variant, scheme, layers).let { if (variant == KButtonVariant.Text) it.withContent(scheme.onSurfaceVariant) else it }

/** Defaults for [KIconButton]. */
public object KIconButtonDefaults {
    /** The colour the icon gets for [variant]; icons read it through `LocalKContentColor`. */
    @Composable
    public fun contentColor(variant: KButtonVariant, enabled: Boolean = true): androidx.compose.ui.graphics.Color {
        val colors = iconButtonColors(variant, MaterialTheme.colorScheme, KompoundTheme.tokens.stateLayer)
        return if (enabled) colors.content else colors.disabledContent
    }

    /** Base style: 40dp circle with 4dp external padding, which makes the 48dp touch target. */
    @Composable
    public fun style(variant: KButtonVariant = KButtonVariant.Text): Style {
        val scheme = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val layers = KompoundTheme.tokens.stateLayer
        return remember(variant, scheme, type, layers) {
            Style(
                buttonStyle(iconButtonColors(variant, scheme, layers), layers, type, CircleShape, 40.dp, 40.dp, 0.dp, 0.dp),
                Style { externalPadding(4.dp) },
            )
        }
    }
}
