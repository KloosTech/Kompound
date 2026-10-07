package tech.kloos.kompound.tooltip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KTooltipTest {
    private val s = ButtonTestScheme

    private fun androidx.compose.ui.test.ComposeUiTest.mount(enabled: Boolean = true, delay: Long = 500) {
        mainClock.autoAdvance = false
        setContent {
            MaterialTheme(s) {
                Box(Modifier.size(200.dp)) {
                    KTooltip("Save the file", Modifier.testTag("anchor"), enabled = enabled, showDelayMillis = delay) {
                        Box(Modifier.size(48.dp))
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
    }

    @Test
    fun hoverShowsTheTooltipOnlyAfterTheDelay() = runComposeUiTest {
        mount()
        onNodeWithText("Save the file", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("anchor").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(400)
        onNodeWithText("Save the file", useUnmergedTree = true).assertDoesNotExist()
        mainClock.advanceTimeBy(300)
        onNodeWithText("Save the file", useUnmergedTree = true).assertExists()
    }

    @Test
    fun movingAwayHidesTheTooltipAndCancelsAPendingOne() = runComposeUiTest {
        mount()
        onNodeWithTag("anchor").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(700)
        onNodeWithText("Save the file", useUnmergedTree = true).assertExists()
        onNodeWithTag("anchor").performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(150f, 150f)) }
        mainClock.advanceTimeBy(200)
        onNodeWithText("Save the file", useUnmergedTree = true).assertDoesNotExist()

        onNodeWithTag("anchor").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(300)
        onNodeWithTag("anchor").performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(150f, 150f)) }
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("Save the file", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun longPressShowsTheTooltipThenHidesItselfAfterTheDuration() = runComposeUiTest {
        mount()
        onNodeWithTag("anchor").performTouchInput { longClick() }
        mainClock.advanceTimeBy(100)
        onNodeWithText("Save the file", useUnmergedTree = true).assertExists()
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("Save the file", useUnmergedTree = true).assertExists()
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("Save the file", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun longPressOnAClickableAnchorShowsTheTooltipToo() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            MaterialTheme(s) {
                Box(Modifier.size(200.dp)) {
                    KTooltip("Saves your work", Modifier.testTag("anchor")) {
                        KButton(onClick = {}) { KText("Save") }
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithTag("anchor").performTouchInput { longClick() }
        mainClock.advanceTimeBy(100)
        onNodeWithText("Saves your work", useUnmergedTree = true).assertExists()
    }

    @Test
    fun disabledTooltipNeverAppears() = runComposeUiTest {
        mount(enabled = false)
        onNodeWithTag("anchor").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)
        onNodeWithText("Save the file", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("anchor").performTouchInput { longClick() }
        mainClock.advanceTimeBy(500)
        onNodeWithText("Save the file", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun placementAboveCentresOnTheAnchor() {
        val pos = calculateTooltipPosition(IntRect(100, 100, 148, 148), IntSize(400, 400), IntSize(60, 20), KTooltipPlacement.Above, 4)
        assertEquals(IntOffset(100 + 24 - 30, 100 - 4 - 20), pos)
    }

    @Test
    fun placementFlipsWhenThereIsNoRoom() {
        // no room above (anchor at the top): goes below
        val below = calculateTooltipPosition(IntRect(100, 5, 148, 53), IntSize(400, 400), IntSize(60, 20), KTooltipPlacement.Above, 4)
        assertEquals(53 + 4, below.y)
        // no room below (anchor at the bottom): goes above
        val above = calculateTooltipPosition(IntRect(100, 350, 148, 398), IntSize(400, 400), IntSize(60, 20), KTooltipPlacement.Below, 4)
        assertEquals(350 - 4 - 20, above.y)
    }

    @Test
    fun belowPlacementIsRespectedWhenItFits() {
        val pos = calculateTooltipPosition(IntRect(100, 100, 148, 148), IntSize(400, 400), IntSize(60, 20), KTooltipPlacement.Below, 4)
        assertEquals(148 + 4, pos.y)
    }

    @Test
    fun tooltipIsClampedInsideTheWindowHorizontally() {
        val left = calculateTooltipPosition(IntRect(0, 100, 20, 120), IntSize(400, 400), IntSize(100, 20), KTooltipPlacement.Above, 4)
        assertEquals(0, left.x)
        val right = calculateTooltipPosition(IntRect(380, 100, 400, 120), IntSize(400, 400), IntSize(100, 20), KTooltipPlacement.Above, 4)
        assertEquals(300, right.x)
    }
}
