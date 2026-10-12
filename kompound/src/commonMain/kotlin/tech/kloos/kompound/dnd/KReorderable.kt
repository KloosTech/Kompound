package tech.kloos.kompound.dnd

import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleState
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.dnd.drag.KDragAxis
import tech.kloos.kompound.dnd.reorder.KReorderableItem
import tech.kloos.kompound.dnd.reorder.KReorderableItemScope
import tech.kloos.kompound.dnd.reorder.ReorderableItemScopeShadowImpl
import tech.kloos.kompound.dnd.scroll.KDragAutoScrollConfig
import tech.kloos.kompound.dnd.scroll.kDragAutoScroll
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.icon.KIcons
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Vertical list the user reorders by dragging, with a [KDragHandle] on every row by default.
 *
 * The list is controlled: [items] is the order you show, [onReorder] is called **once per drag** with the new order when the item
 * is dropped (and once per keyboard move). While a drag is on, the rows swap live so the user sees the result; if you do not
 * accept the new order, the list returns to [items]. Reordering works with the pointer (mouse, touch, pen), the keyboard
 * (focus a row and press Alt+Up / Alt+Down) and screen readers (the "Move up" / "Move down" custom actions).
 *
 * This version lays every row out; use [KReorderableLazyColumn] for long lists. For lists that exchange items, hold a
 * [KDragAndDropState] yourself, pass it as [state] and wrap everything in a [KDragAndDropContainer].
 *
 * @param items The items in their current order.
 * @param onReorder Called with the new order after a drag or a keyboard move.
 * @param key Stable unique key of an item.
 * @param modifier Modifier applied to the outermost node.
 * @param enabled When false rows cannot be moved and use the disabled style block.
 * @param showHandle Show a [KDragHandle] at the start of each row and drag from it only (the row body stays interactive); when
 * false the whole row drags, or call `Modifier.kDragHandle()` from the row scope to place your own handle.
 * @param dragAfterLongPress Start a drag only after a long press (useful for touch lists without a handle that also scroll).
 * @param spacing Gap between rows.
 * @param style Overrides merged over [KReorderableDefaults.listStyle].
 * @param itemStyle Overrides merged over [KReorderableDefaults.itemStyle], applied to every row.
 * @param state Your own drag state to share with other lists or drop zones; null creates one with its own container.
 * @param content Content of a row; the scope exposes `isDragging` and `Modifier.kDragHandle()`.
 */
