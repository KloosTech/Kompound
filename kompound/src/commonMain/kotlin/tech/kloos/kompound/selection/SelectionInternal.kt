package tech.kloos.kompound.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleState
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.size
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.theme.KompoundStateLayer
import tech.kloos.kompound.theme.LocalKContentColor

/** Minimum touch target of every selection control (COMPONENT_SPEC C-042). */
internal val MinTouchTarget = 48.dp

/** Style of the 40dp circular halo behind a control: shows hovered, focused and pressed state layers. */
internal fun haloStyle(tint: Color, layers: KompoundStateLayer): Style = Style {
    shape(CircleShape)
    size(40.dp)
    hovered { background(tint.copy(alpha = layers.hovered)) }
    focused { background(tint.copy(alpha = layers.focused)) }
    pressed { background(tint.copy(alpha = layers.pressed)) }
    disabled { background(Color.Transparent) }
}

/**
 * Outer row of a selection control: [behaviour] (toggleable/selectable) wraps the styled row so the whole
 * row, label included, is the touch target; [control] is drawn first, then the optional [label].
 */
@Composable
internal fun SelectionRow(
    modifier: Modifier,
    behaviour: Modifier,
    state: StyleState,
    defaultStyle: Style,
    style: Style,
    control: @Composable () -> Unit,
    label: (@Composable () -> Unit)?,
) {
    Row(
        modifier = modifier.then(behaviour).styleable(state, defaultStyle, style).defaultMinSize(minHeight = MinTouchTarget, minWidth = MinTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        control()
        label?.invoke()
    }
}

/** A 40dp centred slot holding the halo with [content] on top. */
@Composable
internal fun HaloSlot(state: StyleState, halo: Style, content: @Composable () -> Unit) {
    Box(Modifier.size(MinTouchTarget), contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).styleable(state, halo), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
internal fun ProvideIconColor(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKContentColor provides color, content = content)
}
