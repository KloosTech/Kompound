package tech.kloos.kompound.buttons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import kotlin.math.roundToInt

/**
 * A button that is also its own progress bar: while [progress] is set a coloured fill sweeps across the
 * button behind a label showing the percentage; the label switches colour where the fill passes it, so it
 * stays readable on both sides. With `progress = null` it is an ordinary filled button showing [text].
 * Use it for uploads, downloads and other actions that report progress.
 *
 * Starting a task snaps the fill to empty before it grows, so it never drains backwards.
 *
 * @param text Label while idle.
 * @param progress Fraction from 0 to 1, or `null` while no task runs.
 * @param onClick Called on click; ignored while running unless [disableWhileInProgress] is false.
 * @param modifier Modifier applied to the outermost node.
 * @param enabled When false the button ignores input and uses the disabled style block.
 * @param disableWhileInProgress Ignore clicks while [progress] is set (the button does not look disabled).
 * @param progressText Label while running; receives the percentage (0 to 100). Override it to localise.
 * @param color Accent of the fill and the track; the theme's primary by default.
 * @param style Overrides merged over [KProgressButtonDefaults.style].
 * @param interactionSource Feeds pressed/hovered/focused state into the style.
 */
@Composable
public fun KProgressButton(
    text: String,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disableWhileInProgress: Boolean = true,
    progressText: (percent: Int) -> String = { "$it%" },
    color: Color = Color.Unspecified,
    style: Style = Style,
    interactionSource: MutableInteractionSource? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
    val colors = KProgressButtonDefaults.colors(color)

    val fill = remember { Animatable(if (progress == null) 1f else progress.coerceIn(0f, 1f)) }
    var wasRunning by remember { mutableStateOf(progress != null) }
    LaunchedEffect(progress) {
        if (progress == null) {
            wasRunning = false
            fill.animateTo(1f, tween(150, easing = LinearEasing))
        } else {
            if (!wasRunning) fill.snapTo(0f)
            wasRunning = true
            fill.animateTo(progress.coerceIn(0f, 1f), tween(300, easing = LinearEasing))
        }
    }

    val labelStyle = MaterialTheme.typography.labelLarge
    val onTrack = if (enabled) colors.onTrack else colors.disabledContent
    val onFill = if (enabled) colors.onFill else colors.disabledContent
    val running = progress != null
    val label = if (progress != null) progressText((progress.coerceIn(0f, 1f) * 100).roundToInt()) else text
    val clickable = enabled && !(running && disableWhileInProgress)
    Box(
        modifier = modifier
            .hoverable(source, enabled)
            .clickable(interactionSource = source, indication = null, enabled = clickable, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (progress != null) progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) }
            .styleable(state, KProgressButtonDefaults.style(colors), style),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().drawBehind { drawRect(if (enabled) colors.fill else colors.disabledFill, size = Size(size.width * fill.value, size.height)) })
        // Two copies of the label: coloured for the empty part, and for the filled part, clipped to the fill.
        KText(label, LabelPadding, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Style { contentColor(onTrack); textStyle(labelStyle.copy(color = onTrack)) })
        Box(
            Modifier.matchParentSize().clearAndSetSemantics { }.drawWithContent { clipRect(right = size.width * fill.value) { this@drawWithContent.drawContent() } },
            contentAlignment = Alignment.Center,
        ) {
            KText(label, LabelPadding, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Style { contentColor(onFill); textStyle(labelStyle.copy(color = onFill)) })
        }
    }
}

// The padding sits on the labels, not the container, so the fill spans the whole button.
private val LabelPadding = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)

/** Colours of a [KProgressButton]. */
public class KProgressButtonColors(
    public val fill: Color,
    public val track: Color,
    public val onFill: Color,
    public val onTrack: Color,
    public val disabledFill: Color,
    public val disabledContent: Color,
)

/** Defaults for [KProgressButton]. */
public object KProgressButtonDefaults {
    /** Fill, track and label colours for [accent] (the theme's primary when unspecified). */
    @Composable
    public fun colors(accent: Color = Color.Unspecified): KProgressButtonColors {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        val base = if (accent == Color.Unspecified) c.primary else accent
        val onBase = if (accent == Color.Unspecified) c.onPrimary else if (base.luminance() > 0.5f) Color.Black else Color.White
        return remember(base, onBase, c, l) {
            KProgressButtonColors(
                fill = base,
                track = base.copy(alpha = 0.15f).compositeOver(c.surface),
                onFill = onBase,
                onTrack = base,
                disabledFill = c.onSurface.copy(alpha = l.disabledContainer),
                disabledContent = c.onSurface.copy(alpha = l.disabledContent),
            )
        }
    }

    /** Pill shape, 40dp minimum height, the track as background and state layers on hover, focus and press. */
    @Composable
    public fun style(colors: KProgressButtonColors = colors()): Style {
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        return remember(colors, type, l) {
            Style {
                background(colors.track)
                shape(CircleShape)
                clip(true)
                textStyle(type.labelLarge.copy(color = colors.onTrack))
                contentColor(colors.onTrack)
                minHeight(40.dp)
                hovered { background(colors.fill.copy(alpha = l.hovered).compositeOver(colors.track)) }
                focused { background(colors.fill.copy(alpha = l.focused).compositeOver(colors.track)) }
                pressed { background(colors.fill.copy(alpha = l.pressed).compositeOver(colors.track)) }
                disabled { background(colors.disabledFill) }
            }
        }
    }
}
