package tech.kloos.kompound.slider

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.size
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme
import kotlin.math.roundToInt

/**
 * Slider for choosing a number in [valueRange] by dragging, tapping the track or using the keyboard
 * (arrow keys, Home, End; Shift for bigger steps). With [steps] > 0 the value snaps to evenly spaced positions.
 * Exposed to screen readers as a progress range with a set-progress action. Mirrors in right-to-left layouts.
 *
 * @param value Current value; coerced into [valueRange].
 * @param onValueChange Called continuously while the user drags or taps.
 * @param modifier Modifier applied to the slider (48dp tall, full width by default).
 * @param valueRange Smallest and largest value.
 * @param steps Number of snap positions strictly between the ends; `0` means continuous.
 * @param enabled When false the slider ignores input and uses the disabled style blocks.
 * @param onValueChangeFinished Called once when a drag or tap ends, and after a key press.
 * @param trackStyle Overrides merged over [KSliderDefaults.trackStyle] (the inactive track).
 * @param activeTrackStyle Overrides merged over [KSliderDefaults.activeTrackStyle] (the part up to the thumb).
 * @param thumbStyle Overrides merged over [KSliderDefaults.thumbStyle] (the thumb; the halo uses [KSliderDefaults.haloStyle]).
 * @param interactionSource Feeds pressed/hovered/focused state into the styles.
 */
@Composable
public fun KSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    trackStyle: Style = Style,
    activeTrackStyle: Style = Style,
    thumbStyle: Style = Style,
    interactionSource: MutableInteractionSource? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
    val coerced = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val currentChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val thumbDiameter = with(density) { KSliderDefaults.ThumbSize.toPx() }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(KSliderDefaults.Height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(coerced, valueRange, steps)
                setProgress { target ->
                    if (!enabled) false else { currentChange(snapToStep(target, valueRange, steps)); true }
                }
            }
            .hoverable(source, enabled)
            .focusable(enabled, source)
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val unit = if (steps > 0) (valueRange.endInclusive - valueRange.start) / (steps + 1) else (valueRange.endInclusive - valueRange.start) / 100f
                val big = unit * if (event.isShiftPressed) 10f else 1f
                val next = when (event.key) {
                    Key.DirectionRight -> coerced + if (rtl) -big else big
                    Key.DirectionUp -> coerced + big
                    Key.DirectionLeft -> coerced + if (rtl) big else -big
                    Key.DirectionDown -> coerced - big
                    Key.PageUp -> coerced + unit * 10f
                    Key.PageDown -> coerced - unit * 10f
                    Key.MoveHome -> valueRange.start
                    Key.MoveEnd -> valueRange.endInclusive
                    else -> return@onKeyEvent false
                }
                currentChange(snapToStep(next, valueRange, steps))
                currentFinished?.invoke()
                true
            }
            .pointerInput(enabled, valueRange, steps, rtl, thumbDiameter) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val press = PressInteraction.Press(down.position)
                    source.tryEmit(press)
                    val width = size.width.toFloat()
                    fun report(x: Float) = currentChange(valueFromPosition(x, width, thumbDiameter, valueRange, steps, rtl))
                    report(down.position.x)
                    down.consume()
                    val completed = drag(down.id) { change -> report(change.position.x); change.consume() }
                    source.tryEmit(if (completed) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                    currentFinished?.invoke()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val fraction = if (valueRange.endInclusive == valueRange.start) 0f else (coerced - valueRange.start) / (valueRange.endInclusive - valueRange.start)
        val travel = (widthPx - thumbDiameter).coerceAtLeast(0f)
        // Layout is written for left-to-right: the container and `offset` mirror themselves in RTL.
        val thumbCentre = thumbDiameter / 2f + travel * fraction
        val trackState = remember { MutableStyleState(null) }

        Box(Modifier.fillMaxWidth().styleable(trackState, KSliderDefaults.trackStyle(), trackStyle))
        // Active part: from the start edge to the thumb centre.
        Box(
            Modifier
                .width(with(density) { thumbCentre.toDp() })
                .styleable(state, KSliderDefaults.activeTrackStyle(), activeTrackStyle),
        )
        // Halo (40dp) with the thumb (20dp) centred in it, positioned by the thumb centre.
        Box(
            Modifier
                .offset { IntOffset((thumbCentre - with(density) { KSliderDefaults.HaloSize.toPx() } / 2f).roundToInt(), 0) }
                .styleable(state, KSliderDefaults.haloStyle()),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.styleable(state, KSliderDefaults.thumbStyle(), thumbStyle))
        }
    }
}

/** Maps a pointer x position to a value (accounts for the thumb radius at both ends, RTL and [steps]). */
internal fun valueFromPosition(
    x: Float,
    width: Float,
    thumbDiameter: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    rtl: Boolean,
): Float {
    val travel = (width - thumbDiameter).coerceAtLeast(1f)
    val raw = ((x - thumbDiameter / 2f) / travel).coerceIn(0f, 1f)
    val fraction = if (rtl) 1f - raw else raw
    return snapToStep(range.start + fraction * (range.endInclusive - range.start), range, steps)
}

/** Clamps [value] into [range] and, when [steps] > 0, snaps it to the nearest of `steps + 2` evenly spaced positions. */
internal fun snapToStep(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    if (steps <= 0) return clamped
    val intervals = steps + 1
    val span = range.endInclusive - range.start
    val index = ((clamped - range.start) / span * intervals).roundToInt().coerceIn(0, intervals)
    return range.start + span * index / intervals
}

/** Defaults for [KSlider]. */
public object KSliderDefaults {
    /** Height (touch area) of the slider. */
    public val Height: androidx.compose.ui.unit.Dp = 48.dp

    /** Diameter of the thumb. */
    public val ThumbSize: androidx.compose.ui.unit.Dp = 20.dp

    /** Diameter of the halo behind the thumb that shows hovered, focused and pressed state layers. */
    public val HaloSize: androidx.compose.ui.unit.Dp = 40.dp

    /** Inactive track: 4dp pill in `surfaceContainerHighest`. */
    @Composable
    public fun trackStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            Style { background(c.surfaceContainerHighest); shape(CircleShape); height(4.dp) }
        }
    }

    /** Active track (start edge to thumb): 4dp pill in `primary`, dimmed when disabled. */
    @Composable
    public fun activeTrackStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            Style { background(c.primary); shape(CircleShape); height(4.dp); disabled { background(c.onSurface.copy(alpha = l.disabledContent)) } }
        }
    }

    /** Halo behind the thumb: transparent 40dp circle that tints on hover, focus and press. */
    @Composable
    public fun haloStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            Style {
                shape(CircleShape)
                size(40.dp)
                hovered { background(c.primary.copy(alpha = l.hovered)) }
                focused { background(c.primary.copy(alpha = l.focused)) }
                pressed { background(c.primary.copy(alpha = l.pressed)) }
            }
        }
    }

    /** Thumb: 20dp `primary` circle, dimmed when disabled. */
    @Composable
    public fun thumbStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            Style { shape(CircleShape); size(20.dp); background(c.primary); disabled { background(c.onSurface.copy(alpha = l.disabledContent)) } }
        }
    }
}
