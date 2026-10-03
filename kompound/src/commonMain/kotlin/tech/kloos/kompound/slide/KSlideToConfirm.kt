package tech.kloos.kompound.slide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * A control that asks for a deliberate gesture: drag the handle all the way across to confirm. Use it for
 * actions that must not happen by accident (deleting, paying, factory reset). A short drag springs back.
 *
 * Screen reader and keyboard users cannot drag, so the control also exposes a click action and reacts to
 * Enter and Space while focused; those confirm directly.
 *
 * The handle starts at the leading edge (right in right-to-left layouts). After [onConfirm] ran, the control
 * returns to its start after [resetAfterMillis].
 *
 * @param label Hint on the track, e.g. "Slide to delete".
 * @param onConfirm Called once the handle reached the end and [confirmDelayMillis] passed.
 * @param modifier Modifier applied to the outermost node.
 * @param confirmedLabel Replaces [label] once confirmed, e.g. "Deleting...". `null` keeps [label].
 * @param enabled When false the control ignores drags and uses the disabled style block.
 * @param animated Lets the hint label breathe while idle.
 * @param confirmDelayMillis Time the confirmed state is shown before [onConfirm] is called.
 * @param resetAfterMillis Time after [onConfirm] until the handle returns; `null` keeps it at the end.
 * @param style Overrides merged over [KSlideToConfirmDefaults.style] (the track).
 * @param handleStyle Overrides merged over [KSlideToConfirmDefaults.handleStyle].
 * @param interactionSource Feeds focus state into the style.
 * @param handle The handle's content; a chevron by default.
 */
@Composable
public fun KSlideToConfirm(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmedLabel: String? = null,
    enabled: Boolean = true,
    animated: Boolean = true,
    confirmDelayMillis: Long = 400,
    resetAfterMillis: Long? = 1_500,
    style: Style = Style,
    handleStyle: Style = Style,
    interactionSource: MutableInteractionSource? = null,
    handle: @Composable () -> Unit = { DefaultHandle() },
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val trackState = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
    val handleState = rememberUpdatedStyleState(null) { it.isEnabled = enabled }
    val colors = KSlideToConfirmDefaults.colors()
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sign = if (rtl) -1f else 1f

    var trackWidth by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    var confirmed by remember { mutableStateOf(false) }
    var motion by remember { mutableStateOf<Job?>(null) }   // the one running settle/confirm animation; a new gesture cancels it
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handleSize = with(density) { KSlideToConfirmDefaults.HandleSize.toPx() }
    val inset = with(density) { KSlideToConfirmDefaults.Inset.toPx() }
    val maxOffset = (trackWidth - handleSize - 2 * inset).coerceAtLeast(0f)
    val progress = if (maxOffset > 0f) (offset / maxOffset).coerceIn(0f, 1f) else 0f

    fun confirmNow() {
        if (confirmed || !enabled) return
        confirmed = true
        motion?.cancel()
        motion = scope.launch {
            animate(offset, maxOffset, animationSpec = spring()) { v, _ -> offset = v }
            delay(confirmDelayMillis)
            currentOnConfirm()
            if (resetAfterMillis != null) {
                delay(resetAfterMillis)
                animate(offset, 0f, animationSpec = spring()) { v, _ -> offset = v }
                confirmed = false
            }
        }
    }

    val breathe = remember { Animatable(1f) }
    LaunchedEffect(animated, confirmed, enabled) {
        if (animated && enabled && !confirmed) {
            while (true) {
                breathe.animateTo(0.55f, tween(1_100))
                breathe.animateTo(1f, tween(1_100))
            }
        } else breathe.snapTo(1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(KSlideToConfirmDefaults.TrackHeight)
            .onSizeChanged { trackWidth = it.width }
            .focusable(enabled, source)
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.Spacebar || event.key == Key.NumPadEnter)) { confirmNow(); true } else false
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                if (!enabled) disabled()
                onClick(label = label) { confirmNow(); true }
            }
            .styleable(trackState, KSlideToConfirmDefaults.style(colors), style)
            .clip(CircleShape)
            .drawBehind {
                // The fill follows the handle from the leading edge to the handle's centre; nothing while at rest.
                val reach = if (offset <= 0f) 0f else (inset + handleSize / 2 + offset).coerceAtMost(size.width)
                val left = if (rtl) size.width - reach else 0f
                drawRoundRect(colors.fill.copy(alpha = if (enabled) 1f else 0f), topLeft = androidx.compose.ui.geometry.Offset(left, 0f), size = Size(reach, size.height), cornerRadius = CornerRadius(size.height / 2))
            }
            .draggable(
                state = rememberDraggableState { delta -> if (!confirmed) offset = (offset + delta * sign).coerceIn(0f, maxOffset) },
                orientation = Orientation.Horizontal,
                enabled = enabled && !confirmed,
                onDragStarted = { motion?.cancel() },
                onDragStopped = {
                    if (progress >= KSlideToConfirmDefaults.Threshold) confirmNow()
                    else {
                        motion?.cancel()
                        motion = scope.launch { animate(offset, 0f, animationSpec = spring(dampingRatio = 0.7f)) { v, _ -> offset = v } }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val shown = if (confirmed && confirmedLabel != null) confirmedLabel else label
        val labelColor = if (confirmed || progress > 0.5f) colors.onFill else colors.onTrack
        KText(
            shown,
            Modifier.padding(horizontal = KSlideToConfirmDefaults.HandleSize + 16.dp).graphicsLayer { alpha = if (confirmed) 1f else (breathe.value * (1f - progress * 2f).coerceIn(0f, 1f)) },
            maxLines = 1,
            style = Style { contentColor(labelColor) },
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(KSlideToConfirmDefaults.Inset)
                .graphicsLayer { translationX = offset * sign }
                .size(KSlideToConfirmDefaults.HandleSize)
                .styleable(handleState, KSlideToConfirmDefaults.handleStyle(colors), handleStyle),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalKContentColor provides colors.onHandle) { handle() }
        }
    }
}

