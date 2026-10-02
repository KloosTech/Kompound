package tech.kloos.kompound.buttons

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import tech.kloos.kompound.theme.KompoundStateLayer

/** Look of a button, shared by [KButton], [KIconButton] and [KFab]. */
public enum class KButtonVariant {
    /** High emphasis: primary container. */
    Filled,

    /** Medium emphasis: secondary container. */
    Tonal,

    /** Medium emphasis: transparent with an outline. */
    Outlined,

    /** Low emphasis: transparent, no outline (the "standard" icon button). */
    Text,
}

/** Colours of one variant, resolved from the theme. Used by Style building and for icon tinting. */
internal class ButtonColors(
    val container: Color,
    val content: Color,
    val outline: Color?,
    val disabledContainer: Color,
    val disabledContent: Color,
    val disabledOutline: Color?,
) {
    /** Same colours with a different content colour (the interaction layers follow it). */
    fun withContent(content: Color) = ButtonColors(container, content, outline, disabledContainer, disabledContent, disabledOutline)
}

internal fun buttonColors(variant: KButtonVariant, c: ColorScheme, l: KompoundStateLayer): ButtonColors {
    val disabledContent = c.onSurface.copy(alpha = l.disabledContent)
    val disabledContainer = c.onSurface.copy(alpha = l.disabledContainer)
    return when (variant) {
        KButtonVariant.Filled -> ButtonColors(c.primary, c.onPrimary, null, disabledContainer, disabledContent, null)
        KButtonVariant.Tonal -> ButtonColors(c.secondaryContainer, c.onSecondaryContainer, null, disabledContainer, disabledContent, null)
        KButtonVariant.Outlined -> ButtonColors(Color.Transparent, c.primary, c.outline, Color.Transparent, disabledContent, disabledContainer)
        KButtonVariant.Text -> ButtonColors(Color.Transparent, c.primary, null, Color.Transparent, disabledContent, null)
    }
}

/** Container colour with the interaction [alpha] of the content colour laid over it. */
private fun ButtonColors.layered(alpha: Float): Color = content.copy(alpha = alpha).compositeOver(container)

/**
 * Style shared by the button family: colours and state layers for [variant], with [shape], [minSize]
 * and [padding] supplied by the concrete component.
 */
internal fun buttonStyle(
    colors: ButtonColors,
    layers: KompoundStateLayer,
    type: Typography,
    shape: Shape,
    minHeight: Dp,
    minWidth: Dp?,
    horizontalPadding: Dp,
    verticalPadding: Dp,
): Style {
    val label = type.labelLarge
    return Style {
        background(colors.container)
        contentColor(colors.content)
        textStyle(label.copy(color = colors.content))
        shape(shape)
        contentPadding(horizontalPadding, verticalPadding)
        minHeight(minHeight)
        if (minWidth != null) minWidth(minWidth)
        if (colors.outline != null) {
            borderWidth(androidx.compose.ui.unit.Dp(1f))
            borderColor(colors.outline)
        }
        hovered { background(colors.layered(layers.hovered)) }
        focused { background(colors.layered(layers.focused)) }
        pressed { background(colors.layered(layers.pressed)) }
        disabled {
            background(colors.disabledContainer)
            contentColor(colors.disabledContent)
            textStyle(label.copy(color = colors.disabledContent))
            if (colors.disabledOutline != null) borderColor(colors.disabledOutline)
        }
    }
}
