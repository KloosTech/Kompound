/*
 * Copyright 2023, Mohamed Ben Rejeb and the Compose Dnd project contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tech.kloos.kompound.dnd.drop

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import tech.kloos.kompound.dnd.KDragAndDropState
import tech.kloos.kompound.dnd.LocalDragAndDropInfo
import tech.kloos.kompound.dnd.drag.KDraggedItemState

/**
 * Mark this composable as a drop target.
 *
 * @param key The key used to identify the drop target.
 * @param state The drag and drop state.
 * @param zIndex The z-index of the drop target. When multiple targets overlap,
 * the one with the highest z-index takes priority.
 * @param dropAlignment The alignment of the dropped item within this target
 * for the drop animation.
 * @param dropOffset An additional offset applied to the drop animation position.
 * @param dropAnimationEnabled Whether the drop animation is enabled. When false,
 * the dragged item disappears immediately on drop without animating to the target.
 * @param landOnDroppedItem When true, [onDrop] is called as soon as the item is released and the drag shadow then glides to where the
 * dragged item itself ends up once the app has moved it (its composable keeps its key), instead of gliding to the target and jumping.
 * @param ignoreSelf When true (default), [onDragEnter] and [onDragExit] are not called for the item's own slot (a reorderable item is a
 * drop target for itself), so callbacks never see their own data. [onDrop] is still called.
 * @param canDrop Whether this target currently accepts drops. When false, the target
 * is excluded from hover detection — dragged items cannot hover over it or be dropped on it.
 * This is a Compose-evaluated boolean, so it can read state (e.g., `state.draggedItem?.data`)
 * to dynamically accept or reject specific items.
 * @param onDrop The action to perform when an item is dropped onto the target.
 * Accepts the dragged item state as a parameter. It is called once the drop animation has
 * landed the drag shadow on this target, right before the dragged item's `onDragEnd`; with
 * [dropAnimationEnabled] set to false it is called as soon as the item is released.
 * @param onDragEnter The action to perform when an item is dragged over the target.
 * Accepts the dragged item state as a parameter.
 * @param onDragExit The action to perform when an item is dragged out of the target.
 * Accepts the dragged item state as a parameter.
 */
public fun <T> Modifier.kDropTarget(
    key: Any,
    state: KDragAndDropState<T>,
    zIndex: Float = 0f,
    dropAlignment: Alignment = Alignment.Center,
    dropOffset: Offset = Offset.Zero,
    dropAnimationEnabled: Boolean = true,
    canDrop: Boolean = true,
    landOnDroppedItem: Boolean = false,
    ignoreSelf: Boolean = true,
    onDrop: (state: KDraggedItemState<T>) -> Unit = {},
    onDragEnter: (state: KDraggedItemState<T>) -> Unit = {},
    onDragExit: (state: KDraggedItemState<T>) -> Unit = {},
): Modifier =
    this then DropTargetNodeElement(
        key = key,
        state = state,
        zIndex = zIndex,
        dropAlignment = dropAlignment,
        dropOffset = dropOffset,
        dropAnimationEnabled = dropAnimationEnabled,
        canDrop = canDrop,
        landOnDroppedItem = landOnDroppedItem,
        ignoreSelf = ignoreSelf,
        onDrop = onDrop,
        onDragEnter = onDragEnter,
        onDragExit = onDragExit,
    )

