package tech.kloos.kompound.date

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.buttons.ButtonTestScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class KDateFieldTest {
    private val s = ButtonTestScheme
    private val march15 = 1_710_460_800_000L   // 2024-03-15T00:00:00Z
    private val march20 = 1_710_892_800_000L   // 2024-03-20T00:00:00Z

    @Test
    fun isoFormatMatchesKnownDates() {
        assertEquals("1970-01-01", KDateFormat.iso(0L))
        assertEquals("2024-03-15", KDateFormat.iso(march15))
        assertEquals("2020-02-29", KDateFormat.iso(1_582_934_400_000L), "leap day")
        assertEquals("2100-01-01", KDateFormat.iso(4_102_444_800_000L))
        assertEquals("1969-12-31", KDateFormat.iso(-86_400_000L), "before the epoch")
        assertEquals("1969-12-31", KDateFormat.iso(-1L), "last millisecond before the epoch")
        assertEquals("0001-01-01", KDateFormat.iso(-62_135_596_800_000L))
    }

    @Test
    fun showsPlaceholderThenTheFormattedDate() = runComposeUiTest {
        var value by mutableStateOf<Long?>(null)
        setContent { MaterialTheme(s) { KDateField(value, { value = it }, placeholder = "Pick a date") } }
        onNodeWithText("Pick a date", useUnmergedTree = true).assertExists()
        value = march15
        waitForIdle()
        onNodeWithText("2024-03-15", useUnmergedTree = true).assertExists()
    }

    @Test
    fun customFormatterIsUsed() = runComposeUiTest {
        setContent { MaterialTheme(s) { KDateField(march15, {}, formatDate = { "day-$it" }) } }
        onNodeWithText("day-$march15", useUnmergedTree = true).assertExists()
    }

    @Test
    fun clickOpensTheDialogAndConfirmReturnsTheSelectedDate() = runComposeUiTest {
        var result: Long? = -1L
        setContent { MaterialTheme(s) { KDateField(march15, { result = it }, Modifier.testTag("d"), confirmText = "Done") } }
        onNodeWithText("Done").assertDoesNotExist()
        onNodeWithTag("d").performClick()
        waitForIdle()
        onNodeWithText("Done").assertExists().performClick()
        waitForIdle()
        assertEquals(march15, result)
        onNodeWithText("Done").assertDoesNotExist()
    }

    @Test
    fun cancelClosesWithoutChangingTheValue() = runComposeUiTest {
        var calls = 0
        setContent { MaterialTheme(s) { KDateField(march15, { calls++ }, Modifier.testTag("d"), dismissText = "Never mind") } }
        onNodeWithTag("d").performClick()
        waitForIdle()
        onNodeWithText("Never mind").performClick()
        waitForIdle()
        assertEquals(0, calls)
        onNodeWithText("Never mind").assertDoesNotExist()
    }

    @Test
    fun confirmWithNoDateReportsNull() = runComposeUiTest {
        var result: Long? = 5L
        setContent { MaterialTheme(s) { KDateField(null, { result = it }, Modifier.testTag("d")) } }
        onNodeWithTag("d").performClick()
        waitForIdle()
        onNodeWithText("OK").performClick()
        assertNull(result)
    }

    @Test
    fun disabledFieldDoesNotOpenAndLabelIsTheAccessibleName() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KDateField(null, {}, Modifier.testTag("d"), label = "Birthday", enabled = false)
            }
        }
        onNodeWithTag("d").performClick()
        waitForIdle()
        onNodeWithText("OK").assertDoesNotExist()
        // role and label live on the trigger inside the tagged column
        val trigger = onNode(androidx.compose.ui.test.SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals(listOf("Birthday"), trigger.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
        trigger.assertIsNotEnabled()
    }

    @Test
    fun rangeFieldShowsBothDatesAndConfirmReturnsTheRange() = runComposeUiTest {
        var range: Pair<Long?, Long?>? = null
        setContent {
            MaterialTheme(s) { KDateRangeField(march15, march20, { a, b -> range = a to b }, Modifier.testTag("r")) }
        }
        onNodeWithText("2024-03-15 – 2024-03-20", useUnmergedTree = true).assertExists()
        onNodeWithTag("r").performClick()
        waitForIdle()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals(march15 to march20, range)
    }

    @Test
    fun rangeDialogHeadlineShowsTheRangeOnOneLineAndUpdatesWithTheSelection() = runComposeUiTest {
        setContent { MaterialTheme(s) { KDateRangeField(march15, null, { _, _ -> }, Modifier.testTag("r")) } }
        onNodeWithTag("r").performClick()
        waitForIdle()
        // Field shows only the start; the dialog headline shows start and an open end.
        onNodeWithText("2024-03-15 – …", useUnmergedTree = true).assertExists()
    }

    @Test
    fun rangeFieldWithOnlyAStartShowsJustThatDate() = runComposeUiTest {
        setContent { MaterialTheme(s) { KDateRangeField(march15, null, { _, _ -> }, placeholder = "Range") } }
        onNodeWithText("2024-03-15", useUnmergedTree = true).assertExists()
        onNodeWithText("Range", useUnmergedTree = true).assertDoesNotExist()
    }
}
