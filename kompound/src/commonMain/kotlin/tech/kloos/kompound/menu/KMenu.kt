package tech.kloos.kompound.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Popup list of actions or choices. Call it inside the layout that is its anchor (a `Box` around the
 * button or field): it opens below the anchor, flips above when there is no room, and closes on Escape,
 * back and clicks outside. Up/Down arrow keys move between items.
 *
 * @param expanded Whether the menu is shown.
 * @param onDismissRequest Called when the user dismisses the menu without choosing.
 * @param modifier Modifier applied to the menu surface.
 * @param minWidth Minimum width, typically the anchor's width; defaults to 112dp.
 * @param style Overrides merged over [KMenuDefaults.style].
 * @param content The items, usually [KMenuItem]s.
 */
@Composable
public fun KMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = Dp.Unspecified,
    style: Style = Style,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    Popup(
        popupPositionProvider = DropdownPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Column(
            modifier = modifier
                .widthIn(min = if (minWidth == Dp.Unspecified) 112.dp else minWidth, max = 280.dp.coerceAtLeast(if (minWidth == Dp.Unspecified) 0.dp else minWidth))
                .width(IntrinsicSize.Max)
                .heightIn(max = 320.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                }
                .styleable(state, KMenuDefaults.style(), style)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

/**
 * One row of a [KMenu].
 *
 * @param text Label of the item.
 * @param onClick Called when the item is picked. The menu does not close by itself; the caller decides.
 * @param modifier Modifier applied to the item.
 * @param enabled When false the item is dimmed and cannot be picked.
 * @param selected Marks the current choice: tinted background, and a check mark when [showCheck] is set.
 * @param showCheck Show a trailing check mark while [selected].
 * @param role Semantic role announced to screen readers: [Role.Button] for actions, [Role.RadioButton] or
 * [Role.Checkbox] for choices.
 * @param leading Optional slot before the text, usually a `KIcon`.
 * @param trailing Optional slot after the text.
 * @param supportingText Optional second line under the text, in the quieter supporting colour.
 * @param style Overrides merged over [KMenuDefaults.itemStyle].
 */
@Composable
public fun KMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    showCheck: Boolean = false,
    role: Role = Role.Button,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.isSelected = selected
    }
    val behaviour = if (role == Role.Button) {
        Modifier.clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
            // an action item that marks the current choice (check mark) must say so to screen readers too
            .semantics { if (selected) semanticsSelected = true }
    } else {
        Modifier.selectable(selected, source, null, enabled, role, onClick)
    }
    Row(
        modifier = modifier.fillMaxWidth().hoverable(source, enabled).then(behaviour).styleable(state, KMenuDefaults.itemStyle(), style),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalKContentColor provides KMenuDefaults.contentColor(enabled, selected)) {
            leading?.invoke()
            // Takes all free width, so the check mark and the trailing slot sit at the end of the row.
            Column(Modifier.weight(1f)) {
                KText(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (supportingText != null) KText(supportingText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = KMenuDefaults.supportingStyle())
            }
            if (showCheck && selected) KIcon(KompoundIcons.Check, contentDescription = null)
            trailing?.invoke()
        }
    }
}

/** Defaults for [KMenu] and [KMenuItem]. */
public object KMenuDefaults {
    /** Menu surface: `surfaceContainer` with a 1dp `outlineVariant` border and 8dp vertical padding. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        return remember(c, shapes) {
            Style {
                background(c.surfaceContainer)
                shape(shapes.medium)
                borderWidth(1.dp)
                borderColor(c.outlineVariant)
                contentPadding(horizontal = 0.dp, vertical = 8.dp)
            }
        }
    }

    /** Item: 48dp row, body text, state layers on hover/focus/press, tinted when selected. */
    @Composable
    public fun itemStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        val density = KompoundTheme.tokens.density
        return remember(c, type, l, density) {
            fun layer(alpha: Float, over: Color) = c.onSurface.copy(alpha = alpha).compositeOver(over)
            Style {
                background(Color.Transparent)
                contentColor(c.onSurface)
                textStyle(type.bodyLarge.copy(color = c.onSurface))
                contentPadding(horizontal = 16.dp, vertical = density.space(8.dp))
                minHeight(density.height(48.dp))
                hovered { background(layer(l.hovered, c.surfaceContainer)) }
                focused { background(layer(l.focused, c.surfaceContainer)) }
                pressed { background(layer(l.pressed, c.surfaceContainer)) }
                selected {
                    background(c.secondaryContainer)
                    contentColor(c.onSecondaryContainer)
                    textStyle(type.bodyLarge.copy(color = c.onSecondaryContainer))
                    hovered { background(c.onSecondaryContainer.copy(alpha = l.hovered).compositeOver(c.secondaryContainer)) }
                    focused { background(c.onSecondaryContainer.copy(alpha = l.focused).compositeOver(c.secondaryContainer)) }
                    pressed { background(c.onSecondaryContainer.copy(alpha = l.pressed).compositeOver(c.secondaryContainer)) }
                }
                disabled {
                    background(Color.Transparent)
                    contentColor(c.onSurface.copy(alpha = l.disabledContent))
                    textStyle(type.bodyLarge.copy(color = c.onSurface.copy(alpha = l.disabledContent)))
                }
            }
        }
    }

    /** Style of an item's supporting text: `bodySmall` in `onSurfaceVariant`. */
    @Composable
    public fun supportingStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurfaceVariant); textStyle(type.bodySmall.copy(color = c.onSurfaceVariant)) } }
    }

    /** The colour text and icons in an item get; icons read it through `LocalKContentColor`. */
    @Composable
    public fun contentColor(enabled: Boolean, selected: Boolean): Color {
        val c = MaterialTheme.colorScheme
        return when {
            !enabled -> c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
            selected -> c.onSecondaryContainer
            else -> c.onSurface
        }
    }
}