@Composable
public fun <T> KReorderableColumn(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showHandle: Boolean = true,
    dragAfterLongPress: Boolean = false,
    spacing: Dp = KReorderableDefaults.Spacing,
    style: Style = Style,
    itemStyle: Style = Style,
    state: KDragAndDropState<T>? = null,
    content: @Composable KReorderableItemScope.(item: T) -> Unit,
) {
    ReorderableContainer(state, dragAfterLongPress, enabled, modifier) { dnd, containerModifier ->
        val order = rememberWorkingOrder(items, key, dnd)
        Column(
            modifier = containerModifier.styleable(rememberListStyleState(enabled), KReorderableDefaults.listStyle(), style),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            order.shown.forEachIndexed { index, item ->
                androidx.compose.runtime.key(key(item)) {
                    ReorderableRow(
                        item = item, index = index, order = order, key = key, dnd = dnd, enabled = enabled, showHandle = showHandle,
                        axis = KDragAxis.Vertical, vertical = true, itemStyle = itemStyle, onReorder = onReorder, content = content,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Horizontal counterpart of [KReorderableColumn]: rows become columns, Alt+Left / Alt+Right move an item. */
@Composable
public fun <T> KReorderableRow(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showHandle: Boolean = true,
    dragAfterLongPress: Boolean = false,
    spacing: Dp = KReorderableDefaults.Spacing,
    style: Style = Style,
    itemStyle: Style = Style,
    state: KDragAndDropState<T>? = null,
    content: @Composable KReorderableItemScope.(item: T) -> Unit,
) {
    ReorderableContainer(state, dragAfterLongPress, enabled, modifier) { dnd, containerModifier ->
        val order = rememberWorkingOrder(items, key, dnd)
        Row(
            modifier = containerModifier.styleable(rememberListStyleState(enabled), KReorderableDefaults.listStyle(), style),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            order.shown.forEachIndexed { index, item ->
                androidx.compose.runtime.key(key(item)) {
                    ReorderableRow(
                        item = item, index = index, order = order, key = key, dnd = dnd, enabled = enabled, showHandle = showHandle,
                        axis = KDragAxis.Horizontal, vertical = false, itemStyle = itemStyle, onReorder = onReorder, content = content,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

/**
 * [KReorderableColumn] for long lists: rows are lazy, they animate to their new place, and the list scrolls by itself when an
 * item is held near its edge.
 *
 * @param listState Scroll state of the list.
 * @param autoScroll Edge-scroll tuning while dragging.
 * @see KReorderableColumn
 */
@Composable
public fun <T> KReorderableLazyColumn(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showHandle: Boolean = true,
    dragAfterLongPress: Boolean = false,
    spacing: Dp = KReorderableDefaults.Spacing,
    style: Style = Style,
    itemStyle: Style = Style,
    listState: LazyListState = rememberLazyListState(),
    autoScroll: KDragAutoScrollConfig = KDragAutoScrollConfig(),
    state: KDragAndDropState<T>? = null,
    content: @Composable KReorderableItemScope.(item: T) -> Unit,
) {
    ReorderableContainer(state, dragAfterLongPress, enabled, modifier) { dnd, containerModifier ->
        val order = rememberWorkingOrder(items, key, dnd)
        LazyColumn(
            state = listState,
            modifier = containerModifier
                .styleable(rememberListStyleState(enabled), KReorderableDefaults.listStyle(), style)
                .kDragAutoScroll(dnd, listState, autoScroll),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            itemsIndexed(order.shown, key = { _, item -> key(item) }) { index, item ->
                ReorderableRow(
                    item = item, index = index, order = order, key = key, dnd = dnd, enabled = enabled, showHandle = showHandle,
                    axis = KDragAxis.Vertical, vertical = true, itemStyle = itemStyle, onReorder = onReorder, content = content,
                    modifier = Modifier.fillMaxWidth().animateLazyItem(this),
                )
            }
        }
    }
}

/** Defaults for [KReorderableColumn], [KReorderableRow], [KReorderableLazyColumn] and [KDraggable]. */
public object KReorderableDefaults {
    /** Gap between rows. */
    public val Spacing: Dp = 8.dp

    /** Size of the touch target of a [KDragHandle]. */
    public val HandleSize: Dp = 40.dp

    /** Elevation of the item that follows the pointer. */
    public val DragElevation: Dp = 8.dp

    /** Share of its opacity a row keeps in its slot while the user drags it, so the free slot stays visible. */
    public const val SlotAlpha: Float = 0.25f

    /** Base style of the list container: no decoration; disabled lists fade. */
    @Composable
    public fun listStyle(): Style = remember {
        Style { disabled { alpha(0.6f) } }
    }

    /**
     * Base style of a row and of a [KDraggable]: a bordered surface with state layers. `selected` is the row that follows the pointer
     * (the drag shadow); `focused` shows the keyboard focus.
     */
    @Composable
    public fun itemStyle(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        val density = KompoundTheme.tokens.density
        return remember(c, shapes, type, l, density) {
            Style {
                background(c.surfaceContainerLow)
                contentColor(c.onSurface)
                textStyle(type.bodyMedium.copy(color = c.onSurface))
                shape(shapes.medium)
                borderWidth(1.dp)
                borderColor(c.outlineVariant)
                contentPadding(horizontal = density.space(8.dp), vertical = density.space(4.dp))
                minHeight(density.height(48.dp))
                hovered { background(c.onSurface.copy(alpha = l.hovered).compositeOver(c.surfaceContainerLow)) }
                focused { borderColor(c.primary); borderWidth(2.dp) }
                selected {
                    background(c.surfaceContainerHigh)
                    borderColor(c.primary)
                }
                disabled {
                    contentColor(c.onSurface.copy(alpha = l.disabledContent))
                    background(Color.Transparent)
                    borderColor(c.onSurface.copy(alpha = l.disabledContainer))
                }
            }
        }
    }
}

/**
 * The grip icon of a draggable row. It only draws the grip and names it for screen readers; the drag itself starts from
 * `Modifier.kDragHandle()` in a row scope, which [KReorderableColumn] applies for you when `showHandle` is true.
 *
 * @param modifier Modifier applied to the handle; put `kDragHandle()` here.
 * @param contentDescription Announcement of the handle; defaults to the localised "Drag handle".
 * @param enabled When false the handle is dimmed.
 * @param style Overrides merged over the default icon style.
 */
@Composable
public fun KDragHandle(
    modifier: Modifier = Modifier,
    contentDescription: String = KompoundTheme.strings.dragHandle,
    enabled: Boolean = true,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    Box(
        modifier = modifier
            .size(KReorderableDefaults.HandleSize)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = LocalKContentColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant
        KIcon(KIcons.DragHandle, null, style = style, tint = if (enabled) tint else tint.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent))
    }
}

// -- Implementation --

@Composable
private fun rememberListStyleState(enabled: Boolean): StyleState {
    remember { KompoundStyles.ensureEnabled() }
    return remember { MutableStyleState(null) }.also { it.isEnabled = enabled }
}

/** The order shown while dragging; [items] otherwise. */
private class WorkingOrder<T>(val dnd: KDragAndDropState<T>) {
    var items: List<T> = emptyList()
    var key: (T) -> Any = { it as Any }
    var working: List<T> by mutableStateOf(emptyList())
    val shown: List<T> get() = if (dnd.draggedItem != null) working else items

    fun swap(draggedKey: Any, targetKey: Any) {
        val from = working.indexOfFirst { key(it) == draggedKey }
        val to = working.indexOfFirst { key(it) == targetKey }
        if (from >= 0 && to >= 0 && from != to) working = working.moved(from, to)
    }
}

@Composable
private fun <T> rememberWorkingOrder(items: List<T>, key: (T) -> Any, dnd: KDragAndDropState<T>): WorkingOrder<T> =
    remember(dnd) { WorkingOrder(dnd) }.also {
        it.items = items
        it.key = key
    }

@Composable
private fun <T> ReorderableContainer(
    state: KDragAndDropState<T>?,
    dragAfterLongPress: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable (KDragAndDropState<T>, Modifier) -> Unit,
) {
    if (state != null) {
        content(state, modifier)
    } else {
        val own = rememberKDragAndDropState<T>(dragAfterLongPress = dragAfterLongPress)
        KDragAndDropContainer(own, modifier, enabled) { content(own, Modifier) }
    }
}

@Composable
private fun <T> ReorderableRow(
    item: T,
    index: Int,
    order: WorkingOrder<T>,
    key: (T) -> Any,
    dnd: KDragAndDropState<T>,
    enabled: Boolean,
    showHandle: Boolean,
    axis: KDragAxis,
    vertical: Boolean,
    itemStyle: Style,
    onReorder: (List<T>) -> Unit,
    content: @Composable KReorderableItemScope.(item: T) -> Unit,
    modifier: Modifier,
) {
    val itemKey = key(item)
    val strings = KompoundTheme.strings
    val count = order.shown.size
    val dragging = dnd.isDragging(itemKey)
    fun move(by: Int) {
        val from = order.items.indexOfFirst { key(it) == itemKey }
        val to = (from + by).coerceIn(0, order.items.lastIndex)
        if (from >= 0 && from != to) onReorder(order.items.moved(from, to))
    }
    val backKey = if (vertical) Key.DirectionUp else Key.DirectionLeft
    val forwardKey = if (vertical) Key.DirectionDown else Key.DirectionRight
    val source = remember { MutableInteractionSource() }

    KReorderableItem(
        key = itemKey,
        data = item,
        state = dnd,
        enabled = enabled,
        dragAxis = axis,
        modifier = modifier
            .alpha(if (dragging) KReorderableDefaults.SlotAlpha else 1f)
            .hoverable(source, enabled)
            .focusable(enabled, source)
            .onKeyEvent { e ->
                if (!enabled || e.type != KeyEventType.KeyDown || !e.isAltPressed) return@onKeyEvent false
                when (e.key) {
                    backKey -> { move(-1); true }
                    forwardKey -> { move(1); true }
                    else -> false
                }
            }
            .semantics {
                stateDescription = strings.itemPosition(index + 1, count)
                if (enabled) {
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction(strings.moveUp) { move(-1); true })
                        if (index < count - 1) add(CustomAccessibilityAction(strings.moveDown) { move(1); true })
                    }
                }
            },
        onDragStart = { order.working = order.items },
        onDragEnter = { dragged -> order.swap(dragged.key, itemKey) },
        onDragEnd = { if (order.working.map(key) != order.items.map(key)) onReorder(order.working) },
        draggableContent = {
            RowFrame(
                dragged = true, enabled = enabled, source = source, itemStyle = itemStyle, showHandle = showHandle, vertical = vertical,
                scope = ReorderableItemScopeShadowImpl(itemKey), item = item, content = content,
            )
        },
    ) {
        RowFrame(
            dragged = false, enabled = enabled, source = source, itemStyle = itemStyle, showHandle = showHandle, vertical = vertical,
            scope = this, item = item, content = content,
        )
    }
}

@Composable
private fun <T> RowFrame(
    dragged: Boolean,
    enabled: Boolean,
    source: MutableInteractionSource,
    itemStyle: Style,
    showHandle: Boolean,
    vertical: Boolean,
    scope: KReorderableItemScope,
    item: T,
    content: @Composable KReorderableItemScope.(item: T) -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
        it.isSelected = dragged
    }
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = (if (vertical) Modifier.fillMaxWidth() else Modifier)
            .then(if (dragged) Modifier.shadow(KReorderableDefaults.DragElevation, shape) else Modifier)
            .styleable(state, KReorderableDefaults.itemStyle(), itemStyle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalKContentColor provides MaterialTheme.colorScheme.onSurface) {
            if (showHandle) {
                with(scope) { KDragHandle(modifier = Modifier.kDragHandle(enabled), enabled = enabled) }
            }
            Box(Modifier.weight(1f, fill = vertical)) { with(scope) { content(item) } }
        }
    }
}

/** Moves the item at [from] so it ends up at index [to] (the others shift). */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

private fun Modifier.animateLazyItem(scope: androidx.compose.foundation.lazy.LazyItemScope): Modifier =
    with(scope) { this@animateLazyItem.animateItem() }
