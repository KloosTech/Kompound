package tech.kloos.kompound.showcase.keyvalue

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.badge.KBadgeTone
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.keyvalue.KKeyValue
import tech.kloos.kompound.keyvalue.KMetric

private const val Usage_keyvalue_basic = """import androidx.compose.foundation.gestures.Orientation
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.badge.KBadgeTone
import tech.kloos.kompound.keyvalue.KKeyValue
import tech.kloos.kompound.keyvalue.KMetric

// A label with its value; one item for screen readers.
KKeyValue(label = "Firmware", value = "2.4.1")
KKeyValue(label = "Status", value = "Charging", orientation = Orientation.Vertical)

// Any content as the value.
KKeyValue(label = "Health") { KBadge("Good", tone = KBadgeTone.Success) }

// A number with a smaller unit.
KMetric(value = "12.4", unit = "V")"""

@KompoundDemo(
    id = "keyvalue.basic",
    title = "KKeyValue",
    description = "A label with its value, side by side or stacked, plus KMetric for a number with a small unit.",
    category = KompoundCategory.Display,
    tags = ["key", "value", "label", "detail", "metric", "unit", "properties"],
    since = "0.1.0",
    usage = Usage_keyvalue_basic,
)
@Composable
fun DemoScope.KKeyValueDemo() {
    val vertical = boolControl("Vertical", false)
    val flipped = boolControl("Flipped (value first)", false)
    val label = textControl("Label", "Voltage")
    val value = textControl("Value", "12.4 V")
    val orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KKeyValue(label, value, orientation = orientation, flipped = flipped)
        KKeyValue("Firmware", "2.4.1", orientation = orientation, flipped = flipped)
        KKeyValue("Health", orientation = orientation, flipped = flipped) { KBadge("Good", tone = KBadgeTone.Success) }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            KMetric("12.4", unit = "V")
            KMetric("3.2", unit = "A")
            KMetric("87", unit = "%")
        }
    }
}
