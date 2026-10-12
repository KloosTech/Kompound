package tech.kloos.kompound.dnd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.dnd.drag.KDragAxis
import tech.kloos.kompound.dnd.drag.KDraggableItem
import tech.kloos.kompound.dnd.drag.KDraggableItemScope
import tech.kloos.kompound.dnd.drag.KDropStrategy
import tech.kloos.kompound.dnd.drag.DraggableItemScopeShadowImpl
import tech.kloos.kompound.dnd.drop.kDropTarget
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * An item the user can pick up and drop on a [KDropZone] (or reorder with [KReorderableColumn] when they share a [state]). The item
 * stays in place, dimmed, while a copy follows the pointer; when it is dropped on nothing, the copy returns.
 *
 * The item does not move itself: put it where it belongs when the zone reports the drop (`onDrop`), so the app owns the data.
 *
 * @param key Stable unique key of the item.
 * @param data What the zones receive on a drop.
 * @param state The shared drag state; the item must be inside a [KDragAndDropContainer] using it.
 * @param modifier Modifier applied to the outermost node.
 * @param enabled When false the item cannot be picked up.
 * @param dragAfterLongPress Start the drag only after a long press, so the item can sit in a scrolling list.
 * @param dropTargets Keys of the zones this item may be dropped on; empty means any.
 * @param dropStrategy How the zone under the item is chosen when it overlaps several.
 * @param dragAxis Restricts the movement to one axis.
 * @param style Overrides merged over [KReorderableDefaults.itemStyle].
 * @param content Content of the item; the scope exposes `isDragging` and `Modifier.kDragHandle()`.
 */
@Composable
public fun <T> KDraggable(
    key: Any,
    data: T,
    state: KDragAndDropState<T>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dragAfterLongPress: Boolean = state.dragAfterLongPress,
    dropTargets: List<Any> = emptyList(),
    dropStrategy: KDropStrategy = KDropStrategy.Surface,
    dragAxis: KDragAxis = KDragAxis.Free,
    style: Style = Style,
    content: @Composable KDraggableItemScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    KDraggableItem(
        key = key,
        data = data,
        state = state,
        modifier = modifier.alpha(if (state.isDragging(key)) KReorderableDefaults.SlotAlpha else 1f),
        enabled = enabled,
        dragAfterLongPress = dragAfterLongPress,
        dropTargets = dropTargets,
        dropStrategy = dropStrategy,
        dragAxis = dragAxis,
        draggableContent = { DraggableFrame(dragged = true, enabled = enabled, style = style) { DraggableItemScopeShadowImpl(key).content() } },
    ) {
        DraggableFrame(dragged = false, enabled = enabled, style = style) { content() }
    }
}

@Composable
private fun DraggableFrame(dragged: Boolean, enabled: Boolean, style: Style, content: @Composable () -> Unit) {
    val state = remember { MutableStyleState(null) }
    state.isEnabled = enabled
    state.isSelected = dragged
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = (if (dragged) Modifier.shadow(KReorderableDefaults.DragElevation, shape) else Modifier)
            .styleable(state, KReorderableDefaults.itemStyle(), style),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalKContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

/**
 * An area that accepts dropped [KDraggable] items. While an accepted item is held over it, the zone uses the `selected` block of its
 * [style] (a highlighted border and fill by default).
 *
 * @param key Stable unique key of the zone (referenced by `dropTargets` of a draggable).
 * @param state The shared drag state; the zone must be inside a [KDragAndDropContainer] using it.
 * @param onDrop Called with the item's data once it landed on the zone.
 * @param modifier Modifier applied to the outermost node.
 * @param enabled When false the zone accepts nothing and uses the `disabled` block.
 * @param accepts Whether the item currently held would be accepted; evaluated while dragging, so it can read state.
 * @param style Overrides merged over [KDropZoneDefaults.style].
 * @param content Content of the zone. [isHovered] tells whether an accepted item is over it.
 */
@Composable
public fun <T> KDropZone(
    key: Any,
    state: KDragAndDropState<T>,
    onDrop: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accepts: (T) -> Boolean = { true },
    style: Style = Style,
    content: @Composable (isHovered: Boolean) -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val held = state.draggedItem?.data
    val canDrop = enabled && (state.draggedItem == null || accepts(held as T))
    val hovered = canDrop && state.isDropTargetHovered(key)
    val styleState = remember { MutableStyleState(null) }
    styleState.isEnabled = enabled
    styleState.isSelected = hovered
    Box(
        modifier = modifier
            .kDropTarget(key = key, state = state, canDrop = canDrop, dropAnimationEnabled = true, onDrop = { onDrop(it.data) })
            .styleable(styleState, KDropZoneDefaults.style(), style),
    ) {
        content(hovered)
    }
}

/** Defaults for [KDropZone]. */
public object KDropZoneDefaults {
    /** Base style: an outlined area with a primary highlight when an accepted item is over it. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val density = KompoundTheme.tokens.density
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, shapes, density, l) {
            Style {
                background(Color.Transparent)
                contentColor(c.onSurface)
                shape(shapes.medium)
                borderWidth(1.dp)
                borderColor(c.outlineVariant)
                contentPadding(density.space(8.dp))
                minHeight(density.height(56.dp))
                selected {
                    background(c.primary.copy(alpha = 0.08f))
                    borderColor(c.primary)
                    borderWidth(2.dp)
                }
                disabled {
                    contentColor(c.onSurface.copy(alpha = l.disabledContent))
                    borderColor(c.onSurface.copy(alpha = l.disabledContainer))
                }
            }
        }
    }
}
