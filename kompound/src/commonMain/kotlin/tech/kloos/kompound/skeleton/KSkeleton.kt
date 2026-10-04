package tech.kloos.kompound.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier.Node
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tech.kloos.kompound.theme.KompoundTheme

/**
 * Sweeps a soft highlight across the content, left to right, to say "loading". Draw-only: nothing recomposes or re-lays out while it runs,
 * and the animation stops when [enabled] is false or the node leaves the screen. Put it after `background`/`clip` so the highlight
 * follows the shape. [KSkeleton] uses it; use it directly to make your own placeholders.
 *
 * @param enabled Run the animation; `false` draws the content as is.
 * @param highlight Colour of the highlight band (drawn translucent over the content).
 * @param periodMillis Time for one sweep.
 */
public fun Modifier.kShimmer(
    enabled: Boolean = true,
    highlight: Color = Color.White.copy(alpha = 0.35f),
    periodMillis: Int = 1400,
): Modifier = this.then(ShimmerElement(enabled, highlight, periodMillis.coerceAtLeast(200)))

private data class ShimmerElement(val enabled: Boolean, val highlight: Color, val period: Int) : ModifierNodeElement<ShimmerNode>() {
    override fun create(): ShimmerNode = ShimmerNode(enabled, highlight, period)
    override fun update(node: ShimmerNode) = node.update(enabled, highlight, period)
    override fun InspectorInfo.inspectableProperties() {
        name = "kShimmer"
        properties["enabled"] = enabled
        properties["periodMillis"] = period
    }
}

private class ShimmerNode(private var enabled: Boolean, private var highlight: Color, private var period: Int) : Node(), DrawModifierNode {
    private var phase = 0f
    private var job: Job? = null

    override fun onAttach() { restart() }

    override fun onDetach() { job?.cancel(); job = null }

    fun update(enabled: Boolean, highlight: Color, period: Int) {
        val restart = enabled != this.enabled || period != this.period
        this.enabled = enabled; this.highlight = highlight; this.period = period
        if (restart) restart() else invalidateDraw()
    }

    private fun restart() {
        job?.cancel()
        phase = 0f
        job = if (!enabled) null else coroutineScope.launch {
            val start = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                androidx.compose.runtime.withFrameNanos { now ->
                    phase = (((now - start) / 1_000_000L) % period).toFloat() / period
                }
                invalidateDraw()
            }
        }
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!enabled) return
        val band = size.width * 0.6f
        val x = -band + (size.width + band) * phase
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent, 0.5f to highlight, 1f to Color.Transparent,
                start = Offset(x, 0f), end = Offset(x + band, size.height * 0.3f),
            ),
        )
    }
}

/**
 * A grey placeholder block with a shimmer, standing in for content that is still loading (a title, an image, a card). Size it with
 * [modifier]; it is decorative for screen readers: announce "loading" once for the whole area with [KSkeletonGroup], or give a single one a
 * [contentDescription].
 *
 * @param modifier Size and position (`Modifier.fillMaxWidth().height(16.dp)`).
 * @param shape Corner shape; 8dp rounded by default.
 * @param animated Sweep the highlight; `false` shows a still block (for reduced motion or screenshots).
 * @param contentDescription Announced by screen readers; `null` (default) keeps the block silent.
 */
@Composable
public fun KSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .clip(shape)
            .background(KSkeletonDefaults.color())
            .kShimmer(animated, KSkeletonDefaults.highlight())
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier.clearAndSetSemantics { }),
    )
}

/** A circular [KSkeleton] of [size], for an avatar or icon. */
@Composable
public fun KSkeletonCircle(size: Dp = 40.dp, modifier: Modifier = Modifier, animated: Boolean = true) {
    KSkeleton(modifier.size(size), CircleShape, animated)
}

/**
 * Placeholder lines of text: [lines] bars as high as body text, the last one shorter ([lastLineFraction]) like the end of a paragraph.
 */
@Composable
public fun KSkeletonText(
    modifier: Modifier = Modifier,
    lines: Int = 3,
    lastLineFraction: Float = 0.6f,
    lineHeight: Dp = 14.dp,
    spacing: Dp = 8.dp,
    animated: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        repeat(lines.coerceAtLeast(1)) { i ->
            val last = i == lines - 1 && lines > 1
            KSkeleton(Modifier.fillMaxWidth(if (last) lastLineFraction.coerceIn(0.1f, 1f) else 1f).height(lineHeight), RoundedCornerShape(4.dp), animated)
        }
    }
}

/**
 * Groups the skeletons of one loading area: screen readers announce [description] (default "Loading") once, for the whole area, instead of
 * once per block.
 */
@Composable
public fun KSkeletonGroup(
    modifier: Modifier = Modifier,
    description: String = KompoundTheme.strings.loading,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }, content = content)
}

/** Defaults for the skeleton components. */
public object KSkeletonDefaults {
    /** The block colour: `surfaceContainerHighest`, close enough to the surface to stay quiet and different enough to see. */
    @Composable
    public fun color(): Color = MaterialTheme.colorScheme.surfaceContainerHighest

    /** The highlight band: the surface colour, translucent, so it brightens in dark themes and whitens in light ones. */
    @Composable
    public fun highlight(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
}
