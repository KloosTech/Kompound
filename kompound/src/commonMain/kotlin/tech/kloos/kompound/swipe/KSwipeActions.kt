package tech.kloos.kompound.swipe

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.theme.LocalKContentColor
import kotlin.math.abs
import kotlin.math.roundToInt

/** The side of a row a [KSwipeAction] sits on. */
public enum class KSwipeSide { Start, End }

/**
 * An action revealed behind a row while it is swiped.
 *
 * @property label Accessibility name; also offered as a custom action to screen readers (they cannot swipe).
 * @property background Colour of the revealed area.
 * @property contentColor Colour of [icon].
 * @property icon What is drawn in the revealed area, usually a `KIcon`.
 */
@Immutable
public class KSwipeAction(
    public val label: String,
    public val background: Color,
    public val contentColor: Color,
    public val icon: @Composable () -> Unit,
)

/**
 * A row that can be swiped sideways to trigger an action: the content slides and an action area is revealed behind it. Past [threshold] of the
 * width the action fires on release (with a haptic tick when the threshold is crossed) and the row **springs back**, so the caller can ask for
 * confirmation before it removes anything; remove the row yourself when the work is done.
 *
 * "Start" is the leading edge: dragging towards the end (right in left-to-right layouts, left in right-to-left ones) reveals [startAction].
 * A side without an action cannot be swiped. Screen readers get the actions as custom actions on the row.
 *
 * @param onAction Called with the side whose action was triggered.
 * @param modifier Modifier applied to the row.
 * @param startAction Action behind the leading edge.
 * @param endAction Action behind the trailing edge.
 * @param threshold Fraction of the row width (0..1) that must be passed to trigger.
 * @param enabled When false the row cannot be swiped.
 * @param content The row.
 */
@Composable
public fun KSwipeActions(
    onAction: (KSwipeSide) -> Unit,
    modifier: Modifier = Modifier,
    startAction: KSwipeAction? = null,
    endAction: KSwipeAction? = null,
    threshold: Float = 0.4f,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var width by remember { mutableStateOf(0) }
    var past by remember { mutableStateOf(false) }
    // Screen x grows to the right; the start side is the left in LTR and the right in RTL.
    val startSign = if (rtl) -1f else 1f
    fun limit(x: Float): Float {
        val towardsStart = x * startSign > 0
        val allowed = if (towardsStart) startAction != null else endAction != null
        return if (allowed) x.coerceIn(-width.toFloat(), width.toFloat()) else 0f
    }
    val actions = buildList {
        if (startAction != null) add(CustomAccessibilityAction(startAction.label) { onAction(KSwipeSide.Start); true })
        if (endAction != null) add(CustomAccessibilityAction(endAction.label) { onAction(KSwipeSide.End); true })
    }
    Box(modifier.onSizeChanged { width = it.width }.semantics { customActions = actions }) {
        val x = offset
        val towardsStart = x * startSign > 0
        val shown = if (towardsStart) startAction else endAction
        if (shown != null && x != 0f) {
            Box(
                Modifier.matchParentSize().background(shown.background).padding(horizontal = 20.dp),
                contentAlignment = if (towardsStart) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                CompositionLocalProvider(LocalKContentColor provides shown.contentColor) { shown.icon() }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled && (startAction != null || endAction != null),
                    state = rememberDraggableState { delta ->
                        val next = limit(offset + delta)
                        offset = next
                        val nowPast = width > 0 && abs(next) >= width * threshold
                        if (nowPast != past) {
                            past = nowPast
                            if (nowPast) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragStopped = {
                        val side = if (offset * startSign > 0) KSwipeSide.Start else KSwipeSide.End
                        val fire = width > 0 && abs(offset) >= width * threshold
                        past = false
                        if (fire) onAction(side)
                        animate(offset, 0f) { v, _ -> offset = v }
                    },
                ),
        ) { content() }
    }
}
