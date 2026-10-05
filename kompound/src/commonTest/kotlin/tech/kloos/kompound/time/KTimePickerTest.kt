package tech.kloos.kompound.time

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class KTimePickerTest {
    @Test
    fun timeValueBasics() {
        assertEquals("09:05", KTime(9, 5).toString())
        assertEquals("2:05 PM", KTime(14, 5).format(is24Hour = false))
        assertEquals("12:00 AM", KTime(0, 0).format(is24Hour = false))
        assertEquals(KTime(23, 30), KTime.ofMinutes(-30))
        assertEquals(KTime(1, 0), KTime.ofMinutes(25 * 60))
        assertEquals(KTime(7, 45), KTime.parse("7:45"))
        assertNull(KTime.parse("25:00"))
        assertFailsWith<IllegalArgumentException> { KTime(24, 0) }
    }

    @Test
    fun theArrowButtonsStepHoursAndMinutesAndWrap() = runComposeUiTest {
        var time by mutableStateOf(KTime(23, 59))
        setContent { MaterialTheme(lightColorScheme()) { KTimePicker(time, { time = it }) } }
        onNodeWithContentDescription("Hour +").performClick()
        assertEquals(KTime(0, 59), time)
        onNodeWithContentDescription("Minute +").performClick()
        assertEquals(KTime(0, 0), time, "minutes wrap without touching the hour")
        onNodeWithContentDescription("Minute -").performClick()
        assertEquals(KTime(0, 59), time)
    }

    @Test
    fun minuteStepSnapsToTheStep() = runComposeUiTest {
        var time by mutableStateOf(KTime(10, 7))
        setContent { MaterialTheme(lightColorScheme()) { KTimePicker(time, { time = it }, minuteStep = 15) } }
        onNodeWithContentDescription("Minute +").performClick()
        assertEquals(KTime(10, 15), time, "7 snaps to 0, one step is 15")
    }

    @Test
    fun theTwelveHourClockKeepsAmAndPmWhileStepping() = runComposeUiTest {
        var time by mutableStateOf(KTime(11, 30))
        setContent { MaterialTheme(lightColorScheme()) { KTimePicker(time, { time = it }, is24Hour = false) } }
        onNodeWithText("11").assertIsDisplayed()
        onNodeWithContentDescription("Hour +").performClick()
        assertEquals(KTime(0, 30), time, "11 AM steps to 12 AM (midnight hour), not into the afternoon")
        onNodeWithText("PM").performClick()
        assertEquals(KTime(12, 30), time)
        onNodeWithText("AM").performClick()
        assertEquals(KTime(0, 30), time)
    }

    @Test
    fun theFieldShowsTheFormattedTimeAndOpensADialog() = runComposeUiTest {
        var value by mutableStateOf<KTime?>(KTime(8, 5))
        setContent { MaterialTheme(lightColorScheme()) { KTimeField(value, { value = it }, label = "Start") } }
        onNodeWithText("08:05").assertIsDisplayed()
        onNodeWithText("08:05").performClick()
        waitForIdle()
        onNodeWithContentDescription("Hour +").performClick()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals(KTime(9, 5), value)
    }
}
