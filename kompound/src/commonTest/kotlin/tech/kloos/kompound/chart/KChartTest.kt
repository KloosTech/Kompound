package tech.kloos.kompound.chart

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KChartTest {
    @Test
    fun formatsValuesCompactly() {
        assertEquals("3", formatChartValue(3f))
        assertEquals("0.5", formatChartValue(0.5f))
        assertEquals("1.2k", formatChartValue(1200f))
        assertEquals("2.5M", formatChartValue(2_500_000f))
        assertEquals("-", formatChartValue(Float.NaN))
    }

    @Test
    fun niceTicksCoverTheRangeWithRoundSteps() {
        val t = niceTicks(0f, 87f, 4)
        assertTrue(t.first() <= 0f && t.last() >= 87f, "$t")
        assertEquals(listOf(0f, 50f, 100f), t)
        assertTrue(niceTicks(5f, 5f, 4).size >= 2)
    }

    @Test
    fun sparklineAndChartsAnnounceASummary() = runComposeUiTest {
        setContent {
            MaterialTheme(lightColorScheme()) {
                KSparkline(listOf(1f, 5f, 3f), Modifier.size(80.dp, 24.dp), contentDescription = "Visits")
                KLineChart(listOf(KChartSeries("A", listOf(1f, 2f, 3f))), Modifier.height(160.dp).size(300.dp, 160.dp), categories = listOf("x", "y", "z"), contentDescription = "Line summary")
                KBarChart(listOf(KChartSeries("A", listOf(1f, 2f))), listOf("x", "y"), Modifier.size(300.dp, 160.dp), contentDescription = "Bar summary")
            }
        }
        onNodeWithContentDescription("Visits").assertIsDisplayed()
        onNodeWithContentDescription("Line summary").assertIsDisplayed()
        onNodeWithContentDescription("Bar summary").assertIsDisplayed()
    }

    @Test
    fun generatedSummaryNamesCountAndRange() = runComposeUiTest {
        setContent { MaterialTheme(lightColorScheme()) { KSparkline(listOf(3f, 9f, 6f), Modifier.size(80.dp, 24.dp)) } }
        onNodeWithContentDescription("3 values from 3 to 9", substring = true).assertIsDisplayed()
    }
}