// A chevron that points along the direction of travel, also in right-to-left layouts.
@Composable
private fun DefaultHandle() {
    val flip = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    KIcon(KompoundIcons.ChevronRight, contentDescription = null, modifier = Modifier.graphicsLayer { scaleX = flip })
}

/** Colours of a [KSlideToConfirm]. */
public class KSlideToConfirmColors(
    public val track: Color,
    public val fill: Color,
    public val onTrack: Color,
    public val onFill: Color,
    public val handle: Color,
    public val onHandle: Color,
    public val disabledTrack: Color,
    public val disabledContent: Color,
)

/** Defaults for [KSlideToConfirm]. */
public object KSlideToConfirmDefaults {
    /** Height of the track. */
    public val TrackHeight: androidx.compose.ui.unit.Dp = 64.dp

    /** Diameter of the handle. */
    public val HandleSize: androidx.compose.ui.unit.Dp = 52.dp

    /** Gap between the handle and the track edge. */
    public val Inset: androidx.compose.ui.unit.Dp = 6.dp

    /** Fraction of the travel the handle must pass for the drag to confirm when released. */
    public const val Threshold: Float = 0.85f

    /** Colours from the theme: a tinted track that fills with the primary container colour behind a primary handle. */
    @Composable
    public fun colors(): KSlideToConfirmColors {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            KSlideToConfirmColors(
                track = c.primary.copy(alpha = 0.12f).compositeOver(c.surface),
                fill = c.primaryContainer, onTrack = c.primary, onFill = c.onPrimaryContainer,
                handle = c.primary, onHandle = c.onPrimary,
                disabledTrack = c.onSurface.copy(alpha = l.disabledContainer),
                disabledContent = c.onSurface.copy(alpha = l.disabledContent),
            )
        }
    }

    /** Track: pill-shaped, tinted, with a focus ring. */
    @Composable
    public fun style(colors: KSlideToConfirmColors = colors()): Style {
        val type = MaterialTheme.typography
        val scheme = MaterialTheme.colorScheme
        return remember(colors, type, scheme) {
            Style {
                background(colors.track)
                shape(CircleShape)
                textStyle(type.titleMedium.copy(color = colors.onTrack))
                contentColor(colors.onTrack)
                focused { borderWidth(2.dp); borderColor(scheme.primary) }
                disabled { background(colors.disabledTrack); contentColor(colors.disabledContent); textStyle(type.titleMedium.copy(color = colors.disabledContent)) }
            }
        }
    }

    /** Handle: a filled circle. */
    @Composable
    public fun handleStyle(colors: KSlideToConfirmColors = colors()): Style =
        remember(colors) { Style { background(colors.handle); shape(CircleShape); disabled { background(colors.disabledContent) } } }
}
