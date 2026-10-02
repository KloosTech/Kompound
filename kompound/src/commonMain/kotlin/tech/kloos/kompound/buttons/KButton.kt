package tech.kloos.kompound.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme

/**
 * Button with four looks ([KButtonVariant]), styled through the Compose Styles API.
 * Put [tech.kloos.kompound.text.KText] and [tech.kloos.kompound.icon.KIcon] in [content]; both pick up the
 * button's content colour. Leading and trailing icons are just more content.
 *
 * @param onClick Called when the button is clicked.
 * @param modifier Modifier applied to the outermost node.
 * @param variant Look of the button; defaults to [KButtonVariant.Filled].
 * @param style Overrides merged over [KButtonDefaults.style]; unspecified properties inherit.
 * @param enabled When false the button is not clickable and uses the disabled style block.
 * @param interactionSource Feeds pressed/hovered/focused state into the style.
 * @param content Button content.
 */
@Composable
public fun KButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KButtonVariant = KButtonVariant.Filled,
    style: Style = Style,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
    ButtonBase(
        modifier = modifier, styleState = styleState, defaultStyle = KButtonDefaults.style(variant), style = style,
        enabled = enabled, interactionSource = source, role = Role.Button,
        iconColor = KButtonDefaults.contentColor(variant, enabled), contentDescription = null,
        onClick = onClick, toggle = null, content = content,
    )
}

/** Defaults for [KButton]. */
public object KButtonDefaults {
    /** Base style for [variant] built from the current theme tokens. */
    @Composable
    public fun style(variant: KButtonVariant = KButtonVariant.Filled): Style {
        val scheme = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val layers = KompoundTheme.tokens.stateLayer
        return remember(variant, scheme, type, layers) {
            buttonStyle(buttonColors(variant, scheme, layers), layers, type, CircleShape, 40.dp, null, 24.dp, 10.dp)
        }
    }

    /** The colour text and icons get for [variant]; icons read it through `LocalKContentColor`. */
    @Composable
    public fun contentColor(variant: KButtonVariant, enabled: Boolean = true): Color {
        val colors = buttonColors(variant, MaterialTheme.colorScheme, KompoundTheme.tokens.stateLayer)
        return if (enabled) colors.content else colors.disabledContent
    }
}
