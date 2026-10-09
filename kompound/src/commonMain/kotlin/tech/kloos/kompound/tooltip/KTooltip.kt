package tech.kloos.kompound.tooltip

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText

/** Which side of its anchor a tooltip prefers; it flips to the other side when there is no room. */
public enum class KTooltipPlacement { Above, Below }

/**
 * Wraps [content] (the anchor) and shows a small label next to it: after the pointer hovers the anchor for
 * [showDelayMillis] (desktop and web), or while the anchor is long-pressed (touch; it hides itself after
 * [longPressDurationMillis]). The label is a visual supplement: give the anchor its own accessibility
 * description, because screen readers do not reach the tooltip text through the anchor.
 *
 * @param text Label of the tooltip.
 * @param modifier Modifier applied to the anchor wrapper.
 * @param enabled When false the tooltip never appears.
 * @param placement Preferred side of the anchor.
 * @param showDelayMillis Hover time before the tooltip appears.
 * @param longPressDurationMillis How long a long-press tooltip stays.
 * @param style Overrides merged over [KTooltipDefaults.style].
 * @param content The anchor.
 */
@Composable
public fun KTooltip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placement: KTooltipPlacement = KTooltipPlacement.Above,
    showDelayMillis: Long = 500,
    longPressDurationMillis: Long = 1_500,
    style: Style = Style,
    content: @Composable () -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var hoverVisible by remember { mutableStateOf(false) }
    var pressVisible by remember { mutableStateOf(false) }
    var pressCount by remember { mutableStateOf(0) }
    val currentLongPress by rememberUpdatedState(longPressDurationMillis)
    val state = remember { MutableStyleState(null) }
    val gap = with(LocalDensity.current) { KTooltipDefaults.Gap.roundToPx() }

    LaunchedEffect(hovered, enabled) {
        if (hovered && enabled) {
            delay(showDelayMillis)
            hoverVisible = true
        } else {
            hoverVisible = false
        }
    }
    LaunchedEffect(pressCount) {
        if (pressCount > 0) {
            pressVisible = true
            delay(currentLongPress)
            pressVisible = false
        }
    }
    Box(
        modifier
            .hoverable(source, enabled)
            .pointerInput(enabled) {
                // observed on the initial pass and never consumed: a clickable anchor (a button) takes the press first,
                // so a detector that waits for an unconsumed press would never see the long press
                if (enabled) awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var moved = 0f
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                            moved += change.positionChange().getDistance()
                            if (!change.pressed || moved > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                        }
                        @Suppress("UNREACHABLE_CODE") true
                    }
                    if (held == null) pressCount++
                }
            },
    ) {
        content()
        if (enabled && (hoverVisible || pressVisible)) {
            Popup(
                popupPositionProvider = remember(placement, gap) { TooltipPositionProvider(placement, gap) },
                properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
            ) {
                Box(
                    Modifier
                        .widthIn(max = 240.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .styleable(state, KTooltipDefaults.style(), style),
                ) { KText(text) }
            }
        }
    }
}

/**
 * Tooltip position: centred horizontally on the anchor, on the preferred side, flipped to the other side
 * when it does not fit and clamped inside the window.
 */
internal fun calculateTooltipPosition(
    anchor: IntRect,
    window: IntSize,
    popup: IntSize,
    placement: KTooltipPlacement,
    gap: Int,
): IntOffset {
    val x = (anchor.center.x - popup.width / 2).coerceIn(0, maxOf(0, window.width - popup.width))
    val above = anchor.top - gap - popup.height
    val below = anchor.bottom + gap
    val fitsAbove = above >= 0
    val fitsBelow = below + popup.height <= window.height
    val y = when (placement) {
        KTooltipPlacement.Above -> if (fitsAbove || !fitsBelow) above else below
        KTooltipPlacement.Below -> if (fitsBelow || !fitsAbove) below else above
    }
    return IntOffset(x, y.coerceIn(0, maxOf(0, window.height - popup.height)))
}

private class TooltipPositionProvider(private val placement: KTooltipPlacement, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        calculateTooltipPosition(anchorBounds, windowSize, popupContentSize, placement, gap)
}

/** Defaults for [KTooltip]. */
public object KTooltipDefaults {
    /** Distance between anchor and tooltip. */
    public val Gap: androidx.compose.ui.unit.Dp = 4.dp

    /** Base style: small rounded label in `inverseSurface` with `inverseOnSurface` text. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val type = MaterialTheme.typography
        return remember(c, shapes, type) {
            Style {
                background(c.inverseSurface)
                contentColor(c.inverseOnSurface)
                textStyle(type.bodySmall.copy(color = c.inverseOnSurface))
                shape(shapes.extraSmall)
                contentPadding(horizontal = 8.dp, vertical = 4.dp)
            }
        }
    }
}
