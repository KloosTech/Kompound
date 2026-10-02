package tech.kloos.kompound.surface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Container with a background, shape and content colour; the base of most other components.
 * Provides its content colour to text (through the Style) and to icons (through `LocalKContentColor`).
 *
 * @param modifier Modifier applied to the outermost node.
 * @param style Overrides merged over [KSurfaceDefaults.style].
 * @param contentColor Content colour for text and icons inside; unspecified means the style's own.
 * @param content Surface content.
 */
@Composable
public fun KSurface(
    modifier: Modifier = Modifier,
    style: Style = Style,
    contentColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    SurfaceContent(modifier, state, style, contentColor, interactive = false, content)
}

/**
 * Clickable [KSurface] with hovered, focused, pressed and disabled visuals declared in its Style.
 *
 * @param onClick Called when the surface is clicked.
 * @param enabled When false the surface ignores input and uses the disabled style block.
 * @param role Semantic role announced to screen readers, for example [Role.Button].
 * @param interactionSource Feeds interaction state into the style.
 */
@Composable
public fun KSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
    role: Role? = null,
    contentColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
    // clickable wraps styleable so the style's padding and min size are part of the hit area (C-016a).
    SurfaceContent(
        modifier.clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick),
        state, style, contentColor, interactive = true, content,
    )
}

@Composable
private fun SurfaceContent(
    modifier: Modifier,
    state: androidx.compose.foundation.style.StyleState,
    style: Style,
    contentColor: Color,
    interactive: Boolean,
    content: @Composable () -> Unit,
) {
    val base = KSurfaceDefaults.style(interactive)
    val effective = if (contentColor.isSpecified) Style(base, Style { contentColor(contentColor) }, style) else Style(base, style)
    val icon = contentColor.takeOrElse { MaterialTheme.colorScheme.onSurface }
    Box(modifier.styleable(state, effective)) {
        CompositionLocalProvider(LocalKContentColor provides icon, content = content)
    }
}

private val Color.isSpecified: Boolean get() = this != Color.Unspecified

/** Defaults for [KSurface]. */
public object KSurfaceDefaults {
    /** Base style from the theme: `surface` background, `onSurface` content, interaction layers when [interactive]. */
    @Composable
    public fun style(interactive: Boolean = false): Style {
        val colors = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val layers = KompoundTheme.tokens.stateLayer
        return remember(colors, shapes, layers, interactive) {
            Style {
                background(colors.surface)
                contentColor(colors.onSurface)
                shape(shapes.medium)
                if (interactive) {
                    hovered { background(colors.onSurface.copy(alpha = layers.hovered).compositeOver(colors.surface)) }
                    focused { background(colors.onSurface.copy(alpha = layers.focused).compositeOver(colors.surface)) }
                    pressed { background(colors.onSurface.copy(alpha = layers.pressed).compositeOver(colors.surface)) }
                    disabled {
                        background(colors.onSurface.copy(alpha = layers.disabledContainer).compositeOver(colors.surface))
                        contentColor(colors.onSurface.copy(alpha = layers.disabledContent))
                    }
                }
            }
        }
    }
}
