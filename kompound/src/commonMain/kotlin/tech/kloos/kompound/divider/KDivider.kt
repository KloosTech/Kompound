package tech.kloos.kompound.divider

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.fillHeight
import androidx.compose.foundation.style.fillWidth
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles

/**
 * Thin line separating content. Decorative: it has no semantics.
 *
 * @param modifier Modifier applied to the divider.
 * @param style Overrides merged over [KDividerDefaults.style]; set `background(...)` for colour and
 * `height(...)`/`width(...)` for thickness.
 * @param orientation [Orientation.Horizontal] draws a line across the available width,
 * [Orientation.Vertical] a line over the available height.
 */
@Composable
public fun KDivider(
    modifier: Modifier = Modifier,
    style: Style = Style,
    orientation: Orientation = Orientation.Horizontal,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    Box(modifier.styleable(state, KDividerDefaults.style(orientation), style))
}

/** Defaults for [KDivider]. */
public object KDividerDefaults {
    /** Base style: 1dp line in the theme's `outlineVariant`. */
    @Composable
    public fun style(orientation: Orientation = Orientation.Horizontal): Style {
        val color = MaterialTheme.colorScheme.outlineVariant
        return remember(color, orientation) {
            Style {
                background(color)
                if (orientation == Orientation.Horizontal) {
                    fillWidth()
                    height(1.dp)
                } else {
                    fillHeight()
                    width(1.dp)
                }
            }
        }
    }
}
