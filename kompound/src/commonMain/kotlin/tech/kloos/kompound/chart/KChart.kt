package tech.kloos.kompound.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One data series of a chart: a [name] (legend, announcements), its [values] in category order (`Float.NaN` leaves a gap) and an optional
 * [color] (default: the next colour of [KChartDefaults.colors]).
 */
@Immutable
public class KChartSeries(public val name: String, public val values: List<Float>, public val color: Color = Color.Unspecified)

/** Defaults shared by the charts. */
public object KChartDefaults {
    /** Series colours taken from the theme: primary, tertiary, secondary, info, warning, success; the charts cycle through them. */
    @Composable
    public fun colors(): List<Color> {
        val c = MaterialTheme.colorScheme
        val k = KompoundTheme.tokens.colors
        return remember(c, k) { listOf(c.primary, c.tertiary, c.secondary, k.info, k.warning, k.success) }
    }

    /** The default formatting of axis and tooltip values: `1200` → `1.2k`, `0.5` → `0.5`, `3.0` → `3`. */
    public fun formatValue(value: Float): String = formatChartValue(value)
}

/** Formats a number compactly for axes: whole numbers without decimals, thousands as `k`, millions as `M`, at most two decimals. */
internal fun formatChartValue(v: Float): String {
    if (v.isNaN()) return "-"
    val a = abs(v)
    fun trim(x: Float, digits: Int): String {
        val f = 10f.pow(digits)
        val r = (x * f).roundToInt() / f
        return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
    }
    return when {
        a >= 1_000_000f -> trim(v / 1_000_000f, 1) + "M"
        a >= 10_000f -> trim(v / 1000f, 1) + "k"
        a >= 1000f -> trim(v / 1000f, 2) + "k"
        else -> trim(v, 2)
    }
}

/** "Nice" tick values covering [min]..[max] with about [count] steps (steps of 1, 2 or 5 times a power of ten). */
internal fun niceTicks(min: Float, max: Float, count: Int): List<Float> {
    if (!min.isFinite() || !max.isFinite()) return listOf(0f, 1f)
    var lo = min
    var hi = max
    if (lo == hi) { lo -= 1f; hi += 1f }
    val raw = (hi - lo) / count.coerceAtLeast(1)
    val magnitude = 10f.pow(floor(log10(raw)))
    val residual = raw / magnitude
    val step = magnitude * when { residual <= 1f -> 1f; residual <= 2f -> 2f; residual <= 5f -> 5f; else -> 10f }
    val first = floor(lo / step) * step
    val ticks = ArrayList<Float>()
    var t = first
    while (t <= hi + step * 0.0001f && ticks.size < 50) { ticks += (t / step).roundToInt() * step; t += step }
    if (ticks.last() < hi) ticks += ticks.last() + step
    return ticks
}

private fun summary(series: List<KChartSeries>, strings: tech.kloos.kompound.i18n.KompoundStrings): String {
    val all = series.flatMap { it.values }.filter { it.isFinite() }
    val describe = if (all.isEmpty()) "" else strings.chartSummary(all.size, formatChartValue(all.min()), formatChartValue(all.max()))
    return if (series.size > 1) "$describe: ${series.joinToString { it.name }}" else if (series.size == 1 && series[0].name.isNotEmpty()) "${series[0].name}, $describe" else describe
}

// --- sparkline --------------------------------------------------------------------------------------------

/**
 * A tiny trend line without axes, for tables, list rows and cards. Draws [values] as a line (optionally smooth, optionally with a soft
 * area under it) and a dot on the last value. Size it with [modifier] (for example `Modifier.size(96.dp, 28.dp)`).
 *
 * Screen readers hear a summary (`"Chart, 12 values from 3 to 9"`) or [contentDescription].
 *
 * @param values The numbers in order; `Float.NaN` leaves a gap.
 * @param color Line colour; default: the theme's primary.
 * @param strokeWidth Thickness of the line.
 * @param fill Tint the area under the line.
 * @param smooth Round the corners between points.
 * @param showLast Mark the last value with a dot.
 * @param min Value at the bottom; default: the smallest value.
 * @param max Value at the top; default: the largest value.
 * @param contentDescription Replaces the generated summary.
 */
