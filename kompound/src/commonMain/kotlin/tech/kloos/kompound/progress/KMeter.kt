package tech.kloos.kompound.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.fillWidth
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

/**
 * One part of a [KMeter].
 *
 * @property label Name, shown in the legend and read by screen readers.
 * @property value Amount in the meter's unit (tokens, bytes, MB).
 * @property color Fill; [Color.Unspecified] takes the next colour of [KMeterDefaults.colors].
 */
@Immutable
public class KMeterSegment(public val label: String, public val value: Float, public val color: Color = Color.Unspecified)

/**
 * Stacked bar showing how a budget is split: a context window into system prompt, tools, history and free space,
 * a disk into folders, VRAM into models. Segments are drawn in order, left to right, each as wide as its share of [total].
 *
 * @param segments The parts in order; negative values count as 0.
 * @param modifier Modifier applied to the bar and legend column.
 * @param total The size of the whole; `null` means the sum of the segments. When it is larger than the sum, the rest is the empty track
 * (free space); when smaller, segments are scaled down to fit.
 * @param showLegend Show a swatch, label and value under the bar for every segment.
 * @param formatValue Formats a value for the legend.
 * @param style Overrides merged over [KMeterDefaults.trackStyle] (the bar).
 */
@Composable
public fun KMeter(
    segments: List<KMeterSegment>,
    modifier: Modifier = Modifier,
    total: Float? = null,
    showLegend: Boolean = true,
    formatValue: (Float) -> String = KMeterDefaults::formatValue,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val palette = KMeterDefaults.colors()
    val values = segments.map { it.value.coerceAtLeast(0f) }
    val sum = values.sum()
    val whole = (total ?: sum).let { if (it <= 0f) 1f else it }
    val colors = segments.mapIndexed { i, s -> if (s.color == Color.Unspecified) palette[i % palette.size] else s.color }
    val summary = segments.joinToString(", ") { it.label + " " + formatValue(it.value) } + " / " + formatValue(whole)
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = summary }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().styleable(state, KMeterDefaults.trackStyle(), style).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            values.forEachIndexed { i, v ->
                // The weight of a segment is its share of the whole; a trailing spacer takes what is left over.
                val share = (v / maxOf(whole, sum)).coerceIn(0f, 1f)
                if (share > 0f) Box(Modifier.weight(share).fillMaxHeight().background(colors[i]))
            }
            val rest = 1f - (sum / maxOf(whole, sum)).coerceIn(0f, 1f)
            if (rest > 0.0001f) Box(Modifier.weight(rest).fillMaxHeight())
        }
        if (showLegend) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                segments.forEachIndexed { i, s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i]))
                        KText(s.label + " " + formatValue(s.value), maxLines = 1, style = KMeterDefaults.legendStyle())
                    }
                }
            }
        }
    }
}

/** Defaults for [KMeter]. */
public object KMeterDefaults {
    /** Bar: 8dp tall pill in `surfaceContainerHighest`, full width. */
    @Composable
    public fun trackStyle(): Style {
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        return remember(track) { Style { background(track); shape(CircleShape); fillWidth(); height(8.dp) } }
    }

    /** Legend text: `labelMedium` in `onSurfaceVariant`. */
    @Composable
    public fun legendStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurfaceVariant); textStyle(type.labelMedium.copy(color = c.onSurfaceVariant)) } }
    }

    /** Colours for segments without their own: primary, tertiary, secondary, then the success, warning and info colours. */
    @Composable
    public fun colors(): List<Color> {
        val c = MaterialTheme.colorScheme
        val k = KompoundTheme.tokens.colors
        return listOf(c.primary, c.tertiary, c.secondary, k.success, k.warning, k.info)
    }

    /** `1.2k`, `3.4M`, `12`, `0.5`: compact number for the legend. */
    public fun formatValue(value: Float): String = when {
        value >= 1_000_000f -> trim(value / 1_000_000f) + "M"
        value >= 1_000f -> trim(value / 1_000f) + "k"
        else -> trim(value)
    }

    private fun trim(v: Float): String {
        val tenths = kotlin.math.round(v * 10f).toLong()
        return if (tenths % 10L == 0L) (tenths / 10L).toString() else (tenths / 10L).toString() + "." + (tenths % 10L)
    }
}
