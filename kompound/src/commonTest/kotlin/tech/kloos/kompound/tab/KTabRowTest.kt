package tech.kloos.kompound.tab

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.hasColorIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KTabRowTest {
    private val scheme = lightColorScheme()
    private val tabs = listOf(KTab("Overview"), KTab("Activity", badge = "3"), KTab("Settings", enabled = false), KTab("Billing"))

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.selected() =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.role() =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)

    @Test
    fun tabsAreAnnouncedAsTabsWithTheirSelectedState() = runComposeUiTest {
        var selected by mutableIntStateOf(1)
        setContent { MaterialTheme(scheme) { KTabRow(tabs, selected, { selected = it }, Modifier.testTag("row")) } }
        waitForIdle()
        assertEquals(Role.Tab, onNodeWithText("Overview").role())
        assertEquals(false, onNodeWithText("Overview").selected())
        assertEquals(true, onNodeWithText("Activity").selected())
        onNodeWithText("3").assertExists()
    }

    @Test
    fun clickingPicksATabAndADisabledTabIsIgnored() = runComposeUiTest {
        var selected by mutableIntStateOf(0)
        val picks = mutableListOf<Int>()
        setContent { MaterialTheme(scheme) { KTabRow(tabs, selected, { picks += it; selected = it }) } }
        onNodeWithText("Billing").performClick()
        onNodeWithText("Settings").performClick()
        waitForIdle()
        assertEquals(listOf(3), picks)
        assertEquals(3, selected)
    }

    @Test
    fun arrowKeysMoveToTheNextEnabledTabAndHomeAndEndJump() = runComposeUiTest {
        var selected by mutableIntStateOf(1)
        val picks = mutableListOf<Int>()
        setContent { MaterialTheme(scheme) { KTabRow(tabs, selected, { picks += it; selected = it }) } }
        onNodeWithText("Activity").requestFocus()
        onNodeWithText("Activity").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(3, selected, "the disabled tab is skipped")
        onNodeWithText("Billing").assertIsFocused()
        onNodeWithText("Billing").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(1, selected, "picks so far: $picks")
        onNodeWithText("Activity").performKeyInput { pressKey(Key.MoveHome) }
        waitForIdle()
        assertEquals(0, selected)
        onNodeWithText("Overview").performKeyInput { pressKey(Key.MoveEnd) }
        waitForIdle()
        assertEquals(3, selected)
    }

    @Test
    fun theUnderlineIsDrawnInThePrimaryColourUnderTheSelectedTab() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KTabRow(tabs, 0, {}, Modifier.testTag("row")) } }
        waitForIdle()
        val image = onNodeWithTag("row").captureToImage().toPixelMap()
        val bounds = onNodeWithText("Overview").fetchSemanticsNode().boundsInRoot
        // the bottom 4px under the first tab contain the primary colour, the bottom under the last tab does not
        assertTrue(image.hasColorIn(scheme.primary, bounds.left.toInt() + 4, bounds.right.toInt() - 4, image.height - 5, image.height - 1), "underline under the selected tab")
        val last = onNodeWithText("Billing").fetchSemanticsNode().boundsInRoot
        assertTrue(!image.hasColorIn(scheme.primary, last.left.toInt() + 4, last.right.toInt() - 4, image.height - 5, image.height - 1), "none under the others")
    }

    @Test
    fun scrollableRowsKeepNaturalWidthAndManyTabsDoNotCrash() = runComposeUiTest {
        val many = List(30) { KTab("Tab number $it") }
        setContent { MaterialTheme(scheme) { KTabRow(many, 12, {}, Modifier.width(300.dp), scrollable = true) } }
        waitForIdle()
        onNodeWithText("Tab number 0").assertExists()
    }

    @Test
    fun anEmptyRowAndAnOutOfRangeSelectionAreHarmless() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KTabRow(emptyList(), 5, {}); KTabRow(listOf(KTab("Only")), 9, {}) } }
        waitForIdle()
        onNodeWithText("Only").assertExists()
    }
}
