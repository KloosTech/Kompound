package tech.kloos.kompound.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.near
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KButtonLoadingTest {
    private val s = ButtonTestScheme

    @Test
    fun loadingButtonIgnoresClicksButKeepsTheEnabledLook() = runComposeUiTest {
        mainClock.autoAdvance = false
        var clicks = 0
        setContent { MaterialTheme(s) { KButton(onClick = { clicks++ }, Modifier.testTag("b"), loading = true) { KText("MMMM") } } }
        mainClock.advanceTimeByFrame()
        // sample before clicking: the pointer arriving over the button adds the normal hover layer
        assertTrue(onNodeWithTag("b").captureToImage().topCentre().near(s.primary), "still the normal filled look, not the disabled one")
        onNodeWithTag("b").performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun loadingKeepsTheSizeHidesTheLabelAndShowsASpinner() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            MaterialTheme(s) {
                Column {
                    KButton(onClick = {}, Modifier.testTag("idle")) { KText("MMMMMMMM") }
                    KButton(onClick = {}, Modifier.testTag("busy"), loading = true) { KText("MMMMMMMM") }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        assertEquals(onNodeWithTag("idle").fetchSemanticsNode().size, onNodeWithTag("busy").fetchSemanticsNode().size)
        assertTrue(onNodeWithTag("idle").captureToImage().containsColor(s.onPrimary), "label visible when idle")
        // while loading the spinner uses the same colour but the label glyphs are gone: the spinner is tiny
        val busy = onNodeWithTag("busy").captureToImage()
        val spinnerPixels = (0 until busy.width).sumOf { x -> (0 until busy.height).count { y -> busy.toPixelMap()[x, y].near(s.onPrimary, 0.1f) } }
        val idle = onNodeWithTag("idle").captureToImage()
        val labelPixels = (0 until idle.width).sumOf { x -> (0 until idle.height).count { y -> idle.toPixelMap()[x, y].near(s.onPrimary, 0.1f) } }
        assertTrue(spinnerPixels < labelPixels, "spinner ($spinnerPixels px) should cover less than the label ($labelPixels px)")
        onNodeWithTag("busy").assertExists()
        val indeterminate = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)
        onAllNodes(indeterminate, useUnmergedTree = true).fetchSemanticsNodes().also { assertEquals(1, it.size, "exactly one spinner") }
    }

    @Test
    fun notLoadingStillClicks() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(s) { KButton(onClick = { clicks++ }, Modifier.testTag("b"), loading = false) { KText("x") } } }
        onNodeWithTag("b").performClick()
        assertFalse(clicks == 0)
    }
}
