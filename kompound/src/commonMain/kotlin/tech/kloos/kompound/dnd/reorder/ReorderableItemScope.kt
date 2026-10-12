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
package tech.kloos.kompound.dnd.reorder

import androidx.compose.ui.Modifier
import tech.kloos.kompound.dnd.KDragAndDropState
import tech.kloos.kompound.dnd.drag.DragHandleCounter
import tech.kloos.kompound.dnd.drag.DragHandleModifier
import tech.kloos.kompound.dnd.drag.DragHandleOwner
import tech.kloos.kompound.dnd.drag.KDraggableItemScope
import tech.kloos.kompound.dnd.drag.dragHandleRegistration

/**
 * Scope for [KReorderableItem] content. Extends [KDraggableItemScope] with
 * access to [isDragging] state and [dragHandle] modifier.
 */
public interface KReorderableItemScope : KDraggableItemScope

internal class ReorderableItemScopeImpl<T>(
    val state: KDragAndDropState<T>,
    override val key: Any,
) : KReorderableItemScope,
    DragHandleOwner {
    override val isDragging: Boolean
        get() = state.draggedItem?.key == key

    private val handleCounter = DragHandleCounter()

    override val hasDragHandle: Boolean
        get() = handleCounter.hasDragHandle

    override fun Modifier.kDragHandle(
        enabled: Boolean,
        dragAfterLongPress: Boolean,
        requireFirstDownUnconsumed: Boolean,
    ): Modifier =
        this
            .dragHandleRegistration(handleCounter)
            .then(
                DragHandleModifier(
                    key = key,
                    state = state,
                    enabled = enabled,
                    dragAfterLongPress = dragAfterLongPress,
                    requireFirstDownUnconsumed = requireFirstDownUnconsumed,
                ),
            )
}

internal class ReorderableItemScopeShadowImpl(
    override val key: Any,
) : KReorderableItemScope {
    override val isDragging: Boolean
        get() = false

    override fun Modifier.kDragHandle(
        enabled: Boolean,
        dragAfterLongPress: Boolean,
        requireFirstDownUnconsumed: Boolean,
    ): Modifier = this
}
