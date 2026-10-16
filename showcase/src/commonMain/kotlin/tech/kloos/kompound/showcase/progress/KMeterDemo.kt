package tech.kloos.kompound.showcase.progress

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.progress.KMeter
import tech.kloos.kompound.progress.KMeterSegment

private const val Usage_meter_basic = """import tech.kloos.kompound.progress.KMeter
import tech.kloos.kompound.progress.KMeterSegment

// A context window: the rest of `total` is the empty track (free space).
KMeter(
    segments = listOf(KMeterSegment("System", 800f), KMeterSegment("Tools", 2400f), KMeterSegment("History", 9000f)),
    total = 32000f,
)"""

@KompoundDemo(
    id = "meter.basic",
    title = "KMeter",
    description = "Stacked bar that splits a budget (context window, memory, disk) into labelled segments, with a legend.",
    category = KompoundCategory.Feedback,
    tags = ["meter", "gauge", "stacked", "budget", "usage", "segments"],
    since = "0.1.0",
    usage = Usage_meter_basic,
)
@Composable
fun DemoScope.KMeterDemo() {
    val system = floatControl("System", 0f..4000f, 800f)
    val tools = floatControl("Tools", 0f..8000f, 2400f)
    val history = floatControl("History", 0f..20000f, 9000f)
    val total = floatControl("Total", 1000f..40000f, 32000f)
    val legend = boolControl("Legend", true)
    KMeter(
        segments = listOf(KMeterSegment("System", system), KMeterSegment("Tools", tools), KMeterSegment("History", history)),
        total = total,
        showLegend = legend,
        modifier = Modifier.padding(16.dp),
    )
}
