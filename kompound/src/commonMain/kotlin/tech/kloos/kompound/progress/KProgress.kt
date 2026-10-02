package tech.kloos.kompound.progress

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.fillWidth
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import kotlin.math.roundToInt

/**
 * Horizontal progress bar.
 *
 * @param progress A fraction from 0 to 1 for determinate progress, or `null` for an indeterminate bar
 * whose indicator keeps sliding. Announced to screen readers as a progress bar with its value.
 * @param modifier Modifier applied to the track.
 * @param style Overrides merged over [KProgressDefaults.trackStyle] (4dp pill, full width).
 * @param color Colour of the indicator; unspecified means `primary`.
 */
@Composable
public fun KLinearProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    style: Style = Style,
    color: Color = Color.Unspecified,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val indicator = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    val info = if (progress == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
    Box(
        modifier.semantics { progressBarRangeInfo = info }.styleable(state, KProgressDefaults.trackStyle(), style).clip(CircleShape),
    ) {
        if (progress != null) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(indicator))
        } else {
            SlidingIndicator(indicator)
        }
    }
}

@Composable
private fun SlidingIndicator(color: Color) {
    val transition = rememberInfiniteTransition()
    // Read inside the layout lambda below, so the animation never recomposes this function.
    val position = transition.animateFloat(
        initialValue = -0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
    )
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
        val trackWidth = constraints.maxWidth
        Box(
            Modifier
                .offset { IntOffset((position.value * trackWidth).roundToInt(), 0) }
                .width(with(androidx.compose.ui.platform.LocalDensity.current) { (trackWidth * 0.4f).toDp() })
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * Circular progress or spinner.
 *
 * @param progress A fraction from 0 to 1 for determinate progress (the arc grows clockwise from the top),
 * or `null` for an indeterminate spinner.
 * @param modifier Modifier applied to the indicator.
 * @param size Diameter.
 * @param strokeWidth Thickness of the ring.
 * @param color Colour of the arc; unspecified means `primary`. Colours are parameters, not Style, because
 * the ring is drawn on a canvas.
 * @param trackColor Colour of the ring behind the arc; unspecified means `surfaceContainerHighest`.
 */
@Composable
public fun KCircularProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
) {
    val scheme = MaterialTheme.colorScheme
    val arc = if (color == Color.Unspecified) scheme.primary else color
    val track = if (trackColor == Color.Unspecified) scheme.surfaceContainerHighest else trackColor
    val info = if (progress == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
    val rotation = if (progress == null) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        )
    } else null
    Canvas(modifier.size(size).semantics { progressBarRangeInfo = info }) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val inset = strokeWidth.toPx() / 2f
        val arcSize = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
        val topLeft = Offset(inset, inset)
        drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        if (progress != null) {
            drawArc(arc, -90f, 360f * progress.coerceIn(0f, 1f), useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        } else {
            drawArc(arc, -90f + (rotation?.value ?: 0f), 100f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        }
    }
}

/** Defaults for [KLinearProgress]. */
public object KProgressDefaults {
    /** Track: 4dp tall pill filling the available width, in `surfaceContainerHighest`. */
    @Composable
    public fun trackStyle(): Style {
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        return remember(track) { Style { background(track); shape(CircleShape); fillWidth(); height(4.dp) } }
    }
}
