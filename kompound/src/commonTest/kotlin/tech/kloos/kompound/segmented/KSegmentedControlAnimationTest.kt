package tech.kloos.kompound.segmented

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KSegmentedControlAnimationTest {
    private val pill = Color(0xFF00C853)
    private val scheme = lightColorScheme(secondaryContainer = pill, outline = Color(0xFF888888))

    private fun Color.isPill() = abs(red - pill.red) < 0.15f && abs(green - pill.green) < 0.15f && abs(blue - pill.blue) < 0.15f   // a hover or press layer may tint it

    // y = 4 is inside the pill but above the label text, so only the pill can colour it.
    private fun ImageBitmap.pillAt(x: Int) = toPixelMap()[x, 4].isPill()

    /** Horizontal centre of the highlight, or -1 when there is none. */
    private fun ImageBitmap.pillCentre(): Float {
        val xs = (0 until width).filter { toPixelMap()[it, 4].isPill() }
        return if (xs.isEmpty()) -1f else xs.average().toFloat()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(selected: () -> Int, onSelect: (Int) -> Unit = {}, enabled: Boolean = true) = setContent {
        MaterialTheme(scheme) { KSegmentedControl(listOf("One", "Two", "Three"), selected(), onSelect, Modifier.testTag("c"), enabled = enabled) }
    }

    @Test
    fun theHighlightIsInPlaceOnTheFirstFrameWithoutSlidingIn() = runComposeUiTest {
        var selected by mutableIntStateOf(2)
        show({ selected })
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(16)
        val image = onNodeWithTag("c").captureToImage()
        val third = image.width - image.width / 6
        assertTrue(image.pillAt(third), "pill is not under the initially selected segment")
        assertTrue(!image.pillAt(image.width / 6), "pill also (still) under the first segment")
    }

    @Test
    fun selectingAnotherSegmentSlidesTheHighlightInsteadOfJumping() = runComposeUiTest {
        var selected by mutableIntStateOf(0)
        show({ selected })
        mainClock.advanceTimeBy(300)
        val first = onNodeWithTag("c").captureToImage().width / 6
        mainClock.autoAdvance = false
        selected = 2
        mainClock.advanceTimeBy(30)
        val early = onNodeWithTag("c").captureToImage()
        val third = early.width - early.width / 6
        assertTrue(!early.pillAt(third), "highlight jumped to the new segment at once")
        mainClock.advanceTimeBy(40)
        val middle = onNodeWithTag("c").captureToImage()
        assertTrue(middle.pillCentre() > middle.width * 0.2f && middle.pillCentre() < middle.width * 0.8f, "highlight is not on its way: centre ${middle.pillCentre()} of ${middle.width}")
        mainClock.advanceTimeBy(2_000)
        val end = onNodeWithTag("c").captureToImage()
        assertTrue(end.pillAt(third) && !end.pillAt(first), "highlight did not end under the selected segment")
    }

    @Test
    fun clickingASegmentReportsItAndMovesTheHighlightThere() = runComposeUiTest {
        var selected by mutableIntStateOf(0)
        show({ selected }, { selected = it })
        onNodeWithText("Two").performClick()
        mainClock.advanceTimeBy(2_000)
        val image = onNodeWithTag("c").captureToImage()
        assertTrue(abs(image.pillCentre() - image.width / 2f) < image.width * 0.1f, "highlight not under the clicked middle segment: ${image.pillCentre()} of ${image.width}")
    }

    @Test
    fun emptyOptionsAndOutOfRangeSelectionDoNotCrash() = runComposeUiTest {
        var options by mutableIntStateOf(3)
        setContent { MaterialTheme(scheme) { KSegmentedControl(List(options) { "S$it" }, 7, {}, Modifier.testTag("c")) } }
        options = 0
        waitForIdle()
        options = 2
        waitForIdle()
    }
}
