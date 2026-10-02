package tech.kloos.kompound.keyvalue

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KKeyValueTest {
    private val s = ButtonTestScheme

    private fun androidx.compose.ui.test.ComposeUiTest.pos(text: String) = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun horizontalPutsTheLabelLeftOfTheValueAndFlippedSwaps() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                androidx.compose.foundation.layout.Column {
                    KKeyValue("Voltage", "12.4 V")
                    KKeyValue("Current", "3 A", flipped = true)
                }
            }
        }
        assertTrue(pos("Voltage").right <= pos("12.4 V").left + 1)
        assertTrue(pos("3 A").right <= pos("Current").left + 1)
        assertEquals(pos("Voltage").center.y, pos("12.4 V").center.y, 6f)
    }

    @Test
    fun verticalStacksTheLabelAboveTheValue() = runComposeUiTest {
        setContent { MaterialTheme(s) { KKeyValue("Voltage", "12.4 V", orientation = Orientation.Vertical) } }
        assertTrue(pos("Voltage").bottom <= pos("12.4 V").top + 1)
    }

    @Test
    fun screenReadersGetOneItem() = runComposeUiTest {
        setContent { MaterialTheme(s) { KKeyValue("Voltage", "12.4 V", Modifier.testTag("kv")) } }
        assertEquals("Voltage, 12.4 V", onNodeWithTag("kv").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single())
    }

    @Test
    fun blankLabelsAreOmittedAndSlotValuesWork() = runComposeUiTest {
        setContent { MaterialTheme(s) { KKeyValue("") { KText("custom") } } }
        onNodeWithText("custom").assertExists()
        onNodeWithText("", useUnmergedTree = true).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
class KMetricTest {
    private val s = ButtonTestScheme

    @Test
    fun showsValueAndSmallerUnitInOneText() = runComposeUiTest {
        setContent { MaterialTheme(s) { KMetric("12.4", unit = "V") } }
        val text = onNodeWithText("12.4 V").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.single()
        assertEquals("12.4 V", text.text)
        assertTrue(text.spanStyles.any { it.item.fontSize.value < 1f && it.start == 4 }, "unit not set smaller")
    }

    @Test
    fun noUnitMeansPlainValue() = runComposeUiTest {
        setContent { MaterialTheme(s) { KMetric("42") } }
        onNodeWithText("42").assertExists()
    }

    @Test
    fun blankUnitIsIgnored() = runComposeUiTest {
        setContent { MaterialTheme(s) { KMetric("42", unit = " ") } }
        onNodeWithText("42").assertExists()
    }
}
