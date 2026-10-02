package tech.kloos.kompound.textfield

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * A text-field-looking trigger that opens something (a menu, a dialog). Shared by the dropdowns and the
 * date fields: label above, container styled like [KTextField] (the `selected` look while [open]), the
 * chosen value or a placeholder, a trailing [icon], supporting text below. [popup] is composed next to
 * the trigger and receives the trigger's width, so a menu can match it.
 */
@Composable
internal fun PickerField(
    modifier: Modifier,
    label: String?,
    placeholder: String?,
    supportingText: String?,
    isError: Boolean,
    enabled: Boolean,
    style: Style,
    displayText: String,
    open: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    role: Role,
    rotateIconWhenOpen: Boolean = true,
    popup: @Composable (triggerWidth: Dp) -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.isSelected = open
        it.set(ErrorKey, isError)
    }
    val density = LocalDensity.current
    var triggerWidth by remember { mutableStateOf(0) }
    val iconColor = KTextFieldDefaults.iconColor(isError, enabled)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) KText(label, style = KTextFieldDefaults.labelStyle(isError, enabled))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { triggerWidth = it.width }
                    .semantics { if (label != null) contentDescription = label }
                    .hoverable(source, enabled)
                    .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
                    .styleable(state, KTextFieldDefaults.style(), style),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (displayText.isEmpty() && placeholder != null) {
                    KText(placeholder, Modifier.weight(1f), style = KTextFieldDefaults.placeholderStyle(enabled), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    KText(displayText, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CompositionLocalProvider(LocalKContentColor provides iconColor) {
                    KIcon(icon, contentDescription = null, Modifier.rotate(if (open && rotateIconWhenOpen) 180f else 0f))
                }
            }
            popup(with(density) { triggerWidth.toDp() })
        }
        if (supportingText != null) {
            KText(supportingText, Modifier.padding(horizontal = 16.dp), style = KTextFieldDefaults.supportingStyle(isError, enabled))
        }
    }
}
