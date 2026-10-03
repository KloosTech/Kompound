package tech.kloos.kompound.graph

import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed

/** Whether Shift, Ctrl or Cmd is held while this pointer event happens: the "add to the selection" modifiers. */
internal fun PointerEvent.isAdditive(): Boolean = keyboardModifiers.isShiftPressed || keyboardModifiers.isCtrlPressed || keyboardModifiers.isMetaPressed
