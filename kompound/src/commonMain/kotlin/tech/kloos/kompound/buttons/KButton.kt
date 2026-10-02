package tech.kloos.kompound.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tech.kloos.kompound.KompoundStyles
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Primary action button, styled through the Compose Styles API.
 *
 * @param onClick Called when the button is clicked.
 * @param modifier Modifier applied to the outermost node.
 * @param style Overrides merged over [KButtonDefaults.style]; unspecified properties inherit.
 * @param enabled When false the button is not clickable and uses the disabled style block.
 * @param interactionSource Feeds pressed/hovered/focused state into the style.
 * @param content Button content.
 */
@Composable
public fun KButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
    Row(
        // clickable must wrap styleable so the style's padding and min size are part of the hit area.
        modifier = modifier
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .styleable(styleState, KButtonDefaults.style(), style),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Defaults for [KButton]. */
public object KButtonDefaults {
    /** Base style built from the current M3 theme tokens. */
    @Composable
    public fun style(): Style {
        val colors = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val type = MaterialTheme.typography
        return remember(colors, shapes, type) {
            Style {
                background(colors.primary)
                contentColor(colors.onPrimary)
                shape(shapes.large)
                textStyle(type.labelLarge.copy(color = colors.onPrimary))
                contentPadding(horizontal = 24.dp, vertical = 10.dp)
                minHeight(40.dp)
                pressed { background(colors.primary.copy(alpha = 0.85f)) }
                disabled {
                    background(colors.onSurface.copy(alpha = 0.12f))
                    contentColor(colors.onSurface.copy(alpha = 0.38f))
                    textStyle(type.labelLarge.copy(color = colors.onSurface.copy(alpha = 0.38f)))
                }
            }
        }
    }
}
