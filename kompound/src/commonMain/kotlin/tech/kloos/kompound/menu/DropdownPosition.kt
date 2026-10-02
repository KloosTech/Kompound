package tech.kloos.kompound.menu

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Where a dropdown popup goes: below the anchor, left edges aligned; flipped above the anchor when it
 * does not fit below but fits above; always clamped inside the window horizontally and vertically.
 */
internal fun calculateDropdownPosition(anchor: IntRect, window: IntSize, popup: IntSize): IntOffset {
    val x = anchor.left.coerceIn(0, maxOf(0, window.width - popup.width))
    val below = anchor.bottom
    val above = anchor.top - popup.height
    val y = when {
        below + popup.height <= window.height -> below
        above >= 0 -> above
        else -> maxOf(0, window.height - popup.height)
    }
    return IntOffset(x, y)
}

internal object DropdownPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = calculateDropdownPosition(anchorBounds, windowSize, popupContentSize)
}