@Composable
public fun KSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.dp,
    fill: Boolean = true,
    smooth: Boolean = false,
    showLast: Boolean = true,
    min: Float? = null,
    max: Float? = null,
    contentDescription: String? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val line = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    val description = contentDescription ?: summary(listOf(KChartSeries("", values)), KompoundTheme.strings)
    Canvas(modifier.defaultMinSize(minWidth = 24.dp, minHeight = 12.dp).semantics { this.contentDescription = description }) {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return@Canvas
        val lo = min ?: finite.min()
        var hi = max ?: finite.max()
        if (hi <= lo) hi = lo + 1f
        val pad = strokeWidth.toPx() + 2.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        fun x(i: Int) = pad + if (values.size <= 1) w / 2f else w * i / (values.size - 1)
        fun y(v: Float) = pad + h * (1f - ((v - lo) / (hi - lo)).coerceIn(0f, 1f))
        val segments = segmentsOf(values)
        for (seg in segments) {
            val path = linePath(seg.map { Offset(x(it), y(values[it])) }, smooth)
            if (fill && seg.size > 1) {
                val area = Path().apply { addPath(path); lineTo(x(seg.last()), size.height - pad); lineTo(x(seg.first()), size.height - pad); close() }
                drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0f)), startY = pad, endY = size.height - pad))
            }
            drawPath(path, line, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val last = values.indexOfLast { it.isFinite() }
        if (showLast && last >= 0) drawCircle(line, strokeWidth.toPx() * 1.6f, Offset(x(last), y(values[last])))
    }
}

/** Runs of consecutive finite values, as lists of indices (NaN splits the line). */
private fun segmentsOf(values: List<Float>): List<List<Int>> {
    val out = ArrayList<List<Int>>()
    var current = ArrayList<Int>()
    values.forEachIndexed { i, v -> if (v.isFinite()) current += i else if (current.isNotEmpty()) { out += current; current = ArrayList() } }
    if (current.isNotEmpty()) out += current
    return out
}

private fun linePath(points: List<Offset>, smooth: Boolean): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    if (!smooth || points.size < 3) { for (i in 1 until points.size) lineTo(points[i].x, points[i].y); return@apply }
    for (i in 1 until points.size) {
        val p0 = points[i - 1]
        val p1 = points[i]
        val mx = (p0.x + p1.x) / 2f
        cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
    }
}

// --- axes, legend, interaction shared by the charts -------------------------------------------------------

private class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

private fun DrawScope.drawAxes(
    plot: Plot, ticks: List<Float>, lo: Float, hi: Float, categories: List<String>, categoryX: (Int) -> Float,
    measurer: TextMeasurer, style: TextStyle, gridColor: Color, format: (Float) -> String,
) {
    fun y(v: Float) = plot.bottom - plot.height * ((v - lo) / (hi - lo))
    for (t in ticks) {
        drawLine(gridColor, Offset(plot.left, y(t)), Offset(plot.right, y(t)), 1.dp.toPx())
        val label = measurer.measure(format(t), style)
        drawText(label, topLeft = Offset(plot.left - label.size.width - 6.dp.toPx(), y(t) - label.size.height / 2f))
    }
    // x labels: show every step-th so they never overlap
    var widest = 0
    val measured = categories.map { measurer.measure(it, style).also { m -> widest = max(widest, m.size.width) } }
    val gap = 8.dp.toPx()
    val step = if (categories.isEmpty()) 1 else max(1, ((widest + gap) / max(1f, plot.width / categories.size)).toInt() + if (((widest + gap) / max(1f, plot.width / categories.size)) % 1f > 0f) 1 else 0)
    measured.forEachIndexed { i, m ->
        if (i % step == 0) drawText(m, topLeft = Offset((categoryX(i) - m.size.width / 2f).coerceIn(plot.left - 4.dp.toPx(), size.width - m.size.width.toFloat()), plot.bottom + 6.dp.toPx()))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(series: List<KChartSeries>, colors: List<Color>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        series.forEachIndexed { i, s ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val dot = s.color.takeIf { it != Color.Unspecified } ?: colors[i % colors.size]
                Canvas(Modifier.size(10.dp)) { drawCircle(dot) }
                KText(s.name, maxLines = 1)
            }
        }
    }
}