private data class DropTargetNodeElement<T>(
    val key: Any,
    val state: KDragAndDropState<T>,
    val zIndex: Float,
    val dropAlignment: Alignment,
    val dropOffset: Offset,
    val dropAnimationEnabled: Boolean,
    val canDrop: Boolean,
    val landOnDroppedItem: Boolean,
    val ignoreSelf: Boolean,
    val onDrop: (state: KDraggedItemState<T>) -> Unit,
    val onDragEnter: (state: KDraggedItemState<T>) -> Unit,
    val onDragExit: (state: KDraggedItemState<T>) -> Unit,
) : ModifierNodeElement<DropTargetNode<T>>() {
    private val enter: (KDraggedItemState<T>) -> Unit
        get() = if (ignoreSelf) { dragged -> if (dragged.key != key) onDragEnter(dragged) } else onDragEnter
    private val exit: (KDraggedItemState<T>) -> Unit
        get() = if (ignoreSelf) { dragged -> if (dragged.key != key) onDragExit(dragged) } else onDragExit

    override fun create(): DropTargetNode<T> =
        DropTargetNode(
            dropTargetState = KDropTargetState(
                key = key,
                zIndex = zIndex,
                size = Size.Zero,
                topLeft = Offset.Zero,
                dropAlignment = dropAlignment,
                dropOffset = dropOffset,
                dropAnimationEnabled = dropAnimationEnabled,
                canDrop = canDrop,
                landOnDroppedItem = landOnDroppedItem,
                onDrop = onDrop,
                onDragEnter = enter,
                onDragExit = exit,
            ),
            state = state,
        )

    override fun update(node: DropTargetNode<T>) {
        val targetState = node.dropTargetState
        val oldKey = targetState.key
        val oldState = node.state

        node.state = state

        targetState.key = key
        targetState.zIndex = zIndex
        targetState.dropAlignment = dropAlignment
        targetState.dropOffset = dropOffset
        targetState.dropAnimationEnabled = dropAnimationEnabled
        targetState.canDrop = canDrop
        targetState.landOnDroppedItem = landOnDroppedItem
        targetState.onDrop = onDrop
        targetState.onDragEnter = enter
        targetState.onDragExit = exit

        if (oldKey != key || oldState != state) {
            node.moveRegistration(oldKey, oldState)
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "DropTarget"
        properties["key"] = key
        properties["state"] = state
        properties["zIndex"] = zIndex
        properties["dropAlignment"] = dropAlignment
        properties["dropOffset"] = dropOffset
        properties["dropAnimationEnabled"] = dropAnimationEnabled
        properties["canDrop"] = canDrop
        properties["ignoreSelf"] = ignoreSelf
        properties["onDrop"] = onDrop
        properties["onDragEnter"] = onDragEnter
        properties["onDragExit"] = onDragExit
    }
}

private class DropTargetNode<T>(
    val dropTargetState: KDropTargetState<T>,
    var state: KDragAndDropState<T>,
) : Modifier.Node(),
    LayoutAwareModifierNode,
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    private val key get() = dropTargetState.key

    private var isShadow = false

    /**
     * Re-register under the current key/state after either changed, e.g. when
     * a lazy layout reuses this node for another item.
     */
    fun moveRegistration(oldKey: Any, oldState: KDragAndDropState<T>) {
        if (!isAttached || isShadow) return

        oldState.removeDropTarget(oldKey, dropTargetState)
        state.addDropTarget(dropTargetState)
    }

    override fun onAttach() {
        isShadow = currentValueOf(LocalDragAndDropInfo).isShadow

        if (isShadow) {
            return
        }

        state.addDropTarget(dropTargetState)
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        if (isShadow) {
            return
        }

        state.addDropTarget(dropTargetState)

        val size = coordinates.size.toSize()
        val topLeft = state.positionOf(coordinates)

        dropTargetState.layoutCoordinates = coordinates
        dropTargetState.size = size
        dropTargetState.topLeft = topLeft
    }

    // onPlaced is not called again when only an ancestor moved, e.g. a list scrolling
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (isShadow) {
            return
        }

        dropTargetState.layoutCoordinates = coordinates
        dropTargetState.topLeft = state.positionOf(coordinates)
    }

    override fun onRemeasured(size: IntSize) {
        if (isShadow) {
            return
        }

        dropTargetState.size = size.toSize()
    }

    override fun onReset() {
        if (isShadow) {
            return
        }

        state.removeDropTarget(key, dropTargetState)
    }

    override fun onDetach() {
        if (isShadow) {
            return
        }

        state.removeDropTarget(key, dropTargetState)
    }
}

/**
 * A catch-all drop target for the gaps and empty space of a list, so a drop that lands between items (or in an empty list) still
 * reaches [onDrop] instead of the shadow flying back. It sits below the items (`zIndex = -1`), so an item under the pointer still wins;
 * put it on the list or its container.
 *
 * @param key Unique key of the fallback target.
 * @param state The drag and drop state.
 * @param canDrop Whether the fallback currently accepts drops.
 * @param landOnDroppedItem See [kDropTarget]; true by default here because the app moves the item in [onDrop].
 * @param onDrop Called when an item is dropped on the empty space.
 */
public fun <T> Modifier.kDropTargetFallback(
    key: Any,
    state: KDragAndDropState<T>,
    canDrop: Boolean = true,
    landOnDroppedItem: Boolean = true,
    onDrop: (state: KDraggedItemState<T>) -> Unit,
): Modifier = kDropTarget(
    key = key,
    state = state,
    zIndex = -1f,
    canDrop = canDrop,
    landOnDroppedItem = landOnDroppedItem,
    onDrop = onDrop,
)
