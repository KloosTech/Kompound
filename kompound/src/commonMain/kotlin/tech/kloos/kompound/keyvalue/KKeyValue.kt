package tech.kloos.kompound.keyvalue

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText

/**
 * A label with its value: "Voltage 12.4 V" side by side, or stacked with [Orientation.Vertical]. The label is
 * quiet, the value prominent. Screen readers get one item, "label, value".
 *
 * @param label What the value is.
 * @param value The value.
 * @param modifier Modifier applied to the outermost node.
 * @param orientation [Orientation.Horizontal] puts the label before the value, [Orientation.Vertical] above it.
 * @param flipped Puts the value before the label instead.
 * @param style Overrides merged over [KKeyValueDefaults.style] (spacing and container).
 * @param labelStyle Overrides merged over [KKeyValueDefaults.labelStyle].
 * @param valueStyle Overrides merged over [KKeyValueDefaults.valueStyle].
 */
@Composable
public fun KKeyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Horizontal,
    flipped: Boolean = false,
    style: Style = Style,
    labelStyle: Style = Style,
    valueStyle: Style = Style,
) {
    KKeyValue(
        label = label,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
        orientation = orientation,
        flipped = flipped,
        style = style,
        labelStyle = labelStyle,
    ) { KText(value, style = Style(KKeyValueDefaults.valueStyle(), valueStyle)) }
}

/** [KKeyValue] whose value is any content, e.g. a badge or a progress bar. */
@Composable
public fun KKeyValue(
    label: String,
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Horizontal,
    flipped: Boolean = false,
    style: Style = Style,
    labelStyle: Style = Style,
    value: @Composable () -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val labelItem = @Composable { if (label.isNotBlank()) KText(label, style = Style(KKeyValueDefaults.labelStyle(), labelStyle)) }
    val base = modifier.styleable(state, KKeyValueDefaults.style(), style)
    if (orientation == Orientation.Horizontal) {
        Row(base, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (flipped) { value(); labelItem() } else { labelItem(); value() }
        }
    } else {
        Column(base, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (flipped) { value(); labelItem() } else { labelItem(); value() }
        }
    }
}

/** Defaults for [KKeyValue] and [KMetric]. */
public object KKeyValueDefaults {
    /** Container: no decoration. */
    @Composable
    public fun style(): Style = Style

    /** Label: `bodyMedium` in `onSurfaceVariant`. */
    @Composable
    public fun labelStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurfaceVariant); textStyle(type.bodyMedium.copy(color = c.onSurfaceVariant)) } }
    }

    /** Value: `titleMedium` semi-bold in `onSurface`. */
    @Composable
    public fun valueStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurface); textStyle(type.titleMedium.copy(color = c.onSurface, fontWeight = FontWeight.SemiBold)) } }
    }

    /** Text style of a [KMetric] value. */
    @Composable
    public fun metricTextStyle(): TextStyle {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { type.titleLarge.copy(color = c.onSurface, fontWeight = FontWeight.SemiBold) }
    }
}

/**
 * A number with a small unit: "12.4 V". The unit is set smaller and quieter so the value stays the focus.
 *
 * @param value The value text.
 * @param modifier Modifier applied to the outermost node.
 * @param unit Optional unit; omitted when null or blank.
 * @param textStyle Style of the value; the unit derives from it.
 */
@Composable
public fun KMetric(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    textStyle: TextStyle = KKeyValueDefaults.metricTextStyle(),
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val text: AnnotatedString = remember(value, unit, muted) {
        buildAnnotatedString {
            append(value)
            if (!unit.isNullOrBlank()) withStyle(SpanStyle(fontSize = 0.6.em, fontWeight = FontWeight.Normal, color = muted)) { append(" "); append(unit) }
        }
    }
    KText(text, modifier, textStyle = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