private fun DrawScope.drawTooltip(measurer: TextMeasurer, style: TextStyle, lines: List<Pair<String, Color>>, anchorX: Float, plot: Plot, background: Color, border: Color) {
    val measured = lines.map { measurer.measure(it.first, style) to it.second }
    val pad = 6.dp.toPx()
    val dot = 8.dp.toPx()
    val w = (measured.maxOfOrNull { it.first.size.width } ?: 0) + pad * 2 + dot + 4.dp.toPx()
    val lineH = (measured.maxOfOrNull { it.first.size.height } ?: 0).toFloat()
    val h = lineH * measured.size + pad * 2
    val x = if (anchorX + 10.dp.toPx() + w > size.width) anchorX - 10.dp.toPx() - w else anchorX + 10.dp.toPx()
    val y = plot.top
    drawRoundRect(background, Offset(x, y), Size(w, h), CornerRadius(8.dp.toPx()))
    drawRoundRect(border, Offset(x, y), Size(w, h), CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
    measured.forEachIndexed { i, (text, color) ->
        drawCircle(color, dot / 2f, Offset(x + pad + dot / 2f, y + pad + lineH * i + lineH / 2f))
        drawText(text, topLeft = Offset(x + pad + dot + 4.dp.toPx(), y + pad + lineH * i))
    }
}

private fun Modifier.chartInput(count: Int, plotLeft: () -> Float, plotRight: () -> Float, onActive: (Int?) -> Unit): Modifier = this.pointerInput(count) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.first()
            when (event.type) {
                PointerEventType.Move, PointerEventType.Press, PointerEventType.Enter -> {
                    val l = plotLeft()
                    val r = plotRight()
                    val f = ((change.position.x - l) / max(1f, r - l)).coerceIn(0f, 1f)
                    onActive(if (count <= 1) 0 else (f * (count - 1)).roundToInt())
                }
                PointerEventType.Exit -> if (change.type == PointerType.Mouse) onActive(null)
                else -> {}
            }
        }
    }
}

// --- line chart -------------------------------------------------------------------------------------------

/**
 * A line chart with a value axis, optional category labels, a legend and a crosshair with a tooltip that follows the mouse or finger.
 * With the chart focused, the left and right arrow keys move the crosshair and Escape hides it. Screen readers hear a summary, and the
 * values at the crosshair while it moves.
 *
 * @param series The lines; every series should have one value per category (shorter ones just end early).
 * @param modifier Modifier applied to the chart (give it a height).
 * @param categories Labels along the bottom, one per value (`"Mon"`, `"Tue"`); also named in the tooltip.
 * @param yTicks About how many gridlines the value axis has.
 * @param formatValue How axis and tooltip numbers are written.
 * @param showLegend Show the legend (when there is more than one series, or a named one).
 * @param fill Tint the area under each line.
 * @param smooth Round the corners between points.
 * @param interactive Show the crosshair and tooltip.
 * @param minValue Bottom of the axis; default: lowest value rounded to a nice tick (0 when all values are positive).
 * @param maxValue Top of the axis; default: highest value rounded up to a nice tick.
 * @param contentDescription Replaces the generated summary.
 */
@Composable
public fun KLineChart(
    series: List<KChartSeries>,
    modifier: Modifier = Modifier,
    categories: List<String> = emptyList(),
    yTicks: Int = 4,
    formatValue: (Float) -> String = KChartDefaults::formatValue,
    showLegend: Boolean = true,
    fill: Boolean = false,
    smooth: Boolean = false,
    interactive: Boolean = true,
    minValue: Float? = null,
    maxValue: Float? = null,
    contentDescription: String? = null,
) {
    ChartFrame(series, categories, modifier, yTicks, formatValue, showLegend, interactive, minValue, maxValue, contentDescription, bars = false, stacked = false, fill = fill, smooth = smooth)
}

