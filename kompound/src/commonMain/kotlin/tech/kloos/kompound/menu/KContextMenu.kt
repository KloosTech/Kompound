package tech.kloos.kompound.menu

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Opens a menu of [actions] where the user asks for one: a right-click (secondary mouse button) on desktop and web, a long press on touch
 * screens, and the Menu key or Shift+F10 while something inside the area has keyboard focus (make the content focusable for keyboard users). The menu appears at the pointer (or at the area's top-left for
 * the keyboard) and closes on Escape, back, a click outside or after an item that closes it.
 *
 * Gestures other than those pass through to [content] untouched. [actions] is asked when the menu opens, so it can look at the current
 * selection; return an empty list (or only dividers) and nothing opens.
 *
 * @param actions The entries (items, groups, dividers; see [KMenuAction]); `null` entries are skipped.
 * @param modifier Modifier applied to the area.
 * @param enabled When false no menu opens.
 * @param menuStyle Overrides merged over [KMenuDefaults.style].
 * @param content What the menu belongs to.
 */
@Composable
public fun KContextMenuArea(
    actions: () -> List<KMenuAction?>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuStyle: Style = Style,
    content: @Composable () -> Unit,
) {
    var position by remember { mutableStateOf<IntOffset?>(null) }
    var entries by remember { mutableStateOf<List<KMenuAction?>>(emptyList()) }
    val currentActions by rememberUpdatedState(actions)
    fun openAt(at: IntOffset) {
        val list = currentActions().filterNotNull()
        if (list.any { it !is KMenuActionDivider }) { entries = list; position = at }
    }
    Box(
        modifier
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed)) { openAt(IntOffset.Zero); true } else false
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                // Observe only: presses reach the content as usual.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val p = event.changes.first().position
                            openAt(IntOffset(p.x.roundToInt(), p.y.roundToInt()))
                        }
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onLongPress = { p: Offset -> openAt(IntOffset(p.x.roundToInt(), p.y.roundToInt())) })
            },
    ) {
        content()
        val at = position
        if (at != null) {
            // A zero-size anchor at the pointer: the menu opens below it and flips above near the window's bottom edge.
            Box(Modifier.offset { at }.size(0.dp)) {
                KMenu(expanded = true, onDismissRequest = { position = null }, style = menuStyle) {
                    MenuActionEntries(entries) { position = null }
                }
            }
        }
    }
}
