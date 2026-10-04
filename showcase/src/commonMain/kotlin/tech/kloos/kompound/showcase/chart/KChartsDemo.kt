package tech.kloos.kompound.showcase.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.chart.KBarChart
import tech.kloos.kompound.chart.KChartSeries
import tech.kloos.kompound.chart.KLineChart
import tech.kloos.kompound.chart.KSparkline
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText

private const val Usage_charts = """import tech.kloos.kompound.chart.KBarChart
import tech.kloos.kompound.chart.KChartSeries
import tech.kloos.kompound.chart.KLineChart
import tech.kloos.kompound.chart.KSparkline

KSparkline(listOf(3f, 5f, 4f, 8f, 6f, 9f), Modifier.size(96.dp, 28.dp))

KLineChart(
    series = listOf(KChartSeries("Visits", listOf(120f, 180f, 150f, 240f, 210f))),
    categories = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
    modifier = Modifier.fillMaxWidth().height(220.dp), // give charts a height
    smooth = true, fill = true,
)

KBarChart(
    series = listOf(KChartSeries("2025", listOf(4f, 6f, 5f)), KChartSeries("2026", listOf(5f, 7f, 8f))),
    categories = listOf("Q1", "Q2", "Q3"),
    modifier = Modifier.fillMaxWidth().height(220.dp),
)"""

private val Days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@KompoundDemo(
    id = "chart.simple",
    title = "KSparkline, KLineChart, KBarChart",
    description = "Small themed charts with axes, legend, hover or touch tooltips and keyboard navigation; screen readers get a summary.",
    category = KompoundCategory.Data,
    tags = ["chart", "graph", "sparkline", "line", "bar", "plot", "trend"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_charts,
)
@Composable
fun DemoScope.KChartsDemo() {
    val smooth = boolControl("Smooth", true)
    val fill = boolControl("Fill", true)
    val stacked = boolControl("Stacked bars", false)
    val interactive = boolControl("Interactive", true)
    val visits = listOf(120f, 180f, 150f, 240f, 210f, 300f, 280f)
    val signups = listOf(30f, 45f, 38f, 70f, 64f, 90f, 85f)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KText("Visits")
            KSparkline(visits, Modifier.size(96.dp, 28.dp), smooth = smooth, fill = fill)
        }
        KLineChart(
            listOf(KChartSeries("Visits", visits), KChartSeries("Sign-ups", signups)),
            Modifier.fillMaxWidth().height(240.dp), categories = Days, smooth = smooth, fill = fill, interactive = interactive,
        )
        KBarChart(
            listOf(KChartSeries("Visits", visits), KChartSeries("Sign-ups", signups)),
            Days, Modifier.fillMaxWidth().height(240.dp), stacked = stacked, interactive = interactive,
        )
    }
}