// --- bar chart --------------------------------------------------------------------------------------------

/**
 * A bar chart: one group of bars per category, one bar per series (side by side, or [stacked]), with a value axis, category labels, a
 * legend and a highlight with a tooltip for the hovered or focused category (arrow keys move it). Bars start at zero.
 *
 * @param series The bars of every category: `values[i]` belongs to `categories[i]`.
 * @param categories The category labels (also named in the tooltip).
 * @param stacked Stack the series on top of each other instead of side by side.
 * @param other See [KLineChart].
 */
@Composable
public fun KBarChart(
    series: List<KChartSeries>,
    categories: List<String>,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    yTicks: Int = 4,
    formatValue: (Float) -> String = KChartDefaults::formatValue,
    showLegend: Boolean = true,
    interactive: Boolean = true,
    contentDescription: String? = null,
) {
    ChartFrame(series, categories, modifier, yTicks, formatValue, showLegend, interactive, null, null, contentDescription, bars = true, stacked = stacked, fill = false, smooth = false)
}

@Composable
private fun ChartFrame(
    series: List<KChartSeries>,
    categories: List<String>,
    modifier: Modifier,
    yTicks: Int,
    format: (Float) -> String,
    showLegend: Boolean,
    interactive: Boolean,
    minValue: Float?,
    maxValue: Float?,
    contentDescription: String?,
    bars: Boolean,
    stacked: Boolean,
    fill: Boolean,
    smooth: Boolean,
) {
    remember { KompoundStyles.ensureEnabled() }
    val strings = KompoundTheme.strings
    val colors = KChartDefaults.colors()
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.onSurface)
    val count = max(series.maxOfOrNull { it.values.size } ?: 0, categories.size)
    val finite = series.flatMap { it.values }.filter { it.isFinite() }
    val stackedMax = if (stacked) (0 until count).maxOfOrNull { i -> series.sumOf { (it.values.getOrNull(i)?.takeIf { v -> v.isFinite() && v > 0f } ?: 0f).toDouble() }.toFloat() } else null
    val dataMin = minValue ?: if (bars || finite.none { it < 0f }) min(0f, finite.minOrNull() ?: 0f) else finite.min()
    val dataMax = maxValue ?: (stackedMax ?: finite.maxOrNull() ?: 1f)
    val ticks = remember(dataMin, dataMax, yTicks) { niceTicks(dataMin, if (dataMax <= dataMin) dataMin + 1f else dataMax, yTicks) }
    val lo = minValue ?: ticks.first()
    val hi = maxValue ?: ticks.last()
    var active by remember { mutableIntStateOf(-1) }
    val activeText = if (active in 0 until count) {
        (categories.getOrNull(active)?.let { "$it: " } ?: "") + series.joinToString { "${it.name} ${format(it.values.getOrNull(active) ?: Float.NaN)}" }
    } else null
    val description = contentDescription ?: summary(series, strings)
    var plotLeft = 0f
    var plotRight = 1f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .semantics { this.contentDescription = description; if (activeText != null) stateDescription = activeText }
                .then(if (interactive) Modifier.focusable().onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || count == 0) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionRight -> { active = if (active < 0) 0 else min(count - 1, active + 1); true }
                        Key.DirectionLeft -> { active = if (active < 0) count - 1 else max(0, active - 1); true }
                        Key.Escape -> { active = -1; true }
                        else -> false
                    }
                }.chartInput(count, { plotLeft }, { plotRight }) { active = it ?: -1 } else Modifier),
        ) {
            val axisLabelWidth = ticks.maxOf { measurer.measure(format(it), labelStyle).size.width }
            val bottomLabel = if (categories.isEmpty()) 0f else measurer.measure("Ag", labelStyle).size.height + 10.dp.toPx()
            val plot = Plot(axisLabelWidth + 12.dp.toPx(), 8.dp.toPx(), size.width - 8.dp.toPx(), size.height - bottomLabel - 4.dp.toPx())
            plotLeft = plot.left; plotRight = plot.right
            if (plot.width <= 0f || plot.height <= 0f) return@Canvas
            val slot = plot.width / max(1, count)
            // lines sit on the category centres from edge to edge; bars sit in equal slots
            fun xOf(i: Int) = if (bars) plot.left + slot * (i + 0.5f) else plot.left + if (count <= 1) plot.width / 2f else plot.width * i / (count - 1)
            fun yOf(v: Float) = plot.bottom - plot.height * ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
            drawAxes(plot, ticks, lo, hi, categories, ::xOf, measurer, labelStyle, scheme.outlineVariant.copy(alpha = 0.6f), format)
            if (bars) {
                val groupWidth = slot * 0.7f
                val barWidth = if (stacked) groupWidth else groupWidth / max(1, series.size)
                val zero = yOf(max(0f, lo))
                for (i in 0 until count) {
                    var stackTop = 0f
                    series.forEachIndexed { s, ser ->
                        val v = ser.values.getOrNull(i) ?: return@forEachIndexed
                        if (!v.isFinite()) return@forEachIndexed
                        val color = ser.color.takeIf { it != Color.Unspecified } ?: colors[s % colors.size]
                        val left = if (stacked) xOf(i) - groupWidth / 2f else xOf(i) - groupWidth / 2f + barWidth * s
                        val top: Float
                        val bottom: Float
                        if (stacked) { bottom = yOf(stackTop); stackTop += max(0f, v); top = yOf(stackTop) } else { top = yOf(max(v, 0f)); bottom = if (v >= 0f) zero else yOf(v) }
                        val dim = if (active >= 0 && active != i) 0.45f else 1f
                        drawRoundRect(color.copy(alpha = dim), Offset(left + 1.dp.toPx(), min(top, bottom)), Size(max(1f, barWidth - 2.dp.toPx()), abs(bottom - top)), CornerRadius(3.dp.toPx()))
                    }
                }
            } else {
                series.forEachIndexed { s, ser ->
                    val color = ser.color.takeIf { it != Color.Unspecified } ?: colors[s % colors.size]
                    for (seg in segmentsOf(ser.values)) {
                        val pts = seg.map { Offset(xOf(it), yOf(ser.values[it])) }
                        val path = linePath(pts, smooth)
                        if (fill && pts.size > 1) {
                            val area = Path().apply { addPath(path); lineTo(pts.last().x, plot.bottom); lineTo(pts.first().x, plot.bottom); close() }
                            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f)), startY = plot.top, endY = plot.bottom))
                        }
                        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        if (pts.size == 1) drawCircle(color, 3.dp.toPx(), pts[0])
                    }
                }
            }
            if (active in 0 until count) {
                val x = xOf(active)
                if (!bars) {
                    drawLine(scheme.outline, Offset(x, plot.top), Offset(x, plot.bottom), 1.dp.toPx())
                    series.forEachIndexed { s, ser ->
                        val v = ser.values.getOrNull(active)?.takeIf { it.isFinite() } ?: return@forEachIndexed
                        val color = ser.color.takeIf { it != Color.Unspecified } ?: colors[s % colors.size]
                        drawCircle(scheme.surface, 5.dp.toPx(), Offset(x, yOf(v)))
                        drawCircle(color, 3.5.dp.toPx(), Offset(x, yOf(v)))
                    }
                }
                val lines = buildList {
                    categories.getOrNull(active)?.let { add(it to Color.Transparent) }
                    series.forEachIndexed { s, ser -> add("${ser.name}  ${format(ser.values.getOrNull(active) ?: Float.NaN)}" to (ser.color.takeIf { it != Color.Unspecified } ?: colors[s % colors.size])) }
                }
                drawTooltip(measurer, tipStyle, lines, x, plot, scheme.surfaceContainerHighest, scheme.outlineVariant)
            }
        }
        if (showLegend && (series.size > 1 || series.firstOrNull()?.name?.isNotEmpty() == true)) Legend(series, colors)
    }
}
