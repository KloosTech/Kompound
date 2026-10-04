package tech.kloos.kompound.split

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.theme.KompoundTheme
import kotlin.math.roundToInt

/**
 * The divider position of a [KSplitPane] as a fraction (0..1) of the available space given to the first pane. Create it with
 * [rememberKSplitPaneState] to survive configuration changes, or hold it yourself to save the layout.
 */
@Stable
public class KSplitPaneState(initialFraction: Float = 0.5f) {
    /** Share of the space (after the divider) that the first pane gets. */
    public var fraction: Float by mutableFloatStateOf(initialFraction.coerceIn(0f, 1f))

    public companion object {
        /** Saves and restores the fraction. */
        public val Saver: Saver<KSplitPaneState, Float> = Saver(save = { it.fraction }, restore = { KSplitPaneState(it) })
    }
}

/** A [KSplitPaneState] kept across configuration changes. */
@Composable
public fun rememberKSplitPaneState(initialFraction: Float = 0.5f): KSplitPaneState =
    rememberSaveable(saver = KSplitPaneState.Saver) { KSplitPaneState(initialFraction) }

/**
 * Two panes with a draggable divider between them, side by side ([Orientation.Horizontal]) or stacked ([Orientation.Vertical]). Drag the
 * divider with a mouse or finger; with keyboard focus on it the arrow keys move it by 4% (Home and End jump to the limits). Screen readers
 * announce it as an adjustable control with the share of the first pane, and can change it.
 *
 * Neither pane gets less than [minFirst] or [minSecond]; when the window is too small for both the first one wins.
 *
 * @param first The first (start or top) pane.
 * @param second The second (end or bottom) pane.
 * @param modifier Modifier applied to the container.
 * @param state The divider position; default is a saved 50/50 split.
 * @param orientation Panes side by side (default) or stacked.
 * @param minFirst Smallest size of the first pane.
 * @param minSecond Smallest size of the second pane.
 * @param dividerDescription What screen readers call the divider.
 */
@Composable
public fun KSplitPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: KSplitPaneState = rememberKSplitPaneState(),
    orientation: Orientation = Orientation.Horizontal,
    minFirst: Dp = 80.dp,
    minSecond: Dp = 80.dp,
    dividerDescription: String = KompoundTheme.strings.resizePanes,
) {
    val horizontal = orientation == Orientation.Horizontal
    val thickness = 8.dp
    // The pixels the panes share, written by the layout and read by the divider to turn a drag distance into a fraction.
    val space = remember { FloatArray(1) { 1f } }
    Layout(
        modifier = modifier,
        content = {
            Box { first() }
            Divider(state, horizontal, dividerDescription, space)
            Box { second() }
        },
    ) { measurables, constraints ->
        val total = if (horizontal) constraints.maxWidth else constraints.maxHeight
        val cross = if (horizontal) constraints.maxHeight else constraints.maxWidth
        val bar = thickness.roundToPx().coerceAtMost(total)
        val shared = (total - bar).coerceAtLeast(0)
        space[0] = shared.toFloat().coerceAtLeast(1f)
        val lo = minFirst.roundToPx().coerceAtMost(shared)
        val hi = (shared - minSecond.roundToPx()).coerceAtLeast(lo)
        val firstSize = (shared * state.fraction).roundToInt().coerceIn(lo, hi)
        val secondSize = shared - firstSize
        fun box(main: Int) = if (horizontal) Constraints.fixed(main, cross) else Constraints.fixed(cross, main)
        val a = measurables[0].measure(box(firstSize))
        val d = measurables[1].measure(box(bar))
        val b = measurables[2].measure(box(secondSize))
        layout(if (horizontal) total else cross, if (horizontal) cross else total) {
            if (horizontal) { a.place(0, 0); d.place(firstSize, 0); b.place(firstSize + bar, 0) }
            else { a.place(0, 0); d.place(0, firstSize); b.place(0, firstSize + bar) }
        }
    }
}

@Composable
private fun Divider(state: KSplitPaneState, horizontal: Boolean, description: String, space: FloatArray) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val line = if (hovered || focused) colors.primary else colors.outlineVariant
    Box(
        Modifier
            .then(if (horizontal) Modifier.fillMaxHeight().width(8.dp) else Modifier.fillMaxWidth().height(8.dp))
            .hoverable(source)
            .focusable(interactionSource = source)
            .semantics {
                contentDescription = description
                stateDescription = "${(state.fraction * 100).roundToInt()}%"
                progressBarRangeInfo = ProgressBarRangeInfo(state.fraction, 0f..1f, 24)
                setProgress { target -> state.fraction = target.coerceIn(0f, 1f); true }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = 0.04f
                when (event.key) {
                    if (horizontal) Key.DirectionLeft else Key.DirectionUp -> state.fraction = (state.fraction - step).coerceIn(0f, 1f)
                    if (horizontal) Key.DirectionRight else Key.DirectionDown -> state.fraction = (state.fraction + step).coerceIn(0f, 1f)
                    Key.MoveHome -> state.fraction = 0f
                    Key.MoveEnd -> state.fraction = 1f
                    else -> return@onKeyEvent false
                }
                true
            }
            .pointerInput(horizontal) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val delta = if (horizontal) drag.x else drag.y
                    state.fraction = (state.fraction + delta / space[0]).coerceIn(0f, 1f)
                }
            }
            .drawBehind {
                val w = 2.dp.toPx()
                if (horizontal) drawRoundRect(line, Offset((size.width - w) / 2f, 0f), Size(w, size.height), CornerRadius(w / 2f))
                else drawRoundRect(line, Offset(0f, (size.height - w) / 2f), Size(size.width, w), CornerRadius(w / 2f))
            },
    )
}
