package tech.kloos.kompound.buttons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Shared skeleton of the button family: hover + click (or toggle) behaviour outside, Style inside.
 * `clickable`/`toggleable` wrap `styleable` so the style's padding and min size are part of the hit area
 * (COMPONENT_SPEC C-016a).
 */
@Composable
internal fun ButtonBase(
    modifier: Modifier,
    styleState: StyleState,
    defaultStyle: Style,
    style: Style,
    effectsStyle: Style = Style,
    enabled: Boolean,
    clickEnabled: Boolean = enabled,
    interactionSource: MutableInteractionSource,
    role: Role,
    iconColor: Color,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    toggle: Toggle?,
    content: @Composable RowScope.() -> Unit,
) {
    var m = modifier.hoverable(interactionSource, enabled)
    if (contentDescription != null) m = m.semantics { this.contentDescription = contentDescription }
    m = if (toggle != null) {
        m.toggleable(toggle.checked, interactionSource, null, clickEnabled, role, toggle.onChange)
    } else {
        m.clickable(interactionSource = interactionSource, indication = null, enabled = clickEnabled, role = role, onClick = onClick!!)
    }
    Row(
        modifier = m.styleable(styleState, defaultStyle, effectsStyle, style),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalKContentColor provides iconColor) { content() }
    }
}

internal class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit)
