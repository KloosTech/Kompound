package tech.kloos.kompound.showcase.segmented

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.segmented.KSegmentedControl

private const val Usage_segmented_control = """import tech.kloos.kompound.segmented.KSegmentedControl

val ranges = listOf("Day", "Week", "Month")
var selected by remember { mutableIntStateOf(1) }

KSegmentedControl(
    options = ranges,
    selectedIndex = selected,
    onSelectedIndexChange = { selected = it },
)"""

@KompoundDemo(
    id = "segmented.control",
    title = "KSegmentedControl",
    description = "Row of mutually exclusive options with one selected, announced as radio buttons.",
    category = KompoundCategory.Inputs,
    tags = ["segmented", "tabs", "choice", "radio", "switcher"],
    since = "0.1.0",
    usage = Usage_segmented_control,
)
@Composable
fun DemoScope.KSegmentedControlDemo() {
    val enabled = boolControl("Enabled", true)
    val count = floatControl("Segments", 2f..5f, 3f).toInt()
    var selected by remember { mutableIntStateOf(0) }
    val options = List(count) { listOf("Day", "Week", "Month", "Year", "All time")[it] }
    KSegmentedControl(
        options = options,
        selectedIndex = selected.coerceIn(options.indices),
        onSelectedIndexChange = { selected = it },
        modifier = Modifier.padding(16.dp),
        enabled = enabled,
    )
}
